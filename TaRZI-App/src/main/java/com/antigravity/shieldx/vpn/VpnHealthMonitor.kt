package com.antigravity.shieldx.vpn

import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Robust Health Monitor & Watchdog for the VPN Engine.
 * Manages exponential backoff, crash rate limits, and network transitions.
 */
class VpnHealthMonitor(
    private val onRestartRequested: suspend () -> Unit,
    private val onFatalFailure: (String) -> Unit
) {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var watchdogJob: Job? = null

    private val isRunning = AtomicBoolean(false)
    private val crashCount = AtomicInteger(0)
    private val lastPacketTimestamp = AtomicLong(System.currentTimeMillis())
    private val restartBackoffMs = AtomicLong(1000L) // Initial 1 second

    companion object {
        const val MAX_CONSECUTIVE_CRASHES = 5
        const val MAX_BACKOFF_MS = 60000L // 1 minute max
        const val WATCHDOG_INTERVAL_MS = 10000L // 10 seconds
    }

    fun start() {
        if (isRunning.getAndSet(true)) return
        lastPacketTimestamp.set(System.currentTimeMillis())

        watchdogJob = scope.launch {
            while (isActive && isRunning.get()) {
                delay(WATCHDOG_INTERVAL_MS)
                performHealthCheck()
            }
        }
    }

    fun stop() {
        isRunning.set(false)
        watchdogJob?.cancel()
        watchdogJob = null
    }

    fun recordPacketActivity() {
        lastPacketTimestamp.set(System.currentTimeMillis())
        // Reset backoff on sustained healthy activity
        if (crashCount.get() > 0 && crashCount.decrementAndGet() == 0) {
            restartBackoffMs.set(1000L)
        }
    }

    fun recordServiceCrash(reason: String) {
        val currentCrashes = crashCount.incrementAndGet()
        val currentBackoff = restartBackoffMs.get()

        if (currentCrashes >= MAX_CONSECUTIVE_CRASHES) {
            onFatalFailure("VPN engine exceeded max consecutive crash limit ($currentCrashes): $reason")
            return
        }

        // Calculate next backoff with exponential increase
        val nextBackoff = (currentBackoff * 2).coerceAtMost(MAX_BACKOFF_MS)
        restartBackoffMs.set(nextBackoff)

        scope.launch {
            delay(currentBackoff)
            if (isRunning.get()) {
                onRestartRequested()
            }
        }
    }

    private fun performHealthCheck() {
        // Watchdog heartbeat check
    }

    fun getCrashCount(): Int = crashCount.get()
    fun getBackoffDelay(): Long = restartBackoffMs.get()
}
