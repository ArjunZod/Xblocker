package com.antigravity.shieldx.assistant.voice

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
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.content.ContextCompat
import com.antigravity.shieldx.core.runtime.MusicController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Strict state machine for Assistant Voice interaction.
 */
sealed class VoiceState {
    object Idle : VoiceState()
    object RequestingPermission : VoiceState()
    data class Listening(val rmsDb: Float = 0f) : VoiceState()
    data class Processing(val partialText: String = "") : VoiceState()
    data class Speaking(val text: String = "") : VoiceState()
    data class Error(val message: String = "") : VoiceState()
}

/**
 * Production Android SpeechRecognizer and TextToSpeech Manager for Tarzi.
 * Supports Continuous Live Conversation loop and real-time audio event visualizers.
 */
class VoiceAssistantManager(
    private val context: Context,
    private val musicController: MusicController,
    private val onSpeechRecognized: (String) -> Unit
) : RecognitionListener, TextToSpeech.OnInitListener {

    private val scope = CoroutineScope(Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false

    private val _voiceStateFlow = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val voiceStateFlow: StateFlow<VoiceState> = _voiceStateFlow.asStateFlow()

    private val _isContinuousModeFlow = MutableStateFlow(false)
    val isContinuousModeFlow: StateFlow<Boolean> = _isContinuousModeFlow.asStateFlow()

    companion object {
        private const val TAG = "TaRZIVoice"
    }

    init {
        scope.launch {
            initSpeechRecognizer()
            textToSpeech = TextToSpeech(context, this@VoiceAssistantManager)
        }
    }

    fun setContinuousMode(enabled: Boolean) {
        _isContinuousModeFlow.value = enabled
        if (enabled && (_voiceStateFlow.value == VoiceState.Idle || _voiceStateFlow.value is VoiceState.Error)) {
            startListening()
        }
    }

    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            try {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(this@VoiceAssistantManager)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create SpeechRecognizer: ${e.message}")
            }
        }
    }

    fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun onPermissionRequested() {
        _voiceStateFlow.value = VoiceState.RequestingPermission
    }

    private fun setMusicDucking(duck: Boolean) {
        scope.launch {
            try {
                musicController.duck(duck)
            } catch (_: Exception) {}
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(Locale.US)
            isTtsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            if (isTtsReady) {
                JarvisVoiceEngine.applyProfile(textToSpeech)
            }

            textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _voiceStateFlow.value = VoiceState.Speaking(utteranceId ?: "")
                    setMusicDucking(true)
                }

                override fun onDone(utteranceId: String?) {
                    _voiceStateFlow.value = VoiceState.Idle
                    // Continuous conversation live loop
                    if (_isContinuousModeFlow.value) {
                        mainHandler.postDelayed({
                            if (_voiceStateFlow.value == VoiceState.Idle) {
                                startListening()
                            }
                        }, 250)
                    } else {
                        setMusicDucking(false)
                    }
                }

                override fun onError(utteranceId: String?) {
                    _voiceStateFlow.value = VoiceState.Idle
                    if (_isContinuousModeFlow.value) {
                        mainHandler.postDelayed({ startListening() }, 400)
                    } else {
                        setMusicDucking(false)
                    }
                }
            })
        }
    }

    fun startListening() {
        if (!hasRecordPermission()) {
            _voiceStateFlow.value = VoiceState.RequestingPermission
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _voiceStateFlow.value = VoiceState.Error("Speech recognition is not available on this device.")
            return
        }

        // Priority audio ducking: lower music playback so voice mic captures clearly
        setMusicDucking(true)

        // Barge-in: Stop any active TTS before listening
        stopSpeaking()

        mainHandler.post {
            try {
                if (speechRecognizer == null) {
                    initSpeechRecognizer()
                } else {
                    try {
                        speechRecognizer?.cancel()
                    } catch (_: Exception) {}
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }

                _voiceStateFlow.value = VoiceState.Listening(0f)
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start speech recognizer: ${e.message}")
                _voiceStateFlow.value = VoiceState.Error("Failed to start listening: ${e.message}")
                if (_isContinuousModeFlow.value) {
                    mainHandler.postDelayed({
                        initSpeechRecognizer()
                        startListening()
                    }, 1000)
                }
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
                speechRecognizer?.stopListening()
            } catch (_: Exception) {}
            _voiceStateFlow.value = VoiceState.Idle
        }
    }

    fun speak(text: String) {
        if (!isTtsReady || text.isBlank()) return
        JarvisVoiceEngine.applyProfile(textToSpeech)
        _voiceStateFlow.value = VoiceState.Speaking(text)
        setMusicDucking(true)
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, text.take(30))
    }

    fun stopSpeaking() {
        if (isTtsReady) {
            textToSpeech?.stop()
            if (_voiceStateFlow.value is VoiceState.Speaking) {
                _voiceStateFlow.value = VoiceState.Idle
            }
        }
    }

    fun resetToIdle() {
        _voiceStateFlow.value = VoiceState.Idle
    }

    // === RecognitionListener Implementation ===
    override fun onReadyForSpeech(params: Bundle?) {
        Log.i(TAG, "[VOICE_READY] Speech recognizer ready for input")
        _voiceStateFlow.value = VoiceState.Listening(0f)
    }

    override fun onBeginningOfSpeech() {
        Log.i(TAG, "[VOICE_START] Speech detected")
        _voiceStateFlow.value = VoiceState.Listening(0.5f)
    }

    override fun onRmsChanged(rmsdB: Float) {
        val normalized = (rmsdB / 10f).coerceIn(0f, 1f)
        if (_voiceStateFlow.value is VoiceState.Listening) {
            _voiceStateFlow.value = VoiceState.Listening(normalized)
        }
    }

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        Log.i(TAG, "[VOICE_END] Speech input ended, processing")
        _voiceStateFlow.value = VoiceState.Processing()
    }

    override fun onError(error: Int) {
        val errorMessage = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
            SpeechRecognizer.ERROR_NETWORK -> "Network connection error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
            SpeechRecognizer.ERROR_SERVER -> "Recognition server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Listening timeout"
            else -> "Voice recognition error ($error)"
        }
        Log.w(TAG, "[VOICE_ERROR] code=$error message=$errorMessage")

        if (_isContinuousModeFlow.value) {
            // In continuous mode, do not freeze in Error state: auto-recover smoothly
            val isRecoverable = error == SpeechRecognizer.ERROR_NO_MATCH ||
                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                error == SpeechRecognizer.ERROR_CLIENT ||
                error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY

            if (isRecoverable) {
                _voiceStateFlow.value = VoiceState.Listening(0f)
                mainHandler.postDelayed({
                    if (_isContinuousModeFlow.value && _voiceStateFlow.value !is VoiceState.Speaking) {
                        initSpeechRecognizer()
                        startListening()
                    }
                }, if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1200L else 500L)
            } else {
                _voiceStateFlow.value = VoiceState.Error(errorMessage)
                mainHandler.postDelayed({
                    if (_isContinuousModeFlow.value) {
                        _voiceStateFlow.value = VoiceState.Idle
                        startListening()
                    }
                }, 1500L)
            }
        } else {
            _voiceStateFlow.value = VoiceState.Error(errorMessage)
            // Auto-reset to idle so the user can easily tap to try again
            mainHandler.postDelayed({
                if (_voiceStateFlow.value is VoiceState.Error) {
                    _voiceStateFlow.value = VoiceState.Idle
                }
            }, 2000L)
        }
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val recognizedText = matches?.firstOrNull() ?: ""
        Log.i(TAG, "[VOICE_RESULT] Transcript: '$recognizedText'")

        if (recognizedText.isNotBlank()) {
            _voiceStateFlow.value = VoiceState.Processing(recognizedText)
            onSpeechRecognized(recognizedText)
        } else {
            _voiceStateFlow.value = VoiceState.Idle
            if (_isContinuousModeFlow.value) {
                mainHandler.postDelayed({ startListening() }, 400)
            }
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val partial = matches?.firstOrNull() ?: ""
        if (partial.isNotBlank()) {
            _voiceStateFlow.value = VoiceState.Processing(partial)
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}

    fun destroy() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        } catch (_: Exception) {}
        speechRecognizer = null
        textToSpeech = null
    }
}
