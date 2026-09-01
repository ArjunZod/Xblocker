package com.antigravity.shieldx.classifier

import com.antigravity.shieldx.core.model.BlockReason
import com.antigravity.shieldx.core.model.Category
import com.antigravity.shieldx.core.model.PolicyDecision
import com.antigravity.shieldx.core.util.TextNormalizer
import java.net.URI

/**
 * Deterministic rule-based text classifier using normalized tokens and regex patterns.
 */
class RuleClassifier {

    data class RuleMatch(
        val isExplicit: Boolean,
        val category: Category,
        val confidence: Float,
        val matchedKeyword: String,
        val reason: BlockReason
    )

    private val HIGH_CONFIDENCE_EXPLICIT_KEYWORDS = mapOf(
        "porn" to Category.PORNOGRAPHY,
        "porno" to Category.PORNOGRAPHY,
        "pornography" to Category.PORNOGRAPHY,
        "xvideos" to Category.PORNOGRAPHY,
        "xnxx" to Category.PORNOGRAPHY,
        "xhamster" to Category.PORNOGRAPHY,
        "hentai" to Category.PORNOGRAPHY,
        "blowjob" to Category.PORNOGRAPHY,
        "handjob" to Category.PORNOGRAPHY,
        "cunnilingus" to Category.PORNOGRAPHY,
        "fellatio" to Category.PORNOGRAPHY,
        "masturbat" to Category.PORNOGRAPHY,
        "dildo" to Category.PORNOGRAPHY,
        "vibrator" to Category.PORNOGRAPHY,
        "hardcore" to Category.PORNOGRAPHY,
        "chaturbate" to Category.CAM,
        "camsoda" to Category.CAM,
        "stripchat" to Category.CAM,
        "livejasmin" to Category.CAM,
        "myfreecams" to Category.CAM,
        "camgirl" to Category.CAM,
        "webcam model" to Category.CAM,
        "escort service" to Category.SEXUAL_SERVICES,
        "erotic massage" to Category.SEXUAL_SERVICES,
        "happy ending massage" to Category.SEXUAL_SERVICES,
        "prostitute" to Category.SEXUAL_SERVICES,
        "adult dating" to Category.ADULT_DATING,
        "sex dating" to Category.ADULT_DATING,
        "hookup sex" to Category.ADULT_DATING,
        "nsfw leaks" to Category.NSFW_MEDIA,
        "onlyfans leaks" to Category.NSFW_MEDIA,
        "nude leak" to Category.NSFW_MEDIA
    )

    fun classifyText(rawText: String): RuleMatch {
        if (rawText.isBlank()) {
            return RuleMatch(false, Category.SAFE, 0.0f, "", BlockReason.SECURE_DEFAULT_FALLBACK)
        }

        val normalized = TextNormalizer.normalize(rawText)
        val compact = TextNormalizer.stripPunctuationAndSpacing(rawText)

        // Check high confidence explicit dictionary
        for ((keyword, category) in HIGH_CONFIDENCE_EXPLICIT_KEYWORDS) {
            val normalizedKw = TextNormalizer.normalize(keyword)
            val compactKw = TextNormalizer.stripPunctuationAndSpacing(keyword)

            if (normalized.contains(normalizedKw) || compact.contains(compactKw)) {
                return RuleMatch(
                    isExplicit = true,
                    category = category,
                    confidence = 0.95f,
                    matchedKeyword = keyword,
                    reason = BlockReason.LOCAL_TEXT_EXPLICIT
                )
            }
        }

        return RuleMatch(false, Category.SAFE, 0.1f, "", BlockReason.SECURE_DEFAULT_FALLBACK)
    }
}

/**
 * URL heuristic classifier that analyzes paths, query strings, and file extensions.
 */
class UrlClassifier(private val ruleClassifier: RuleClassifier) {

    data class UrlClassificationResult(
        val decision: PolicyDecision,
        val category: Category,
        val confidence: Float,
        val reason: BlockReason,
        val details: String
    )

