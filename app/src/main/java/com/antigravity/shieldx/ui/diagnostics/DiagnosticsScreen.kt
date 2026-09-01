package com.antigravity.shieldx.ui.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.antigravity.shieldx.core.model.TamperState
import com.antigravity.shieldx.core.security.SecurityManager
import com.antigravity.shieldx.ui.components.*
import com.antigravity.shieldx.ui.theme.*
import com.antigravity.shieldx.vpn.ProtectionVpnService
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Facts about the current install, for when something is not behaving. Values
 * on the right, no gauges or meters - this screen is read, not admired.
 */
@Composable
fun DiagnosticsScreen(
    securityManager: SecurityManager,
    onBack: () -> Unit = {}
) {
    val tamperState by securityManager.stateMachine.currentStateFlow.collectAsState()
    val tamperEvents by securityManager.auditRepository.recentTamperEventsFlow
        .collectAsState(initial = emptyList())
    val isVpnRunning by ProtectionVpnService.isRunningFlow.collectAsState()
    val formatter = remember { SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()) }

    val isDeviceOwner = remember { securityManager.deviceOwnerController.isDeviceOwner() }
    val isProfileOwner = remember { securityManager.deviceOwnerController.isProfileOwner() }
    val isDeviceAdmin = remember { securityManager.deviceOwnerController.isDeviceAdminActive() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Canvas),
        contentPadding = ContentBottomPadding
    ) {
        item { ScreenHeader(title = "Diagnostics", onBack = onBack) }

        item { SectionHeader("Status") }
        item {
            Grouped {
                SettingRow(
                    title = "Filtering service",
                    trailing = {
                        StatusChip(
                            text = if (isVpnRunning) "Running" else "Stopped",
                            tone = if (isVpnRunning) StatusTone.Positive else StatusTone.Neutral
                        )
                    }
                )
                RowDivider()
                SettingRow(
                    title = "Integrity",
                    trailing = {
                        StatusChip(
                            text = tamperState.name.lowercase().replaceFirstChar { it.uppercase() },
                            tone = when (tamperState) {
                                TamperState.NORMAL -> StatusTone.Positive
                                TamperState.SUSPICIOUS -> StatusTone.Caution
                                else -> StatusTone.Critical
                            }
                        )
                    }
                )
            }
        }

        item { SectionHeader("Device management") }
        item {
            Grouped {
                SettingRow(
                    title = "Device owner",
                    trailing = { RowValue(if (isDeviceOwner) "Yes" else "No") }
                )
                RowDivider()
                SettingRow(
                    title = "Profile owner",
                    trailing = { RowValue(if (isProfileOwner) "Yes" else "No") }
                )
                RowDivider()
                SettingRow(
                    title = "Device admin",
                    trailing = { RowValue(if (isDeviceAdmin) "Active" else "Inactive") }
                )
            }
        }

        item { SectionHeader("Recent events") }

        if (tamperEvents.isEmpty()) {
            item {
                StateMessage(
                    title = "No events",
                    description = "Integrity events would be listed here."
                )
            }
        } else {
            items(tamperEvents, key = { it.id }) { event ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.gutter, vertical = Space.md)
                ) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            text = event.eventType.lowercase().replace('_', ' ')
                                .replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(Space.sm))
                        Text(
                            text = formatter.format(event.timestamp),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary
                        )
                    }
                    if (event.description.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = event.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
                RowDivider(insetStart = Space.gutter)
            }
        }
    }
}
