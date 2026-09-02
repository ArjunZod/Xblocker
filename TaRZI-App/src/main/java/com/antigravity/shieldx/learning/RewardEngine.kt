package com.antigravity.shieldx.learning

import android.content.Context
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * The scoreboard.
 *
 * Every block is a moment the user could uninstall the app. This turns that
 * moment into the one place they earn something, so the filter stops reading as
 * a punishment and starts reading as the thing that levels them up.
 *
 * Two rules shape the maths. XP never goes down, and levels never go backwards
 * - a bad night costs progress not yet made, never progress already earned,
 * because "you lost your streak" is the message that gets an app deleted. And
 * the daily challenge resets rather than accumulating, so someone who fell off
 * for a week starts today on equal footing with someone who didn't.
 */
class RewardEngine(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("tarzi_rewards", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_XP = "xp"
        private const val KEY_RESISTS = "total_resists"
        private const val KEY_DAY_KEY = "challenge_day"
        private const val KEY_DAY_RESISTS = "day_resists"
        private const val KEY_DAY_WORDS = "day_words"
        private const val KEY_DAY_SONG = "day_song_played"
        private const val KEY_DAY_CLAIMED = "day_claimed"
        private const val KEY_BEST_DAY = "best_day_resists"
        private const val KEY_LAST_ACTIVE_DAY = "last_active_day"
        private const val KEY_STREAK_DAYS = "streak_days"

        const val XP_PER_RESIST = 10
        const val XP_CORRECT_ANSWER = 15
        const val XP_DAILY_COMPLETE = 50

        /** Daily targets. Deliberately small - these must be reachable on a bad day. */
        const val TARGET_RESISTS = 3
        const val TARGET_WORDS = 2
    }

    // --- level maths -------------------------------------------------------

    /**
     * Levels get gently more expensive: level n costs 100 * n XP. Early levels
     * arrive fast, which is when someone is deciding whether this app is worth
     * keeping.
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

    /** XP into the current level, and what the level costs. */
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

    // --- earning -----------------------------------------------------------

    /** A block was shown and survived. Returns XP awarded. */
    fun recordResist(): Int {
        rollDayIfNeeded()
        addXp(XP_PER_RESIST)
        prefs.edit()
            .putInt(KEY_RESISTS, totalResists() + 1)
            .putInt(KEY_DAY_RESISTS, dayResists() + 1)
            .apply()
        updateStreak()
        val best = prefs.getInt(KEY_BEST_DAY, 0)
        if (dayResists() > best) prefs.edit().putInt(KEY_BEST_DAY, dayResists()).apply()
        return XP_PER_RESIST
    }

    fun recordCorrectAnswer(): Int {
        rollDayIfNeeded()
        addXp(XP_CORRECT_ANSWER)
        return XP_CORRECT_ANSWER
    }

    fun recordWordLearned() {
        rollDayIfNeeded()
        prefs.edit().putInt(KEY_DAY_WORDS, dayWords() + 1).apply()
    }

    fun recordSongPlayed() {
        rollDayIfNeeded()
        prefs.edit().putBoolean(KEY_DAY_SONG, true).apply()
    }

    private fun addXp(amount: Int) {
        prefs.edit().putInt(KEY_XP, xp() + amount).apply()
    }

    // --- daily challenge ---------------------------------------------------

    fun dayResists(): Int = prefs.getInt(KEY_DAY_RESISTS, 0)
    fun dayWords(): Int = prefs.getInt(KEY_DAY_WORDS, 0)
    fun daySongPlayed(): Boolean = prefs.getBoolean(KEY_DAY_SONG, false)
    fun totalResists(): Int = prefs.getInt(KEY_RESISTS, 0)
    fun bestDay(): Int = prefs.getInt(KEY_BEST_DAY, 0)
    fun streakDays(): Int = prefs.getInt(KEY_STREAK_DAYS, 0)

    fun dailyComplete(): Boolean =
        dayResists() >= TARGET_RESISTS && dayWords() >= TARGET_WORDS && daySongPlayed()

    /** Awards the daily bonus once. Returns XP granted, or 0. */
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
        Challenge("Shut it down $TARGET_RESISTS times", dayResists().coerceAtMost(TARGET_RESISTS), TARGET_RESISTS),
        Challenge("Bank $TARGET_WORDS new words", dayWords().coerceAtMost(TARGET_WORDS), TARGET_WORDS),
        Challenge("Cash in a reward track", if (daySongPlayed()) 1 else 0, 1)
    )

    // --- day + streak bookkeeping ------------------------------------------

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
                .putInt(KEY_DAY_WORDS, 0)
                .putBoolean(KEY_DAY_SONG, false)
                .putBoolean(KEY_DAY_CLAIMED, false)
                .apply()
        }
    }

    /**
     * Counts consecutive days the app was actually used. Missing a day resets
     * this one number - and nothing else, which is the point: XP and level
     * survive, so a reset streak is a missed bonus rather than a wipe.
     */
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

    /** Content unlocks, so levelling up visibly gives something. */
    fun unlockedPackLabel(): String = when {
        level() >= 8 -> "Every pack unlocked"
        level() >= 6 -> "Legend memes unlocked"
        level() >= 4 -> "Deep-cut stories unlocked"
        level() >= 2 -> "Extra memes unlocked"
        else -> "Reach level 2 to unlock more memes"
    }

    fun nextUnlockAt(): Int = when {
        level() >= 8 -> 0
        level() >= 6 -> 8
        level() >= 4 -> 6
        level() >= 2 -> 4
        else -> 2
    }
}