    fun classifyUrl(rawUrl: String): UrlClassificationResult {
        if (rawUrl.isBlank()) {
            return UrlClassificationResult(PolicyDecision.ALLOW, Category.SAFE, 0.0f, BlockReason.SECURE_DEFAULT_FALLBACK, "Empty URL")
        }

        try {
            val uri = URI(rawUrl)
            val host = uri.host ?: ""
            val path = uri.path ?: ""
            val query = uri.query ?: ""

            // 1. Analyze Hostname
            val hostMatch = ruleClassifier.classifyText(host)
            if (hostMatch.isExplicit) {
                return UrlClassificationResult(
                    PolicyDecision.BLOCK,
                    hostMatch.category,
                    hostMatch.confidence,
                    BlockReason.KNOWN_ADULT_DOMAIN,
                    "Explicit host keyword: ${hostMatch.matchedKeyword}"
                )
            }

            // 2. Analyze Path
            val pathMatch = ruleClassifier.classifyText(path)
            if (pathMatch.isExplicit) {
                return UrlClassificationResult(
                    PolicyDecision.BLOCK,
                    pathMatch.category,
                    pathMatch.confidence * 0.9f,
                    BlockReason.URL_PATH_EXPLICIT,
                    "Explicit path keyword: ${pathMatch.matchedKeyword}"
                )
            }

            // 3. Analyze Query
            val queryMatch = ruleClassifier.classifyText(query)
            if (queryMatch.isExplicit) {
                return UrlClassificationResult(
                    PolicyDecision.BLOCK,
                    queryMatch.category,
                    queryMatch.confidence * 0.85f,
                    BlockReason.URL_QUERY_EXPLICIT,
                    "Explicit query parameter: ${queryMatch.matchedKeyword}"
                )
            }

            return UrlClassificationResult(
                PolicyDecision.ALLOW,
                Category.SAFE,
                0.1f,
                BlockReason.SECURE_DEFAULT_FALLBACK,
                "URL passed heuristic inspection"
            )

        } catch (_: Exception) {
            // If malformed URL, test entire string directly
            val fallbackMatch = ruleClassifier.classifyText(rawUrl)
            return if (fallbackMatch.isExplicit) {
                UrlClassificationResult(
                    PolicyDecision.BLOCK,
                    fallbackMatch.category,
                    fallbackMatch.confidence,
                    BlockReason.URL_PATH_EXPLICIT,
                    "Matched in malformed URL string"
                )
            } else {
                UrlClassificationResult(PolicyDecision.ALLOW, Category.SAFE, 0.1f, BlockReason.SECURE_DEFAULT_FALLBACK, "Allowed fallback")
            }
        }
    }
}

/**
 * Local content aggregator evaluating text, URL, and metadata.
 */
class LocalContentClassifier(
    private val ruleClassifier: RuleClassifier,
    private val urlClassifier: UrlClassifier
) {

    data class ContentDecision(
        val decision: PolicyDecision,
        val category: Category,
        val confidence: Float,
        val isUncertain: Boolean,
        val reason: BlockReason
    )

    fun evaluateContent(text: String?, url: String?): ContentDecision {
        var highestConfidence = 0.0f
        var detectedCategory = Category.SAFE
        var blockReason = BlockReason.SECURE_DEFAULT_FALLBACK

        if (!url.isNullOrBlank()) {
            val urlResult = urlClassifier.classifyUrl(url)
            if (urlResult.decision == PolicyDecision.BLOCK) {
                return ContentDecision(
                    decision = PolicyDecision.BLOCK,
                    category = urlResult.category,
                    confidence = urlResult.confidence,
                    isUncertain = false,
                    reason = urlResult.reason
                )
            }
            highestConfidence = highestConfidence.coerceAtLeast(urlResult.confidence)
        }

        if (!text.isNullOrBlank()) {
            val textResult = ruleClassifier.classifyText(text)
            if (textResult.isExplicit) {
                return ContentDecision(
                    decision = PolicyDecision.BLOCK,
                    category = textResult.category,
                    confidence = textResult.confidence,
                    isUncertain = false,
                    reason = textResult.reason
                )
            }
            highestConfidence = highestConfidence.coerceAtLeast(textResult.confidence)
            detectedCategory = textResult.category
        }

        // Check if content is in uncertain range (0.35 - 0.75) for optional AI classification
        val isUncertain = highestConfidence in 0.35f..0.75f

        val finalDecision = when {
            highestConfidence >= 0.75f -> PolicyDecision.BLOCK
            highestConfidence < 0.35f -> PolicyDecision.ALLOW
            else -> PolicyDecision.UNCERTAIN
        }

        return ContentDecision(
            decision = finalDecision,
            category = if (finalDecision == PolicyDecision.BLOCK) detectedCategory else Category.SAFE,
            confidence = highestConfidence,
            isUncertain = isUncertain,
            reason = blockReason
        )
    }
}
