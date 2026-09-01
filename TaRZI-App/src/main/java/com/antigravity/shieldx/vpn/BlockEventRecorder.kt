package com.antigravity.shieldx.vpn

import com.antigravity.shieldx.core.model.BlockReason
import com.antigravity.shieldx.core.model.Category
import com.antigravity.shieldx.core.model.DeviceMode
import com.antigravity.shieldx.data.repository.AuditRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Records blocks without letting the packet loop pay for them.
 *
 * Blocking is bursty in a way that punishes naive logging: one page load fans
 * out into dozens of blocked subdomains, and a browser denied its DoH resolver
 * retries hard. Writing a row per packet meant a coroutine and a database
 * insert per packet, on the same loop that has to keep forwarding traffic.
 *
 * So the loop only touches an in-memory counter, and a single background job
 * flushes on an interval, collapsing repeats of the same target into one row.
 * The audit trail keeps the information that matters - what was blocked, why,
 * how often, when it last happened - and loses only the duplicate rows nobody
 * reads.
 */
class BlockEventRecorder(
    private val auditRepository: AuditRepository,
    private val scope: CoroutineScope,
    private val flushIntervalMs: Long = 3_000L,
    /** Above this many distinct targets we flush early rather than grow. */
    private val maxPendingKeys: Int = 256
) {

    private data class Key(
        val target: String,
        val category: Category,
        val reason: BlockReason,
        val detail: String
    )

    private class Tally {
        val count = AtomicInteger(0)
        @Volatile var lastSeenAt: Long = System.currentTimeMillis()
    }

    private val pending = ConcurrentHashMap<Key, Tally>()
    private var flushJob: Job? = null

    fun start() {
        if (flushJob?.isActive == true) return
        flushJob = scope.launch {
            while (isActive) {
                delay(flushIntervalMs)
                flush()
            }
        }
    }

    /**
     * Called from the packet loop. Must stay allocation-light and must never
     * suspend or touch disk.
     */
    fun record(
        target: String,
        category: Category,
        reason: BlockReason,
        detail: String
    ) {
        val key = Key(target, category, reason, detail)
        val tally = pending.computeIfAbsent(key) { Tally() }
        tally.count.incrementAndGet()
        tally.lastSeenAt = System.currentTimeMillis()

        if (pending.size >= maxPendingKeys) {
            scope.launch { flush() }
        }
    }

    /** Writes what has accumulated. Safe to call when nothing is pending. */
    suspend fun flush() {
        if (pending.isEmpty()) return

        // Snapshot and clear first: the loop keeps recording into a fresh map
        // while these rows are being written.
        val batch = pending.entries.toList()
        batch.forEach { pending.remove(it.key) }

        for ((key, tally) in batch) {
            val times = tally.count.get()
            if (times <= 0) continue
            runCatching {
                auditRepository.logBlockedEvent(
                    target = key.target,
                    category = key.category,
                    reason = key.reason,
                    deviceMode = DeviceMode.NORMAL_CONSUMER,
                    details = if (times == 1) {
                        key.detail
                    } else {
                        "${key.detail} (x$times)"
                    }
                )
            }
        }
    }

    fun stop() {
        flushJob?.cancel()
        flushJob = null
        // Nothing is awaited here; the caller's scope is being torn down, and a
        // handful of unwritten counters is not worth delaying shutdown for.
    }

    /** Distinct targets waiting to be written. Used by diagnostics. */
    fun pendingCount(): Int = pending.size
}
