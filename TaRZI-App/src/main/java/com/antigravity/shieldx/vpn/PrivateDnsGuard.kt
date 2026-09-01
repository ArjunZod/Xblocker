package com.antigravity.shieldx.vpn

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.antigravity.shieldx.device.DeviceOwnerController

/**
 * Closes the Private DNS bypass.
 *
 * Android's Private DNS setting sends every lookup over DNS-over-TLS to a
 * resolver the user picks. That traffic is encrypted, aimed at an address the
 * blocklist may never have seen, and on a device without a default route it
 * never reaches the tunnel at all - so a single settings toggle silently
 * defeats domain filtering.
 *
 * As device owner we can put it back to a mode we can actually inspect. Without
 * device owner we cannot change it, and the honest response is to report the
 * bypass rather than let the app claim protection it is not delivering.
 */
class PrivateDnsGuard(
    private val context: Context,
    private val deviceOwnerController: DeviceOwnerController
) {

    enum class Mode {
        /** No Private DNS: lookups are plaintext and the filter sees them. */
        OFF,

        /** Tries DoT to the network's resolver, falls back to plaintext. Filterable. */
        OPPORTUNISTIC,

        /** Pinned to a specific DoT host. This is the bypass. */
        STRICT_HOSTNAME,

        UNKNOWN
    }

    data class Status(
        val mode: Mode,
        val hostname: String?,
        /** True when lookups are leaving the device unfiltered. */
        val isBypassingFilter: Boolean,
        /** True when this app is able to correct it. */
        val canEnforce: Boolean
    )

    companion object {
        private const val TAG = "PrivateDnsGuard"

        // Global settings keys. Public constants exist only in @hide APIs, so
        // they are named directly; a missing key reads back as null and is
        // reported as UNKNOWN rather than assumed safe.
        private const val KEY_MODE = "private_dns_mode"
        private const val KEY_SPECIFIER = "private_dns_specifier"

        private const val MODE_OFF = "off"
        private const val MODE_OPPORTUNISTIC = "opportunistic"
        private const val MODE_HOSTNAME = "hostname"
    }

    fun status(): Status {
        val raw = runCatching {
            Settings.Global.getString(context.contentResolver, KEY_MODE)
        }.getOrNull()

        val hostname = runCatching {
            Settings.Global.getString(context.contentResolver, KEY_SPECIFIER)
        }.getOrNull()

        val mode = when (raw?.lowercase()) {
            MODE_OFF -> Mode.OFF
            MODE_OPPORTUNISTIC -> Mode.OPPORTUNISTIC
            MODE_HOSTNAME -> Mode.STRICT_HOSTNAME
            null -> Mode.UNKNOWN
            else -> Mode.UNKNOWN
        }

        return Status(
            mode = mode,
            hostname = hostname?.takeIf { it.isNotBlank() },
            isBypassingFilter = mode == Mode.STRICT_HOSTNAME,
            canEnforce = canEnforce()
        )
    }

    private fun canEnforce(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && deviceOwnerController.isDeviceOwner()

    /**
     * Moves Private DNS to opportunistic, which keeps lookups inside the tunnel:
     * the virtual resolver does not answer DoT, so Android falls back to
     * plaintext DNS and the filter sees every query.
     *
     * Returns false when it could not be changed - the caller should surface
     * that rather than treat it as success.
     */
    fun enforceFilterableDns(): Boolean {
        if (!canEnforce()) return false
        return runCatching {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            dpm.setGlobalPrivateDnsModeOpportunistic(deviceOwnerController.adminComponent)
            Log.i(TAG, "Private DNS set to opportunistic so lookups stay filterable")
            true
        }.onFailure {
            Log.w(TAG, "Could not set Private DNS mode: ${it.message}")
        }.getOrDefault(false)
    }

    /** One line for the UI, describing the actual state rather than an ideal one. */
    fun describe(): String {
        val s = status()
        return when {
            !s.isBypassingFilter && s.mode != Mode.UNKNOWN -> "Private DNS is not bypassing the filter"
            s.isBypassingFilter && s.canEnforce -> "Private DNS is set to ${s.hostname ?: "a fixed resolver"} — correcting it"
            s.isBypassingFilter -> "Private DNS is set to ${s.hostname ?: "a fixed resolver"}, which bypasses filtering. Turn it off in Settings › Network › Private DNS."
            else -> "Private DNS state could not be read"
        }
    }
}
