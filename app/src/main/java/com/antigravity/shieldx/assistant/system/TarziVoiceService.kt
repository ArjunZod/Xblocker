package com.antigravity.shieldx.assistant.system

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.NotificationCompat
import com.antigravity.shieldx.R
import com.antigravity.shieldx.assistant.TarziBrain
import com.antigravity.shieldx.core.security.SecurityManager
import com.antigravity.shieldx.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/** What Tarzi is doing right now, mirrored into the overlay bubble. */
enum class TarziState { OFFLINE, WAITING, LISTENING, THINKING, SPEAKING }

/**
 * Keeps Tarzi resident. This is what makes the assistant answer while the user
 * is in YouTube or anywhere else on the device, rather than only inside the app.
 *
 * The lifecycle is a loop: wait for the wake word, capture one command, think,
 * speak the answer, then go back to waiting.
 */
class TarziVoiceService : Service(), TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "TarziVoice"
        const val ACTION_START = "com.antigravity.shieldx.tarzi.VOICE_START"
        const val ACTION_STOP = "com.antigravity.shieldx.tarzi.VOICE_STOP"
        const val ACTION_LISTEN_NOW = "com.antigravity.shieldx.tarzi.LISTEN_NOW"

        private const val NOTIFICATION_ID = 3001
        private const val CHANNEL_ID = "tarzi_assistant_channel"

        private val _stateFlow = MutableStateFlow(TarziState.OFFLINE)
        val stateFlow: StateFlow<TarziState> = _stateFlow.asStateFlow()

        private val _lastTranscriptFlow = MutableStateFlow("")
        val lastTranscriptFlow: StateFlow<String> = _lastTranscriptFlow.asStateFlow()

        private val _lastReplyFlow = MutableStateFlow("")
        val lastReplyFlow: StateFlow<String> = _lastReplyFlow.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, TarziVoiceService::class.java).apply { action = ACTION_START }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, TarziVoiceService::class.java).apply { action = ACTION_STOP }
            )
        }

        /** Skip the wake word and start capturing a command immediately. */
        fun listenNow(context: Context) {
            val intent = Intent(context, TarziVoiceService::class.java).apply {
                action = ACTION_LISTEN_NOW
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var securityManager: SecurityManager
    private lateinit var brain: TarziBrain

    private var wakeWordEngine: WakeWordEngine? = null
    private var commandRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        securityManager = SecurityManager.getInstance(applicationContext)
        brain = TarziBrain(securityManager)
        tts = TextToSpeech(this, this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                shutdown()
                return START_NOT_STICKY
            }

            ACTION_LISTEN_NOW -> {
                promoteToForeground()
                captureCommand()
                return START_STICKY
            }

            else -> {
                promoteToForeground()
                startWakeWordDetection()
            }
        }
        // Restart if the system kills us: an assistant that silently dies is useless.
        return START_STICKY
    }

    private fun promoteToForeground() {
        val notification = buildNotification("Waiting for \"Tarzi\"")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    // ==========================================================
    // Wake word
    // ==========================================================

    private fun startWakeWordDetection() {
        if (wakeWordEngine != null) return

        val engine = WakeWordEngine(applicationContext) { onWakeWordDetected() }
        wakeWordEngine = engine

        scope.launch {
            val picovoiceKey = securityManager.configRepository.get("picovoice_access_key")
            engine.start(picovoiceKey)
            _stateFlow.value = if (engine.backend == WakeWordEngine.Backend.UNAVAILABLE) {
                TarziState.OFFLINE
            } else {
                TarziState.WAITING
            }
            updateNotification(
                when (engine.backend) {
                    WakeWordEngine.Backend.PORCUPINE -> "Waiting for \"Tarzi\""
                    WakeWordEngine.Backend.SPEECH_RECOGNIZER -> "Waiting for \"Tarzi\" (basic mode)"
                    WakeWordEngine.Backend.UNAVAILABLE -> "Microphone unavailable"
                }
            )
        }
    }

    private fun onWakeWordDetected() {
        Log.i(TAG, "[WAKE] Tarzi summoned")
        playChime()
        captureCommand()
    }

    // ==========================================================
    // Command capture
    // ==========================================================

    private fun captureCommand() {
        wakeWordEngine?.pause()
        _stateFlow.value = TarziState.LISTENING
        updateNotification("Listening...")

        mainHandler.post {
            try {
                commandRecognizer?.destroy()
                commandRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                    setRecognitionListener(commandListener)
                    startListening(
                        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(
                                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                            )
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "[CAPTURE_FAIL] " + e.message)
                returnToWaiting()
            }
        }
    }

    private val commandListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onEndOfSpeech() {
            _stateFlow.value = TarziState.THINKING
            updateNotification("Thinking...")
        }

        override fun onPartialResults(partialResults: Bundle?) {
            partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.let { _lastTranscriptFlow.value = it }
        }

        override fun onResults(results: Bundle?) {
            val spoken = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()

            // The wake word often lands in the same buffer as the command.
            val command = WakeWordEngine.stripWakeWord(spoken).ifBlank { spoken }

            if (command.isBlank()) {
                returnToWaiting()
                return
            }

            _lastTranscriptFlow.value = command
            handleCommand(command)
        }

        override fun onError(error: Int) {
            Log.w(TAG, "[CAPTURE_ERROR] code=" + error)
            if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            ) {
                speak("I did not catch that.")
            }
            returnToWaiting()
        }
    }

    private fun handleCommand(command: String) {
        _stateFlow.value = TarziState.THINKING
        scope.launch {
            val response = try {
                brain.process(command)
            } catch (e: Exception) {
                Log.e(TAG, "[BRAIN_FAIL] " + e.message)
                com.antigravity.shieldx.assistant.BrainResponse("Something went wrong handling that.")
            }
            _lastReplyFlow.value = response.spokenReply
            speak(response.spokenReply)
        }
    }

    // ==========================================================
    // Speech output
    // ==========================================================

    private fun duckMusic(duck: Boolean) {
        scope.launch {
            try {
                securityManager.musicController.duck(duck)
            } catch (_: Exception) {}
        }
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            Log.w(TAG, "[TTS_FAIL] TextToSpeech unavailable")
            return
        }
        val result = tts?.setLanguage(Locale.US)
        ttsReady = result != TextToSpeech.LANG_MISSING_DATA &&
            result != TextToSpeech.LANG_NOT_SUPPORTED

        if (ttsReady) {
            com.antigravity.shieldx.assistant.voice.JarvisVoiceEngine.applyProfile(tts)
        }

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _stateFlow.value = TarziState.SPEAKING
                duckMusic(true)
            }

            override fun onDone(utteranceId: String?) {
                returnToWaiting()
            }

            @Deprecated("Required by the base class")
            override fun onError(utteranceId: String?) {
                returnToWaiting()
            }
        })
    }

    private fun speak(text: String) {
        if (text.isBlank()) {
            returnToWaiting()
            return
        }
        _stateFlow.value = TarziState.SPEAKING
        updateNotification(text.take(60))
        duckMusic(true)

        if (!ttsReady) {
            // Without TTS there is nothing to wait on, so resume immediately.
            returnToWaiting()
            return
        }
        com.antigravity.shieldx.assistant.voice.JarvisVoiceEngine.applyProfile(tts)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tarzi-" + System.currentTimeMillis())
    }

    /** Short confirmation tone so the user knows Tarzi heard the wake word. */
    private fun playChime() {
        try {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70)
                .startTone(ToneGenerator.TONE_PROP_BEEP, 120)
        } catch (_: Exception) {
        }
    }

    private fun returnToWaiting() {
        _stateFlow.value = TarziState.WAITING
        updateNotification("Waiting for \"Tarzi\"")
        duckMusic(false)
        // Small gap so the recogniser does not pick up the tail of our own speech.
        mainHandler.postDelayed({ wakeWordEngine?.resume() }, 400)
    }

    // ==========================================================
    // Notification
    // ==========================================================

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Tarzi Assistant",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps Tarzi listening for its wake word"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun buildNotification(status: String): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, TarziVoiceService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Tarzi")
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_tarzi_notification)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, "Stop", stopIntent)
            .build()
    }

    private fun updateNotification(status: String) {
        try {
            getSystemService(NotificationManager::class.java)
                ?.notify(NOTIFICATION_ID, buildNotification(status))
        } catch (_: Exception) {
        }
    }

    // ==========================================================
    // Teardown
    // ==========================================================

    private fun shutdown() {
        wakeWordEngine?.stop()
        wakeWordEngine = null
        try {
            commandRecognizer?.destroy()
        } catch (_: Exception) {
        }
        commandRecognizer = null
        _stateFlow.value = TarziState.OFFLINE
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        wakeWordEngine?.stop()
        try {
            commandRecognizer?.destroy()
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        _stateFlow.value = TarziState.OFFLINE
    }
}
