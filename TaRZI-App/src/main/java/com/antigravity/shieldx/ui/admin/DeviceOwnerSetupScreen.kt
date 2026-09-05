package com.antigravity.shieldx.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.antigravity.shieldx.core.security.SecurityManager
import com.antigravity.shieldx.ui.components.*
import com.antigravity.shieldx.ui.theme.*

/**
 * Enterprise Device Owner Provisioning Guide for Xblocker.
 * Explains requirements for permanent Device Owner mode:
 * - Prevents uninstallation by any user
 * - Enables Always-On VPN lockdown without bypass toggles
 * - Must be provisioned via ADB on a factory-reset or account-free device.
 */
@Composable
fun DeviceOwnerSetupScreen(
    securityManager: SecurityManager,
    onBack: () -> Unit = {}
) {
    val clipboard = LocalClipboardManager.current
    var refreshKey by remember { mutableIntStateOf(0) }
    val isDeviceOwner = remember(refreshKey) { securityManager.deviceOwnerController.isDeviceOwner() }
    val command = remember { securityManager.deviceOwnerController.getAdbProvisioningCommand() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Canvas),
        contentPadding = ContentBottomPadding
    ) {
        item { ScreenHeader(title = "Device Owner Setup", onBack = onBack) }

        item {
            Column(Modifier.padding(horizontal = Space.gutter)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusChip(
                        text = if (isDeviceOwner) "Device Owner Active" else "Not Provisioned",
                        tone = if (isDeviceOwner) StatusTone.Positive else StatusTone.Caution
                    )
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh status",
                        tint = TextTertiary,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { refreshKey++ }
                    )
                }

                Spacer(Modifier.height(Space.md))
                Text(
                    text = if (isDeviceOwner) {
                        "Xblocker is active as Device Owner. Uninstall protection and system-level VPN lockdown are enforced."
                    } else {
                        "Device Owner mode gives Xblocker complete tamper immunity: users cannot uninstall the app or toggle off VPN protection. " +
                            "Android security requires this to be provisioned once via ADB on a device with no accounts added yet."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )
            }
        }

        if (!isDeviceOwner) {
            item { SectionHeader("Provisioning Command") }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.gutter)
                        .clip(Radius.sm)
                        .background(SurfaceRaised)
                        .border(1.dp, Border, Radius.sm)
                        .padding(Space.md),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = command,
                        style = MonoText,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(Space.sm))
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy command",
                        tint = TextSecondary,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { clipboard.setText(AnnotatedString(command)) }
                    )
                }
            }

            item { SectionHeader("Step-by-Step Instructions") }
            item {
                Grouped {
                    SettingRow(
                        title = "1. Connect Phone via USB",
                        description = "Plug phone into PC with a high-quality USB data cable"
                    )
                    RowDivider()
                    SettingRow(
                        title = "2. Enable USB Debugging",
                        description = "Go to Settings > Developer Options > Enable USB Debugging"
                    )
                    RowDivider()
                    SettingRow(
                        title = "3. Authorize PC",
                        description = "Tap 'Always allow from this computer' on your phone prompt"
                    )
                    RowDivider()
                    SettingRow(
                        title = "4. Remove Google Accounts",
                        description = "Temporarily remove accounts in Settings > Accounts (can re-add after)"
                    )
                    RowDivider()
                    SettingRow(
                        title = "5. Execute ADB Command",
                        description = "Run the command above in terminal or let the assistant configure it"
                    )
                }
            }
        }

        item { Spacer(Modifier.height(Space.section)) }
    }
}
