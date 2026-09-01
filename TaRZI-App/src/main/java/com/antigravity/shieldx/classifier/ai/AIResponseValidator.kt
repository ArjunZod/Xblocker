package com.antigravity.shieldx.classifier.ai

import com.antigravity.shieldx.core.model.Category
import com.antigravity.shieldx.core.model.PolicyDecision
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * Strict validator for Gemini structured output responses.
 */
class AIResponseValidator {

    data class RawAIResponse(
        @SerializedName("decision") val decision: String?,
        @SerializedName("category") val category: String?,
        @SerializedName("confidence") val confidence: Double?,
        @SerializedName("reason_code") val reasonCode: String?
    )

    data class ValidatedDecision(
        val decision: PolicyDecision,
        val category: Category,
        val confidence: Float,
        val reasonCode: String
    )

    private val gson = Gson()

    /**
     * Validate and parse raw JSON text into strongly typed decision.
     * Returns null if JSON is invalid or violates schema.
     */
    fun validateJson(rawJson: String): ValidatedDecision? {
        return try {
            // Strip markdown code fences if model accidentally wraps JSON
            var cleanedJson = rawJson.trim()
            if (cleanedJson.startsWith("```json")) {
                cleanedJson = cleanedJson.removePrefix("```json").removeSuffix("```").trim()
            } else if (cleanedJson.startsWith("```")) {
                cleanedJson = cleanedJson.removePrefix("```").removeSuffix("```").trim()
            }

            val parsed = gson.fromJson(cleanedJson, RawAIResponse::class.java) ?: return null

            // Validate decision
            val decision = when (parsed.decision?.uppercase()) {
                "ALLOW" -> PolicyDecision.ALLOW
                "BLOCK" -> PolicyDecision.BLOCK
                "UNCERTAIN" -> PolicyDecision.UNCERTAIN
                else -> return null
            }

            // Validate category
            val category = when (parsed.category?.uppercase()) {
                "SAFE" -> Category.SAFE
                "NSFW" -> Category.NSFW_MEDIA
                "EXPLICIT" -> Category.PORNOGRAPHY
                "SEXUAL_SERVICE" -> Category.SEXUAL_SERVICES
                "OTHER" -> Category.OTHER_EXPLICIT
                else -> Category.UNKNOWN
            }

            // Validate confidence (0.0 to 1.0)
            val conf = parsed.confidence?.toFloat() ?: return null
            if (conf !in 0.0f..1.0f) return null

            val reasonCode = parsed.reasonCode?.trim()?.ifEmpty { "AI_CLASSIFICATION" } ?: "AI_CLASSIFICATION"

            ValidatedDecision(
                decision = decision,
                category = category,
                confidence = conf,
                reasonCode = reasonCode
            )
        } catch (_: Exception) {
            null
        }
    }
}
