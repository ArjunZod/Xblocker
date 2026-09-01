package com.antigravity.shieldx.assistant

import com.antigravity.shieldx.assistant.intent.IntentEngine
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class IntentEngineTest {

    private lateinit var intentEngine: IntentEngine

    @Before
    fun setUp() {
        intentEngine = IntentEngine()
    }

    @Test
    fun testProtectionIntents() {
        val r1 = intentEngine.parse("TaRZI, block porn")
        assertEquals(1, r1.size)
        assertEquals("SET_PROTECTION_PROFILE", r1[0].toolName)
        assertEquals("MAXIMUM", r1[0].parameters["profile"])

        val r2 = intentEngine.parse("enable protection")
        assertEquals("ENABLE_PROTECTION", r2[0].toolName)
        assertEquals(true, r2[0].parameters["enabled"])

        val r3 = intentEngine.parse("what is my protection status?")
        assertEquals("GET_PROTECTION_STATUS", r3[0].toolName)
    }

    @Test
    fun testMusicIntents() {
        val r1 = intentEngine.parse("TaRZI, play midnight waves")
        assertEquals("PLAY", r1[0].toolName)
        assertEquals("midnight waves", r1[0].parameters["query"])

        val r2 = intentEngine.parse("pause music")
        assertEquals("PAUSE", r2[0].toolName)

        val r3 = intentEngine.parse("next song")
        assertEquals("NEXT", r3[0].toolName)
    }

    @Test
    fun testMemoryIntents() {
        val r1 = intentEngine.parse("remember my favorite pizza is paneer pizza")
        assertEquals("MEMORY_WRITE", r1[0].toolName)
        assertEquals("favorite pizza", r1[0].parameters["key"])
        assertEquals("paneer pizza", r1[0].parameters["value"])

        val r2 = intentEngine.parse("what is my favorite pizza?")
        assertEquals("MEMORY_READ", r2[0].toolName)
        assertEquals("favorite pizza", r2[0].parameters["key"])
    }

    @Test
    fun testMultiStepCommandDecomposition() {
        val query = "TaRZI, connect to my home setup, and enable maximum protection, and play my evening playlist, and remind me to call Mom at 8 PM"
        val requests = intentEngine.parse(query)

        assertEquals(4, requests.size)
        assertEquals("SET_NETWORK_PROFILE", requests[0].toolName)
        assertEquals("SET_PROTECTION_PROFILE", requests[1].toolName)
        assertEquals("PLAY", requests[2].toolName)
        assertEquals("CREATE_REMINDER", requests[3].toolName)
    }
}
