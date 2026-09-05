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
import java.util.concurrent.ConcurrentHashMap

/**
 * Predefined Application Groups for bulk policy enforcement.
 */
enum class AppGroup(val displayName: String) {
    ALL_BROWSERS("All Web Browsers"),
    ALL_SOCIAL_MEDIA("All Social Media"),
    ALL_MESSAGING("All Messaging Apps"),
    ALL_GAMING("All Games"),
    ALL_VPN_BYPASS("All VPN & Proxy Apps")
}

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
        "org.havenapp.main",
        "com.wireguard.android",
        "org.openvpn.openvpn",
        "de.blinkt.openvpn",
        "com.cloudflare.onedotonedotonedotone",
        "com.psiphon3.subscription",
        "com.tunnelbear.android",
        "com.nordvpn.android",
        "com.expressvpn.vpn",
        "ch.protonvpn.android"
    )

    val SOCIAL_APPS = setOf(
        "com.instagram.android",
        "com.twitter.android",
        "com.zhiliaoapp.musically", // TikTok
        "com.snapchat.android",
        "com.reddit.frontpage",
        "com.facebook.katana",
        "com.pinterest"
    )

    val MESSAGING_APPS = setOf(
        "org.telegram.messenger",
        "com.whatsapp",
        "com.discord",
        "org.thoughtcrime.securesms", // Signal
        "com.facebook.orca" // Messenger
    )

    fun isBrowser(packageName: String): Boolean = KNOWN_BROWSERS.contains(packageName)
    fun isAnonymizingBypassApp(packageName: String): Boolean = ANONYMIZING_BYPASS_APPS.contains(packageName)
    fun isSocialApp(packageName: String): Boolean = SOCIAL_APPS.any { packageName.startsWith(it) }
    fun isMessagingApp(packageName: String): Boolean = MESSAGING_APPS.any { packageName.startsWith(it) }
}

/**
 * Scanner and in-memory cache for installed applications on the device.
 */
class AppScanner(private val context: Context) {

    data class ScannedApp(
        val packageName: String,
        val appLabel: String,
        val isSystem: Boolean,
        val isBrowser: Boolean,
        val category: String,
        val group: AppGroup? = null
    )

    // In-memory cache for fast UID/package lookups without constant PackageManager overhead
    private val appCache = ConcurrentHashMap<String, ScannedApp>()

    suspend fun scanInstalledApps(forceRefresh: Boolean = false): List<ScannedApp> = withContext(Dispatchers.IO) {
        if (!forceRefresh && appCache.isNotEmpty()) {
            return@withContext appCache.values.toList().sortedBy { it.appLabel.lowercase() }
        }

        val pm = context.packageManager
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val result = mutableListOf<ScannedApp>()

        for (app in installedApps) {
            val pkg = app.packageName
            if (pkg == context.packageName) continue // Skip self

            val label = app.loadLabel(pm).toString()
            val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isBrowser = BrowserRegistry.isBrowser(pkg)

            val (category, group) = when {
                BrowserRegistry.isAnonymizingBypassApp(pkg) -> Pair("BYPASS_APP", AppGroup.ALL_VPN_BYPASS)
                isBrowser -> Pair("BROWSER", AppGroup.ALL_BROWSERS)
                BrowserRegistry.isSocialApp(pkg) -> Pair("SOCIAL", AppGroup.ALL_SOCIAL_MEDIA)
                BrowserRegistry.isMessagingApp(pkg) -> Pair("MESSAGING", AppGroup.ALL_MESSAGING)
                isSystem -> Pair("SYSTEM", null)
                else -> Pair("UTILITY", null)
            }

            val scanned = ScannedApp(
                packageName = pkg,
                appLabel = label,
                isSystem = isSystem,
                isBrowser = isBrowser,
                category = category,
                group = group
            )
            appCache[pkg] = scanned
            result.add(scanned)
        }
        result.sortedBy { it.appLabel.lowercase() }
    }

    fun getCachedApp(packageName: String): ScannedApp? = appCache[packageName]
}

/**
 * Coordinates application policies and enforces them across the device.
 */
class AppPolicyEngine(
    private val appPolicyRepository: AppPolicyRepository,
    private val restrictionController: RestrictionController,
    private val deviceOwnerController: DeviceOwnerController,
    private val appScanner: AppScanner
) {

    suspend fun evaluateAndApplyPolicy(appRule: AppRuleEntity) = withContext(Dispatchers.IO) {
        appPolicyRepository.updateAppPolicy(
            packageName = appRule.packageName,
            appLabel = appRule.appLabel,
            category = appRule.category,
            policy = appRule.policy,
            reason = appRule.reason
        )

        // If running as Device Owner, enforce application suspension when BLOCKED
        if (deviceOwnerController.isDeviceOwner()) {
            when (appRule.policy) {
                AppPolicy.BLOCKED -> restrictionController.suspendPackage(appRule.packageName, true)
                AppPolicy.ALLOWED, AppPolicy.RESTRICTED, AppPolicy.SYSTEM_EXEMPT -> restrictionController.suspendPackage(appRule.packageName, false)
                AppPolicy.UNKNOWN -> { /* Default monitor */ }
            }
        }
    }

    /**
     * Applies a uniform policy across all applications within an AppGroup.
     */
    suspend fun applyGroupPolicy(group: AppGroup, policy: AppPolicy) = withContext(Dispatchers.IO) {
        val apps = appScanner.scanInstalledApps().filter { it.group == group }
        for (app in apps) {
            val rule = AppRuleEntity(
                packageName = app.packageName,
                appLabel = app.appLabel,
                category = app.category,
                policy = policy,
                reason = "GROUP_POLICY_${group.name}"
            )
            evaluateAndApplyPolicy(rule)
        }
    }
}
