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
 * DeepSeek backend, over its OpenAI-compatible chat completions API.
 *
 * Chosen as the primary backend because it answers and, more importantly,
 * returns real tool calls - which is what turns Tarzi from a phrase-matcher into
 * something that can actually decide to do things.
 *
 * DeepSeek is text-only, so a screen frame is described rather than attached;
 * the accessibility element list carries the on-screen context instead.
 */
class DeepSeekAiService(
    private val getApiKey: suspend () -> String?,
    private val getModel: suspend () -> String? = { null }
) : AiBackend {

    override val displayName: String = "DeepSeek"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    companion object {
        private const val TAG = "TarziDeepSeek"
        private const val ENDPOINT = "https://api.deepseek.com/chat/completions"

        /** Fast general model. deepseek-reasoner trades latency for depth. */
        const val DEFAULT_MODEL = "deepseek-chat"
    }

    override suspend fun processUserQuery(
        query: String,
        conversationHistory: List<Pair<String, String>>,
        screenFrameBase64: String?
    ): AiResponse = withContext(Dispatchers.IO) {

        val apiKey = getApiKey()?.trim()
        if (apiKey.isNullOrEmpty()) {
            return@withContext AiResponse("", emptyList(), failed = true)
        }

        val model = getModel()?.trim().takeUnless { it.isNullOrEmpty() } ?: DEFAULT_MODEL

        try {
            val body = buildRequest(query, conversationHistory, model)
            val request = Request.Builder()
                .url(ENDPOINT)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val error = response.body?.string().orEmpty()
                    Log.w(TAG, "[HTTP_" + response.code + "] " + error.take(300))
                    return@withContext AiResponse("", emptyList(), failed = true)
                }
                val text = response.body?.string()
                if (text.isNullOrBlank()) {
                    return@withContext AiResponse("", emptyList(), failed = true)
                }
                parseResponse(text)
            }
        } catch (e: Exception) {
            Log.e(TAG, "[EXCEPTION] " + e.message)
            AiResponse("", emptyList(), failed = true)
        }
    }

    private suspend fun buildRequest(
        query: String,
        history: List<Pair<String, String>>,
        model: String
    ): JsonObject = JsonObject().apply {
        addProperty("model", model)
        addProperty("max_tokens", 180)
        // Responsive and concise
        addProperty("temperature", 0.5)

        val messages = JsonArray()

        messages.add(JsonObject().apply {
            addProperty("role", "system")
            addProperty("content", AiToolCatalog.SYSTEM_PROMPT)
        })

        history.takeLast(8).forEach { (sender, text) ->
            if (text.isNotBlank()) {
                messages.add(JsonObject().apply {
                    addProperty("role", if (sender == "USER") "user" else "assistant")
                    addProperty("content", text)
                })
            }
        }

        messages.add(JsonObject().apply {
            addProperty("role", "user")
            addProperty("content", query)
        })

        add("messages", messages)
        add("tools", buildTools())
        addProperty("tool_choice", "auto")
    }

    private fun buildTools(): JsonArray {
        val array = JsonArray()
        AiToolCatalog.tools.forEach { tool ->
            val properties = JsonObject()
            val required = JsonArray()

            tool.params.forEach { param ->
                properties.add(param.name, JsonObject().apply {
                    addProperty("type", param.type)
                    addProperty("description", param.description)
                })
                required.add(param.name)
            }

            array.add(JsonObject().apply {
                addProperty("type", "function")
                add("function", JsonObject().apply {
                    addProperty("name", tool.name)
                    addProperty("description", tool.description)
                    add("parameters", JsonObject().apply {
                        addProperty("type", "object")
                        add("properties", properties)
                        if (required.size() > 0) add("required", required)
                    })
                })
            })
        }
        return array
    }

    private fun parseResponse(json: String): AiResponse {
        return try {
            val root = gson.fromJson(json, JsonObject::class.java)
            val choices = root.getAsJsonArray("choices")
            if (choices == null || choices.size() == 0) {
                return AiResponse("", emptyList(), failed = true)
            }

            val message = choices[0].asJsonObject.getAsJsonObject("message")
                ?: return AiResponse("", emptyList(), failed = true)

            val reply = message.get("content")
                ?.takeUnless { it.isJsonNull }
                ?.asString
                .orEmpty()
                .trim()

            val toolRequests = mutableListOf<ToolRequest>()

            message.getAsJsonArray("tool_calls")?.forEach { element ->
                val call = element.asJsonObject.getAsJsonObject("function") ?: return@forEach
                val name = call.get("name")?.asString ?: return@forEach

                // Arguments arrive as a JSON string, not an object.
                val rawArgs = call.get("arguments")?.asString.orEmpty()
                val params = mutableMapOf<String, Any>()

                if (rawArgs.isNotBlank()) {
                    runCatching { gson.fromJson(rawArgs, JsonObject::class.java) }
                        .getOrNull()
                        ?.entrySet()
                        ?.forEach { (key, value) ->
                            if (value.isJsonPrimitive) {
                                val primitive = value.asJsonPrimitive
                                params[key] = when {
                                    primitive.isBoolean -> primitive.asBoolean
                                    primitive.isNumber -> primitive.asNumber
                                    else -> primitive.asString
                                }
                            }
                        }
                }

                toolRequests.add(
                    ToolRequest(AiToolCatalog.toToolName(name), params)
                )
            }

            // A turn with neither text nor a tool call is a failure to act on.
            if (reply.isBlank() && toolRequests.isEmpty()) {
                AiResponse("", emptyList(), failed = true)
            } else {
                AiResponse(reply, toolRequests)
            }
        } catch (e: Exception) {
            Log.e(TAG, "[PARSE_FAIL] " + e.message)
            AiResponse("", emptyList(), failed = true)
        }
    }

    override suspend fun verifyCredentials(): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()?.trim()
        if (apiKey.isNullOrEmpty()) return@withContext "No key saved"
        val model = getModel()?.trim().takeUnless { it.isNullOrEmpty() } ?: DEFAULT_MODEL

        try {
            val body = JsonObject().apply {
                addProperty("model", model)
                addProperty("max_tokens", 4)
                add("messages", JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("role", "user")
                        addProperty("content", "hi")
                    })
                })
            }

            val request = Request.Builder()
                .url(ENDPOINT)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> "Connected - $model"
                    response.code == 401 -> "Key rejected"
                    response.code == 402 -> "Out of credit on this account"
                    response.code == 429 -> "Rate limited"
                    else -> "Error " + response.code
                }
            }
        } catch (e: Exception) {
            "Network error: " + (e.message ?: "unknown")
        }
    }
}
