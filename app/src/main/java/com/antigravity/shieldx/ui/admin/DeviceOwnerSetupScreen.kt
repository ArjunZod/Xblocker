package com.antigravity.shieldx.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.antigravity.shieldx.core.security.SecurityManager
import com.antigravity.shieldx.ui.components.*
import com.antigravity.shieldx.ui.theme.*

/**
 * Device owner provisioning.
 *
 * The copy here is deliberately honest about the constraint: Android only lets
 * an app become device owner on a device with no configured accounts, via ADB.
 * Promising uninstall protection without saying that would set the user up to
 * discover it the hard way.
 */
@Composable
fun DeviceOwnerSetupScreen(
    securityManager: SecurityManager,
    onBack: () -> Unit = {}
) {
    val clipboard = LocalClipboardManager.current
    val isDeviceOwner = remember { securityManager.deviceOwnerController.isDeviceOwner() }
    val command = remember { securityManager.deviceOwnerController.getAdbProvisioningCommand() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Canvas),
        contentPadding = ContentBottomPadding
    ) {
        item { ScreenHeader(title = "Managed device", onBack = onBack) }

        item {
            Column(Modifier.padding(horizontal = Space.gutter)) {
                StatusChip(
                    text = if (isDeviceOwner) "Enabled" else "Not enabled",
                    tone = if (isDeviceOwner) StatusTone.Positive else StatusTone.Neutral
                )
                Spacer(Modifier.height(Space.md))
                Text(
                    text = if (isDeviceOwner) {
                        "Tarzi is the device owner. Uninstall protection and VPN lockdown are available."
                    } else {
                        "Device owner mode lets Tarzi block its own uninstall and lock VPN settings. " +
                            "Android only allows this to be set up over ADB, on a device with no " +
                            "Google accounts added yet."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )
            }
        }

        if (!isDeviceOwner) {
            item { SectionHeader("Setup command") }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.gutter)
                        .clip(Radius.sm)
                        .background(SurfaceRaised)
                        .border(1.dp, Border, Radius.sm)
                        .padding(Space.md),
                    verticalAlignment = androidx.compose.ui.Alignment.Top
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

            item { SectionHeader("Steps") }
            item {
                Grouped {
                    SettingRow(
                        title = "1. Remove all accounts",
                        description = "Settings, Accounts, remove every Google account"
                    )
                    RowDivider()
                    SettingRow(
                        title = "2. Enable USB debugging",
                        description = "Settings, Developer options, USB debugging"
                    )
                    RowDivider()
                    SettingRow(
                        title = "3. Run the command above",
                        description = "From a computer with the device connected"
                    )
                }
            }
        }

        item { Spacer(Modifier.height(Space.section)) }
    }
}
