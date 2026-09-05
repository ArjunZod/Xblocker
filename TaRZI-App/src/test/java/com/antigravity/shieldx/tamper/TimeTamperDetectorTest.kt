package com.antigravity.shieldx.tamper

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TimeTamperDetectorTest {

    private lateinit var detector: TimeTamperDetector

    @Before
    fun setUp() {
        detector = TimeTamperDetector(maxDriftToleranceMs = 60_000L) // 1 minute tolerance
    }

    @Test
    fun testInitialCheckPasses() {
        val result = detector.checkTimeIntegrity()
        // First check or normal delta should not be flagged as tampered
        assertFalse(result.isTampered)
        assertNull(result.reason)
    }

    @Test
    fun testResetBaseline() {
        detector.resetBaseline()
        val result = detector.checkTimeIntegrity()
        assertFalse(result.isTampered)
    }
}
