package com.antigravity.shieldx.vpn

import com.antigravity.shieldx.core.model.Category
import com.antigravity.shieldx.core.model.PolicyDecision
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DnsDecisionCacheTest {

    private lateinit var cache: DnsDecisionCache

    @Before
    fun setUp() {
        // Small capacity cache for predictable testing
        cache = DnsDecisionCache(maxCapacity = 3, defaultTtlMillis = 1000L)
    }

    @Test
    fun testPutAndGet() {
        val entry = DnsDecisionCache.DecisionEntry(
            decision = PolicyDecision.BLOCK,
            category = Category.PORNOGRAPHY,
            reason = "TEST_MATCH",
            cachedAt = System.currentTimeMillis(),
            ttlMillis = 5000L
        )

        cache.put("example.com", 1, entry)
        val retrieved = cache.get("example.com", 1)

        assertNotNull(retrieved)
        assertEquals(PolicyDecision.BLOCK, retrieved?.decision)
        assertEquals(Category.PORNOGRAPHY, retrieved?.category)
    }

    @Test
    fun testDomainNormalizationCaseInsensitive() {
        val entry = DnsDecisionCache.DecisionEntry(
            decision = PolicyDecision.ALLOW,
            category = Category.SAFE,
            reason = "SAFE_SITE",
            cachedAt = System.currentTimeMillis(),
            ttlMillis = 5000L
        )

        cache.put("WikiPedia.ORG.", 1, entry)
        val retrieved = cache.get("wikipedia.org", 1)

        assertNotNull(retrieved)
        assertEquals(PolicyDecision.ALLOW, retrieved?.decision)
    }

    @Test
    fun testLruEviction() {
        val entry = { name: String ->
            DnsDecisionCache.DecisionEntry(
                decision = PolicyDecision.ALLOW,
                category = Category.SAFE,
                reason = name,
                cachedAt = System.currentTimeMillis(),
                ttlMillis = 5000L
            )
        }

        cache.put("site1.com", 1, entry("site1"))
        cache.put("site2.com", 1, entry("site2"))
        cache.put("site3.com", 1, entry("site3"))

        // Capacity is 3, all 3 present
        assertNotNull(cache.get("site1.com", 1))
        assertNotNull(cache.get("site2.com", 1))
        assertNotNull(cache.get("site3.com", 1))

        // Inserting 4th should evict the least recently used
        // Since we accessed site1, site2, site3 in that order, site1 was accessed first, site2 next, site3 last
        // If we access site1 again:
        cache.get("site1.com", 1)
        // Now site2 is the LRU.
        cache.put("site4.com", 1, entry("site4"))

        assertNotNull(cache.get("site1.com", 1))
        assertNotNull(cache.get("site3.com", 1))
        assertNotNull(cache.get("site4.com", 1))
        assertNull(cache.get("site2.com", 1))
    }

    @Test
    fun testTtlExpiration() {
        val entry = DnsDecisionCache.DecisionEntry(
            decision = PolicyDecision.BLOCK,
            category = Category.MALWARE,
            reason = "EXPIRED_TEST",
            cachedAt = System.currentTimeMillis() - 2000L, // 2s in the past
            ttlMillis = 1000L // 1s TTL
        )

        cache.put("malware.com", 1, entry)
        val retrieved = cache.get("malware.com", 1)

        // Must expire and return null
        assertNull(retrieved)
    }

    @Test
    fun testClear() {
        val entry = DnsDecisionCache.DecisionEntry(
            decision = PolicyDecision.ALLOW,
            category = Category.SAFE,
            reason = "CLEARED",
            cachedAt = System.currentTimeMillis(),
            ttlMillis = 5000L
        )

        cache.put("site.com", 1, entry)
        cache.clear()

        assertNull(cache.get("site.com", 1))
        assertEquals(0, cache.size())
    }
}
