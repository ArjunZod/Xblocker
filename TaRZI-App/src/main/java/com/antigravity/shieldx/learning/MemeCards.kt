package com.antigravity.shieldx.learning

import com.antigravity.shieldx.R

/**
 * The loud half of the reward screen.
 *
 * These are drawn, not shipped as images: real meme images would mean bundling
 * other people's copyrighted pictures, and a blocker quietly shipping stolen
 * JPEGs is not a thing worth doing. Rendering big type on a colour block gets
 * the same hit, unlocks by level, and costs nothing to add more of.
 *
 * The voice is a hype coach - loud, on the user's side, celebrating the fact
 * that they showed up at all. It never shames, never mentions the site, and
 * never implies they are broken. The joke is always aimed at the moment, not
 * at the person.
 */
data class MemeCard(
    val id: String,
    /** Big top line. Shouted. */
    val punch: String,
    /** Smaller line underneath. */
    val sub: String,
    /** Drawable from the app's own icon set - never an emoji, which would
     *  render in the system font and break the type. */
    val iconRes: Int,
    /** Minimum level before this card appears. */
    val unlockLevel: Int = 1
)

object MemeCards {

    val all: List<MemeCard> = listOf(
        // Level 1 - always available
        MemeCard("caught", "NOPE.", "Blocked it before you even finished typing.", R.drawable.ic_tz_shield, 1),
        MemeCard("speed", "TOO SLOW, URGE", "You were faster. Again.", R.drawable.ic_tz_bolt, 1),
        MemeCard("gym", "THAT'S A REP", "Willpower is a muscle. You just trained it.", R.drawable.ic_tz_bolt, 1),
        MemeCard("boss", "BLOCKED AND LOADED", "Another one bites the dust.", R.drawable.ic_tz_shield, 1),
        MemeCard("showed-up", "YOU SHOWED UP ANYWAY", "That's the whole game right there.", R.drawable.ic_tz_flame, 1),
        MemeCard("threepm", "NOT TODAY", "The urge had one job. It failed.", R.drawable.ic_tz_stop, 1),
        MemeCard("bank", "+10 XP. CASHED.", "You're literally getting paid to not.", R.drawable.ic_tz_bolt, 1),
        MemeCard("boring", "BORING? GOOD.", "Boring is what winning looks like from inside.", R.drawable.ic_tz_check, 1),

        // Level 2
        MemeCard("streak", "STREAK INTACT", "The graph only goes one way. Up.", R.drawable.ic_tz_flame, 2),
        MemeCard("delete", "TRIED IT. DIDN'T WORK.", "The filter doesn't negotiate.", R.drawable.ic_tz_shield, 2),
        MemeCard("2am", "IT'S LATE. SO WHAT.", "Late-you and morning-you just shook hands.", R.drawable.ic_tz_circle, 2),
        MemeCard("main-character", "MAIN CHARACTER ENERGY", "Plot twist: you said no.", R.drawable.ic_tz_bolt, 2),
        MemeCard("respect", "RESPECT.", "Most people tap through. You didn't.", R.drawable.ic_tz_shield_check, 2),

        // Level 4
        MemeCard("built", "BUILT DIFFERENT", "Four levels deep and still standing.", R.drawable.ic_tz_shield_check, 4),
        MemeCard("compound", "THIS COMPOUNDS", "Today's boring choice is next month's easy one.", R.drawable.ic_tz_bolt, 4),
        MemeCard("villain", "THE URGE IS LOSING", "It used to win. Look at it now.", R.drawable.ic_tz_target, 4),

        // Level 6
        MemeCard("legend", "LEGEND BEHAVIOUR", "You've done this more times than most people try.", R.drawable.ic_tz_trophy, 6),
        MemeCard("unbothered", "UNBOTHERED. MOISTURISED.", "Blocked. Focused. Thriving.", R.drawable.ic_tz_check, 6),
        MemeCard("final-boss", "YOU'RE THE FINAL BOSS NOW", "The urge needs a strategy guide for you.", R.drawable.ic_tz_trophy, 6),

        // Level 8
        MemeCard("hall-of-fame", "HALL OF FAME", "At this point it's just who you are.", R.drawable.ic_tz_trophy, 8),
        MemeCard("teach", "TEACH A CLASS ON THIS", "Genuinely. You'd have material.", R.drawable.ic_tz_book, 8)
    )

    /** Cards the user has actually unlocked at this level. */
    fun availableFor(level: Int): List<MemeCard> =
        all.filter { it.unlockLevel <= level }.ifEmpty { all.take(1) }

    fun forMoment(level: Int, nowMs: Long = System.currentTimeMillis()): MemeCard {
        val pool = availableFor(level)
        return pool[Math.floorMod((nowMs / 1000L).toInt(), pool.size)]
    }
}

/**
 * The reward track. The blocker owns no music engine - this is a suggestion it
 * hands to the TaRZI Music app, which does the playing.
 */
data class SongReward(val title: String, val artist: String, val why: String) {
    val query: String get() = "$title $artist"
}

object SongRewards {

    val all: List<SongReward> = listOf(
        SongReward("Infinity", "Jaymes Young", "For when you need the volume up."),
        SongReward("Counting Stars", "OneRepublic", "Loud, fast, hard to sit still to."),
        SongReward("Believer", "Imagine Dragons", "Literally about being forged by the hard part."),
        SongReward("Stronger", "Kanye West", "The title is the whole instruction."),
        SongReward("Eye of the Tiger", "Survivor", "Unserious. Extremely effective."),
        SongReward("Blinding Lights", "The Weeknd", "Impossible to be still through."),
        SongReward("Levitating", "Dua Lipa", "Mood reset in three minutes."),
        SongReward("Can't Hold Us", "Macklemore", "For a fast walk out of the room."),
        SongReward("Thunder", "Imagine Dragons", "Short, loud, does the job."),
        SongReward("On Top of the World", "Imagine Dragons", "Annoyingly upbeat. That's the point.")
    )

    fun forMoment(nowMs: Long = System.currentTimeMillis()): SongReward =
        all[Math.floorMod((nowMs / 60_000L).toInt(), all.size)]
}
