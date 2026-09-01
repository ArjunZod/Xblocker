package com.antigravity.shieldx.classifier.video

import com.antigravity.shieldx.classifier.image.ImageClassifier
import com.antigravity.shieldx.core.model.BlockReason
import com.antigravity.shieldx.core.model.Category
import com.antigravity.shieldx.core.model.PolicyDecision

/**
 * Local video classifier that samples representative frames and aggregates scores.
 * Optimized for CPU and battery efficiency.
 */
class VideoClassifier(private val imageClassifier: ImageClassifier) {

    data class VideoClassificationResult(
        val decision: PolicyDecision,
        val category: Category,
        val averageConfidence: Float,
        val explicitFramesCount: Int,
        val totalFramesSampled: Int,
        val reason: BlockReason
    )

    /**
     * Classify video by evaluating representative sampled frames.
     */
    fun classifySampledFrames(
        frames: List<ByteArray>,
        blockThresholdScore: Float = 0.65f,
        minExplicitFramesToBlock: Int = 2
    ): VideoClassificationResult {
        if (frames.isEmpty()) {
            return VideoClassificationResult(
                decision = PolicyDecision.ALLOW,
                category = Category.SAFE,
                averageConfidence = 0.0f,
                explicitFramesCount = 0,
                totalFramesSampled = 0,
                reason = BlockReason.SECURE_DEFAULT_FALLBACK
            )
        }

        var explicitCount = 0
        var totalConfidence = 0.0f
        var detectedCategory = Category.SAFE

        for (frame in frames) {
            val result = imageClassifier.classifyImage(frame)
            totalConfidence += result.confidence
            if (result.decision == PolicyDecision.BLOCK) {
                explicitCount++
                detectedCategory = result.category
            }
        }

        val avgConfidence = totalConfidence / frames.size
        val shouldBlock = explicitCount >= minExplicitFramesToBlock || avgConfidence >= blockThresholdScore

        return VideoClassificationResult(
            decision = if (shouldBlock) PolicyDecision.BLOCK else PolicyDecision.ALLOW,
            category = if (shouldBlock) detectedCategory else Category.SAFE,
            averageConfidence = avgConfidence,
            explicitFramesCount = explicitCount,
            totalFramesSampled = frames.size,
            reason = if (shouldBlock) BlockReason.LOCAL_VIDEO_EXPLICIT else BlockReason.SECURE_DEFAULT_FALLBACK
        )
    }
}
