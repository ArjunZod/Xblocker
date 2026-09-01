package com.antigravity.shieldx.assistant

import com.antigravity.shieldx.assistant.system.WakeWordEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wake word is matched against whatever the speech recogniser produced, and
 * recognisers render "Tarzi" inconsistently. These lock in that tolerance.
 */
class WakeWordEngineTest {

    @Test
    fun `detects the exact wake word`() {
        assertTrue(WakeWordEngine.containsWakeWord("tarzi"))
        assertTrue(WakeWordEngine.containsWakeWord("Tarzi open youtube"))
        assertTrue(WakeWordEngine.containsWakeWord("hey TARZI"))
    }

    @Test
    fun `detects common misrecognitions`() {
        // Every one of these has been observed standing in for "Tarzi".
        listOf(
            "tarzee open the app",
            "tarzy what is my battery",
            "tarsi scroll down",
            "hey tarzan go back",
            "darzi play music"
        ).forEach { transcript ->
            assertTrue("should wake on: $transcript", WakeWordEngine.containsWakeWord(transcript))
        }
    }

    @Test
    fun `ignores unrelated speech`() {
        listOf(
            "what is the weather today",
            "play some music please",
            "open youtube",
            "call mom"
        ).forEach { transcript ->
            assertFalse("should not wake on: $transcript", WakeWordEngine.containsWakeWord(transcript))
        }
    }

    @Test
    fun `punctuation and casing do not defeat detection`() {
        assertTrue(WakeWordEngine.containsWakeWord("Tarzi, open the first video!"))
        assertTrue(WakeWordEngine.containsWakeWord("TARZI... scroll down."))
    }

    @Test
    fun `strips the wake word leaving only the command`() {
        assertEquals("open the first video", WakeWordEngine.stripWakeWord("Tarzi open the first video"))
        assertEquals("scroll down", WakeWordEngine.stripWakeWord("tarzee, scroll down"))
        assertEquals("what is my battery", WakeWordEngine.stripWakeWord("Tarzi what is my battery"))
    }

    @Test
    fun `stripping a bare wake word yields nothing to act on`() {
        assertEquals("", WakeWordEngine.stripWakeWord("Tarzi"))
        assertEquals("", WakeWordEngine.stripWakeWord("tarzi!"))
    }

    @Test
    fun `commands without a wake word survive stripping unchanged`() {
        assertEquals("open youtube", WakeWordEngine.stripWakeWord("open youtube"))
    }
}
