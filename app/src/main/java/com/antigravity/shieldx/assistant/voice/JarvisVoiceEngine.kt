package com.antigravity.shieldx.assistant.voice

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale

/**
 * Enforces a deep, resonant masculine Jarvis voice profile across all Android TTS engines.
 */
object JarvisVoiceEngine {

    private const val TAG = "JarvisVoiceEngine"

    // Deep baritone pitch for unmistakable masculine Jarvis resonance
    const val PITCH_JARVIS_BASS = 0.70f
    const val SPEECH_RATE_JARVIS = 1.05f

    private val MALE_KEYWORDS = listOf(
        "male", "man", "guy", "david", "george", "james", "john", "michael", "paul",
        "en-us-x-sfg", "en-us-x-iom", "en-us-x-iob", "en-us-x-iol",
        "en-gb-x-rjs", "en-gb-x-gkb", "en-gb-x-fis",
        "en-in-x-cxx", "en-in-x-enc", "en-au-x-afh", "en-au-x-aub"
    )

    private val FEMALE_KEYWORDS = listOf(
        "female", "woman", "girl", "zira", "susan", "samantha", "victoria", "karen",
        "wavenet-a", "wavenet-c", "wavenet-e", "wavenet-f", "sfg-network", "cxx-network"
    )

    /**
     * Apply deep-bass Jarvis voice settings. Must be called before EVERY speak() call.
     */
    fun applyProfile(tts: TextToSpeech?) {
        if (tts == null) return
        try {
            // 1. Configure deep pitch and crisp pace
            tts.setPitch(PITCH_JARVIS_BASS)
            tts.setSpeechRate(SPEECH_RATE_JARVIS)

            // 2. Select explicit English male voice from installed TTS engines
            val voices: Set<Voice>? = try { tts.voices } catch (_: Exception) { null }
            if (!voices.isNullOrEmpty()) {
                val englishVoices = voices.filter {
                    val lang = it.locale.language.lowercase(Locale.ROOT)
                    lang == "en" || lang.startsWith("en")
                }

                // Best match: Explicit male keyword and no female keyword
                val bestMaleVoice = englishVoices.firstOrNull { voice ->
                    val name = voice.name.lowercase(Locale.ROOT)
                    MALE_KEYWORDS.any { name.contains(it) } && FEMALE_KEYWORDS.none { name.contains(it) }
                } ?: englishVoices.firstOrNull { voice ->
                    val name = voice.name.lowercase(Locale.ROOT)
                    MALE_KEYWORDS.any { name.contains(it) }
                } ?: englishVoices.firstOrNull { voice ->
                    val name = voice.name.lowercase(Locale.ROOT)
                    FEMALE_KEYWORDS.none { name.contains(it) }
                }

                if (bestMaleVoice != null) {
                    tts.voice = bestMaleVoice
                    Log.i(TAG, "Selected Jarvis voice: ${bestMaleVoice.name}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed applying Jarvis voice profile: ${e.message}")
        }
    }
}
