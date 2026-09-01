package com.antigravity.shieldx.assistant.system

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import java.util.Locale

/**
 * Detects the "Tarzi" wake word while the app is in the background.
 *
 * Two backends, chosen automatically:
 *  - [Backend.PORCUPINE] when a Picovoice access key and a tarzi.ppn keyword are
 *    present. True always-on hotword spotting, low battery, works screen-off.
 *  - [Backend.SPEECH_RECOGNIZER] otherwise. Needs no key or signup so it works
 *    out of the box, at the cost of more battery and more false negatives.
 *
 * The engine upgrades itself the moment a key is saved - no code change needed.
 */
class WakeWordEngine(
    private val context: Context,
    private val onWakeWord: () -> Unit
) {

    enum class Backend { PORCUPINE, SPEECH_RECOGNIZER, UNAVAILABLE }

    companion object {
        private const val TAG = "TarziWake"
        const val KEYWORD_ASSET = "tarzi.ppn"

        /** The confident spellings, safe for other code to strip from a transcript. */
        val WAKE_NAMES = listOf("tarzi", "tarzee", "tarzy", "tarsi", "tarzie", "tarji")

        /**
         * Speech recognisers mangle uncommon proper nouns badly. These are the
         * renderings of "Tarzi" observed most often in practice, so we accept
         * any of them rather than demanding an exact transcript.
         */
        private val WAKE_VARIANTS = listOf(
            "tarzi", "tarzee", "tarzy", "tarsi", "tarsee", "tarzie",
            "tarz", "tarzan", "starzi", "tarji", "targi", "darzi",
            "the rz", "tar z", "tar zee", "tar see"
        )

        /** True when [transcript] plausibly contains the wake word. */
        fun containsWakeWord(transcript: String): Boolean {
            val normalized = transcript.lowercase(Locale.US).replace(Regex("[^a-z ]"), " ")
            return WAKE_VARIANTS.any { variant ->
                normalized.contains(variant)
            }
        }

        /** Strip the wake word off the front so only the command remains. */
        fun stripWakeWord(transcript: String): String {
            var result = transcript
            for (variant in WAKE_VARIANTS.sortedByDescending { it.length }) {
                result = result.replace(variant, " ", ignoreCase = true)
            }
            return result
                .replace(Regex("\\s+"), " ")
                .trim()
                .trimStart(',', '.', '!', '?')
                .trim()
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private var porcupineManager: Any? = null
    private var recognizer: SpeechRecognizer? = null

    @Volatile
    private var running = false

    @Volatile
    private var paused = false

    var backend: Backend = Backend.UNAVAILABLE
        private set

    // ==========================================================
    // Lifecycle
    // ==========================================================

    /** Begin listening for the wake word. [accessKey] enables the Porcupine path. */
    fun start(accessKey: String?) {
        if (running) return
        if (!hasMicPermission()) {
            Log.w(TAG, "[WAKE_NO_PERMISSION] RECORD_AUDIO not granted")
            backend = Backend.UNAVAILABLE
            return
        }

        running = true
        paused = false

        val keyword = resolveKeywordFile()
        if (!accessKey.isNullOrBlank() && keyword != null) {
            if (startPorcupine(accessKey, keyword)) {
                backend = Backend.PORCUPINE
                Log.i(TAG, "[WAKE_BACKEND] Porcupine hotword active")
                return
            }
            Log.w(TAG, "[WAKE_FALLBACK] Porcupine failed to start, using SpeechRecognizer")
        }

        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            backend = Backend.SPEECH_RECOGNIZER
            Log.i(TAG, "[WAKE_BACKEND] SpeechRecognizer wake word active")
            startRecognizerLoop()
        } else {
            backend = Backend.UNAVAILABLE
            running = false
            Log.e(TAG, "[WAKE_UNAVAILABLE] No speech recognition on this device")
        }
    }

    fun stop() {
        running = false
        stopPorcupine()
        mainHandler.post {
            try {
                recognizer?.cancel()
                recognizer?.destroy()
            } catch (_: Exception) {
            }
            recognizer = null
        }
    }

    /**
     * Suspend detection while Tarzi is capturing a command or speaking, so the
     * assistant never hears itself and re-triggers.
     */
    fun pause() {
        if (!running || paused) return
        paused = true
        if (backend == Backend.PORCUPINE) {
            invokeQuietly(porcupineManager, "stop")
        } else {
            mainHandler.post {
                try {
                    recognizer?.cancel()
                } catch (_: Exception) {
                }
            }
        }
    }

    fun resume() {
        if (!running || !paused) return
        paused = false
        if (backend == Backend.PORCUPINE) {
            invokeQuietly(porcupineManager, "start")
        } else {
            mainHandler.postDelayed({ restartRecognizer() }, 300)
        }
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    // ==========================================================
    // Porcupine backend
    // ==========================================================

    /**
     * The custom "Tarzi" keyword ships as an asset once the user generates it at
     * console.picovoice.ai. Copied out to files/ because Porcupine wants a real path.
     */
    private fun resolveKeywordFile(): String? {
        return try {
            val target = File(context.filesDir, KEYWORD_ASSET)
            if (!target.exists()) {
                val available = context.assets.list("")?.contains(KEYWORD_ASSET) ?: false
                if (!available) return null
                context.assets.open(KEYWORD_ASSET).use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }
            if (target.exists() && target.length() > 0) target.absolutePath else null
        } catch (e: Exception) {
            Log.w(TAG, "[WAKE_KEYWORD_MISSING] " + e.message)
            null
        }
    }

    /**
     * Started reflectively so the app still builds and runs if the Porcupine
     * artifact is absent - the SpeechRecognizer path simply takes over.
     */
    private fun startPorcupine(accessKey: String, keywordPath: String): Boolean {
        return try {
            val builderClass = Class.forName("ai.picovoice.porcupine.PorcupineManager\$Builder")
            val callbackClass = Class.forName("ai.picovoice.porcupine.PorcupineManagerCallback")

            val builder = builderClass.getDeclaredConstructor().newInstance()
            builderClass.getMethod("setAccessKey", String::class.java).invoke(builder, accessKey)
            builderClass.getMethod("setKeywordPath", String::class.java).invoke(builder, keywordPath)
            builderClass.getMethod("setSensitivity", Float::class.javaPrimitiveType)
                .invoke(builder, 0.7f)

            val callback = java.lang.reflect.Proxy.newProxyInstance(
                callbackClass.classLoader,
                arrayOf(callbackClass)
            ) { _, method, _ ->
                if (method.name == "invoke") {
                    handleDetection()
                }
                null
            }

            val manager = builderClass
                .getMethod("build", Context::class.java, callbackClass)
                .invoke(builder, context, callback)

            manager?.javaClass?.getMethod("start")?.invoke(manager)
            porcupineManager = manager
            manager != null
        } catch (e: Throwable) {
            Log.w(TAG, "[WAKE_PORCUPINE_FAIL] " + e.message)
            porcupineManager = null
            false
        }
    }

    private fun stopPorcupine() {
        invokeQuietly(porcupineManager, "stop")
        invokeQuietly(porcupineManager, "delete")
        porcupineManager = null
    }

    private fun invokeQuietly(target: Any?, method: String) {
        if (target == null) return
        try {
            target.javaClass.getMethod(method).invoke(target)
        } catch (_: Throwable) {
        }
    }

    // ==========================================================
    // SpeechRecognizer fallback backend
    // ==========================================================

    private fun startRecognizerLoop() {
        mainHandler.post { restartRecognizer() }
    }

    private fun restartRecognizer() {
        if (!running || paused) return
        try {
            recognizer?.destroy()
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(wakeListener)
            }
            recognizer?.startListening(buildRecognizerIntent())
        } catch (e: Exception) {
            Log.w(TAG, "[WAKE_RESTART_FAIL] " + e.message)
            scheduleRestart(1500)
        }
    }

    private fun buildRecognizerIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }

    private fun scheduleRestart(delayMs: Long) {
        mainHandler.postDelayed({ restartRecognizer() }, delayMs)
    }

    private fun handleDetection() {
        Log.i(TAG, "[WAKE_DETECTED] Tarzi wake word heard")
        pause()
        mainHandler.post { onWakeWord() }
    }

    private val wakeListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            val hits = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                .orEmpty()
            if (hits.any { containsWakeWord(it) }) {
                handleDetection()
            }
        }

        override fun onResults(results: Bundle?) {
            val hits = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                .orEmpty()
            if (hits.any { containsWakeWord(it) }) {
                handleDetection()
            } else {
                scheduleRestart(200)
            }
        }

        override fun onError(error: Int) {
            // NO_MATCH and SPEECH_TIMEOUT are the normal idle case: just listen again.
            val backoff = when (error) {
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 2000L
                SpeechRecognizer.ERROR_CLIENT -> 1000L
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> return
                else -> 400L
            }
            scheduleRestart(backoff)
        }
    }
}
