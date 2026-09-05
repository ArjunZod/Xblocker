package com.antigravity.shieldx.learning

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RewardEngineTest {

    private lateinit var context: Context
    private lateinit var rewardEngine: RewardEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Clear prefs before each test
        context.getSharedPreferences("tarzi_rewards", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        rewardEngine = RewardEngine(context)
    }

    @Test
    fun testLevelMathNeverDecreases() {
        assertEquals(0, rewardEngine.xp())
        assertEquals(1, rewardEngine.level())
        assertEquals("Rookie", rewardEngine.rank())

        // 100 XP takes to Level 2
        repeat(10) { rewardEngine.recordCorrectQuiz() } // 10 * 15 = 150 XP
        assertTrue(rewardEngine.xp() >= 150)
        assertEquals(2, rewardEngine.level())
        assertEquals("Warming Up", rewardEngine.rank())
    }

    @Test
    fun testResistXpCapPreventsBlockFarming() {
        // First 5 resists earn 10 XP each (up to MAX_DAILY_RESIST_XP = 50)
        var totalAwarded = 0
        repeat(5) {
            val gained = rewardEngine.recordResist()
            assertEquals(RewardEngine.XP_PER_RESIST, gained)
            totalAwarded += gained
        }
        assertEquals(RewardEngine.MAX_DAILY_RESIST_XP, totalAwarded)

        // 6th resist on same day must earn 0 XP to eliminate perverse incentive of spamming blocks
        val sixthGained = rewardEngine.recordResist()
        assertEquals(0, sixthGained)
        assertEquals(RewardEngine.MAX_DAILY_RESIST_XP, rewardEngine.xp())

        // But totalResists counter still increments
        assertEquals(6, rewardEngine.totalResists())
    }

    @Test
    fun testLevelProgressCalculation() {
        val (into, cost) = rewardEngine.levelProgress()
        assertEquals(0, into)
        assertEquals(100, cost)
    }
}
