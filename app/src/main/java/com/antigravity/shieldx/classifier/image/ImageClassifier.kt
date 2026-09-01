package com.antigravity.shieldx.classifier.image

import com.antigravity.shieldx.core.model.BlockReason
import com.antigravity.shieldx.core.model.Category
import com.antigravity.shieldx.core.model.PolicyDecision
import java.security.MessageDigest

/**
 * Local image classifier using deterministic feature heuristics and SHA-256 fingerprinting.
 */
class ImageClassifier {

    data class ImageClassificationResult(
        val decision: PolicyDecision,
        val category: Category,
        val confidence: Float,
        val imageHash: String,
        val isUncertain: Boolean,
        val reason: BlockReason
    )

    /**
     * Compute SHA-256 fingerprint of image bytes.
     */
    fun computeFingerprint(imageBytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(imageBytes)
        return hash.joinToString("") { "%02x".format(it) }
    }

    /**
     * Classify image based on local heuristics (e.g. skin tone color cluster distribution, dimensions).
     */
    fun classifyImage(imageBytes: ByteArray, width: Int = 0, height: Int = 0): ImageClassificationResult {
        val fingerprint = computeFingerprint(imageBytes)
        if (imageBytes.isEmpty()) {
            return ImageClassificationResult(
                decision = PolicyDecision.ALLOW,
                category = Category.SAFE,
                confidence = 0.0f,
                imageHash = fingerprint,
                isUncertain = false,
                reason = BlockReason.SECURE_DEFAULT_FALLBACK
            )
        }

        // Heuristic analysis on image buffer (skin-tone pixel density estimation)
        var skinPixels = 0
        val sampleStep = 4.coerceAtLeast(imageBytes.size / 1000) // Sample up to 1000 pixels for speed
        var samples = 0

        var i = 0
        while (i < imageBytes.size - 2) {
            val r = imageBytes[i].toInt() and 0xFF
            val g = imageBytes[i + 1].toInt() and 0xFF
            val b = imageBytes[i + 2].toInt() and 0xFF

            if (isSkinTone(r, g, b)) {
                skinPixels++
            }
            samples++
            i += sampleStep * 3
        }

        val skinRatio = if (samples > 0) skinPixels.toFloat() / samples else 0.0f

        return when {
            skinRatio > 0.65f -> ImageClassificationResult(
                decision = PolicyDecision.BLOCK,
                category = Category.NUDITY,
                confidence = skinRatio,
                imageHash = fingerprint,
                isUncertain = false,
                reason = BlockReason.LOCAL_IMAGE_EXPLICIT
            )
            skinRatio in 0.35f..0.65f -> ImageClassificationResult(
                decision = PolicyDecision.UNCERTAIN,
                category = Category.UNKNOWN,
                confidence = skinRatio,
                imageHash = fingerprint,
                isUncertain = true,
                reason = BlockReason.SECURE_DEFAULT_FALLBACK
            )
            else -> ImageClassificationResult(
                decision = PolicyDecision.ALLOW,
                category = Category.SAFE,
                confidence = skinRatio,
                imageHash = fingerprint,
                isUncertain = false,
                reason = BlockReason.SECURE_DEFAULT_FALLBACK
            )
        }
    }

    private fun isSkinTone(r: Int, g: Int, b: Int): Boolean {
        // Standard RGB skin color bounding box
        return r > 95 && g > 40 && b > 20 &&
                (Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) > 15) &&
                Math.abs(r - g) > 15 && r > g && r > b
    }
}
