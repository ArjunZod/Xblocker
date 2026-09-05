package com.antigravity.shieldx.ui.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.antigravity.shieldx.backup.BackupRestoreManager
import com.antigravity.shieldx.core.security.SecurityManager
import com.antigravity.shieldx.learning.RewardEngine
import com.antigravity.shieldx.tamper.PrivateDnsDetector
import com.antigravity.shieldx.ui.components.*
import com.antigravity.shieldx.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Settings and Configuration screen for Xblocker.
 * Grouped sections for Appearance, Permissions, Security, Backup & Migration, and Diagnostics.
 */
@Composable
fun TarziControlScreen(
    securityManager: SecurityManager,
    onNavigateToAutomations: () -> Unit = {},
    onNavigateToDiagnostics: () -> Unit = {},
    onNavigateToApps: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var refresh by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val hasNotifications = remember(refresh) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        } else true
    }
    val ignoresBattery = remember(refresh) { context.ignoresBatteryOptimization() }

    var hasAdminPin by remember(refresh) {
        mutableStateOf(securityManager.adminRecoveryController.isPinSet())
    }
    var isLocked by remember(refresh) {
        mutableStateOf(securityManager.lockdownController.isActive())
    }

    val privateDnsDetector = remember { PrivateDnsDetector(context) }
    val privateDnsStatus = remember(refresh) { privateDnsDetector.checkPrivateDns() }

    val backupManager = remember {
        BackupRestoreManager(context, securityManager.database, RewardEngine(context))
    }

    var showPinDialog by remember { mutableStateOf(false) }
    var showLockConfirm by remember { mutableStateOf(false) }
    var generatedSecret by remember { mutableStateOf<String?>(null) }
    var showUnlock by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf<String?>(null) }
    var showRestoreDialog by remember { mutableStateOf(false) }

    val permissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refresh++ }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Canvas),
        contentPadding = ContentBottomPadding
    ) {

        item { ScreenHeader(title = "Settings") }

        // Appearance ---------------------------------------------------------

        item { SectionHeader("Appearance") }
        item {
            val themeCtl = com.antigravity.shieldx.ui.theme.LocalThemeController.current
            Grouped {
                com.antigravity.shieldx.ui.theme.ThemeMode.values().forEachIndexed { i, m ->
                    if (i > 0) RowDivider()
                    SettingRow(
                        title = when (m) {
                            com.antigravity.shieldx.ui.theme.ThemeMode.System -> "Follow system"
                            com.antigravity.shieldx.ui.theme.ThemeMode.Light -> "Light"
                            com.antigravity.shieldx.ui.theme.ThemeMode.Dark -> "Dark"
                        },
                        onClick = { themeCtl.setMode(m) },
                        trailing = {
                            if (themeCtl.mode == m) RowValue("On", Accent) else RowValue("")
                        }
                    )
                }
            }
        }

        // Permissions --------------------------------------------------------

        item { SectionHeader("Permissions") }
        item {
            Grouped {
                PermissionRow(
                    title = "Notifications",
                    description = "Lets the filter show that it is running",
                    granted = hasNotifications
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissions.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                    }
                }
                RowDivider()
                PermissionRow(
                    title = "Unrestricted battery",
                    description = "Optional. Stops Android pausing the filter",
                    granted = ignoresBattery
                ) { context.requestIgnoreBatteryOptimization() }
            }
        }

        // Security & Anti-Tamper ---------------------------------------------

        item { SectionHeader("Security & Anti-Tamper") }
        item {
            Grouped {
                SettingRow(
                    title = "Admin PIN",
                    description = if (hasAdminPin) {
                        "Your 4-8 digit PIN for protected settings (apps, rules, logs)."
                    } else {
                        "Not set. Set a PIN to protect policy changes and logs."
                    },
                    onClick = { showPinDialog = true },
                    trailing = {
                        if (hasAdminPin) {
                            RowValue("Set", TextSecondary)
                        } else {
                            StatusChip("Not set", StatusTone.Caution)
                        }
                    }
                )
                RowDivider()
                SettingRow(
                    title = "Lockdown Mode",
                    description = if (isLocked) {
                        "Active. Protection cannot be turned off with your PIN - only with emergency recovery key."
                    } else {
                        "Strict anti-relapse lock. Generates an emergency recovery key."
                    },
                    onClick = { if (isLocked) showUnlock = true else showLockConfirm = true },
                    trailing = {
                        if (isLocked) {
                            StatusChip("Locked", StatusTone.Caution)
                        } else {
                            RowValue("Off")
                        }
                    }
                )
                RowDivider()
                SettingRow(
                    title = "Private DNS (DoT)",
                    description = when (privateDnsStatus.mode) {
                        PrivateDnsDetector.PrivateDnsMode.STRICT -> "Strict Mode (${privateDnsStatus.specifier}) - Encrypted DoT port 853 is dropped to prevent filter bypass"
                        PrivateDnsDetector.PrivateDnsMode.OPPORTUNISTIC -> "Opportunistic / Auto - Handled via local VPN sinkhole"
                        PrivateDnsDetector.PrivateDnsMode.OFF -> "Off - Standard wire DNS active"
                        else -> "System Default"
                    },
                    trailing = {
                        if (privateDnsStatus.potentialBypassRisk) {
                            StatusChip("DoT Dropped", StatusTone.Neutral)
                        } else {
                            StatusChip("Secure", StatusTone.Positive)
                        }
                    }
                )
            }
        }

        // Backup & Migration -------------------------------------------------

        item { SectionHeader("Backup & Transfer") }
        item {
            Grouped {
                SettingRow(
                    title = "Export Backup",
                    description = "Export custom rules, policies, and streaks into an encrypted, versioned JSON backup",
                    onClick = {
                        scope.launch {
                            val json = backupManager.exportBackupJson()
                            showExportDialog = json
                        }
                    },
                    trailing = {
                        RowValue("Export", Accent)
                    }
                )
                RowDivider()
                SettingRow(
                    title = "Restore from Backup",
                    description = "Import and validate a previously exported Xblocker configuration",
                    onClick = { showRestoreDialog = true },
                    trailing = {
                        RowValue("Restore", Accent)
                    }
                )
            }
        }

        // Subsystems ---------------------------------------------------------

        item { SectionHeader("Subsystems & Diagnostics") }
        item {
            Grouped {
                NavRow("Applications & App Policies", onNavigateToApps)
                RowDivider()
                NavRow("System Self-Test Diagnostics", onNavigateToDiagnostics)
            }
        }

        item { Spacer(Modifier.height(Space.section)) }
    }

    // Dialogs ----------------------------------------------------------------

    showExportDialog?.let { json ->
        AlertDialog(
            onDismissRequest = { showExportDialog = null },
            containerColor = Surface,
            title = { Text("Backup Configuration", style = MaterialTheme.typography.titleLarge, color = TextPrimary) },
            text = {
                Column {
                    Text(
                        "Includes custom domains, app rules, policy settings, and streak. Verified with SHA-256 integrity check.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(Space.sm))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .clip(Radius.sm)
                            .background(SurfaceRaised)
                            .padding(Space.sm)
                    ) {
                        Text(
                            text = json,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = TextPrimary
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboardManager.setText(AnnotatedString(json))
                    Toast.makeText(context, "Backup copied to clipboard", Toast.LENGTH_SHORT).show()
                    showExportDialog = null
                }) {
                    Text("Copy to Clipboard", color = Accent, style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = null }) {
                    Text("Close", color = TextSecondary, style = MaterialTheme.typography.labelLarge)
                }
            }
        )
    }

    if (showRestoreDialog) {
        var restoreText by remember { mutableStateOf("") }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            containerColor = Surface,
            title = { Text("Restore Configuration", style = MaterialTheme.typography.titleLarge, color = TextPrimary) },
            text = {
                Column {
                    Text(
                        "Paste your exported Xblocker JSON backup below. Integrity will be validated before applying.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(Space.sm))
                    OutlinedTextField(
                        value = restoreText,
                        onValueChange = {
                            restoreText = it
                            errorMessage = null
                        },
                        placeholder = { Text("Paste JSON here...", color = TextTertiary) },
                        shape = Radius.sm,
                        colors = fieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    )
                    errorMessage?.let { err ->
                        Spacer(Modifier.height(Space.xs))
                        Text(err, style = MaterialTheme.typography.bodySmall, color = Danger)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (restoreText.isBlank()) {
                        errorMessage = "Please enter backup JSON"
                        return@TextButton
                    }
                    val validated = backupManager.validateBackup(restoreText)
                    if (validated.isFailure) {
                        errorMessage = "Invalid or corrupted backup payload: ${validated.exceptionOrNull()?.message}"
                        return@TextButton
                    }
                    scope.launch {
                        val result = backupManager.restoreBackup(validated.getOrThrow())
                        if (result.isSuccess) {
                            Toast.makeText(context, "Configuration restored successfully!", Toast.LENGTH_LONG).show()
                            showRestoreDialog = false
                            refresh++
                        } else {
                            errorMessage = "Restore failed: ${result.exceptionOrNull()?.message}"
                        }
                    }
                }) {
                    Text("Validate & Restore", color = Accent, style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) {
                    Text("Cancel", color = TextSecondary, style = MaterialTheme.typography.labelLarge)
                }
            }
        )
    }

    if (showPinDialog) {
        SetPinDialog(
            onDismiss = { showPinDialog = false },
            onSave = { pin ->
                val ok = securityManager.adminRecoveryController.setPin(pin)
                if (ok) hasAdminPin = true
                showPinDialog = false
            }
        )
    }

    if (showLockConfirm) {
        AlertDialog(
            onDismissRequest = { showLockConfirm = false },
            containerColor = Surface,
            title = { Text("Turn on lockdown?", style = MaterialTheme.typography.titleLarge, color = TextPrimary) },
            text = {
                Text(
                    "A recovery phrase will be generated and shown once. Xblocker keeps only a " +
                        "one-way hash of it. Without that phrase, protection cannot be turned off " +
                        "on this install.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showLockConfirm = false
                    generatedSecret = securityManager.lockdownController.activate()
                    isLocked = true
                }) { Text("Turn on", color = Danger, style = MaterialTheme.typography.labelLarge) }
            },
            dismissButton = {
                TextButton(onClick = { showLockConfirm = false }) {
                    Text("Cancel", color = TextSecondary, style = MaterialTheme.typography.labelLarge)
                }
            }
        )
    }

    generatedSecret?.let { secret ->
        RecoveryPhraseDialog(secret = secret, onDone = { generatedSecret = null })
    }

    if (showUnlock) {
        UnlockDialog(
            onDismiss = { showUnlock = false },
            onSubmit = { candidate ->
                val ok = securityManager.lockdownController.deactivate(candidate)
                if (ok) {
                    isLocked = false
                    showUnlock = false
                }
                ok
            }
        )
    }
}

