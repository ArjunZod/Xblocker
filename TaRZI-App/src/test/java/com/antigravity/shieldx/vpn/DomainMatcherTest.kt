package com.antigravity.shieldx.vpn

import com.antigravity.shieldx.core.model.Category
import com.antigravity.shieldx.core.model.MatchType
import com.antigravity.shieldx.core.model.PolicyDecision
import com.antigravity.shieldx.data.local.entities.DomainRuleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DomainMatcherTest {

    private lateinit var matcher: DomainMatcher

    @Before
    fun setUp() {
        matcher = DomainMatcher()
        val rules = listOf(
            DomainRuleEntity(
                domain = "pornhub.com",
                category = Category.PORNOGRAPHY,
                matchType = MatchType.SUBDOMAIN,
                action = PolicyDecision.BLOCK
            ),
            DomainRuleEntity(
                domain = "xvideos.com",
                category = Category.PORNOGRAPHY,
                matchType = MatchType.SUBDOMAIN,
                action = PolicyDecision.BLOCK
            ),
            DomainRuleEntity(
                domain = "safe-adult-health.pornhub.com",
                category = Category.SAFE,
                matchType = MatchType.EXACT,
                action = PolicyDecision.ALLOW
            )
        )
        matcher.loadRules(rules)
    }

    @Test
    fun testExactDomainMatch() {
        val result = matcher.match("pornhub.com")
        assertTrue(result.isBlocked)
        assertEquals(Category.PORNOGRAPHY, result.category)
        assertEquals(PolicyDecision.BLOCK, result.decision)
    }

    @Test
    fun testSubdomainHierarchyMatch() {
        val sub1 = matcher.match("m.pornhub.com")
        assertTrue(sub1.isBlocked)

        val sub2 = matcher.match("video.cdn.xvideos.com")
        assertTrue(sub2.isBlocked)
    }

    @Test
    fun testSafeDomainNotMatched() {
        val result = matcher.match("google.com")
        assertFalse(result.isBlocked)
        assertEquals(PolicyDecision.ALLOW, result.decision)
    }

    @Test
    fun testAdultTldMatch() {
        val resultXxx = matcher.match("somewebsite.xxx")
        assertTrue(resultXxx.isBlocked)

        val resultPorn = matcher.match("random.porn")
        assertTrue(resultPorn.isBlocked)

        val resultAdult = matcher.match("media.adult")
        assertTrue(resultAdult.isBlocked)
    }

    @Test
    fun testAllowlistOverride() {
        val result = matcher.match("safe-adult-health.pornhub.com")
        assertFalse(result.isBlocked)
        assertEquals(PolicyDecision.ALLOW, result.decision)
    }

    @Test
    fun testNormalizationOfUrlsAndProtocols() {
        val result = matcher.match("https://www.pornhub.com:443/video/123")
        assertTrue(result.isBlocked)
    }
}
