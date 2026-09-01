package com.antigravity.shieldx.assistant

import android.util.Log
import com.antigravity.shieldx.assistant.ai.AiBackend
import com.antigravity.shieldx.assistant.ai.DeepSeekAiService
import com.antigravity.shieldx.assistant.ai.GeminiAiService
import com.antigravity.shieldx.assistant.system.ScreenVisionService
import com.antigravity.shieldx.assistant.system.TarziAccessibilityService
import com.antigravity.shieldx.core.model.ToolRequest
import com.antigravity.shieldx.core.security.SecurityManager
import java.util.Locale

/**
 * What Tarzi decided to do about one utterance.
 */
data class BrainResponse(
    val spokenReply: String,
    val displayText: String = spokenReply,
    val toolRequests: List<ToolRequest> = emptyList(),
    val pendingConfirmation: ToolRequest? = null,
    val confirmationPrompt: String? = null
)

/**
 * The single reasoning path for every Tarzi utterance, wherever it came from -
 * the in-app screen, the floating bubble, or the background wake word.
 *
 * Routing order is deliberate:
 *  1. On-screen control commands, matched locally. These must never depend on the
 *     network, because "go back" should not need a round trip.
 *  2. The cloud model, when a key is configured, with live screen context attached.
 *  3. The on-device IntentEngine as a final offline fallback.
 */
