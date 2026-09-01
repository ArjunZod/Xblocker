package com.antigravity.shieldx.assistant.intent

import com.antigravity.shieldx.core.model.ToolRequest

/**
 * Natural language intent parser mapping voice and text commands into typed ToolRequests.
 */
class IntentEngine {

    fun parse(input: String): List<ToolRequest> {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return emptyList()

        // Split multi-step instructions ("and then", "and", ";")
        val segments = splitMultiStep(trimmed)
        return segments.mapNotNull { parseSingleSegment(it) }
    }

    private fun splitMultiStep(input: String): List<String> {
        // Strip the wake word however the recogniser rendered it.
        var normalized = input
        for (variant in com.antigravity.shieldx.assistant.system.WakeWordEngine.WAKE_NAMES) {
            normalized = normalized.replace(variant, "", ignoreCase = true)
        }
        normalized = normalized.trim()
        if (normalized.startsWith(",")) normalized = normalized.removePrefix(",").trim()

        return normalized.split(Regex("\\s+and\\s+then\\s+|\\s+and\\s+also\\s+|\\s+and\\s+|\\s+then\\s+|;"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun parseSingleSegment(text: String): ToolRequest? {
        val lower = text.lowercase()

        return when {
            // External App Launching
            lower.startsWith("open ") || lower.startsWith("launch ") || lower.startsWith("start ") -> {
                val appName = text.substring(text.indexOf(' ') + 1).trim()
                ToolRequest("OPEN_APP", mapOf("appName" to appName))
            }

            // Calling Contacts
            lower.startsWith("call ") || lower.startsWith("dial ") || lower.startsWith("phone ") -> {
                val raw = text.substring(text.indexOf(' ') + 1).trim()
                val simMatch = Regex("(?i)\\s+(?:using|on|with|from|via)\\s+sim\\s*([12])").find(raw)
                val sim = simMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
                val contact = if (simMatch != null) {
                    raw.substring(0, simMatch.range.first).trim()
                } else raw
                val params = mutableMapOf<String, Any>("name" to contact)
                if (sim != null) params["sim"] = sim
                ToolRequest("CALL_CONTACT", params)
            }

            // Flashlight / Torch
            lower.contains("flashlight on") || lower.contains("turn on flashlight") || lower.contains("torch on") || lower.contains("turn on torch") || lower.contains("enable torch") -> {
                ToolRequest("SET_FLASHLIGHT", mapOf("enable" to true))
            }
            lower.contains("flashlight off") || lower.contains("turn off flashlight") || lower.contains("torch off") || lower.contains("turn off torch") || lower.contains("disable torch") -> {
                ToolRequest("SET_FLASHLIGHT", mapOf("enable" to false))
            }

            // Volume Controls
            lower.contains("volume") -> {
                val percentMatch = Regex("(\\d+)").find(lower)?.value?.toIntOrNull()
                when {
                    lower.contains("mute") || lower.contains("zero") -> ToolRequest("SET_VOLUME", mapOf("levelPercent" to 0))
                    lower.contains("max") || lower.contains("full") -> ToolRequest("SET_VOLUME", mapOf("levelPercent" to 100))
                    lower.contains("up") || lower.contains("increase") -> ToolRequest("SET_VOLUME", mapOf("levelPercent" to 80))
                    lower.contains("down") || lower.contains("decrease") -> ToolRequest("SET_VOLUME", mapOf("levelPercent" to 30))
                    percentMatch != null -> ToolRequest("SET_VOLUME", mapOf("levelPercent" to percentMatch))
                    else -> ToolRequest("SET_VOLUME", mapOf("levelPercent" to 70))
                }
            }

            // Reminders: "remind me to call Mom at 8 PM"
            lower.startsWith("remind me") || lower.contains("set a reminder") -> {
                val timeMatch = Regex("\\bat\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?").find(lower)
                var hour = timeMatch?.groupValues?.get(1)?.toIntOrNull() ?: 9
                val minute = timeMatch?.groupValues?.get(2)?.toIntOrNull() ?: 0
                val ampm = timeMatch?.groupValues?.get(3)
                if (ampm == "pm" && hour < 12) hour += 12
                if (ampm == "am" && hour == 12) hour = 0

                // Whatever sits between "remind me to" and the time is the subject.
                val what = text
                    .replace(Regex("^remind me( to)?", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("\\bat\\s+\\d{1,2}(?::\\d{2})?\\s*(am|pm)?", RegexOption.IGNORE_CASE), "")
                    .trim()
                    .trim(',', '.')
                    .ifBlank { "Reminder" }

                ToolRequest(
                    "CREATE_REMINDER",
                    mapOf("text" to what, "hour" to hour, "minute" to minute)
                )
            }

            // Network profiles: "connect to my home setup"
            lower.contains("setup") || lower.contains("network profile") ||
                (lower.startsWith("connect to") && lower.contains("my")) -> {
                val profile = Regex("my\\s+(\\w+)\\s+(setup|network|profile)").find(lower)
                    ?.groupValues?.get(1)
                    ?: "home"
                ToolRequest("SET_NETWORK_PROFILE", mapOf("profileName" to profile))
            }

            // Alarms & Timers
            lower.contains("set alarm") || lower.contains("alarm for") -> {
                val hourMatch = Regex("(\\d+)(?::(\\d+))?\\s*(am|pm)?").find(lower)
                var hour = hourMatch?.groupValues?.get(1)?.toIntOrNull() ?: 7
                val minute = hourMatch?.groupValues?.get(2)?.toIntOrNull() ?: 0
                val ampm = hourMatch?.groupValues?.get(3)
                if (ampm == "pm" && hour < 12) hour += 12
                if (ampm == "am" && hour == 12) hour = 0
                ToolRequest("SET_ALARM", mapOf("hour" to hour, "minute" to minute, "message" to "TaRZI Alarm"))
            }
            lower.contains("set timer") || lower.contains("timer for") -> {
                val minMatch = Regex("(\\d+)\\s*(min|minute|sec|second)").find(lower)
                val count = minMatch?.groupValues?.get(1)?.toIntOrNull() ?: 5
                val unit = minMatch?.groupValues?.get(2) ?: "min"
                val seconds = if (unit.startsWith("sec")) count else count * 60
                ToolRequest("SET_TIMER", mapOf("seconds" to seconds, "message" to "TaRZI Timer"))
            }

            // WhatsApp / SMS Messaging
            lower.startsWith("whatsapp ") || lower.contains("send whatsapp") -> {
                val rest = text.replace(Regex("^(whatsapp|send whatsapp to) ", RegexOption.IGNORE_CASE), "").trim()
                val parts = rest.split(" that ", " saying ", " : ", limit = 2)
                val recipient = parts[0].trim()
                val msg = if (parts.size > 1) parts[1].trim() else "Hello"
                ToolRequest("SEND_MESSAGE", mapOf("name" to recipient, "message" to msg, "useWhatsApp" to true))
            }
            lower.startsWith("message ") || lower.startsWith("text ") || lower.startsWith("sms ") -> {
                val rest = text.substring(text.indexOf(' ') + 1).trim()
                val parts = rest.split(" that ", " saying ", " : ", limit = 2)
                val recipient = parts[0].trim()
                val msg = if (parts.size > 1) parts[1].trim() else "Hello"
                ToolRequest("SEND_MESSAGE", mapOf("name" to recipient, "message" to msg, "useWhatsApp" to false))
            }

            // Web Search
            lower.startsWith("search web for ") || lower.startsWith("search google for ") || lower.startsWith("google ") -> {
                val query = text.replace(Regex("^(search web for|search google for|google) ", RegexOption.IGNORE_CASE), "").trim()
                ToolRequest("SEARCH_WEB", mapOf("query" to query))
            }

            // Protection Family
            lower.contains("block porn") || lower.contains("block explicit") || lower.contains("enable maximum protection") -> {
                ToolRequest("SET_PROTECTION_PROFILE", mapOf("profile" to "MAXIMUM", "blockPorn" to true))
            }
            lower.contains("enable protection") || lower.contains("turn on protection") -> {
                ToolRequest("ENABLE_PROTECTION", mapOf("enabled" to true))
            }
            lower.contains("disable protection") || lower.contains("pause protection") || lower.contains("turn off protection") -> {
                ToolRequest("ENABLE_PROTECTION", mapOf("enabled" to false))
            }
            lower.contains("protection status") || lower.contains("is protection active") || lower.contains("security status") -> {
                ToolRequest("GET_PROTECTION_STATUS")
            }
            lower.contains("show blocked") || lower.contains("blocked activity") || lower.contains("blocked events") -> {
                ToolRequest("SHOW_BLOCKED_EVENTS")
            }

            // Music Family
            lower.contains("liked songs") || lower == "play liked" || lower == "play my liked" -> {
                ToolRequest("PLAY_LIKED")
            }
            lower.startsWith("play ") -> {
                val query = text.substring(5).trim()
                ToolRequest("PLAY", mapOf("query" to query))
            }
            lower.startsWith("search youtube music for ") || lower.startsWith("search music for ") -> {
                val query = text.replace(Regex("^(search youtube music for|search music for)\\s+", RegexOption.IGNORE_CASE), "").trim()
                ToolRequest("PLAY", mapOf("query" to query))
            }
            lower == "play" || lower == "resume" || lower == "resume music" -> {
                ToolRequest("RESUME")
            }
            lower == "pause" || lower == "pause music" || lower == "stop music" -> {
                ToolRequest("PAUSE")
            }
            lower == "next" || lower == "next song" || lower == "skip" || lower == "skip this" -> {
                ToolRequest("NEXT")
            }
            lower == "previous" || lower == "previous song" -> {
                ToolRequest("PREVIOUS")
            }
            lower.contains("shuffle") -> {
                ToolRequest("SHUFFLE")
            }
            lower.contains("repeat") -> {
                ToolRequest("REPEAT")
            }
            lower.contains("like this") || lower.contains("save this song") || lower.contains("save song") || lower.contains("add to favorites") -> {
                ToolRequest("LIKE")
            }
            lower.contains("what's playing") || lower.contains("what is playing") || lower.contains("what song is this") || lower.contains("current song") -> {
                ToolRequest("WHAT_IS_PLAYING")
            }

            // Device & Telemetry Queries
            lower.contains("battery") -> ToolRequest("GET_BATTERY")
            lower.contains("storage") -> ToolRequest("GET_STORAGE")
            lower.contains("open settings") -> ToolRequest("OPEN_SETTINGS")
            lower.contains("what wifi") || lower.contains("wifi status") || lower.contains("network status") -> {
                ToolRequest("GET_WIFI")
            }

            // Memory Family
            lower.startsWith("remember ") -> {
                val content = text.substring(9).trim()
                val parts = content.split(" is ", " that ", limit = 2)
                var key = if (parts.size > 1) parts[0].trim() else "user_fact"
                if (key.startsWith("my ", ignoreCase = true)) {
                    key = key.substring(3).trim()
                }
                val value = if (parts.size > 1) parts[1].trim() else parts[0].trim()
                ToolRequest("MEMORY_WRITE", mapOf("key" to key, "value" to value))
            }
            lower.startsWith("what is my ") || lower.startsWith("what's my ") -> {
                val key = text.replace(Regex("what('s| is) my ", RegexOption.IGNORE_CASE), "").trim().removeSuffix("?")
                ToolRequest("MEMORY_READ", mapOf("key" to key))
            }

            // Conversational & General Family
            lower.contains("hello") || lower.contains("hi ") || lower == "hi" || lower.contains("hey") ||
            lower.contains("how are you") || lower.contains("who are you") || lower.contains("what can you do") || lower.contains("help") -> {
                ToolRequest("GENERAL_QUERY", mapOf("query" to text))
            }

            else -> {
                // If it looks like a query, search or general query
                ToolRequest("GENERAL_QUERY", mapOf("query" to text))
            }
        }
    }
}
