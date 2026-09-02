package com.antigravity.shieldx.learning

/**
 * What the app gives back.
 *
 * A blocker that only ever says "no" is an app people delete. This is the other
 * half of the trade: every time the filter stops something, the user gets
 * thirty seconds of English instead of a browser error.
 *
 * The tone matters as much as the content. Nothing here lectures, praises
 * abstinence, or mentions what was blocked. It is a different subject, offered
 * without comment - which is what makes it a break in the moment rather than a
 * sermon about it.
 *
 * Everything ships inside the app. No network call, so it works with the tunnel
 * up, on a plane, or with no data - and nothing about what a person blocks ever
 * leaves the device.
 */

enum class LessonKind { Word, Idiom, Story }

data class Lesson(
    val id: String,
    val kind: LessonKind,
    /** The word, the idiom, or the story's title. */
    val headline: String,
    /** Plain-English meaning. No dictionary formality. */
    val meaning: String,
    /** One sentence someone would actually say. */
    val example: String,
    /** A question with exactly one right answer, used by the disable gate. */
    val question: String,
    val options: List<String>,
    val correctIndex: Int
)

object EnglishLessons {

    val all: List<Lesson> = listOf(
        Lesson(
            id = "resilient",
            kind = LessonKind.Word,
            headline = "Resilient",
            meaning = "Able to recover quickly after something knocks you down.",
            example = "She had a rough year, but she's resilient — she found her feet again.",
            question = "Someone bounces back fast after a setback. They are…",
            options = listOf("Resilient", "Reluctant", "Resentful"),
            correctIndex = 0
        ),
        Lesson(
            id = "momentum",
            kind = LessonKind.Word,
            headline = "Momentum",
            meaning = "The force something builds up once it's already moving.",
            example = "Two good days in a row and you start to feel the momentum.",
            question = "\"We finally have some momentum\" means the work is…",
            options = listOf("Building speed", "Completely stuck", "About to be cancelled"),
            correctIndex = 0
        ),
        Lesson(
            id = "deliberate",
            kind = LessonKind.Word,
            headline = "Deliberate",
            meaning = "Done on purpose, after thinking — not by accident.",
            example = "That wasn't luck. It was a deliberate choice.",
            question = "A deliberate decision is one that is…",
            options = listOf("Intentional", "Accidental", "Forgotten"),
            correctIndex = 0
        ),
        Lesson(
            id = "restraint",
            kind = LessonKind.Word,
            headline = "Restraint",
            meaning = "Holding yourself back when you could easily not.",
            example = "He said nothing, which took more restraint than shouting would have.",
            question = "Showing restraint means you…",
            options = listOf("Held back on purpose", "Lost your temper", "Gave up entirely"),
            correctIndex = 0
        ),
        Lesson(
            id = "urge",
            kind = LessonKind.Word,
            headline = "Urge",
            meaning = "A strong sudden want. It rises, and it also passes.",
            example = "The urge came at midnight and was gone by ten past.",
            question = "An urge is best described as…",
            options = listOf("A strong passing impulse", "A long-term plan", "A written promise"),
            correctIndex = 0
        ),
        Lesson(
            id = "consistent",
            kind = LessonKind.Word,
            headline = "Consistent",
            meaning = "The same, again and again, over time.",
            example = "He isn't the fastest. He's just consistent, and it adds up.",
            question = "Being consistent means doing something…",
            options = listOf("Regularly over time", "Once, perfectly", "Only when you feel like it"),
            correctIndex = 0
        ),
        Lesson(
            id = "patience",
            kind = LessonKind.Word,
            headline = "Patience",
            meaning = "Waiting without letting the waiting ruin you.",
            example = "Patience isn't doing nothing. It's doing something else while you wait.",
            question = "Patience is the ability to…",
            options = listOf("Wait calmly", "Rush ahead", "Complain loudly"),
            correctIndex = 0
        ),
        Lesson(
            id = "sleep-on-it",
            kind = LessonKind.Idiom,
            headline = "Sleep on it",
            meaning = "Wait until tomorrow before deciding.",
            example = "Don't answer tonight. Sleep on it and tell them in the morning.",
            question = "\"Sleep on it\" is advice to…",
            options = listOf("Decide tomorrow instead", "Decide immediately", "Never decide"),
            correctIndex = 0
        ),
        Lesson(
            id = "bite-the-bullet",
            kind = LessonKind.Idiom,
            headline = "Bite the bullet",
            meaning = "Do the hard thing you've been avoiding.",
            example = "I bit the bullet and finally called the dentist.",
            question = "To \"bite the bullet\" means to…",
            options = listOf("Face something difficult", "Run away from it", "Wait for someone else"),
            correctIndex = 0
        ),
        Lesson(
            id = "turn-a-corner",
            kind = LessonKind.Idiom,
            headline = "Turn a corner",
            meaning = "Pass the worst point, so things start improving.",
            example = "Week three was awful. After that we turned a corner.",
            question = "If you've \"turned a corner\", things are…",
            options = listOf("Starting to get better", "Getting much worse", "Exactly the same"),
            correctIndex = 0
        ),
        Lesson(
            id = "in-the-long-run",
            kind = LessonKind.Idiom,
            headline = "In the long run",
            meaning = "When you look at it over a long stretch of time.",
            example = "It's slower now, but cheaper in the long run.",
            question = "\"In the long run\" refers to…",
            options = listOf("Over a long period", "Within one hour", "Never"),
            correctIndex = 0
        ),
        Lesson(
            id = "second-wind",
            kind = LessonKind.Idiom,
            headline = "Second wind",
            meaning = "Fresh energy that arrives after you thought you had none left.",
            example = "I was ready to quit at 9pm, then got a second wind.",
            question = "Getting a \"second wind\" means…",
            options = listOf("New energy returns", "You fall asleep", "You give up"),
            correctIndex = 0
        ),
        Lesson(
            id = "cold-turkey",
            kind = LessonKind.Idiom,
            headline = "Go cold turkey",
            meaning = "Stop something all at once, with no tapering off.",
            example = "He went cold turkey on late-night scrolling and slept better within a week.",
            question = "Going \"cold turkey\" means stopping…",
            options = listOf("Suddenly and completely", "Slowly over months", "Only on weekends"),
            correctIndex = 0
        ),
        Lesson(
            id = "story-marathon",
            kind = LessonKind.Story,
            headline = "The runner who slowed down",
            meaning = "A short read, about ninety seconds.",
            example = "A first-time marathoner kept passing people in the first hour, then " +
                "collapsed at the twenty-fifth kilometre. The next year he ran the first hour " +
                "so slowly that strangers offered him water out of pity. He finished that one. " +
                "Nothing about him had changed except the first hour.",
            question = "What changed between his two races?",
            options = listOf("He started slower", "He trained less", "He ran a shorter race"),
            correctIndex = 0
        ),
        Lesson(
            id = "story-violin",
            kind = LessonKind.Story,
            headline = "Twenty minutes a day",
            meaning = "A short read, about ninety seconds.",
            example = "A woman started violin at forty-one, convinced she was too late. She gave " +
                "it twenty minutes a day — less than most people spend deciding what to watch. " +
                "Four years on she plays in a small orchestra. She is not gifted. She is just " +
                "four years of twenty minutes.",
            question = "What made the difference for her?",
            options = listOf("Small daily practice", "Natural talent", "Starting young"),
            correctIndex = 0
        ),
        Lesson(
            id = "story-wave",
            kind = LessonKind.Story,
            headline = "Surfers call it a set",
            meaning = "A short read, about sixty seconds.",
            example = "Surfers know waves arrive in sets, and that between sets the water goes " +
                "flat. Beginners panic in the churn and swim hard. Experienced surfers do " +
                "nothing — they float, because they know the flat part is coming. The urge " +
                "works the same way. It is a set, not the sea.",
            question = "What do experienced surfers do between sets?",
            options = listOf("Wait, because it passes", "Swim as hard as possible", "Leave the water"),
            correctIndex = 0
        ),
        Lesson(
            id = "candid",
            kind = LessonKind.Word,
            headline = "Candid",
            meaning = "Honest, even when honesty is a bit uncomfortable.",
            example = "Can I be candid? That plan won't survive contact with Monday.",
            question = "A candid answer is…",
            options = listOf("Honest and direct", "Polite but false", "Deliberately vague"),
            correctIndex = 0
        ),
        Lesson(
            id = "inevitable",
            kind = LessonKind.Word,
            headline = "Inevitable",
            meaning = "Certain to happen; impossible to avoid.",
            example = "A slip at some point is close to inevitable. Quitting after it isn't.",
            question = "Something inevitable is…",
            options = listOf("Certain to happen", "Very unlikely", "Already finished"),
            correctIndex = 0
        ),
        Lesson(
            id = "worthwhile",
            kind = LessonKind.Word,
            headline = "Worthwhile",
            meaning = "Worth the time, effort or trouble it costs.",
            example = "It was a long drive, but a worthwhile one.",
            question = "If something is worthwhile, it is…",
            options = listOf("Worth the effort", "A waste of time", "Free of cost"),
            correctIndex = 0
        ),
        Lesson(
            id = "get-a-grip",
            kind = LessonKind.Idiom,
            headline = "Get a grip",
            meaning = "Regain control of yourself.",
            example = "I stood up, drank some water, and got a grip.",
            question = "\"Get a grip\" means…",
            options = listOf("Regain self-control", "Hold something tightly", "Ask for help"),
            correctIndex = 0
        ),
        Lesson(
            id = "clean-slate",
            kind = LessonKind.Idiom,
            headline = "A clean slate",
            meaning = "A fresh start, with past mistakes set aside.",
            example = "New month, clean slate.",
            question = "A \"clean slate\" is…",
            options = listOf("A fresh start", "A final warning", "A long punishment"),
            correctIndex = 0
        ),
        Lesson(
            id = "diminishing",
            kind = LessonKind.Word,
            headline = "Diminishing",
            meaning = "Getting smaller or weaker over time.",
            example = "Each time you don't act on it, the urge has diminishing power.",
            question = "Something diminishing is…",
            options = listOf("Getting weaker", "Getting stronger", "Staying identical"),
            correctIndex = 0
        ),
        Lesson(
            id = "compound",
            kind = LessonKind.Word,
            headline = "Compound",
            meaning = "To grow by building on top of what already grew.",
            example = "Small habits compound; that's why week six feels easier than week one.",
            question = "When gains compound, they…",
            options = listOf("Build on each other", "Cancel each other out", "Disappear overnight"),
            correctIndex = 0
        ),
        Lesson(
            id = "story-fence",
            kind = LessonKind.Story,
            headline = "The fence at the top of the cliff",
            meaning = "A short read, about sixty seconds.",
            example = "A village kept sending ambulances to the bottom of a cliff where people " +
                "fell. Someone finally suggested a fence at the top. It was cheaper than one " +
                "ambulance and it ended the problem. Most self-control is a fence, not an " +
                "ambulance — you build it on a calm afternoon, not during the fall.",
            question = "What is the point of the fence?",
            options = listOf("Prevent the problem in advance", "Treat it faster afterwards", "Ignore it"),
            correctIndex = 0
        ),
        Lesson(
            id = "tide-over",
            kind = LessonKind.Idiom,
            headline = "Tide you over",
            meaning = "Enough to get you through a short gap.",
            example = "Have a snack to tide you over until dinner.",
            question = "Something that \"tides you over\" gets you through…",
            options = listOf("A short gap", "The rest of your life", "A written exam"),
            correctIndex = 0
        )
    )

    private val byId = all.associateBy { it.id }

    fun byId(id: String): Lesson? = byId[id]

    /**
     * Picks the lesson for this moment. Stable within the same minute so a page
     * with several blocked requests shows one lesson rather than flickering
     * through a new one per request.
     */
    fun forMoment(seenIds: Set<String> = emptySet(), nowMs: Long = System.currentTimeMillis()): Lesson {
        val unseen = all.filterNot { seenIds.contains(it.id) }
        val pool = if (unseen.isNotEmpty()) unseen else all
        val bucket = (nowMs / 60_000L).toInt()
        return pool[Math.floorMod(bucket, pool.size)]
    }

    /** A question drawn from what the user has already been shown. */
    fun quizFrom(seenIds: Set<String>, nowMs: Long = System.currentTimeMillis()): Lesson {
        val seen = all.filter { seenIds.contains(it.id) }
        val pool = if (seen.isNotEmpty()) seen else all
        return pool[Math.floorMod((nowMs / 1000L).toInt(), pool.size)]
    }

    val total: Int get() = all.size
}
