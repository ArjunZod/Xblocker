package com.antigravity.shieldx.classifier.ai

import com.antigravity.shieldx.core.model.Category
import com.antigravity.shieldx.core.model.PolicyDecision
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Secondary classifier interfacing with Gemini 3.7 Flash for uncertain/ambiguous content.
 */
class GeminiClassifier(
    private val costController: AICostController,
    private val responseValidator: AIResponseValidator,
    private var apiKeyProvider: () -> String? = { null }
) {

    sealed class AIClassificationResult {
        data class Success(
            val decision: PolicyDecision,
            val category: Category,
            val confidence: Float,
            val reasonCode: String,
            val latencyMs: Long
        ) : AIClassificationResult()

        data class Unavailable(val reason: String) : AIClassificationResult()
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(3000, TimeUnit.MILLISECONDS)
        .readTimeout(3000, TimeUnit.MILLISECONDS)
        .writeTimeout(3000, TimeUnit.MILLISECONDS)
        .build()

    private val gson = Gson()

    fun setApiKeyProvider(provider: () -> String?) {
        this.apiKeyProvider = provider
    }

    suspend fun classifyUncertainContent(
        targetIdentifier: String,
        contentSnippet: String
    ): AIClassificationResult = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider()?.trim()
        if (apiKey.isNullOrEmpty()) {
            return@withContext AIClassificationResult.Unavailable("Gemini API key is not configured")
        }

        val inputHash = costController.computeHash("$targetIdentifier:$contentSnippet")

        // 1. Check local cache first
        val cached = costController.getCachedDecision(inputHash)
        if (cached != null) {
            return@withContext AIClassificationResult.Success(
                decision = cached.decision,
                category = cached.category,
                confidence = cached.confidence,
                reasonCode = "CACHED_AI_DECISION",
                latencyMs = 0L
            )
        }

        // 2. Check Circuit Breaker & Rate Limiter
        if (costController.isCircuitBreakerOpen()) {
            return@withContext AIClassificationResult.Unavailable("AI Circuit breaker is open (cooling down)")
        }

        if (!costController.allowRateLimit()) {
            return@withContext AIClassificationResult.Unavailable("AI Rate limit exceeded")
        }

        // 3. Execute with Deduplication
        costController.executeWithDeduplication(inputHash) {
            val startTime = System.currentTimeMillis()
            try {
                val prompt = """
                    You are a system-level safety classifier for adult and sexually explicit content protection.
                    Classify whether the given target/text contains pornography, nudity, sexual services, adult webcam, or sexually explicit content.
                    Respond ONLY with a valid JSON object matching this schema:
                    {
                      "decision": "ALLOW" | "BLOCK" | "UNCERTAIN",
                      "category": "SAFE" | "NSFW" | "EXPLICIT" | "SEXUAL_SERVICE" | "OTHER",
                      "confidence": 0.0 to 1.0,
                      "reason_code": "string"
                    }
                    
                    Target: $targetIdentifier
                    Content snippet: $contentSnippet
                """.trimIndent()

                val requestBodyJson = JsonObject().apply {
                    val contentsArray = com.google.gson.JsonArray()
                    val contentObj = JsonObject()
                    val partsArray = com.google.gson.JsonArray()
                    val partObj = JsonObject().apply { addProperty("text", prompt) }
                    partsArray.add(partObj)
                    contentObj.add("parts", partsArray)
                    contentsArray.add(contentObj)
                    add("contents", contentsArray)

                    val generationConfig = JsonObject().apply {
                        addProperty("responseMimeType", "application/json")
                        addProperty("temperature", 0.0)
                    }
                    add("generationConfig", generationConfig)
                }

                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
                    .post(requestBodyJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = httpClient.newCall(request).execute()
                val latency = System.currentTimeMillis() - startTime

                if (!response.isSuccessful) {
                    costController.recordFailure()
                    return@executeWithDeduplication AIClassificationResult.Unavailable("HTTP Error ${response.code}")
                }

                val responseBody = response.body?.string() ?: ""
                val rawAiJson = extractTextFromGeminiResponse(responseBody)
                if (rawAiJson.isNullOrEmpty()) {
                    costController.recordFailure()
                    return@executeWithDeduplication AIClassificationResult.Unavailable("Empty AI response body")
                }

                val validated = responseValidator.validateJson(rawAiJson)
                if (validated == null) {
                    costController.recordFailure()
                    return@executeWithDeduplication AIClassificationResult.Unavailable("AI response failed schema validation")
                }

                // Cache successful decision
                costController.cacheDecision(
                    hash = inputHash,
                    target = targetIdentifier,
                    decision = validated.decision,
                    category = validated.category,
                    confidence = validated.confidence
                )

                costController.recordSuccess(
                    inputHash = inputHash,
                    decision = validated.decision,
                    category = validated.category,
                    confidence = validated.confidence,
                    latencyMs = latency,
                    reasonCode = validated.reasonCode
                )

                AIClassificationResult.Success(
                    decision = validated.decision,
                    category = validated.category,
                    confidence = validated.confidence,
                    reasonCode = validated.reasonCode,
                    latencyMs = latency
                )

            } catch (e: Exception) {
                costController.recordFailure()
                AIClassificationResult.Unavailable("AI request failed: ${e.message}")
            }
        }
    }

    private fun extractTextFromGeminiResponse(jsonString: String): String? {
        return try {
            val root = gson.fromJson(jsonString, JsonObject::class.java)
            val candidates = root.getAsJsonArray("candidates")
            if (candidates.size() > 0) {
                val candidate = candidates.get(0).asJsonObject
                val content = candidate.getAsJsonObject("content")
                val parts = content.getAsJsonArray("parts")
                if (parts.size() > 0) {
                    return parts.get(0).asJsonObject.get("text").asString
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }
}
