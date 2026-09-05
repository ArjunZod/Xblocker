package com.antigravity.shieldx.tamper

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import java.util.concurrent.atomic.AtomicLong

/**
 * High-precision detector for system time manipulation.
 *
 * Android users attempting to bypass time-based restrictions (e.g., bed-time lockouts,
 * daily quota windows, streak expirations) frequently roll back the system clock in
 * device settings.
 *
 * SystemClock.elapsedRealtime() is monotonic and ticks continuously even in deep sleep,
 * and CANNOT be modified by setting the system date or time. By cross-referencing
 * wall-clock delta with monotonic delta, time tampering is detected instantly.
 */
class TimeTamperDetector(
    private val maxDriftToleranceMs: Long = 120_000L // 2 minutes tolerance for NTP sync
) {
    private val lastWallTime = AtomicLong(System.currentTimeMillis())
    private val lastMonotonicTime = AtomicLong(SystemClock.elapsedRealtime())

    data class TimeCheckResult(
        val isTampered: Boolean,
        val wallDeltaMs: Long,
        val monotonicDeltaMs: Long,
        val driftMs: Long,
        val reason: String?
    )

    /**
     * Inspects elapsed time since previous check.
     */
    fun checkTimeIntegrity(): TimeCheckResult {
        val currentWall = System.currentTimeMillis()
        val currentMonotonic = SystemClock.elapsedRealtime()

        val prevWall = lastWallTime.getAndSet(currentWall)
        val prevMonotonic = lastMonotonicTime.getAndSet(currentMonotonic)

        val wallDelta = currentWall - prevWall
        val monotonicDelta = currentMonotonic - prevMonotonic

        // Monotonic time must always increase
        if (monotonicDelta < 0) {
            return TimeCheckResult(
                isTampered = true,
                wallDeltaMs = wallDelta,
                monotonicDeltaMs = monotonicDelta,
                driftMs = 0L,
                reason = "Monotonic clock anomaly detected"
            )
        }

        // Backward jump in wall clock
        if (wallDelta < -maxDriftToleranceMs) {
            return TimeCheckResult(
                isTampered = true,
                wallDeltaMs = wallDelta,
                monotonicDeltaMs = monotonicDelta,
                driftMs = wallDelta - monotonicDelta,
                reason = "System clock rolled backward by ${-wallDelta / 1000}s"
            )
        }

        // Excessive forward jump compared to monotonic elapsed time
        val drift = Math.abs(wallDelta - monotonicDelta)
        if (drift > maxDriftToleranceMs && wallDelta > monotonicDelta) {
            return TimeCheckResult(
                isTampered = true,
                wallDeltaMs = wallDelta,
                monotonicDeltaMs = monotonicDelta,
                driftMs = drift,
                reason = "System clock abruptly advanced by ${drift / 1000}s"
            )
        }

        return TimeCheckResult(
            isTampered = false,
            wallDeltaMs = wallDelta,
            monotonicDeltaMs = monotonicDelta,
            driftMs = drift,
            reason = null
        )
    }

    /**
     * Resets baseline reference (e.g. after authorized reboot or system update).
     */
    fun resetBaseline() {
        lastWallTime.set(System.currentTimeMillis())
        lastMonotonicTime.set(SystemClock.elapsedRealtime())
    }
}

/**
 * Inspects Android Private DNS (DNS over TLS / DoT) system configuration.
 */
class PrivateDnsDetector(private val context: Context) {

    enum class PrivateDnsMode {
        OFF,
        OPPORTUNISTIC, // Automatic / system default
        STRICT,        // Hostname specified (e.g. dns.google, 1dot1dot1dot1.cloudflare-dns.com)
        UNKNOWN
    }

    data class PrivateDnsStatus(
        val mode: PrivateDnsMode,
        val specifier: String?,
        val potentialBypassRisk: Boolean
    )

    fun checkPrivateDns(): PrivateDnsStatus {
        return try {
            val modeStr = Settings.Global.getString(context.contentResolver, "private_dns_mode")
            val specifier = Settings.Global.getString(context.contentResolver, "private_dns_specifier")

            val mode = when (modeStr?.lowercase()) {
                "off" -> PrivateDnsMode.OFF
                "opportunistic" -> PrivateDnsMode.OPPORTUNISTIC
                "hostname" -> PrivateDnsMode.STRICT
                else -> PrivateDnsMode.UNKNOWN
            }

            // In STRICT mode, Android routes DoT on port 853 to a custom resolver,
            // which can bypass standard port 53 VPN interception if not dropped by firewall.
            val risk = mode == PrivateDnsMode.STRICT

            PrivateDnsStatus(
                mode = mode,
                specifier = specifier,
                potentialBypassRisk = risk
            )
        } catch (e: Exception) {
            PrivateDnsStatus(
                mode = PrivateDnsMode.UNKNOWN,
                specifier = null,
                potentialBypassRisk = false
            )
        }
    }
}