// ============================================================================

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    granted: Boolean,
    onGrant: () -> Unit
) {
    SettingRow(
        title = title,
        description = description,
        onClick = if (granted) null else onGrant,
        trailing = {
            if (granted) {
                StatusChip("Granted", StatusTone.Positive)
            } else {
                Text(
                    text = "Grant",
                    style = MaterialTheme.typography.labelLarge,
                    color = Accent
                )
            }
        }
    )
}

@Composable
private fun NavRow(title: String, onClick: () -> Unit) {
    SettingRow(
        title = title,
        onClick = onClick,
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

@Composable
private fun SetPinDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("Set Admin PIN", style = MaterialTheme.typography.titleLarge, color = TextPrimary) },
        text = {
            Column {
                Text(
                    "Enter a 4-8 digit PIN used to unlock protected settings and policies.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(Space.md))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) pin = it },
                    label = { Text("New PIN") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    shape = Radius.sm,
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(Space.sm))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) confirm = it },
                    label = { Text("Confirm PIN") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    shape = Radius.sm,
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Spacer(Modifier.height(Space.xs))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Danger)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (pin.length < 4) {
                    error = "PIN must be at least 4 digits"
                } else if (pin != confirm) {
                    error = "PINs do not match"
                } else {
                    onSave(pin)
                }
            }) { Text("Save", color = Accent, style = MaterialTheme.typography.labelLarge) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary, style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}

