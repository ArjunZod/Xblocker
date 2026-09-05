package com.antigravity.shieldx.vpn

import com.antigravity.shieldx.core.model.BlockReason
import com.antigravity.shieldx.core.model.Category
import com.antigravity.shieldx.core.model.PolicyDecision
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * High-performance, TTL-aware, memory-bounded LRU decision cache for DNS resolutions.
 * Eliminates redundant trie scans, database queries, and SafeSearch calculations for
 * recurring hostnames.
 */
class DnsDecisionCache(private val maxEntries: Int = 2048) {

    data class CachedDecision(
        val decision: PolicyDecision,
        val category: Category,
        val reason: BlockReason?,
        val responseBytes: ByteArray?,
        val expiresAtMs: Long
    ) {
        val isExpired: Boolean
            get() = System.currentTimeMillis() > expiresAtMs
    }

    private val lock = ReentrantReadWriteLock()

    // Access-order LinkedHashMap for thread-safe bounded LRU eviction
    private val lruMap = object : LinkedHashMap<String, CachedDecision>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedDecision>?): Boolean {
            return size > maxEntries
        }
    }

    private var hitCount = 0L
    private var missCount = 0L

    private fun cacheKey(domain: String, qType: Int): String = "$domain#$qType"

    /**
     * Retrieve a cached resolution if it exists and has not expired.
     */
    fun get(domain: String, qType: Int): CachedDecision? {
        val key = cacheKey(domain, qType)
        val now = System.currentTimeMillis()

        lock.read {
            val entry = lruMap[key]
            if (entry != null && entry.expiresAtMs > now) {
                hitCount++
                return entry
            }
        }

        // Clean up expired entry if found
        lock.write {
            val entry = lruMap[key]
            if (entry != null && entry.expiresAtMs <= now) {
                lruMap.remove(key)
            }
            missCount++
        }

        return null
    }

    /**
     * Cache a resolution with a bounded TTL (clamped between 5 seconds and 3600 seconds).
     */
    fun put(
        domain: String,
        qType: Int,
        decision: PolicyDecision,
        category: Category,
        reason: BlockReason?,
        responseBytes: ByteArray?,
        ttlSeconds: Int
    ) {
        val clampedTtl = ttlSeconds.coerceIn(5, 3600)
        val expiresAt = System.currentTimeMillis() + (clampedTtl * 1000L)
        val entry = CachedDecision(
            decision = decision,
            category = category,
            reason = reason,
            responseBytes = responseBytes,
            expiresAtMs = expiresAt
        )

        val key = cacheKey(domain, qType)
        lock.write {
            lruMap[key] = entry
        }
    }

    /**
     * Invalidate entire cache (e.g. when policy rules change).
     */
    fun clear() {
        lock.write {
            lruMap.clear()
        }
    }

    /**
     * Cache metrics for diagnostics and monitoring.
     */
    fun getStats(): Map<String, Any> = lock.read {
        mapOf(
            "size" to lruMap.size,
            "maxEntries" to maxEntries,
            "hits" to hitCount,
            "misses" to missCount,
            "hitRate" to if (hitCount + missCount > 0) hitCount.toDouble() / (hitCount + missCount) else 0.0
        )
    }
}
