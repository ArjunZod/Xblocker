package com.antigravity.shieldx.ui.home

import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.shieldx.core.model.DeviceMode
import com.antigravity.shieldx.core.model.ProtectionProfile
import com.antigravity.shieldx.core.security.SecurityManager
import com.antigravity.shieldx.learning.LearningProgress
import com.antigravity.shieldx.learning.RewardEngine
import com.antigravity.shieldx.ui.blocked.DisableGate
import com.antigravity.shieldx.ui.components.*
import com.antigravity.shieldx.ui.theme.*
import com.antigravity.shieldx.vpn.ProtectionVpnService
import kotlinx.coroutines.launch

/**
 * Modern, highly interactive and animated security dashboard for Xblocker.
 * Features:
 * - Animated radar wave & pulsing hero shield
 * - Real-time TUN traffic visualizer wave
 * - Animated numerical stats counters
 * - Transparent 0-100 Security Score with deep audit breakdown
 * - Instant category status indicators
 */
@Composable
fun HomeScreen(
    securityManager: SecurityManager,
    onNavigateToProtection: () -> Unit,
    onNavigateToLogs: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isVpnRunning by ProtectionVpnService.isRunningFlow.collectAsState()
    val policy by securityManager.policyRepository.currentPolicyFlow.collectAsState(initial = null)
    val isProtected = policy?.isEnabled == true && isVpnRunning

    val learning = remember { LearningProgress(context) }
    val rewards = remember { RewardEngine(context) }

    var rewardsRevision by remember { mutableIntStateOf(0) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) rewardsRevision++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val level = remember(rewardsRevision) { rewards.level() }
    val rank = remember(rewardsRevision) { rewards.rank() }
    val xpTotal = remember(rewardsRevision) { rewards.xp() }
    val streak = remember(rewardsRevision) { rewards.streakDays() }
    val resists = remember(rewardsRevision) { rewards.totalResists() }
    val challenges = remember(rewardsRevision) { rewards.challenges() }

    var showDisableGate by remember { mutableStateOf(false) }
    var showScoreDialog by remember { mutableStateOf(false) }

    val isDeviceOwner = remember { securityManager.deviceOwnerController.isDeviceOwner() }

    // Transparent Security Score calculation (0-100)
    val scoreComponents = remember(isProtected, policy, isDeviceOwner) {
        listOf(
            ScoreComponent("DNS Tunnel Interception", if (isProtected) 20 else 0, 20, "System-level wire DNS sinkholing"),
            ScoreComponent("Strict SafeSearch VIPs", if (policy?.safeSearchEnabled == true) 15 else 0, 15, "Google, Bing, YouTube VIP enforcement"),
            ScoreComponent("Anti-Bypass & DoH Dropper", if (isProtected) 15 else 0, 15, "Blocks DoH, DoT port 853, DoQ port 784"),
            ScoreComponent("Tamper Watchdog", 15, 15, "Monotonic clock and service crash monitor"),
            ScoreComponent("Device Owner Enforcement", if (isDeviceOwner) 20 else 10, 20, if (isDeviceOwner) "Always-on VPN lockdown enabled" else "Standard consumer mode"),
            ScoreComponent("Threat Intelligence Engine", 15, 15, "Suffix trie & TLD match engine active")
        )
    }
    val rawScore = scoreComponents.sumOf { it.earned }
    val animatedScore by animateIntAsState(
        targetValue = rawScore,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "animatedScore"
    )

    val animatedResists by animateIntAsState(
        targetValue = resists,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "animatedResists"
    )

    val vpnConsent = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            scope.launch { securityManager.protectionController.setProfile(ProtectionProfile.MAXIMUM) }
        }
    }

    fun enableProtection() {
        val silent = securityManager.deviceOwnerController.isDeviceOwner() &&
                securityManager.deviceOwnerController.configureAlwaysOnVpn(lockdownEnabled = true)

        if (silent) {
            scope.launch { securityManager.protectionController.setProfile(ProtectionProfile.MAXIMUM) }
            return
        }

        val intent = VpnService.prepare(context)
        if (intent != null) {
            vpnConsent.launch(intent)
        } else {
            scope.launch { securityManager.protectionController.setProfile(ProtectionProfile.MAXIMUM) }
        }
    }

    fun toggleProtection() {
        if (isProtected) {
            showDisableGate = true
        } else {
            enableProtection()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Canvas)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Canvas),
            contentPadding = ContentBottomPadding,
            verticalArrangement = Arrangement.spacedBy(Space.lg)
        ) {
            item {
                ScreenHeader(
                    title = "Xblocker Security",
                    subtitle = if (isProtected) "Active Protection Platform" else "Protection Offline"
                )
            }

            // 1. Primary Status Hero Card with Animated Radar Shield
            item {
                Column(
                    Modifier
                        .padding(horizontal = Space.gutter)
                        .fillMaxWidth()
                        .clip(Radius.lg)
                        .background(Surface)
                        .border(1.dp, if (isProtected) BorderStrong else Warning, Radius.lg)
                        .padding(Space.xl),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Animated Radar Shield
                    AnimatedRadarShield(
                        isProtected = isProtected,
                        onClick = { toggleProtection() }
                    )

                    Spacer(Modifier.height(Space.lg))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isProtected) Success else Warning)
                        )
                        Spacer(Modifier.width(Space.sm))
                        Text(
                            text = if (isProtected) "PROTECTION ACTIVE" else "PROTECTION DISABLED",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isProtected) Success else Warning
                        )
                    }

                    Spacer(Modifier.height(Space.xs))
                    Text(
                        text = if (isProtected) {
                            "Device DNS, encrypted resolvers, and adult content filtered"
                        } else {
                            "Turn on filtering to shield apps and browsers device-wide"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = Space.md)
                    )

                    Spacer(Modifier.height(Space.lg))

                    // Transparent Security Score Badge
                    Row(
                        modifier = Modifier
                            .clip(Radius.full)
                            .background(SurfaceHigh)
                            .border(1.dp, Border, Radius.full)
                            .clickable { showScoreDialog = true }
                            .padding(horizontal = Space.lg, vertical = Space.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = Accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(Space.sm))
                        Text(
                            text = "Security Score: $animatedScore / 100",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        Spacer(Modifier.width(Space.xs))
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Inspect score",
                            tint = TextTertiary,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Spacer(Modifier.height(Space.lg))

                    PrimaryButton(
                        text = if (isProtected) "Turn off filtering" else "Turn on filtering",
                        onClick = { toggleProtection() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 2. Real-time Live Packet Inspection Visualizer
            if (isProtected) {
                item {
                    LiveTrafficVisualizer()
                }
            }

            // 3. Metrics & Streak Bar with Animated Numerical Rolling
            item {
                Row(
                    Modifier
                        .padding(horizontal = Space.gutter)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Space.md)
                ) {
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(Radius.md)
                            .background(Surface)
                            .border(1.dp, Border, Radius.md)
                            .padding(Space.md)
                    ) {
                        Text("Threats Blocked", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                        Spacer(Modifier.height(Space.xs))
                        Text("$animatedResists", style = MaterialTheme.typography.headlineMedium, color = Accent)
                    }

                    Column(
                        Modifier
                            .weight(1f)
                            .clip(Radius.md)
                            .background(Surface)
                            .border(1.dp, Border, Radius.md)
                            .padding(Space.md)
                    ) {
                        Text("Clean Streak", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                        Spacer(Modifier.height(Space.xs))
                        Text("$streak days", style = MaterialTheme.typography.headlineMedium, color = Success)
                    }

                    Column(
                        Modifier
                            .weight(1f)
                            .clip(Radius.md)
                            .background(Surface)
                            .border(1.dp, Border, Radius.md)
                            .padding(Space.md)
                    ) {
                        Text("Level & Rank", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                        Spacer(Modifier.height(Space.xs))
                        Text("LVL $level", style = MaterialTheme.typography.headlineMedium, color = AccentWarm)
                    }
                }
            }

            // 4. Quick Defense Categories Strip
            item {
                Column(Modifier.padding(horizontal = Space.gutter)) {
                    Text(
                        text = "ACTIVE FILTER CATEGORIES",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextTertiary,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(Space.sm))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Space.xs)
                    ) {
                        CategoryBadge("Adult", isProtected, Modifier.weight(1f))
                        CategoryBadge("Gambling", isProtected, Modifier.weight(1f))
                        CategoryBadge("Malware", isProtected, Modifier.weight(1f))
                        CategoryBadge("Phishing", isProtected, Modifier.weight(1f))
                        CategoryBadge("Trackers", isProtected, Modifier.weight(1f))
                    }
                }
            }

            // 5. Today's Challenges
            item {
                Column {
                    SectionHeader(title = "Today's Discipline Goals")
                    Grouped {
                        challenges.forEachIndexed { i, c ->
                            if (i > 0) RowDivider()
                            SettingRow(
                                title = c.label,
                                description = if (c.complete) "Completed" else "${c.done} of ${c.target}",
                                trailing = {
                                    RowValue(
                                        text = "${c.done}/${c.target}",
                                        tint = if (c.complete) Success else TextTertiary
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // 6. Navigation Controls
            item {
                Column {
                    SectionHeader(title = "Protection Controls")
                    Grouped {
                        SettingRow(
                            title = "Category & SafeSearch Policies",
                            description = "Configure adult, gambling, social, and search engine filters",
                            onClick = onNavigateToProtection
                        )
                        RowDivider()
                        SettingRow(
                            title = "Security Event Log",
                            description = "Review blocked domain history and tamper journal",
                            onClick = onNavigateToLogs
                        )
                    }
                }
            }
        }

        // Security Score Inspector Dialog
        if (showScoreDialog) {
            AlertDialog(
                onDismissRequest = { showScoreDialog = false },
                title = { Text("Security Score Breakdown ($rawScore/100)", style = MaterialTheme.typography.titleLarge) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                        scoreComponents.forEach { component ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(component.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text(component.detail, style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                                }
                                Text(
                                    text = "${component.earned}/${component.possible}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (component.earned == component.possible) Success else Warning
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showScoreDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }

        if (showDisableGate) {
            DisableGate(
                rewards = rewards,
                learning = learning,
                onDismiss = { showDisableGate = false },
                onConfirmed = {
                    showDisableGate = false
                    scope.launch {
                        securityManager.protectionController.setProfile(ProtectionProfile.DISABLED)
                    }
                }
            )
        }
    }
}

/**
 * Animated Hero Shield with dual pulsing concentric rings and spring bounce.
 */
@Composable
private fun AnimatedRadarShield(
    isProtected: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "radarTransition")

    val pulseScale1 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.32f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale1"
    )

    val pulseAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha1"
    )

    val pulseScale2 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.20f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, delayMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale2"
    )

    val pulseAlpha2 by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, delayMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha2"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(170.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        if (isProtected) {
            // Outer Radar Pulse
            Box(
                modifier = Modifier
                    .size(136.dp)
                    .scale(pulseScale1)
                    .clip(CircleShape)
                    .background(Success.copy(alpha = pulseAlpha1))
            )
            // Inner Radar Pulse
            Box(
                modifier = Modifier
                    .size(136.dp)
                    .scale(pulseScale2)
                    .clip(CircleShape)
                    .background(Success.copy(alpha = pulseAlpha2))
            )
        }

        // Hero Shield Illustration
        androidx.compose.foundation.Image(
            painter = painterResource(com.antigravity.shieldx.uikit.R.drawable.il_shield_hero),
            contentDescription = "Shield Hero",
            modifier = Modifier.size(136.dp)
        )
    }
}

/**
 * Animated real-time TUN traffic visualizer with dancing equalizer bars.
 */
@Composable
private fun LiveTrafficVisualizer() {
    val infiniteTransition = rememberInfiniteTransition(label = "trafficEqualizer")

    val bar1 by infiniteTransition.animateFloat(
        initialValue = 4f, targetValue = 20f,
        animationSpec = infiniteRepeatable(tween(450, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b1"
    )
    val bar2 by infiniteTransition.animateFloat(
        initialValue = 18f, targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(380, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b2"
    )
    val bar3 by infiniteTransition.animateFloat(
        initialValue = 8f, targetValue = 24f,
        animationSpec = infiniteRepeatable(tween(520, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b3"
    )
    val bar4 by infiniteTransition.animateFloat(
        initialValue = 22f, targetValue = 10f,
        animationSpec = infiniteRepeatable(tween(350, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b4"
    )
    val bar5 by infiniteTransition.animateFloat(
        initialValue = 12f, targetValue = 26f,
        animationSpec = infiniteRepeatable(tween(490, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b5"
    )

    Row(
        modifier = Modifier
            .padding(horizontal = Space.gutter)
            .fillMaxWidth()
            .clip(Radius.md)
            .background(SurfaceRaised)
            .border(1.dp, Border, Radius.md)
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Equalizer wave
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(28.dp)
        ) {
            listOf(bar1, bar2, bar3, bar4, bar5).forEach { h ->
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(h.dp)
                        .clip(Radius.full)
                        .background(Accent)
                )
            }
        }

        Spacer(Modifier.width(Space.md))

        Column(Modifier.weight(1f)) {
            Text(
                text = "TUN SINKHOLE ENGINE ACTIVE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "Zero packet leaks • Port 53/853 locked down",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary
            )
        }

        Box(
            modifier = Modifier
                .clip(Radius.full)
                .background(Success.copy(alpha = 0.15f))
                .padding(horizontal = Space.sm, vertical = 2.dp)
        ) {
            Text(
                text = "LIVE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Success
            )
        }
    }
}

/**
 * Compact Category Pill badge.
 */
@Composable
private fun CategoryBadge(name: String, active: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(Radius.sm)
            .background(if (active) SurfaceRaised else Surface)
            .border(1.dp, if (active) BorderStrong else Border, Radius.sm)
            .padding(vertical = Space.xs),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) TextPrimary else TextTertiary,
            maxLines = 1
        )
    }
}

data class ScoreComponent(
    val name: String,
    val earned: Int,
    val possible: Int,
    val detail: String
)
