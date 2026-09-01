package com.antigravity.shieldx.classifier

import com.antigravity.shieldx.classifier.ai.GeminiClassifier
import com.antigravity.shieldx.core.model.BlockReason
import com.antigravity.shieldx.core.model.Category
import com.antigravity.shieldx.core.model.PolicyDecision
import com.antigravity.shieldx.data.repository.PolicyRepository
import com.antigravity.shieldx.vpn.DomainMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Unified Classification Manager orchestrating the layered decision pipeline.
 * Ensures deterministic offline security always acts as the authoritative boundary.
 */
class ClassificationManager(
    private val domainMatcher: DomainMatcher,
    private val ruleClassifier: RuleClassifier,
    private val urlClassifier: UrlClassifier,
    private val localContentClassifier: LocalContentClassifier,
    private val geminiClassifier: GeminiClassifier,
    private val policyRepository: PolicyRepository
) {

    data class UnifiedClassificationResult(
        val decision: PolicyDecision,
        val category: Category,
        val confidence: Float,
        val reason: BlockReason,
        val usedAi: Boolean,
        val details: String
    )

    suspend fun evaluate(
        targetDomain: String? = null,
        url: String? = null,
        textContent: String? = null
    ): UnifiedClassificationResult = withContext(Dispatchers.Default) {
        val policy = policyRepository.getPolicy()

        // 1. Layer 1: Deterministic Domain Check
        if (!targetDomain.isNullOrBlank()) {
            val domainMatch = domainMatcher.match(targetDomain)
            if (domainMatch.isBlocked) {
                return@withContext UnifiedClassificationResult(
                    decision = PolicyDecision.BLOCK,
                    category = domainMatch.category,
                    confidence = 1.0f,
                    reason = BlockReason.KNOWN_ADULT_DOMAIN,
                    usedAi = false,
                    details = "Matched blocked domain rule: ${domainMatch.matchedRule}"
                )
            }
        }

        // 2. Layer 2: Deterministic URL Heuristics
        if (!url.isNullOrBlank()) {
            val urlResult = urlClassifier.classifyUrl(url)
            if (urlResult.decision == PolicyDecision.BLOCK) {
                return@withContext UnifiedClassificationResult(
                    decision = PolicyDecision.BLOCK,
                    category = urlResult.category,
                    confidence = urlResult.confidence,
                    reason = urlResult.reason,
                    usedAi = false,
                    details = urlResult.details
                )
            }
        }

        // 3. Layer 3: Deterministic Local Text Content Heuristics
        if (!textContent.isNullOrBlank()) {
            val textMatch = ruleClassifier.classifyText(textContent)
            if (textMatch.isExplicit) {
                return@withContext UnifiedClassificationResult(
                    decision = PolicyDecision.BLOCK,
                    category = textMatch.category,
                    confidence = textMatch.confidence,
                    reason = textMatch.reason,
                    usedAi = false,
                    details = "Matched explicit text token: ${textMatch.matchedKeyword}"
                )
            }
        }

        // 4. Layer 4: Local Content Aggregator
        val localDecision = localContentClassifier.evaluateContent(textContent, url)
        if (localDecision.decision == PolicyDecision.BLOCK) {
            return@withContext UnifiedClassificationResult(
                decision = PolicyDecision.BLOCK,
                category = localDecision.category,
                confidence = localDecision.confidence,
                reason = localDecision.reason,
                usedAi = false,
                details = "Local heuristic classifier triggered"
            )
        }

        // 5. Layer 5: Optional AI Classifier for Uncertain Cases
        if (localDecision.isUncertain && policy.aiClassificationEnabled) {
            val target = url ?: targetDomain ?: "text_snippet"
            val snippet = textContent ?: url ?: targetDomain ?: ""

            val aiResult = geminiClassifier.classifyUncertainContent(target, snippet)
            if (aiResult is GeminiClassifier.AIClassificationResult.Success) {
                if (aiResult.decision == PolicyDecision.BLOCK && aiResult.confidence >= policy.aiConfidenceThreshold) {
                    return@withContext UnifiedClassificationResult(
                        decision = PolicyDecision.BLOCK,
                        category = aiResult.category,
                        confidence = aiResult.confidence,
                        reason = BlockReason.AI_CONFIRMED_EXPLICIT,
                        usedAi = true,
                        details = "AI classified as explicit: ${aiResult.reasonCode}"
                    )
                } else if (aiResult.decision == PolicyDecision.ALLOW) {
                    return@withContext UnifiedClassificationResult(
                        decision = PolicyDecision.ALLOW,
                        category = Category.SAFE,
                        confidence = aiResult.confidence,
                        reason = BlockReason.SECURE_DEFAULT_FALLBACK,
                        usedAi = true,
                        details = "AI classified as safe: ${aiResult.reasonCode}"
                    )
                }
            }
            // If AI is unavailable or uncertain, fallback to deterministic result
        }

        // 6. Final Deterministic Safe Allow
        UnifiedClassificationResult(
            decision = PolicyDecision.ALLOW,
            category = Category.SAFE,
            confidence = localDecision.confidence,
            reason = BlockReason.SECURE_DEFAULT_FALLBACK,
            usedAi = false,
            details = "Passed all deterministic safety boundaries"
        )
    }
}
