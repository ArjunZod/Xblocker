package com.antigravity.shieldx.classifier.ai

import com.antigravity.shieldx.core.model.Category
import com.antigravity.shieldx.core.model.PolicyDecision
import com.antigravity.shieldx.data.local.AppDatabase
import com.antigravity.shieldx.data.local.entities.ClassificationCacheEntity
import com.antigravity.shieldx.data.local.entities.ModelDecisionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Manages caching, rate limiting, request deduplication, and circuit breaker for AI classification.
 */
class AICostController(private val database: AppDatabase) {

    private val inFlightRequests = ConcurrentHashMap<String, Mutex>()
    private val consecutiveFailures = AtomicInteger(0)
    private val circuitBreakerTripTime = AtomicLong(0L)

    private val requestCountInCurrentMinute = AtomicInteger(0)
    private val minuteWindowStart = AtomicLong(System.currentTimeMillis())

    companion object {
        const val MAX_REQUESTS_PER_MINUTE = 30
        const val CIRCUIT_BREAKER_THRESHOLD = 3
        const val CIRCUIT_BREAKER_RESET_MS = 60000L // 1 minute
        const val CACHE_TTL_MS = 86400000L // 24 hours
    }

    /**
     * Compute SHA-256 hash of input text/URL.
     */
    fun computeHash(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Check if a cached decision exists for the given hash and is still valid.
     */
    suspend fun getCachedDecision(hash: String): ClassificationCacheEntity? = withContext(Dispatchers.IO) {
        val cached = database.classificationCacheDao().getByHash(hash) ?: return@withContext null
        if (System.currentTimeMillis() - cached.cachedAt > cached.ttlMillis) {
            return@withContext null
        }
        cached
    }

    /**
     * Save decision in classification cache.
     */
    suspend fun cacheDecision(
        hash: String,
        target: String,
        decision: PolicyDecision,
        category: Category,
        confidence: Float
    ) = withContext(Dispatchers.IO) {
        database.classificationCacheDao().insert(
            ClassificationCacheEntity(
                contentHash = hash,
                target = target,
                decision = decision,
                category = category,
                confidence = confidence,
                cachedAt = System.currentTimeMillis(),
                ttlMillis = CACHE_TTL_MS
            )
        )
    }

    /**
     * Check if circuit breaker allows outgoing AI requests.
     */
    fun isCircuitBreakerOpen(): Boolean {
        val tripTime = circuitBreakerTripTime.get()
        if (tripTime == 0L) return false

        // Check if cooldown period elapsed
        if (System.currentTimeMillis() - tripTime > CIRCUIT_BREAKER_RESET_MS) {
            consecutiveFailures.set(0)
            circuitBreakerTripTime.set(0L)
            return false
        }
        return true
    }

    /**
     * Check rate limit before executing request.
     */
    fun allowRateLimit(): Boolean {
        val now = System.currentTimeMillis()
        val windowStart = minuteWindowStart.get()

        if (now - windowStart > 60000L) {
            minuteWindowStart.set(now)
            requestCountInCurrentMinute.set(1)
            return true
        }

        val currentCount = requestCountInCurrentMinute.incrementAndGet()
        return currentCount <= MAX_REQUESTS_PER_MINUTE
    }

    fun recordSuccess(
        inputHash: String,
        decision: PolicyDecision,
        category: Category,
        confidence: Float,
        latencyMs: Long,
        reasonCode: String
    ) {
        consecutiveFailures.set(0)
        circuitBreakerTripTime.set(0L)

        // Log decision to Room DB
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                database.modelDecisionDao().insert(
                    ModelDecisionEntity(
                        inputHash = inputHash,
                        decision = decision,
                        category = category,
                        confidence = confidence,
                        latencyMs = latencyMs,
                        reasonCode = reasonCode,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun recordFailure() {
        val failures = consecutiveFailures.incrementAndGet()
        if (failures >= CIRCUIT_BREAKER_THRESHOLD) {
            circuitBreakerTripTime.set(System.currentTimeMillis())
        }
    }

    suspend fun <T> executeWithDeduplication(hash: String, block: suspend () -> T): T {
        val mutex = inFlightRequests.getOrPut(hash) { Mutex() }
        return mutex.withLock {
            try {
                block()
            } finally {
                inFlightRequests.remove(hash)
            }
        }
    }
}
