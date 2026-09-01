package com.antigravity.shieldx.fault

import com.antigravity.shieldx.classifier.RuleClassifier
import com.antigravity.shieldx.classifier.UrlClassifier
import com.antigravity.shieldx.classifier.ai.AIResponseValidator
import com.antigravity.shieldx.core.model.Category
import com.antigravity.shieldx.core.model.PolicyDecision
import com.antigravity.shieldx.core.util.TextNormalizer
import com.antigravity.shieldx.data.local.entities.DomainRuleEntity
import com.antigravity.shieldx.vpn.DomainMatcher
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FaultInjectionAndBypassTest {

    private lateinit var domainMatcher: DomainMatcher
    private lateinit var ruleClassifier: RuleClassifier
    private lateinit var urlClassifier: UrlClassifier
    private lateinit var aiValidator: AIResponseValidator

    @Before
    fun setUp() {
        domainMatcher = DomainMatcher()
        ruleClassifier = RuleClassifier()
        urlClassifier = UrlClassifier(ruleClassifier)
        aiValidator = AIResponseValidator()

        domainMatcher.loadRules(
            listOf(
                DomainRuleEntity(
                    domain = "adultsite.com",
                    category = Category.PORNOGRAPHY,
                    action = PolicyDecision.BLOCK
                )
            )
        )
    }

    // --- FAULT INJECTION TESTS ---

    @Test
    fun testFault_MalformedAIResponse_GracefulRejection() {
        val malformedResponses = listOf(
            "",
            "null",
            "{\"decision\": \"INVALID_DECISION\"}",
            "{\"decision\": \"BLOCK\", \"confidence\": -0.5}",
            "<html><body>502 Bad Gateway</body></html>"
        )

        for (raw in malformedResponses) {
            val result = aiValidator.validateJson(raw)
            assertNull("Malformed AI payload should be rejected safely: $raw", result)
        }
    }

    // --- BYPASS RESISTANCE TEST MATRIX ---

    @Test
    fun testBypass_CaseInsensitivity() {
        val mixedCase = domainMatcher.match("AdUlTsItE.CoM")
        assertTrue("Mixed case domain must be blocked", mixedCase.isBlocked)
    }

    @Test
    fun testBypass_SubdomainNesting() {
        val deepSubdomain = domainMatcher.match("cdn.edge.videos.adultsite.com")
        assertTrue("Deeply nested subdomain must be blocked", deepSubdomain.isBlocked)
    }

    @Test
    fun testBypass_PortAndPathAppended() {
        val withPortAndPath = domainMatcher.normalizeDomain("adultsite.com:8443/content/stream.m3u8")
        val match = domainMatcher.match(withPortAndPath)
        assertTrue("Domain with port and path must normalize and block", match.isBlocked)
    }

    @Test
    fun testBypass_HomoglyphEvasion() {
        // 'р' is Cyrillic small letter er (U+0440)
        val cyrillicEvasion = "\u0440orn"
        val normalized = TextNormalizer.normalize(cyrillicEvasion)
        assertEquals("porn", normalized)

        val classification = ruleClassifier.classifyText(cyrillicEvasion)
        assertTrue("Homoglyph evasion attempt must be identified and blocked", classification.isExplicit)
    }

    @Test
    fun testBypass_UrlPercentEncoding() {
        val encodedUrl = "https://example.com/search?tag=%70%6f%72%6e" // "porn"
        val classification = urlClassifier.classifyUrl(encodedUrl)
        assertEquals("Percent-encoded adult query must be blocked", PolicyDecision.BLOCK, classification.decision)
    }

    @Test
    fun testBypass_ZeroWidthAndPunctuationInterleaving() {
        val interleaved = "p.o-r_n"
        val stripped = TextNormalizer.stripPunctuationAndSpacing(interleaved)
        assertEquals("porn", stripped)

        val classification = ruleClassifier.classifyText(interleaved)
        assertTrue("Punctuation-interleaved keyword must be blocked", classification.isExplicit)
    }

    @Test
    fun testBypass_AdultTldVariants() {
        val tld1 = domainMatcher.match("free-streaming.xxx")
        assertTrue(".xxx TLD must be blocked", tld1.isBlocked)

        val tld2 = domainMatcher.match("live-shows.cam")
        assertTrue(".cam TLD must be blocked", tld2.isBlocked)

        val tld3 = domainMatcher.match("unrated.tube")
        assertTrue(".tube TLD must be blocked", tld3.isBlocked)
    }
}
