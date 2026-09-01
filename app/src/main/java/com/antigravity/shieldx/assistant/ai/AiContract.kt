package com.antigravity.shieldx.assistant.ai

import com.antigravity.shieldx.core.model.ToolRequest

/**
 * What any model backend returns.
 *
 * [failed] exists so a caller can tell "the model chose to say nothing" apart
 * from "the call did not work". Without that distinction a rejected key looks
 * identical to a thoughtful silence, and the assistant ends up speaking error
 * text instead of falling back to its offline commands.
 */
data class AiResponse(
    val replyText: String,
    val toolRequests: List<ToolRequest> = emptyList(),
    val failed: Boolean = false
)

/** A language-model backend Tarzi can reason with. */
interface AiBackend {

    /** Human-readable name, shown in settings. */
    val displayName: String

    suspend fun processUserQuery(
        query: String,
        conversationHistory: List<Pair<String, String>> = emptyList(),
        screenFrameBase64: String? = null
    ): AiResponse

    /** Probe the configured credentials so settings can report real status. */
    suspend fun verifyCredentials(): String
}

/**
 * The tools every backend exposes, described once.
 *
 * Both backends send the same capability list, so switching provider never
 * silently changes what the assistant can do.
 */
object AiToolCatalog {

    data class Param(val name: String, val type: String, val description: String)
    data class Tool(val name: String, val description: String, val params: List<Param>)

    private fun p(name: String, type: String, description: String) = Param(name, type, description)

    val tools: List<Tool> = listOf(
        Tool("open_app", "Launch an installed application by name", listOf(
            p("appName", "string", "Name of the app, for example YouTube or WhatsApp")
        )),
        Tool("call_contact", "Call a contact by name or a phone number", listOf(
            p("name", "string", "Contact name or phone number")
        )),
        Tool("send_message", "Send an SMS or WhatsApp message", listOf(
            p("name", "string", "Contact name or number"),
            p("message", "string", "Message text"),
            p("useWhatsApp", "boolean", "True for WhatsApp, false for SMS")
        )),
        Tool("set_flashlight", "Turn the torch on or off", listOf(
            p("enable", "boolean", "True to turn on")
        )),
        Tool("set_volume", "Set media volume", listOf(
            p("levelPercent", "integer", "Volume from 0 to 100")
        )),
        Tool("set_alarm", "Set an alarm", listOf(
            p("hour", "integer", "Hour in 24-hour time"),
            p("minute", "integer", "Minute"),
            p("message", "string", "Alarm label")
        )),
        Tool("set_timer", "Start a countdown timer", listOf(
            p("seconds", "integer", "Duration in seconds"),
            p("message", "string", "Timer label")
        )),
        Tool("create_reminder", "Create a reminder at a time", listOf(
            p("text", "string", "What to be reminded of"),
            p("hour", "integer", "Hour in 24-hour time"),
            p("minute", "integer", "Minute")
        )),
        Tool("play_music", "Search for and play a song or artist", listOf(
            p("query", "string", "Song title or artist")
        )),
        Tool("pause_music", "Pause playback", emptyList()),
        Tool("resume_music", "Resume playback", emptyList()),
        Tool("search_web", "Run a web search", listOf(
            p("query", "string", "Search terms")
        )),
        Tool("get_battery_status", "Report battery level and charging state", emptyList()),
        Tool("get_storage_status", "Report free and total storage", emptyList()),
        Tool("set_protection_profile", "Set content filtering to MAXIMUM, BALANCED or OFF", listOf(
            p("profile", "string", "MAXIMUM, BALANCED or OFF")
        )),
        Tool("remember_fact", "Save a fact about the user", listOf(
            p("key", "string", "Subject"),
            p("value", "string", "Value to remember")
        )),
        Tool("recall_memory", "Recall a previously saved fact", listOf(
            p("key", "string", "Subject to recall")
        )),

        // Screen control, acting inside whatever app is open.
        Tool("open_item", "Open the Nth video, result or item on the current screen", listOf(
            p("ordinal", "integer", "1 for the first item, 2 for the second")
        )),
        Tool("tap_text", "Tap an on-screen element by its visible label", listOf(
            p("text", "string", "The visible text to tap")
        )),
        Tool("scroll_screen", "Scroll the current screen", listOf(
            p("direction", "string", "up or down")
        )),
        Tool("go_back", "Press the system back button", emptyList()),
        Tool("go_home", "Go to the home screen", emptyList()),
        Tool("read_screen", "Read the text currently on screen", emptyList()),
        Tool("type_text", "Type into the focused field", listOf(
            p("text", "string", "Text to type")
        )),
        Tool("get_foreground_app", "Report which app is open", emptyList())
    )

    /** Map a model's function name onto the registered tool name. */
    fun toToolName(functionName: String): String = when (functionName.lowercase()) {
        "open_app" -> "OPEN_APP"
        "call_contact" -> "CALL_CONTACT"
        "send_message" -> "SEND_MESSAGE"
        "set_flashlight" -> "SET_FLASHLIGHT"
        "set_volume" -> "SET_VOLUME"
        "set_alarm" -> "SET_ALARM"
        "set_timer" -> "SET_TIMER"
        "create_reminder" -> "CREATE_REMINDER"
        "play_music" -> "PLAY"
        "pause_music" -> "PAUSE"
        "resume_music" -> "RESUME"
        "search_web" -> "SEARCH_WEB"
        "get_battery_status" -> "GET_BATTERY"
        "get_storage_status" -> "GET_STORAGE"
        "set_protection_profile" -> "SET_PROTECTION_PROFILE"
        "remember_fact" -> "MEMORY_WRITE"
        "recall_memory" -> "MEMORY_READ"
        else -> functionName.uppercase()
    }

    /**
     * The assistant's character. Written as Tarzi, a sharp, Jarvis-like personal AI assistant.
     */
    const val SYSTEM_PROMPT = """
You are Tarzi, the user's personal AI assistant running on their Android phone.

Identity:
- Your name is Tarzi. When asked who you are, always state clearly: "I am Tarzi, your personal AI assistant."
- Never refer to yourself as a generic text-to-speech engine or language model. You are Tarzi.

Tone & Style:
- Speak naturally, sharply, and warmly like Jarvis.
- Keep voice replies concise and direct (1 to 2 short sentences).
- Avoid markdown symbols, asterisks, bullet points, and emoji since replies are spoken aloud.

Action & Tools:
- When the user asks you to perform an action, immediately invoke the appropriate tool.
- You have access to on-screen context, device controls (flashlight, volume, apps, calls), and media playback.
- If you cannot perform an action, briefly explain why.
"""
}
