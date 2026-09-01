package com.antigravity.shieldx.assistant.ai

import android.util.Log
import com.antigravity.shieldx.core.model.ToolRequest
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Autonomous Gemini LLM reasoning service for Tarzi.
 * Connects to Google Generative AI REST API with structured Function/Tool Calling schema.
 */
class GeminiAiService(
    private val getApiKey: suspend () -> String?,
    private val getModel: suspend () -> String? = { null }
) : AiBackend {

    override val displayName: String = "Gemini"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    companion object {
        private const val TAG = "TaRZIGemini"
        private const val GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta/models"

        /**
         * The "-latest" alias tracks whatever the current Flash model is, so a
         * model retirement on Google's side does not take the assistant down.
         * Overridable from settings via the gemini_model config key.
         */
        const val DEFAULT_MODEL = "gemini-flash-latest"

        private const val SYSTEM_PROMPT = """
You are Tarzi, an advanced, highly capable, autonomous AI assistant embedded directly on an Android device.
You have direct access to system tools to control hardware, make phone calls, open apps like YouTube/WhatsApp, stream music, adjust volume/flashlight, manage alarms, protect the device, and remember user facts.

Core Behavioral Directives:
1. Always analyze the user's intent. When they request an action (e.g. "call mom", "open youtube", "play blinding lights", "turn on flashlight", "set volume to 80%", "set alarm for 7am", "block explicit content"), call the corresponding tool immediately.
2. If multi-step actions are needed (e.g. "open youtube and turn on flashlight"), invoke multiple tools or sequential function calls.
3. Keep spoken replies concise, professional, charismatic, and natural. Avoid unnecessary markdown symbols in spoken summaries.
4. If the user is having a casual conversation, answer knowledgeably, concisely, and warmly.
5. You can see and control the screen of whatever app the user is currently in. When a
   request refers to something on screen ("open the first video", "tap that", "scroll down",
   "what does this say"), use the screen tools rather than guessing. A [Live screen context]
   block, when present, lists the elements currently visible with their index numbers.
6. Prefer open_item for "the first/second/third one" and tap_text when the user names a label.
"""
    }


    /**
     * Send a prompt to Gemini with tool definitions.
     */
    override suspend fun processUserQuery(
        query: String,
        conversationHistory: List<Pair<String, String>>,
        screenFrameBase64: String?
    ): AiResponse = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()?.trim()
        if (apiKey.isNullOrEmpty()) {
            Log.d(TAG, "No Gemini API key configured, using the on-device engine.")
            return@withContext AiResponse("", emptyList(), failed = true)
        }

        val model = getModel()?.trim().takeUnless { it.isNullOrEmpty() } ?: DEFAULT_MODEL

        try {
            val requestBody = buildGeminiRequestBody(query, conversationHistory, screenFrameBase64)
            val request = Request.Builder()
                .url("$GEMINI_BASE/$model:generateContent?key=$apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string().orEmpty()
                    Log.w(TAG, "[GEMINI_HTTP_" + response.code + "] " + errBody.take(300))
                    // Fall through to the offline engine instead of speaking the error.
                    return@withContext AiResponse("", emptyList(), failed = true)
                }

                val bodyString = response.body?.string()
                if (bodyString.isNullOrBlank()) {
                    return@withContext AiResponse("", emptyList(), failed = true)
                }
                parseGeminiResponse(bodyString)
            }
        } catch (e: Exception) {
            Log.e(TAG, "[GEMINI_EXCEPTION] " + e.message)
            AiResponse("", emptyList(), failed = true)
        }
    }

    /** Probe the configured key and model. Used by settings to show live status. */
    override suspend fun verifyCredentials(): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()?.trim()
        if (apiKey.isNullOrEmpty()) return@withContext "No key saved"
        val model = getModel()?.trim().takeUnless { it.isNullOrEmpty() } ?: DEFAULT_MODEL

        try {
            val body = JsonObject().apply {
                val contents = JsonArray()
                contents.add(JsonObject().apply {
                    addProperty("role", "user")
                    add("parts", JsonArray().apply {
                        add(JsonObject().apply { addProperty("text", "ping") })
                    })
                })
                add("contents", contents)
            }
            val request = Request.Builder()
                .url("$GEMINI_BASE/$model:generateContent?key=$apiKey")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> "Connected - $model"
                    response.code == 403 ->
                        "Key rejected: this Google project is denied API access"
                    response.code == 404 ->
                        "Model '" + model + "' unavailable on this key"
                    response.code == 429 -> "Rate limited - quota exhausted"
                    else -> "Error " + response.code
                }
            }
        } catch (e: Exception) {
            "Network error: " + (e.message ?: "unknown")
        }
    }

    private fun buildGeminiRequestBody(
        query: String,
        history: List<Pair<String, String>>,
        screenFrameBase64: String? = null
    ): JsonObject {
        val root = JsonObject()

        // 1. System Instruction
        val systemInstruction = JsonObject().apply {
            val parts = JsonArray().apply {
                add(JsonObject().apply { addProperty("text", SYSTEM_PROMPT) })
            }
            add("parts", parts)
        }
        root.add("systemInstruction", systemInstruction)

        // 2. Contents (History + Current Query)
        val contentsArray = JsonArray()

        // Add recent history turns
        history.takeLast(6).forEach { (sender, text) ->
            val role = if (sender == "USER") "user" else "model"
            contentsArray.add(JsonObject().apply {
                addProperty("role", role)
                val parts = JsonArray().apply {
                    add(JsonObject().apply { addProperty("text", text) })
                }
                add("parts", parts)
            })
        }

        // Current turn, optionally carrying a live screenshot so the model can
        // answer questions about what the user is actually looking at.
        contentsArray.add(JsonObject().apply {
            addProperty("role", "user")
            val parts = JsonArray().apply {
                add(JsonObject().apply { addProperty("text", query) })
                if (!screenFrameBase64.isNullOrBlank()) {
                    add(JsonObject().apply {
                        add("inlineData", JsonObject().apply {
                            addProperty("mimeType", "image/jpeg")
                            addProperty("data", screenFrameBase64)
                        })
                    })
                }
            }
            add("parts", parts)
        })
        root.add("contents", contentsArray)

        // 3. Tools Declaration (Function Calling)
        val toolsArray = JsonArray()
        val functionDeclarations = JsonArray().apply {
            add(createFunctionDecl("open_app", "Launch any installed application on the device (e.g. YouTube, WhatsApp, Camera, Chrome, Instagram, Maps, Settings, Spotify, Calculator)", mapOf(
                "appName" to "string: Name of the application to open"
            )))
            add(createFunctionDecl("call_contact", "Initiate a phone call to a contact name or phone number", mapOf(
                "name" to "string: Name of contact or phone number to call"
            )))
            add(createFunctionDecl("send_message", "Send SMS or WhatsApp message to a recipient", mapOf(
                "name" to "string: Name of contact or phone number",
                "message" to "string: Message text",
                "useWhatsApp" to "boolean: Set true to send via WhatsApp, false for SMS"
            )))
            add(createFunctionDecl("set_flashlight", "Turn device camera flashlight / torch on or off", mapOf(
                "enable" to "boolean: true to turn on, false to turn off"
            )))
            add(createFunctionDecl("set_volume", "Adjust media audio volume level percentage (0 to 100)", mapOf(
                "levelPercent" to "integer: Volume level percentage from 0 to 100"
            )))
            add(createFunctionDecl("set_alarm", "Set a device alarm clock", mapOf(
                "hour" to "integer: Hour in 24-hour format (0-23)",
                "minute" to "integer: Minute (0-59)",
                "message" to "string: Alarm title/label"
            )))
            add(createFunctionDecl("set_timer", "Set a countdown timer in seconds or minutes", mapOf(
                "seconds" to "integer: Duration in seconds",
                "message" to "string: Timer label"
            )))
            add(createFunctionDecl("play_music", "Search and play a song, artist, album, or playlist on YouTube Music", mapOf(
                "query" to "string: Song title or artist to play"
            )))
            add(createFunctionDecl("pause_music", "Pause ongoing music playback", emptyMap()))
            add(createFunctionDecl("resume_music", "Resume music playback", emptyMap()))
            add(createFunctionDecl("search_web", "Perform a Google web search", mapOf(
                "query" to "string: Search query string"
            )))
            add(createFunctionDecl("get_battery_status", "Check current battery level and charging state", emptyMap()))
            add(createFunctionDecl("get_storage_status", "Check internal storage free and total space", emptyMap()))
            add(createFunctionDecl("set_protection_profile", "Configure adult content blocking and security shield (MAXIMUM, BALANCED, OFF)", mapOf(
                "profile" to "string: Protection profile name (MAXIMUM, BALANCED, OFF)"
            )))
            add(createFunctionDecl("remember_fact", "Save a fact, preference, or piece of user data into persistent memory", mapOf(
                "key" to "string: Key/subject",
                "value" to "string: Fact or value to remember"
            )))
            add(createFunctionDecl("recall_memory", "Recall a previously remembered user fact from memory", mapOf(
                "key" to "string: Key to recall"
            )))

            // Screen control: these act inside whatever app is currently open.
            add(createFunctionDecl("open_item", "Open the Nth video, result, or item on the screen the user is currently looking at. Use for 'open the first video', 'play the second one'.", mapOf(
                "ordinal" to "integer: 1 for the first item, 2 for the second, and so on"
            )))
            add(createFunctionDecl("tap_text", "Tap the on-screen button, link, or element matching a visible label", mapOf(
                "text" to "string: The visible text or label to tap"
            )))
            add(createFunctionDecl("scroll_screen", "Scroll the current screen up or down", mapOf(
                "direction" to "string: up or down"
            )))
            add(createFunctionDecl("go_back", "Press the system back button", emptyMap()))
            add(createFunctionDecl("go_home", "Return to the device home screen", emptyMap()))
            add(createFunctionDecl("read_screen", "Read all text currently visible on screen", emptyMap()))
            add(createFunctionDecl("type_text", "Type text into the currently focused input field", mapOf(
                "text" to "string: The text to type"
            )))
            add(createFunctionDecl("search_in_app", "Search for something inside the app currently on screen", mapOf(
                "query" to "string: What to search for"
            )))
            add(createFunctionDecl("get_foreground_app", "Report which app is currently open", emptyMap()))
        }

        val toolObj = JsonObject().apply {
            add("functionDeclarations", functionDeclarations)
        }
        toolsArray.add(toolObj)
        root.add("tools", toolsArray)

        return root
    }

    private fun createFunctionDecl(
        name: String,
        description: String,
        params: Map<String, String>
    ): JsonObject {
        val decl = JsonObject().apply {
            addProperty("name", name)
            addProperty("description", description)
            val parameters = JsonObject().apply {
                addProperty("type", "object")
                val properties = JsonObject()
                val required = JsonArray()
                params.forEach { (pName, pType) ->
                    val typeStr = pType.substringBefore(":")
                    val descStr = pType.substringAfter(":").trim()
                    properties.add(pName, JsonObject().apply {
                        addProperty("type", typeStr)
                        addProperty("description", descStr)
                    })
                    required.add(pName)
                }
                add("properties", properties)
                if (required.size() > 0) {
                    add("required", required)
                }
            }
            add("parameters", parameters)
        }
        return decl
    }

    private fun parseGeminiResponse(jsonString: String): AiResponse {
        try {
            val root = gson.fromJson(jsonString, JsonObject::class.java)
            val candidates = root.getAsJsonArray("candidates") ?: return AiResponse("", emptyList(), failed = true)
            if (candidates.size() == 0) return AiResponse("", emptyList(), failed = true)

            val firstCandidate = candidates.get(0).asJsonObject
            val content = firstCandidate.getAsJsonObject("content")
                ?: return AiResponse("", emptyList(), failed = true)
            val parts = content.getAsJsonArray("parts")
                ?: return AiResponse("", emptyList(), failed = true)

            var textReply = ""
            val toolRequests = mutableListOf<ToolRequest>()

            for (partElem in parts) {
                val partObj = partElem.asJsonObject

                // Text output
                if (partObj.has("text")) {
                    textReply += partObj.get("text").asString.trim() + " "
                }

                // Function Call output
                if (partObj.has("functionCall")) {
                    val functionCall = partObj.getAsJsonObject("functionCall")
                    val funcName = functionCall.get("name")?.asString ?: continue
                    val argsObj = functionCall.getAsJsonObject("args") ?: JsonObject()

                    val paramMap = mutableMapOf<String, Any>()
                    argsObj.entrySet().forEach { (k, v) ->
                        if (v.isJsonPrimitive) {
                            val prim = v.asJsonPrimitive
                            when {
                                prim.isBoolean -> paramMap[k] = prim.asBoolean
                                prim.isNumber -> paramMap[k] = prim.asNumber
                                else -> paramMap[k] = prim.asString
                            }
                        }
                    }

                    val toolName = mapGeminiFuncToToolName(funcName)
                    toolRequests.add(ToolRequest(toolName = toolName, parameters = paramMap))
                }
            }

            return AiResponse(
                replyText = textReply.trim(),
                toolRequests = toolRequests
            )
        } catch (e: Exception) {
            Log.e(TAG, "[GEMINI_PARSE_FAIL] " + e.message)
            return AiResponse("", emptyList(), failed = true)
        }
    }

    private fun mapGeminiFuncToToolName(funcName: String): String {
        return when (funcName.lowercase()) {
            "open_app" -> "OPEN_APP"
            "call_contact" -> "CALL_CONTACT"
            "send_message" -> "SEND_MESSAGE"
            "set_flashlight" -> "SET_FLASHLIGHT"
            "set_volume" -> "SET_VOLUME"
            "set_alarm" -> "SET_ALARM"
            "set_timer" -> "SET_TIMER"
            "play_music" -> "PLAY"
            "pause_music" -> "PAUSE"
            "resume_music" -> "RESUME"
            "search_web" -> "SEARCH_WEB"
            "get_battery_status" -> "GET_BATTERY"
            "get_storage_status" -> "GET_STORAGE"
            "set_protection_profile" -> "SET_PROTECTION_PROFILE"
            "remember_fact" -> "MEMORY_WRITE"
            "recall_memory" -> "MEMORY_READ"
            else -> funcName.uppercase()
        }
    }
}
