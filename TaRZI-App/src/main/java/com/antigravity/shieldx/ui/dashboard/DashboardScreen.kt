package com.antigravity.shieldx.ui.dashboard

import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.antigravity.shieldx.core.model.Category
import com.antigravity.shieldx.core.model.ProtectionProfile
import com.antigravity.shieldx.core.security.SecurityManager
import com.antigravity.shieldx.ui.components.*
import com.antigravity.shieldx.ui.theme.*
import com.antigravity.shieldx.vpn.ProtectionVpnService
import kotlinx.coroutines.launch

/**
 * Enterprise Protection & Category Management Dashboard.
 * Provides fine-grained toggles across the 14-category protection taxonomy:
 * Adult, SafeSearch, Gambling, Malware/Phishing, Social Media, Short Video, and Trackers.
 */
@Composable
fun DashboardScreen(
    securityManager: SecurityManager,
    onNavigateToSetup: () -> Unit,
    onNavigateToLogs: () -> Unit,
    onNavigateToPolicies: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isVpnRunning by ProtectionVpnService.isRunningFlow.collectAsState()
    val policy by securityManager.policyRepository.currentPolicyFlow.collectAsState(initial = null)
    val blockedToday by securityManager.auditRepository.getBlockedTodayCountFlow()
        .collectAsState(initial = 0)

    val isProtected = policy?.isEnabled == true && isVpnRunning
    val isLockedDown = remember { securityManager.lockdownController.isActive() }

    var message by remember { mutableStateOf<String?>(null) }

    // Category State Toggles from ConfigStore
    val blockGamblingStr by securityManager.configRepository.getFlow("cat_gambling").collectAsState(initial = "true")
    val blockMalwareStr by securityManager.configRepository.getFlow("cat_malware").collectAsState(initial = "true")
    val blockSocialStr by securityManager.configRepository.getFlow("cat_social").collectAsState(initial = "false")
    val blockShortVideoStr by securityManager.configRepository.getFlow("cat_short_video").collectAsState(initial = "false")
    val blockTrackersStr by securityManager.configRepository.getFlow("cat_trackers").collectAsState(initial = "true")

    val blockGambling = blockGamblingStr != "false"
    val blockMalware = blockMalwareStr != "false"
    val blockSocial = blockSocialStr == "true"
    val blockShortVideo = blockShortVideoStr == "true"
    val blockTrackers = blockTrackersStr != "false"

    fun syncActiveCategories(
        adult: Boolean = policy?.blockAllAdult ?: true,
        gambling: Boolean = blockGambling,
        malware: Boolean = blockMalware,
        social: Boolean = blockSocial,
        shortVideo: Boolean = blockShortVideo,
        trackers: Boolean = blockTrackers
    ) {
        val active = mutableSetOf<Category>()
        if (adult) {
            active.addAll(
                listOf(
                    Category.PORNOGRAPHY, Category.NUDITY, Category.SEXUAL_SERVICES,
                    Category.ADULT_DATING, Category.CAM, Category.EXPLICIT_STREAMING,
                    Category.ADULT_SOCIAL, Category.ADULT_FORUM, Category.ADULT_SEARCH,
                    Category.NSFW_MEDIA, Category.OTHER_EXPLICIT
                )
            )
        }
        if (gambling) active.add(Category.GAMBLING)
        if (malware) {
            active.addAll(listOf(Category.MALWARE, Category.PHISHING, Category.SCAM, Category.CRYPTOMINING, Category.SUSPICIOUS_DOMAINS))
        }
        if (social) active.add(Category.SOCIAL_MEDIA)
        if (shortVideo) active.add(Category.SHORT_VIDEO)
        if (trackers) {
            active.addAll(listOf(Category.TRACKING, Category.ADVERTISING))
        }
        securityManager.domainMatcher.setActiveCategories(active)
    }

    val vpnConsent = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            scope.launch { securityManager.protectionController.setProfile(ProtectionProfile.MAXIMUM) }
        }
    }

    fun setProtection(on: Boolean) {
        scope.launch {
            if (on) {
                val intent = VpnService.prepare(context)
                if (intent != null) {
                    vpnConsent.launch(intent)
                } else {
                    securityManager.protectionController.setProfile(ProtectionProfile.MAXIMUM)
                }
            } else {
                val result = securityManager.protectionController.setProfile(ProtectionProfile.OFF)
                message = result.exceptionOrNull()?.message
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Canvas),
        contentPadding = ContentBottomPadding
    ) {

        item { ScreenHeader(title = "Protection") }

        item {
            Column(Modifier.padding(horizontal = Space.gutter)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isProtected) Success else TextTertiary)
                    )
                    Spacer(Modifier.width(Space.sm))
                    Text(
                        text = if (isProtected) "Protected" else "Not protected",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                }

                Spacer(Modifier.height(Space.sm))

                Text(
                    text = if (isProtected) {
                        "Wire-level DNS interception and threat filtering active across all browsers and apps."
                    } else {
                        "Turn on protection to shield device from explicit content, malware, and trackers."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )

                Spacer(Modifier.height(Space.lg))

                if (isProtected) {
                    SecondaryButton(
                        text = "Turn off protection",
                        onClick = { setProtection(false) }
                    )
                } else {
                    PrimaryButton(
                        text = "Turn on protection",
                        onClick = { setProtection(true) }
                    )
                }

                message?.let {
                    Spacer(Modifier.height(Space.md))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = Warning,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(Space.sm))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Warning
                        )
                    }
                }

                if (isLockedDown) {
                    Spacer(Modifier.height(Space.md))
                    StatusChip(text = "Locked by Admin", tone = StatusTone.Caution)
                }
            }
        }

        // Activity ------------------------------------------------------------

        item { SectionHeader("Activity & History") }
        item {
            Grouped {
                SettingRow(
                    title = "Blocked today",
                    trailing = { RowValue(blockedToday.toString(), TextPrimary) }
                )
                RowDivider()
                SettingRow(
                    title = "Blocked activity log",
                    description = "Inspect blocked queries, timestamps, and reason codes",
                    onClick = onNavigateToLogs,
                    trailing = {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = TextTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
        }

        // Filtering Categories ------------------------------------------------

        item { SectionHeader("Protection Categories") }
        item {
            Grouped {
                // 1. Adult Content
                SwitchRow(
                    title = "Adult & Explicit Content",
                    description = "Filter 11 adult categories, adult TLDs, and cam portals at the wire level",
                    checked = policy?.blockAllAdult == true,
                    enabled = !isLockedDown,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            policy?.let {
                                securityManager.policyRepository.updatePolicy(it.copy(blockAllAdult = enabled))
                            }
                            syncActiveCategories(adult = enabled)
                        }
                    }
                )
                RowDivider()

                // 2. SafeSearch
                SwitchRow(
                    title = "Strict SafeSearch VIPs",
                    description = "Force SafeSearch on Google, Bing, YouTube and DuckDuckGo",
                    checked = policy?.safeSearchEnabled == true,
                    enabled = !isLockedDown,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            policy?.let {
                                securityManager.policyRepository.updatePolicy(it.copy(safeSearchEnabled = enabled))
                            }
                        }
                    }
                )
                RowDivider()

                // 3. Gambling & Betting
                SwitchRow(
                    title = "Gambling & Sports Betting",
                    description = "Block online casinos, poker, lottery, and betting portals",
                    checked = blockGambling,
                    enabled = !isLockedDown,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            securityManager.configRepository.set("cat_gambling", enabled.toString())
                            syncActiveCategories(gambling = enabled)
                        }
                    }
                )
                RowDivider()

                // 4. Malware & Phishing
                SwitchRow(
                    title = "Malware & Phishing Shields",
                    description = "Block ransomware, deceptive credential harvesters, and cryptominers",
                    checked = blockMalware,
                    enabled = !isLockedDown,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            securityManager.configRepository.set("cat_malware", enabled.toString())
                            syncActiveCategories(malware = enabled)
                        }
                    }
                )
                RowDivider()

                // 5. Social Media
                SwitchRow(
                    title = "Social Media Platforms",
                    description = "Block Instagram, Facebook, Twitter/X, and Reddit domains",
                    checked = blockSocial,
                    enabled = !isLockedDown,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            securityManager.configRepository.set("cat_social", enabled.toString())
                            syncActiveCategories(social = enabled)
                        }
                    }
                )
                RowDivider()

                // 6. Short-Form Video
                SwitchRow(
                    title = "Short-form Video (Doomscroll)",
                    description = "Block TikTok, Reels, and YouTube Shorts endpoints",
                    checked = blockShortVideo,
                    enabled = !isLockedDown,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            securityManager.configRepository.set("cat_short_video", enabled.toString())
                            syncActiveCategories(shortVideo = enabled)
                        }
                    }
                )
                RowDivider()

                // 7. Trackers & Telemetry
                SwitchRow(
                    title = "Telemetry & Trackers",
                    description = "Block aggressive ad networks and tracking beacons",
                    checked = blockTrackers,
                    enabled = !isLockedDown,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            securityManager.configRepository.set("cat_trackers", enabled.toString())
                            syncActiveCategories(trackers = enabled)
                        }
                    }
                )
                RowDivider()

                // 8. Custom Domains Link
                SettingRow(
                    title = "Custom Domain Rules",
                    description = "Add personal custom blocklists and allowlist overrides",
                    onClick = onNavigateToPolicies,
                    trailing = {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = TextTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
        }

        // Advanced ------------------------------------------------------------

        item { SectionHeader("Advanced & Lockdown") }
        item {
            Grouped {
                SettingRow(
                    title = "Device Owner Protection",
                    description = "Configure permanent Device Owner mode for uninstall immunity",
                    onClick = onNavigateToSetup,
                    trailing = {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = TextTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
        }

        item { Spacer(Modifier.height(Space.section)) }
    }
}
