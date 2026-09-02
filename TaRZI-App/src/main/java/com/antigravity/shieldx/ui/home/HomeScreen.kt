package com.antigravity.shieldx.ui.home

import android.Manifest
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.antigravity.shieldx.core.model.ProtectionProfile
import com.antigravity.shieldx.core.security.SecurityManager
import com.antigravity.shieldx.learning.LearningProgress
import com.antigravity.shieldx.learning.RewardEngine
import com.antigravity.shieldx.ui.blocked.DisableGate
import com.antigravity.shieldx.ui.components.*
import com.antigravity.shieldx.ui.theme.*
import com.antigravity.shieldx.vpn.ProtectionVpnService
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Assistant-Centric Home Screen for TaRZI.
 * The AI Assistant is the primary interaction surface; music, protection, and
 * utilities support it.
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

    // Rewards are earned in the block screen, which runs as its own activity.
    // Without re-reading on resume the home screen keeps showing the level and
    // XP from before the block, so the loop looks like it did nothing.
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
    val learningLine = remember(rewardsRevision) { learning.summary() }
    var showDisableGate by remember { mutableStateOf(false) }

    val vpnConsent = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            scope.launch { securityManager.protectionController.setProfile(ProtectionProfile.MAXIMUM) }
        }
    }

    fun enableProtection() {
        // As device owner, always-on VPN carries its own consent, so the system
        // dialog never appears. Otherwise Android requires the prompt once -
        // there is no supported way to capture traffic without it - and never
        // asks again afterwards.
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
            // Turning it off goes through the gate; turning it on never does.
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
                    title = greeting(),
                    subtitle = null
                )
            }

            // 1. Primary AI Assistant Prompt Bar
            item {
                Column(Modifier.padding(horizontal = Space.gutter)) {
                    Text(
                        text = if (isProtected) "Filtering is on" else "Filtering is off",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        text = if (isProtected) {
                            "Adult content is filtered across your browsers and apps."
                        } else {
                            "Turn filtering on to block adult content device-wide."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(Space.md))
                    Text(
                        text = "LVL $level · $rank · $xpTotal XP" +
                            if (streak > 1) "  ·  $streak day streak" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Accent
                    )
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        text = "$resists shut down · $learningLine",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTertiary
                    )
                    Spacer(Modifier.height(Space.lg))
                    PrimaryButton(
                        text = if (isProtected) "Turn off filtering" else "Turn on filtering",
                        onClick = { toggleProtection() }
                    )
                }
            }

            item {
                Column {
                    SectionHeader(title = "Today's run")
                    Grouped {
                        challenges.forEachIndexed { i, c ->
                            if (i > 0) RowDivider()
                            SettingRow(
                                title = c.label,
                                description = if (c.complete) "Done" else "${c.done} of ${c.target}",
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

            item {
                Column {
                    SectionHeader(title = "Activity")
                    Grouped {
                        SettingRow(
                            title = "Blocked activity",
                            description = "See what has been blocked and why",
                            onClick = onNavigateToLogs
                        )
                    }
                }
            }

            item {
                Column {
                    SectionHeader(title = "Protection")
                    Grouped {
                        SettingRow(
                            title = "Filtering settings",
                            description = "SafeSearch, blocked domains and app rules",
                            onClick = onNavigateToProtection
                        )
                    }
                }
            }
        }

    }

    if (showDisableGate) {
        DisableGate(
            onDismiss = { showDisableGate = false },
            onConfirmed = {
                showDisableGate = false
                scope.launch { securityManager.protectionController.setProfile(ProtectionProfile.OFF) }
            }
        )
    }
}

private fun greeting(): String {
    return when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 4..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Good night"
    }

}
