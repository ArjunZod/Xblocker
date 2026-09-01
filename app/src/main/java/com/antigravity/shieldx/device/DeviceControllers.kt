package com.antigravity.shieldx.device

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.UserManager
import com.antigravity.shieldx.core.model.DeviceMode
import com.antigravity.shieldx.core.model.TamperState
import com.antigravity.shieldx.data.local.AppDatabase
import com.antigravity.shieldx.data.repository.AuditRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ShieldXDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        val database = AppDatabase.getInstance(context)
        val auditRepo = AuditRepository(database)
        CoroutineScope(Dispatchers.IO).launch {
            auditRepo.logTamperEvent(
                "DEVICE_ADMIN_ENABLED",
                "LOW",
                "ShieldX Device Admin has been activated.",
                TamperState.NORMAL
            )
        }
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        val database = AppDatabase.getInstance(context)
        val auditRepo = AuditRepository(database)
        CoroutineScope(Dispatchers.IO).launch {
            auditRepo.logTamperEvent(
                "DEVICE_ADMIN_DISABLED",
                "CRITICAL",
                "ShieldX Device Admin was disabled.",
                TamperState.RECOVERY_REQUIRED
            )
        }
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Disabling ShieldX Device Admin will deactivate system-level adult content protection and VPN lockdown."
    }
}

class DeviceOwnerController(private val context: Context) {

    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val adminComponent = ComponentName(context, ShieldXDeviceAdminReceiver::class.java)

    /**
     * Check if ShieldX is currently running as the active Device Owner.
     */
    fun isDeviceOwner(): Boolean {
        return try {
            dpm.isDeviceOwnerApp(context.packageName)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Check if ShieldX is running as Profile Owner.
     */
    fun isProfileOwner(): Boolean {
        return try {
            dpm.isProfileOwnerApp(context.packageName)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Check if ShieldX is an active Device Administrator.
     */
    fun isDeviceAdminActive(): Boolean {
        return try {
            dpm.isAdminActive(adminComponent)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Returns the detected operational device mode.
     */
    fun getDeviceMode(): DeviceMode {
        return if (isDeviceOwner()) DeviceMode.MANAGED_DEVICE_OWNER else DeviceMode.NORMAL_CONSUMER
    }

    /**
     * Configure Always-On VPN and Lockdown mode via DevicePolicyManager.
     * In lockdown mode, networking is denied if VPN is disconnected.
     */
    fun configureAlwaysOnVpn(lockdownEnabled: Boolean = true): Boolean {
        if (!isDeviceOwner() && !isProfileOwner()) return false
        return try {
            dpm.setAlwaysOnVpnPackage(adminComponent, context.packageName, lockdownEnabled)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Enable or disable uninstall protection for ShieldX.
     */
    fun setUninstallProtection(packageName: String = context.packageName, blocked: Boolean = true): Boolean {
        if (!isDeviceOwner() && !isProfileOwner()) return false
        return try {
            dpm.setUninstallBlocked(adminComponent, packageName, blocked)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Returns the exact ADB provisioning command for Device Owner activation.
     */
    fun getAdbProvisioningCommand(): String {
        return "adb shell dpm set-device-owner ${context.packageName}/.device.ShieldXDeviceAdminReceiver"
    }
}

class RestrictionController(
    private val context: Context,
    private val deviceOwnerController: DeviceOwnerController
) {
    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    /**
     * Apply full enterprise security restrictions to protect VPN and device integrity.
     */
    fun applyManagedRestrictions(): Boolean {
        if (!deviceOwnerController.isDeviceOwner()) return false

        return try {
            val admin = deviceOwnerController.adminComponent

            // 1. Prevent user from altering or disconnecting VPN
            dpm.addUserRestriction(admin, UserManager.DISALLOW_CONFIG_VPN)

            // 2. Prevent uninstalling ShieldX
            dpm.setUninstallBlocked(admin, context.packageName, true)

            // 3. Disallow booting into Safe Mode (bypasses apps on some devices)
            dpm.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)

            // 4. Disallow adding new secondary users (which might bypass VPN)
            dpm.addUserRestriction(admin, UserManager.DISALLOW_ADD_USER)

            // 5. Enforce Always-On VPN with lockdown fail-closed behavior
            deviceOwnerController.configureAlwaysOnVpn(lockdownEnabled = true)

            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Suspend a prohibited application package (prevents launch and background activity).
     */
    fun suspendPackage(packageName: String, suspend: Boolean = true): Boolean {
        if (!deviceOwnerController.isDeviceOwner() && !deviceOwnerController.isProfileOwner()) return false
        return try {
            val result = dpm.setPackagesSuspended(deviceOwnerController.adminComponent, arrayOf(packageName), suspend)
            result.isEmpty() // Empty list means all packages were successfully suspended
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Hide or unhide an application package.
     */
    fun hidePackage(packageName: String, hide: Boolean = true): Boolean {
        if (!deviceOwnerController.isDeviceOwner() && !deviceOwnerController.isProfileOwner()) return false
        return try {
            dpm.setApplicationHidden(deviceOwnerController.adminComponent, packageName, hide)
        } catch (_: Exception) {
            false
        }
    }
}
