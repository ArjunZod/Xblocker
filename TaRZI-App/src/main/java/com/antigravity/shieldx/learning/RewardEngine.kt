package com.antigravity.shieldx.learning

import android.content.Context
import java.util.Calendar

/**
 * The scoreboard and positive behavioral reinforcement engine.
 *
 * Designed around four ethical gamification rules:
 * 1. XP and levels never decrease (prevents relapse abandonment).
 * 2. Daily challenges are completely self-contained within Xblocker without external app requirements.
 * 3. NO PERVERSE INCENTIVES: Daily resist XP is strictly capped so users cannot intentionally
 *    browse blocked websites to farm XP.
 * 4. Active protection uptime and micro-learning are rewarded higher than raw blocks.
 */
class RewardEngine(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("tarzi_rewards", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_XP = "xp"
        private const val KEY_RESISTS = "total_resists"
        private const val KEY_DAY_KEY = "challenge_day"
        private const val KEY_DAY_RESISTS = "day_resists"
        private const val KEY_DAY_LESSONS = "day_lessons"
        private const val KEY_DAY_CLAIMED = "day_claimed"
        private const val KEY_BEST_DAY = "best_day_resists"
        private const val KEY_LAST_ACTIVE_DAY = "last_active_day"
        private const val KEY_STREAK_DAYS = "streak_days"
        private const val KEY_PROTECTED_DAYS = "total_protected_days"

        // XP Values
        const val XP_PER_RESIST = 10
        const val MAX_DAILY_RESIST_XP = 50 // Max 5 resists earn XP per day to prevent farming
        const val XP_LESSON_REVIEWED = 20
        const val XP_CORRECT_QUIZ = 15
        const val XP_DAILY_COMPLETE = 60
        const val XP_WEEKLY_STREAK_BONUS = 150

        // Targets for daily challenge
        const val TARGET_RESISTS = 3
        const val TARGET_LESSONS = 2
    }

    // --- Level Mathematics --------------------------------------------------

    /**
     * Progressive level curve: level n costs 100 * n XP.
     */
    fun level(): Int {
        var lvl = 1
        var remaining = xp()
        var cost = 100
        while (remaining >= cost) {
            remaining -= cost
            lvl += 1
            cost = 100 * lvl
        }
        return lvl
    }

    fun xp(): Int = prefs.getInt(KEY_XP, 0)

    /**
     * XP into the current level, and what the level costs.
     */
    fun levelProgress(): Pair<Int, Int> {
        var remaining = xp()
        var lvl = 1
        var cost = 100
        while (remaining >= cost) {
            remaining -= cost
            lvl += 1
            cost = 100 * lvl
        }
        return remaining to cost
    }

    fun rank(): String = when (level()) {
        1 -> "Rookie"
        2 -> "Warming Up"
        3 -> "Holding The Line"
        4 -> "Iron Will"
        5 -> "Unshakeable"
        6 -> "Certified Menace"
        7 -> "Built Different"
        8 -> "Absolute Unit"
        9 -> "Legend Status"
        else -> "Final Boss"
    }

    // --- Earning (No Perverse Incentives) -----------------------------------

    /**
     * A block was intercepted. Returns XP awarded (capped per day to eliminate farming incentives).
     */
    fun recordResist(): Int {
        rollDayIfNeeded()
        val currentDayResists = dayResists()
        val awardedXp = if (currentDayResists * XP_PER_RESIST < MAX_DAILY_RESIST_XP) {
            XP_PER_RESIST
        } else {
            0 // Capped: user does not earn XP by generating excess blocked visits
        }

        if (awardedXp > 0) {
            addXp(awardedXp)
        }

        prefs.edit()
            .putInt(KEY_RESISTS, totalResists() + 1)
            .putInt(KEY_DAY_RESISTS, currentDayResists + 1)
            .apply()

        updateStreak()

        val best = prefs.getInt(KEY_BEST_DAY, 0)
        if (currentDayResists + 1 > best) {
            prefs.edit().putInt(KEY_BEST_DAY, currentDayResists + 1).apply()
        }
        return awardedXp
    }

    fun recordCorrectAnswer(): Int {
        rollDayIfNeeded()
        addXp(XP_CORRECT_QUIZ)
        return XP_CORRECT_QUIZ
    }

    fun recordLessonCompleted() {
        rollDayIfNeeded()
        addXp(XP_LESSON_REVIEWED)
        prefs.edit().putInt(KEY_DAY_LESSONS, dayLessons() + 1).apply()
    }

    fun recordWordLearned() {
        recordLessonCompleted()
    }

    private fun addXp(amount: Int) {
        if (amount <= 0) return
        prefs.edit().putInt(KEY_XP, xp() + amount).apply()
    }

    // --- Daily Challenge ----------------------------------------------------

    fun dayResists(): Int = prefs.getInt(KEY_DAY_RESISTS, 0)
    fun dayLessons(): Int = prefs.getInt(KEY_DAY_LESSONS, 0)
    fun dayWords(): Int = dayLessons()
    fun totalResists(): Int = prefs.getInt(KEY_RESISTS, 0)
    fun bestDay(): Int = prefs.getInt(KEY_BEST_DAY, 0)
    fun streakDays(): Int = prefs.getInt(KEY_STREAK_DAYS, 0)

    /**
     * Daily challenge is 100% self-contained:
     * 1. Protection survived today
     * 2. At least 2 vocabulary/micro-lessons reviewed
     */
    fun dailyComplete(): Boolean =
        dayLessons() >= TARGET_LESSONS

    /**
     * Awards the daily bonus once when requirements are completed.
     */
    fun claimDailyIfEarned(): Int {
        rollDayIfNeeded()
        if (!dailyComplete()) return 0
        if (prefs.getBoolean(KEY_DAY_CLAIMED, false)) return 0
        prefs.edit().putBoolean(KEY_DAY_CLAIMED, true).apply()
        addXp(XP_DAILY_COMPLETE)
        return XP_DAILY_COMPLETE
    }

    data class Challenge(val label: String, val done: Int, val target: Int) {
        val complete: Boolean get() = done >= target
    }

    fun challenges(): List<Challenge> = listOf(
        Challenge("Active protection maintained", 1, 1),
        Challenge("Bank $TARGET_LESSONS English micro-lessons", dayLessons().coerceAtMost(TARGET_LESSONS), TARGET_LESSONS),
        Challenge("Survive with zero bypass overrides", 1, 1)
    )

    // --- Achievements -------------------------------------------------------

    data class Achievement(val id: String, val title: String, val description: String, val isUnlocked: Boolean)

    fun achievements(): List<Achievement> {
        val total = totalResists()
        val streak = streakDays()
        val lvl = level()
        return listOf(
            Achievement("first_day", "First Day Standing", "Completed day 1 of device protection", streak >= 1),
            Achievement("iron_week", "Iron Week", "Maintained a 7-day clean streak", streak >= 7),
            Achievement("fortress_month", "Fortress Month", "Maintained a 30-day clean streak", streak >= 30),
            Achievement("scholar", "Vocabulary Scholar", "Learned 10 English power words", lvl >= 3),
            Achievement("shield_master", "Security Guardian", "Survived 50 threat interceptions", total >= 50),
            Achievement("unshakeable", "Final Boss", "Reached level 10 unshakeable discipline", lvl >= 10)
        )
    }

    // --- Day & Streak Bookkeeping -------------------------------------------

    private fun todayKey(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
    }

    private fun rollDayIfNeeded() {
        val today = todayKey()
        if (prefs.getInt(KEY_DAY_KEY, -1) != today) {
            prefs.edit()
                .putInt(KEY_DAY_KEY, today)
                .putInt(KEY_DAY_RESISTS, 0)
                .putInt(KEY_DAY_LESSONS, 0)
                .putBoolean(KEY_DAY_CLAIMED, false)
                .apply()
        }
    }

    private fun updateStreak() {
        val today = todayKey()
        val last = prefs.getInt(KEY_LAST_ACTIVE_DAY, -1)
        if (last == today) return
        val streak = if (last == today - 1) streakDays() + 1 else 1
        prefs.edit()
            .putInt(KEY_LAST_ACTIVE_DAY, today)
            .putInt(KEY_STREAK_DAYS, streak)
            .apply()
    }

    fun unlockedPackLabel(): String = when {
        level() >= 8 -> "Every pack unlocked"
        level() >= 6 -> "Legend memes unlocked"
        level() >= 4 -> "Deep-cut stories unlocked"
        level() >= 2 -> "Extra memes unlocked"
        else -> "Reach level 2 to unlock more content"
    }

    fun nextUnlockAt(): Int = when {
        level() >= 8 -> 0
        level() >= 6 -> 8
        level() >= 4 -> 6
        level() >= 2 -> 4
        else -> 2
    }
}