@Composable
private fun RecoveryPhraseDialog(secret: String, onDone: () -> Unit) {
    val clipboard = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDone,
        containerColor = Surface,
        title = { Text("Emergency Recovery Phrase", style = MaterialTheme.typography.titleLarge, color = TextPrimary) },
        text = {
            Column {
                Text(
                    "Store this phrase in a safe place. Without it, you cannot disable lockdown.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(Space.md))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(Radius.sm)
                        .background(SurfaceRaised)
                        .padding(Space.md)
                ) {
                    Text(
                        text = secret,
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                        color = Accent
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(secret))
                onDone()
            }) { Text("Copy & Done", color = Accent, style = MaterialTheme.typography.labelLarge) }
        }
    )
}

@Composable
private fun UnlockDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Boolean
) {
    var candidate by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("Disable Lockdown", style = MaterialTheme.typography.titleLarge, color = TextPrimary) },
        text = {
            Column {
                Text(
                    "Enter the emergency recovery phrase generated when lockdown was enabled.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(Space.md))
                OutlinedTextField(
                    value = candidate,
                    onValueChange = { candidate = it },
                    label = { Text("Recovery Phrase") },
                    singleLine = true,
                    shape = Radius.sm,
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Spacer(Modifier.height(Space.xs))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Danger)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val ok = onSubmit(candidate.trim())
                if (!ok) error = "Invalid recovery phrase"
            }) { Text("Unlock", color = Danger, style = MaterialTheme.typography.labelLarge) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary, style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}

private fun Context.hasPermission(p: String): Boolean =
    androidx.core.content.ContextCompat.checkSelfPermission(this, p) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED

private fun Context.ignoresBatteryOptimization(): Boolean {
    val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return pm.isIgnoringBatteryOptimizations(packageName)
}

private fun Context.requestIgnoreBatteryOptimization() {
    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    startActivity(intent)
}

@Composable
private fun fieldColors(): TextFieldColors =
    OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Accent,
        unfocusedBorderColor = Border,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        cursorColor = Accent
    )
