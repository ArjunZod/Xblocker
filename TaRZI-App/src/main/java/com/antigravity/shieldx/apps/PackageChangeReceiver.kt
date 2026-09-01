package com.antigravity.shieldx.apps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.antigravity.shieldx.core.model.AppPolicy
import com.antigravity.shieldx.core.model.TamperState
import com.antigravity.shieldx.data.local.AppDatabase
import com.antigravity.shieldx.data.repository.AppPolicyRepository
import com.antigravity.shieldx.data.repository.AuditRepository
import com.antigravity.shieldx.data.repository.ConfigRepository
import com.antigravity.shieldx.data.repository.DomainRepository
import com.antigravity.shieldx.device.DeviceOwnerController
import com.antigravity.shieldx.device.RestrictionController
import com.antigravity.shieldx.vpn.ProtectionVpnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PackageChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val database = AppDatabase.getInstance(context)
        val auditRepo = AuditRepository(database)
        val appPolicyRepo = AppPolicyRepository(context, database)
        val domainRepo = DomainRepository(context, database)
        val configRepo = ConfigRepository(database)
        val deviceOwnerController = DeviceOwnerController(context)
        val restrictionController = RestrictionController(context, deviceOwnerController)

        CoroutineScope(Dispatchers.IO).launch {
            when (action) {
                Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                    // Seed default blocklists if first launch
                    domainRepo.populateDefaultBlocklistIfEmpty()
                    appPolicyRepo.populateDefaultAppPoliciesIfEmpty()

                    // Restart VPN service if enabled
                    ProtectionVpnService.startService(context)

                    // Re-enforce managed restrictions if Device Owner
                    if (deviceOwnerController.isDeviceOwner()) {
                        restrictionController.applyManagedRestrictions()
                    }

                    // The assistant is its own app now and restores its own
                    // always-on services at boot; this receiver only restores
                    // protection.

                    auditRepo.logTamperEvent(
                        "SYSTEM_BOOT_RESTORE",
                        "LOW",
                        "ShieldX protection restored upon boot/upgrade.",
                        TamperState.NORMAL
                    )
                }

                Intent.ACTION_PACKAGE_ADDED -> {
                    val pkgName = intent.data?.schemeSpecificPart ?: return@launch
                    val appLabel = runCatching {
                        val pm = context.packageManager
                        val appInfo = pm.getApplicationInfo(pkgName, 0)
                        appInfo.loadLabel(pm).toString()
                    }.getOrDefault(pkgName)

                    val isBypassApp = BrowserRegistry.isAnonymizingBypassApp(pkgName)
                    val isBrowser = BrowserRegistry.isBrowser(pkgName)

                    val initialPolicy = when {
                        isBypassApp -> AppPolicy.BLOCKED
                        isBrowser -> AppPolicy.RESTRICTED
                        else -> AppPolicy.UNKNOWN
                    }

                    appPolicyRepo.updateAppPolicy(
                        packageName = pkgName,
                        appLabel = appLabel,
                        category = if (isBrowser) "BROWSER" else if (isBypassApp) "BYPASS_APP" else "UTILITY",
                        policy = initialPolicy,
                        reason = "Automatically classified upon installation"
                    )

                    // If Device Owner and app is prohibited, suspend it immediately
                    if (deviceOwnerController.isDeviceOwner() && initialPolicy == AppPolicy.BLOCKED) {
                        restrictionController.suspendPackage(pkgName, true)
                    }

                    auditRepo.logTamperEvent(
                        "PACKAGE_INSTALLED",
                        if (isBypassApp) "HIGH" else "LOW",
                        "New package installed: $appLabel ($pkgName), policy assigned: $initialPolicy",
                        if (isBypassApp) TamperState.SUSPICIOUS else TamperState.NORMAL
                    )
                }

                Intent.ACTION_PACKAGE_REMOVED -> {
                    val pkgName = intent.data?.schemeSpecificPart ?: return@launch
                    auditRepo.logTamperEvent(
                        "PACKAGE_REMOVED",
                        "LOW",
                        "Package removed: $pkgName",
                        TamperState.NORMAL
                    )
                }
            }
        }
    }
}
