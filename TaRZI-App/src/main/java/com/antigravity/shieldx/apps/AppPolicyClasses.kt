package com.antigravity.shieldx.apps

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.antigravity.shieldx.core.model.AppPolicy
import com.antigravity.shieldx.data.local.entities.AppRuleEntity
import com.antigravity.shieldx.data.repository.AppPolicyRepository
import com.antigravity.shieldx.device.DeviceOwnerController
import com.antigravity.shieldx.device.RestrictionController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Registry of known browser packages, custom tabs, and anonymizing bypass apps.
 */
object BrowserRegistry {

    val KNOWN_BROWSERS = setOf(
        "com.android.chrome",
        "org.mozilla.firefox",
        "org.mozilla.firefox_beta",
        "org.mozilla.fenix",
        "com.brave.browser",
        "com.microsoft.emmx",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.opera.touch",
        "com.sec.android.app.sbrowser",
        "com.duckduckgo.mobile.android",
        "com.vivaldi.browser",
        "com.kiwibrowser.browser",
        "com.aloha.browser",
        "mark.via.gp",
        "org.bromite.bromite",
        "com.ecosia.android",
        "com.UCMobile.intl"
    )

    val ANONYMIZING_BYPASS_APPS = setOf(
        "org.torproject.torbrowser",
        "org.torproject.torbrowser_alpha",
        "org.torproject.android",
        "org.havenapp.main"
    )

    fun isBrowser(packageName: String): Boolean = KNOWN_BROWSERS.contains(packageName)
    fun isAnonymizingBypassApp(packageName: String): Boolean = ANONYMIZING_BYPASS_APPS.contains(packageName)
}

/**
 * Scanner for installed applications on the device.
 */
class AppScanner(private val context: Context) {

    data class ScannedApp(
        val packageName: String,
        val appLabel: String,
        val isSystem: Boolean,
        val isBrowser: Boolean,
        val category: String
    )

    suspend fun scanInstalledApps(): List<ScannedApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val result = mutableListOf<ScannedApp>()

        for (app in installedApps) {
            val pkg = app.packageName
            // Skip self
            if (pkg == context.packageName) continue

            val label = app.loadLabel(pm).toString()
            val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isBrowser = BrowserRegistry.isBrowser(pkg)

            val category = when {
                BrowserRegistry.isAnonymizingBypassApp(pkg) -> "BYPASS_APP"
                isBrowser -> "BROWSER"
                isSystem -> "SYSTEM"
                isSocialApp(pkg) -> "SOCIAL"
                else -> "UTILITY"
            }

            result.add(
                ScannedApp(
                    packageName = pkg,
                    appLabel = label,
                    isSystem = isSystem,
                    isBrowser = isBrowser,
                    category = category
                )
            )
        }
        result.sortedBy { it.appLabel.lowercase() }
    }

    private fun isSocialApp(pkg: String): Boolean {
        val socialPrefixes = listOf("com.instagram", "com.twitter", "com.reddit", "com.snapchat", "com.tiktok", "com.discord", "org.telegram")
        return socialPrefixes.any { pkg.startsWith(it) }
    }
}

/**
 * Coordinates application policies and enforces them across the device.
 */
class AppPolicyEngine(
    private val appPolicyRepository: AppPolicyRepository,
    private val restrictionController: RestrictionController,
    private val deviceOwnerController: DeviceOwnerController
) {

    suspend fun evaluateAndApplyPolicy(appRule: AppRuleEntity) = withContext(Dispatchers.IO) {
        appPolicyRepository.updateAppPolicy(
            packageName = appRule.packageName,
            appLabel = appRule.appLabel,
            category = appRule.category,
            policy = appRule.policy,
            reason = appRule.reason
        )

        // If in Managed Mode (Device Owner), enforce suspension if BLOCKED
        if (deviceOwnerController.isDeviceOwner()) {
            when (appRule.policy) {
                AppPolicy.BLOCKED -> restrictionController.suspendPackage(appRule.packageName, true)
                AppPolicy.ALLOWED, AppPolicy.RESTRICTED, AppPolicy.SYSTEM_EXEMPT -> restrictionController.suspendPackage(appRule.packageName, false)
                AppPolicy.UNKNOWN -> { /* Default allow with network monitoring */ }
            }
        }
    }
}