class TarziBrain(
    private val securityManager: SecurityManager
) {

    companion object {
        private const val TAG = "TarziBrain"

        /** Spoken ordinals mapped to their 1-based position. */
        private val ORDINALS = mapOf(
            "first" to 1, "1st" to 1, "one" to 1,
            "second" to 2, "2nd" to 2, "two" to 2,
            "third" to 3, "3rd" to 3, "three" to 3,
            "fourth" to 4, "4th" to 4, "four" to 4,
            "fifth" to 5, "5th" to 5, "five" to 5,
            "last" to -1
        )
    }

    private val deepSeek = DeepSeekAiService(
        getApiKey = { securityManager.configRepository.get("deepseek_api_key") },
        getModel = { securityManager.configRepository.get("deepseek_model") }
    )

    private val gemini = GeminiAiService(
        getApiKey = { securityManager.configRepository.get("gemini_api_key") },
        getModel = { securityManager.configRepository.get("gemini_model") }
    )

    /**
     * Whichever backend actually has a key. DeepSeek is preferred because it is
     * the one currently configured and it returns real tool calls; Gemini stays
     * available for its image input.
     */
    private suspend fun activeBackend(): AiBackend? {
        val deepSeekKey = securityManager.configRepository.get("deepseek_api_key")
        if (!deepSeekKey.isNullOrBlank()) return deepSeek
        val geminiKey = securityManager.configRepository.get("gemini_api_key")
        if (!geminiKey.isNullOrBlank()) return gemini
        return null
    }

    private val history = mutableListOf<Pair<String, String>>()

    /**
     * Process one utterance end to end and return what Tarzi should say and do.
     * Tool execution happens here so callers only render the outcome.
     */
    suspend fun process(query: String): BrainResponse {
        if (query.isBlank()) return BrainResponse("I did not catch that.")
        Log.i(TAG, "[BRAIN_QUERY] " + query)

        history.add("USER" to query)
        if (history.size > 20) history.removeAt(0)

        val response = route(query)
        history.add("TARZI" to response.spokenReply)
        return response
    }

    suspend fun executeConfirmedTool(request: ToolRequest): BrainResponse {
        return executeAll(listOf(request))
    }

    private suspend fun route(query: String): BrainResponse {
        // 1. Instant local conversational & identity match (0ms latency).
        matchInstantConversation(query)?.let { return it }

        // 2. On-device memory & user fact learning / recall.
        matchMemory(query)?.let { return it }

        // 3. On-device hardware & system computing (RAM, storage, battery, time, math).
        matchHardwareAndCompute(query)?.let { return it }

        // 4. Local screen-control fast path.
        parseScreenCommand(query)?.let { request ->
            return executeAll(listOf(request))
        }

        // 5. Local high-priority device commands (flashlight, volume, playback toggles)
        val localRequests = securityManager.intentEngine.parse(query)
        val isFastLocalAction = localRequests.isNotEmpty() && localRequests.all { req ->
            req.toolName in setOf("SET_FLASHLIGHT", "SET_VOLUME", "PAUSE", "RESUME", "GET_BATTERY", "GET_STORAGE")
        }
        if (isFastLocalAction) {
            return executeAll(localRequests)
        }

        // 6. Cloud reasoning, with live screen & memory context attached.
        val backend = activeBackend()
        if (backend != null) {
            val enriched = attachScreenContext(query)
            val frame = if (backend === gemini) captureScreenFrame() else null
            val ai = backend.processUserQuery(enriched, history.toList(), frame)

            if (!ai.failed) {
                if (ai.toolRequests.isNotEmpty()) {
                    return executeAll(ai.toolRequests, ai.replyText)
                }
                if (ai.replyText.isNotBlank()) {
                    return BrainResponse(ai.replyText)
                }
            }
        }

        // 7. Offline intent parsing fallback.
        if (localRequests.isEmpty()) {
            return BrainResponse(
                "I am Tarzi. I heard you, but I do not have a tool configured for that query.",
                displayText = "No matching tool for: \"$query\""
            )
        }
        return executeAll(localRequests)
    }

    private fun matchInstantConversation(query: String): BrainResponse? {
        val q = query.trim().lowercase(Locale.US).replace(Regex("[?!.,]"), "")
        return when {
            q == "who are you" || q == "what is your name" || q == "what are you" || q == "who are u" ||
            q.contains("your name") || q.contains("who made you") || q.contains("who is this") -> {
                BrainResponse("I am Tarzi, your personal AI assistant. I'm running right here on your device with active memory and system intelligence.")
            }
            q in listOf("hi", "hello", "hey", "hey tarzi", "hello tarzi", "hi tarzi", "good morning", "good afternoon", "good evening", "what's up", "how are you", "how are you doing") -> {
                BrainResponse("Hello! Tarzi online and ready. What can I do for you?")
            }
            q == "what can you do" || q == "help" || q == "commands" || q == "what do you do" -> {
                BrainResponse("I can play music, launch apps, call contacts, set alarms and timers, toggle the flashlight, monitor device RAM and storage, calculate math, remember facts, and enforce device protection. What do you need?")
            }
            q in listOf("thank you", "thanks", "thanks tarzi", "thank you tarzi") -> {
                BrainResponse("You're very welcome! Let me know if you need anything else.")
            }
            else -> null
        }
    }

    private suspend fun matchMemory(query: String): BrainResponse? {
        val trimmed = query.trim()
        val lower = trimmed.lowercase(Locale.US)

        // Store user name: "my name is Alex"
        val nameMatch = Regex("^(?:my name is|call me|i am)\\s+([a-zA-Z0-9 ]+)", RegexOption.IGNORE_CASE).find(trimmed)
        if (nameMatch != null && !lower.startsWith("i am tarzi") && !lower.startsWith("i am busy")) {
            val name = nameMatch.groupValues[1].trim().trim(',', '.')
            if (name.isNotEmpty() && name.split(" ").size <= 4) {
                securityManager.memoryManager.saveFact("user_name", name, "PROFILE")
                return BrainResponse("Pleasure to meet you, $name. I have stored your name in my persistent memory.")
            }
        }

        // Store favorite: "my favorite music is synthwave"
        val favMatch = Regex("^(?:my favorite|my fav|i love)\\s+([a-zA-Z0-9 ]+?)\\s+is\\s+([a-zA-Z0-9 ]+)", RegexOption.IGNORE_CASE).find(trimmed)
        if (favMatch != null) {
            val category = favMatch.groupValues[1].trim()
            val value = favMatch.groupValues[2].trim().trim(',', '.')
            securityManager.memoryManager.saveFact("favorite_$category", value, "PREFERENCE")
            return BrainResponse("Got it! I will remember that your favorite $category is $value.")
        }

        // General fact storage: "remember that my car is parked at level 2"
        val rememberMatch = Regex("^remember(?: that)?\\s+(.+)", RegexOption.IGNORE_CASE).find(trimmed)
        if (rememberMatch != null) {
            val fact = rememberMatch.groupValues[1].trim()
            val key = "fact_" + fact.take(20).replace(Regex("[^a-zA-Z0-9]"), "_").lowercase(Locale.US)
            securityManager.memoryManager.saveFact(key, fact, "NOTE")
            return BrainResponse("I have saved that to memory: \"$fact\"")
        }

        // Recall user name: "who am i" / "what is my name"
        if (lower.contains("who am i") || lower.contains("what is my name") || lower.contains("what's my name")) {
            val name = securityManager.memoryManager.recallFact("user_name")
            return if (name != null) {
                BrainResponse("Your name is $name, according to my memory.")
            } else {
                BrainResponse("I don't know your name yet. You can tell me by saying \"My name is...\"")
            }
        }

        // Recall favorite: "what is my favorite music"
        val favQueryMatch = Regex("^(?:what is|what's)\\s+my\\s+favorite\\s+([a-zA-Z0-9 ]+)", RegexOption.IGNORE_CASE).find(trimmed)
        if (favQueryMatch != null) {
            val cat = favQueryMatch.groupValues[1].trim().replace(Regex("[?!.]"), "")
            val value = securityManager.memoryManager.recallFact("favorite_$cat")
            return if (value != null) {
                BrainResponse("Your favorite $cat is $value.")
            } else {
                BrainResponse("I don't have your favorite $cat saved yet.")
            }
        }

        // Recall all memory: "what do you remember about me" / "show memory"
        if (lower.contains("what do you remember") || lower == "show memory" || lower == "list memory" || lower.contains("what is in your memory")) {
            val facts = securityManager.memoryManager.searchFacts("")
            return if (facts.isNotEmpty()) {
                val list = facts.joinToString("; ") { "${it.key.replace('_', ' ')}: ${it.value}" }
                BrainResponse("Here is what I have saved in memory: $list", displayText = "Stored Memory:\n" + facts.joinToString("\n") { "• ${it.key}: ${it.value}" })
            } else {
                BrainResponse("My persistent memory is currently empty.")
            }
        }

        // Forget fact: "forget my favorite music"
        val forgetMatch = Regex("^forget(?: my)?\\s+(.+)", RegexOption.IGNORE_CASE).find(trimmed)
        if (forgetMatch != null) {
            val item = forgetMatch.groupValues[1].trim().replace(Regex("[?!.]"), "")
            val key = if (item.startsWith("favorite")) "favorite_" + item.removePrefix("favorite").trim() else item
            val success = securityManager.memoryManager.forgetFact(key) || securityManager.memoryManager.forgetFact(item)
            return if (success) {
                BrainResponse("I have deleted $item from memory.")
            } else {
                BrainResponse("I could not find $item in my memory records.")
            }
        }

        return null
    }

    private fun matchHardwareAndCompute(query: String): BrainResponse? {
        val lower = query.trim().lowercase(Locale.US)

        // 1. RAM and Memory Diagnostics
        if (lower.contains("ram") || lower.contains("mobile ram") || lower.contains("memory usage") || lower.contains("check ram")) {
            return try {
                val actMan = securityManager.appContext.getSystemService(android.content.Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
                val memInfo = android.app.ActivityManager.MemoryInfo()
                actMan?.getMemoryInfo(memInfo)
                val totalGb = String.format(Locale.US, "%.1f", memInfo.totalMem / (1024.0 * 1024.0 * 1024.0))
                val availGb = String.format(Locale.US, "%.1f", memInfo.availMem / (1024.0 * 1024.0 * 1024.0))
                val usedGb = String.format(Locale.US, "%.1f", (memInfo.totalMem - memInfo.availMem) / (1024.0 * 1024.0 * 1024.0))
                BrainResponse(
                    "Your device has $totalGb GB of total RAM. $usedGb GB is actively in use, and $availGb GB is free.",
                    displayText = "RAM Status:\n• Total: $totalGb GB\n• Used: $usedGb GB\n• Available: $availGb GB"
                )
            } catch (e: Exception) {
                BrainResponse("Could not read RAM details: ${e.message}")
            }
        }

        // 2. Storage
        if (lower.contains("storage") || lower.contains("disk space") || lower.contains("free space")) {
            return try {
                val statFs = android.os.StatFs(android.os.Environment.getDataDirectory().path)
                val totalGb = String.format(Locale.US, "%.1f", statFs.totalBytes / (1024.0 * 1024.0 * 1024.0))
                val freeGb = String.format(Locale.US, "%.1f", statFs.availableBytes / (1024.0 * 1024.0 * 1024.0))
                BrainResponse("You have $freeGb GB free out of $totalGb GB total internal storage.")
            } catch (e: Exception) {
                BrainResponse("Could not retrieve storage metrics.")
            }
        }

        // 3. Time & Date
        if (lower.contains("what time is it") || lower == "time" || lower == "current time") {
            val time = java.text.SimpleDateFormat("h:mm a", Locale.getDefault()).format(java.util.Date())
            return BrainResponse("It is currently $time.")
        }
        if (lower.contains("what day is it") || lower.contains("what is today's date") || lower.contains("what date is it") || lower == "date" || lower == "today") {
            val date = java.text.SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(java.util.Date())
            return BrainResponse("Today is $date.")
        }

        // 4. On-Device Math Evaluation
        evaluateMath(lower)?.let { return it }

        return null
    }

    private fun evaluateMath(lower: String): BrainResponse? {
        // Percentage: "what is 15 percent of 200"
        val pctMatch = Regex("(?:what is|calculate)?\\s*(\\d+(?:\\.\\d+)?)\\s*(?:percent|%)\\s*of\\s*(\\d+(?:\\.\\d+)?)").find(lower)
        if (pctMatch != null) {
            val pct = pctMatch.groupValues[1].toDoubleOrNull() ?: return null
            val total = pctMatch.groupValues[2].toDoubleOrNull() ?: return null
            val result = (pct / 100.0) * total
            val formatted = if (result % 1.0 == 0.0) result.toInt().toString() else String.format(Locale.US, "%.2f", result)
            return BrainResponse("$pct% of $total is $formatted.")
        }

        // Square root: "square root of 144"
        val sqrtMatch = Regex("square root of\\s*(\\d+(?:\\.\\d+)?)").find(lower)
        if (sqrtMatch != null) {
            val num = sqrtMatch.groupValues[1].toDoubleOrNull() ?: return null
            val result = kotlin.math.sqrt(num)
            val formatted = if (result % 1.0 == 0.0) result.toInt().toString() else String.format(Locale.US, "%.2f", result)
            return BrainResponse("The square root of $num is $formatted.")
        }

        // Basic arithmetic: "what is 45 * 8", "120 + 35", "500 divided by 5", "80 minus 30"
        val cleanMath = lower
            .replace("what is", "")
            .replace("calculate", "")
            .replace("times", "*")
            .replace("multiplied by", "*")
            .replace("x", "*")
            .replace("divided by", "/")
            .replace("plus", "+")
            .replace("minus", "-")
            .replace("?", "")
            .trim()

        val arithMatch = Regex("^(\\d+(?:\\.\\d+)?)\\s*([+\\-*/])\\s*(\\d+(?:\\.\\d+)?)$").find(cleanMath)
        if (arithMatch != null) {
            val a = arithMatch.groupValues[1].toDoubleOrNull() ?: return null
            val op = arithMatch.groupValues[2]
            val b = arithMatch.groupValues[3].toDoubleOrNull() ?: return null

            val res = when (op) {
                "+" -> a + b
                "-" -> a - b
                "*" -> a * b
                "/" -> if (b != 0.0) a / b else return BrainResponse("Division by zero is undefined.")
                else -> return null
            }
            val formatted = if (res % 1.0 == 0.0) res.toInt().toString() else String.format(Locale.US, "%.2f", res)
            return BrainResponse("$cleanMath = $formatted")
        }

        return null
    }

    /**
     * Run the planned tools in order, stopping at the first that needs the user
     * to confirm. Returns a spoken summary of what actually happened.
     */
    private suspend fun executeAll(
        requests: List<ToolRequest>,
        preferredReply: String = ""
    ): BrainResponse {
        val summaries = mutableListOf<String>()

        for (request in requests) {
            val result = securityManager.deterministicExecutor.execute(request)

            if (result.requiresConfirmation) {
                val prompt = result.confirmationPrompt
                    ?: "Should I go ahead with ${request.toolName.lowercase().replace('_', ' ')}?"
                return BrainResponse(
                    spokenReply = prompt,
                    displayText = prompt,
                    toolRequests = requests,
                    pendingConfirmation = request,
                    confirmationPrompt = prompt
                )
            }
            summaries.add(result.resultSummary)
        }

        // Prefer the model's own phrasing when it gave us one.
        val spoken = when {
            preferredReply.isNotBlank() -> preferredReply
            summaries.size == 1 -> summaries.first()
            summaries.isEmpty() -> "Done."
            else -> summaries.joinToString(". ")
        }

        return BrainResponse(
            spokenReply = spoken,
            displayText = if (summaries.isEmpty()) spoken else summaries.joinToString("\n"),
            toolRequests = requests
        )
    }

    // ==========================================================
    // Local screen-command parsing
    // ==========================================================

    /**
     * Match the handful of phrasings that must work offline and instantly.
     * Returns null when this is not a screen-control utterance.
     */
    private fun parseScreenCommand(raw: String): ToolRequest? {
        val q = raw.lowercase().trim()

        // "open the first video" / "play the second one" / "click the third result"
        val opensSomething = Regex(
            "\\b(open|play|click|tap|select|choose)\\b.*\\b(video|result|item|one|song|link|post|thing)\\b"
        ).containsMatchIn(q)

        if (opensSomething) {
            val ordinal = ORDINALS.entries.firstOrNull { (word, _) ->
                Regex("\\b" + Regex.escape(word) + "\\b").containsMatchIn(q)
            }?.value
            if (ordinal != null) {
                return ToolRequest("OPEN_ITEM", mapOf("ordinal" to ordinal))
            }
        }

        // Bare ordinal reference: "the first one", "second one"
        if (Regex("\\b(first|second|third|fourth|fifth)\\b\\s+(one|video|result|item)\\b").containsMatchIn(q)) {
            val ordinal = ORDINALS.entries.first { Regex("\\b" + it.key + "\\b").containsMatchIn(q) }.value
            return ToolRequest("OPEN_ITEM", mapOf("ordinal" to ordinal))
        }

        return when {
            Regex("\\b(go|navigate)?\\s*back\\b").containsMatchIn(q) && q.length < 25 ->
                ToolRequest("GO_BACK")

            Regex("\\b(go\\s+)?home\\s*(screen)?\\b").containsMatchIn(q) && q.length < 25 ->
                ToolRequest("GO_HOME")

            Regex("\\brecent\\s*(apps)?\\b").containsMatchIn(q) ->
                ToolRequest("SHOW_RECENTS")

            Regex("\\b(show|open|check)\\b.*\\bnotification").containsMatchIn(q) ->
                ToolRequest("OPEN_NOTIFICATIONS")

            Regex("\\bscroll\\b").containsMatchIn(q) -> {
                val dir = if (q.contains("up")) "up" else "down"
                ToolRequest("SCROLL_SCREEN", mapOf("direction" to dir))
            }

            Regex("\\b(what|read).*(on\\s+(the\\s+)?screen|this\\s+say)").containsMatchIn(q) ->
                ToolRequest("READ_SCREEN")

            Regex("\\b(what|which)\\s+app\\b").containsMatchIn(q) ->
                ToolRequest("GET_FOREGROUND_APP")

            else -> null
        }
    }

    /**
     * Give the model the user's persistent memory facts and on-screen view.
     */
    private suspend fun attachScreenContext(query: String): String {
        val memories = try {
            securityManager.memoryManager.searchFacts("").take(6)
        } catch (_: Exception) { emptyList() }

        val memSection = if (memories.isNotEmpty()) {
            "[User Facts & Memory]\n" + memories.joinToString("\n") { "• ${it.key}: ${it.value}" } + "\n\n"
        } else ""

        val service = TarziAccessibilityService.get() ?: return memSection + query
        return try {
            val snapshot = service.captureScreen()
            if (snapshot.elements.isEmpty()) {
                memSection + query
            } else {
                buildString {
                    if (memSection.isNotEmpty()) append(memSection)
                    append("[Live screen context]\n")
                    append(snapshot.toPromptContext())
                    append("\n\n[User said]\n")
                    append(query)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "[BRAIN_SCREEN_CONTEXT_FAIL] " + e.message)
            memSection + query
        }
    }

    /** A live screenshot, when the user has granted screen capture. Null otherwise. */
    private suspend fun captureScreenFrame(): String? {
        val vision = ScreenVisionService.get() ?: return null
        return try {
            vision.captureFrameBase64()
        } catch (e: Exception) {
            Log.w(TAG, "[BRAIN_FRAME_FAIL] " + e.message)
            null
        }
    }

    /** Execute a request the user just approved. */
    suspend fun confirmAndRun(request: ToolRequest): BrainResponse {
        val result = securityManager.deterministicExecutor.execute(
            request.copy(userConfirmed = true)
        )
        return BrainResponse(result.resultSummary, result.resultSummary, listOf(request))
    }

    fun clearHistory() = history.clear()
}
