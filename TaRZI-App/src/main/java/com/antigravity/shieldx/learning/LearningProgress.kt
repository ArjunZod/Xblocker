package com.antigravity.shieldx.learning

import android.content.Context
import java.util.concurrent.TimeUnit

/**
 * The part the user accumulates.
 *
 * A streak of "days you didn't slip" makes a relapse feel like losing something,
 * which is exactly the wrong incentive - one bad night and the counter says
 * start over, so people delete the app instead. So the number that grows here
 * is what they learned, and it never resets. A slip costs nothing that was
 * already earned.
 *
 * Stored in plain preferences on the device. None of it is sent anywhere, and
 * it deliberately records no history of what was blocked.
 */
class LearningProgress(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("tarzi_learning", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SEEN = "seen_lesson_ids"
        private const val KEY_ANSWERED = "answered_correctly"
        private const val KEY_FIRST_USE = "first_use_at"
        private const val KEY_LAST_SHOWN = "last_shown_at"

        /** How long a block screen stays quiet before it will appear again. */
        val QUIET_PERIOD_MS: Long = TimeUnit.MINUTES.toMillis(2)
    }

    /** Lesson ids the user has actually been shown. */
    fun seenIds(): Set<String> =
        prefs.getStringSet(KEY_SEEN, emptySet())?.toSet() ?: emptySet()

    fun markSeen(lessonId: String) {
        val updated = seenIds() + lessonId
        prefs.edit()
            .putStringSet(KEY_SEEN, updated)
            .putLong(KEY_LAST_SHOWN, System.currentTimeMillis())
            .apply()
        ensureFirstUseRecorded()
    }

    fun markAnsweredCorrectly() {
        prefs.edit().putInt(KEY_ANSWERED, answeredCorrectly() + 1).apply()
    }

    fun answeredCorrectly(): Int = prefs.getInt(KEY_ANSWERED, 0)

    fun learnedCount(): Int = seenIds().size

    /** Days since the filter started doing this, not days since a slip. */
    fun daysSinceStart(): Int {
        ensureFirstUseRecorded()
        val first = prefs.getLong(KEY_FIRST_USE, System.currentTimeMillis())
        val elapsed = System.currentTimeMillis() - first
        return (TimeUnit.MILLISECONDS.toDays(elapsed) + 1).toInt().coerceAtLeast(1)
    }

    /**
     * True when enough time has passed to show the screen again. Without this a
     * single page load - which can trigger dozens of blocked lookups - would
     * throw the same screen up over and over.
     */
    fun shouldShowNow(nowMs: Long = System.currentTimeMillis()): Boolean {
        val last = prefs.getLong(KEY_LAST_SHOWN, 0L)
        return nowMs - last >= QUIET_PERIOD_MS
    }

    fun noteShown(nowMs: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_LAST_SHOWN, nowMs).apply()
    }

    private fun ensureFirstUseRecorded() {
        if (!prefs.contains(KEY_FIRST_USE)) {
            prefs.edit().putLong(KEY_FIRST_USE, System.currentTimeMillis()).apply()
        }
    }

    /** One line for the home screen. Reports progress, never a lecture. */
    fun summary(): String {
        val learned = learnedCount()
        return when {
            learned == 0 -> "You'll pick up a word each time something is blocked"
            learned == 1 -> "1 word learned so far"
            learned >= EnglishLessons.total -> "$learned learned — you've seen them all"
            else -> "$learned words and phrases learned"
        }
    }
}
