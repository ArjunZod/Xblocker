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
import com.antigravity.shieldx.core.model.ProtectionProfile
import com.antigravity.shieldx.core.security.SecurityManager
import com.antigravity.shieldx.ui.components.*
import com.antigravity.shieldx.ui.theme.*
import com.antigravity.shieldx.vpn.ProtectionVpnService
import kotlinx.coroutines.launch

/**
 * Protection, presented the way a platform settings screen would present it:
 * current state stated plainly, one primary action, then the individual
 * controls as ordinary rows.
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
                // Reports the real outcome rather than assuming success - this
                // call is refused outright while lockdown is active.
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
                        "Adult content is filtered across your browsers and apps."
                    } else {
                        "Turn on protection to start filtering adult content."
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
                    StatusChip(text = "Locked", tone = StatusTone.Caution)
                }
            }
        }

        // Activity ------------------------------------------------------------

        item { SectionHeader("Activity") }
        item {
            Grouped {
                SettingRow(
                    title = "Blocked today",
                    trailing = { RowValue(blockedToday.toString(), TextPrimary) }
                )
                RowDivider()
                SettingRow(
                    title = "Blocked activity",
                    description = "See what has been blocked and why",
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

        // Filtering -----------------------------------------------------------

        item { SectionHeader("Filtering") }
        item {
            Grouped {
                SwitchRow(
                    title = "SafeSearch",
                    description = "Force safe results on Google, Bing, YouTube and DuckDuckGo",
                    checked = policy?.safeSearchEnabled == true,
                    enabled = !isLockedDown,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            policy?.let {
                                securityManager.policyRepository.updatePolicy(
                                    it.copy(safeSearchEnabled = enabled)
                                )
                            }
                        }
                    }
                )
                RowDivider()
                SwitchRow(
                    title = "Block adult content",
                    description = "Filter known adult domains at the DNS layer",
                    checked = policy?.blockAllAdult == true,
                    enabled = !isLockedDown,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            policy?.let {
                                securityManager.policyRepository.updatePolicy(
                                    it.copy(blockAllAdult = enabled)
                                )
                            }
                        }
                    }
                )
                RowDivider()
                SettingRow(
                    title = "Blocked domains",
                    description = "Manage the domain list and categories",
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

        item { SectionHeader("Advanced") }
        item {
            Grouped {
                SettingRow(
                    title = "Managed device",
                    description = "Set up device owner mode to prevent removal",
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
