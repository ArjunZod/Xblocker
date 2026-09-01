package com.antigravity.shieldx.ui.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.antigravity.shieldx.core.security.SecurityManager
import com.antigravity.shieldx.core.util.CompanionApp
import com.antigravity.shieldx.ui.components.*
import com.antigravity.shieldx.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Settings, laid out the way Android settings are: grouped rows, a plain
 * statement of each permission's purpose, and the current state on the right.
 *
 * Permission state is re-read on resume because the user grants these in system
 * Settings, so we only find out when they come back to the app.
 */
@Composable
fun TarziControlScreen(
    securityManager: SecurityManager,
    onNavigateToAutomations: () -> Unit = {},
    onNavigateToDiagnostics: () -> Unit = {},
    onNavigateToApps: () -> Unit = {}
) {
    val context = LocalContext.current
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

    val hasMic = remember(refresh) { context.hasPermission(Manifest.permission.RECORD_AUDIO) }
    val hasNotifications = remember(refresh) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        } else true
    }
    val hasOverlay = remember(refresh) { Settings.canDrawOverlays(context) }
    val ignoresBattery = remember(refresh) { context.ignoresBatteryOptimization() }
    val hasContacts = remember(refresh) { context.hasPermission(Manifest.permission.READ_CONTACTS) }
    val hasPhone = remember(refresh) { context.hasPermission(Manifest.permission.CALL_PHONE) }

    val assistantInstalled = remember(refresh) { CompanionApp.Assistant.isInstalled(context) }

    var hasAdminPin by remember(refresh) {
        mutableStateOf(securityManager.adminRecoveryController.isPinSet())
    }
    var isLocked by remember(refresh) {
        mutableStateOf(securityManager.lockdownController.isActive())
    }

    var deepSeekKey by remember { mutableStateOf("") }
    var geminiKey by remember { mutableStateOf("") }
    var picovoiceKey by remember { mutableStateOf("") }
    var showKeyDialog by remember { mutableStateOf<KeyKind?>(null) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showLockConfirm by remember { mutableStateOf(false) }
    var generatedSecret by remember { mutableStateOf<String?>(null) }
    var showUnlock by remember { mutableStateOf(false) }

    // The assistant degrades to offline commands when no model is reachable.
    // That state has to be visible here, otherwise a rejected key just looks
    // like the assistant being stupid.
    var aiStatus by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        deepSeekKey = securityManager.configRepository.get("deepseek_api_key").orEmpty()
        geminiKey = securityManager.configRepository.get("gemini_api_key").orEmpty()
        picovoiceKey = securityManager.configRepository.get("picovoice_access_key").orEmpty()
    }

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

        // Assistant ----------------------------------------------------------

        item { SectionHeader("Assistant") }
        item {
            Grouped {
                SettingRow(
                    title = "TaRZI Assistant",
                    description = if (assistantInstalled) {
                        "Voice, screen control and automations live in the assistant app"
                    } else {
                        "Not installed on this device"
                    },
                    onClick = { CompanionApp.Assistant.launch(context) }
                )
            }
        }

        // Permissions --------------------------------------------------------

        item { SectionHeader("Permissions") }
        item {
            Grouped {
                PermissionRow(
                    title = "Microphone",
                    description = "Required for voice commands",
                    granted = hasMic
                ) {
                    val wanted = mutableListOf(Manifest.permission.RECORD_AUDIO)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        wanted += Manifest.permission.POST_NOTIFICATIONS
                    }
                    permissions.launch(wanted.toTypedArray())
                }
                RowDivider()
                PermissionRow(
                    title = "Display over apps",
                    description = "Shows the assistant button anywhere",
                    granted = hasOverlay
                ) {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + context.packageName)
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                RowDivider()
                PermissionRow(
                    title = "Notifications",
                    description = "Keeps the assistant running in the background",
                    granted = hasNotifications
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissions.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                    }
                }
                RowDivider()
                PermissionRow(
                    title = "Phone and contacts",
                    description = "Needed to look up a name and place a call",
                    granted = hasContacts && hasPhone
                ) {
                    permissions.launch(
                        arrayOf(
                            Manifest.permission.READ_CONTACTS,
                            Manifest.permission.CALL_PHONE
                        )
                    )
                }
                RowDivider()
                PermissionRow(
                    title = "Unrestricted battery",
                    description = "Optional. Stops Android pausing the assistant",
                    granted = ignoresBattery
                ) { context.requestIgnoreBatteryOptimization() }
            }
        }

        // Security -----------------------------------------------------------

        item { SectionHeader("Security") }
        item {
            Grouped {
                SettingRow(
                    title = "Admin PIN",
                    description = if (hasAdminPin) {
                        "Your 4–8 digit PIN for daily protected settings (apps, rules, logs)."
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
                        "Active. Protection cannot be turned off with your PIN—only with the emergency recovery phrase."
                    } else {
                        "Strict anti-relapse lock. Generates an emergency recovery key so you cannot easily turn off protection."
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
            }
        }

        // Intelligence -------------------------------------------------------

        item { SectionHeader("Intelligence") }
        item {
            Grouped {
                SettingRow(
                    title = "DeepSeek API key",
                    description = when {
                        deepSeekKey.isBlank() -> "The model Tarzi thinks and talks with"
                        aiStatus == null -> "Checking..."
                        else -> aiStatus!!
                    },
                    onClick = { showKeyDialog = KeyKind.DeepSeek },
                    trailing = {
                        when {
                            deepSeekKey.isBlank() -> RowValue("Not set")
                            aiStatus == null -> RowValue("...")
                            aiStatus!!.startsWith("Connected") ->
                                StatusChip("Working", StatusTone.Positive)
                            else -> StatusChip("Failed", StatusTone.Critical)
                        }
                    }
                )
                RowDivider()
                SettingRow(
                    title = "Gemini API key",
                    description = "Optional. Adds image understanding for screen sharing.",
                    onClick = { showKeyDialog = KeyKind.Gemini },
                    trailing = {
                        if (geminiKey.isNotBlank()) RowValue("Added", Success) else RowValue("Not set")
                    }
                )
                RowDivider()
                SettingRow(
                    title = "Picovoice key",
                    description = "Optional. Improves wake word accuracy and battery use",
                    onClick = { showKeyDialog = KeyKind.Picovoice },
                    trailing = {
                        if (picovoiceKey.isNotBlank()) {
                            RowValue("Added", Success)
                        } else {
                            RowValue("Not set")
                        }
                    }
                )
            }
        }

        // More ---------------------------------------------------------------

        item { SectionHeader("More") }
        item {
            Grouped {
                NavRow("Routines", onNavigateToAutomations)
                RowDivider()
                NavRow("App control", onNavigateToApps)
                RowDivider()
                NavRow("Diagnostics", onNavigateToDiagnostics)
            }
        }

        item { Spacer(Modifier.height(Space.section)) }
    }

    // Dialogs ----------------------------------------------------------------

    showKeyDialog?.let { kind ->
        val current = when (kind) {
            KeyKind.DeepSeek -> deepSeekKey
            KeyKind.Gemini -> geminiKey
            KeyKind.Picovoice -> picovoiceKey
        }
        ApiKeyDialog(
            title = when (kind) {
                KeyKind.DeepSeek -> "DeepSeek API key"
                KeyKind.Gemini -> "Gemini API key"
                KeyKind.Picovoice -> "Picovoice key"
            },
            hint = when (kind) {
                KeyKind.DeepSeek ->
                    "This is what lets Tarzi hold a conversation and decide which " +
                        "actions to take. Get one at platform.deepseek.com."
                KeyKind.Gemini ->
                    "Optional. Adds image understanding so Tarzi can answer questions " +
                        "about what is on your screen."
                KeyKind.Picovoice ->
                    "Optional. Enables the on-device wake word from console.picovoice.ai."
            },
            initial = current,
            onDismiss = { showKeyDialog = null },
            onSave = { value ->
                val trimmed = value.trim()
                scope.launch {
                    when (kind) {
                        KeyKind.DeepSeek -> {
                            securityManager.configRepository.set("deepseek_api_key", trimmed)
                            deepSeekKey = trimmed
                        }
                        KeyKind.Gemini -> {
                            securityManager.configRepository.set("gemini_api_key", trimmed)
                            geminiKey = trimmed
                        }
                        KeyKind.Picovoice -> {
                            securityManager.configRepository.set("picovoice_access_key", trimmed)
                            picovoiceKey = trimmed
                        }
                    }
                }
                showKeyDialog = null
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
                    "A recovery phrase will be generated and shown once. Tarzi keeps only a " +
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

private enum class KeyKind { DeepSeek, Gemini, Picovoice }

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
private fun ApiKeyDialog(
    title: String,
    hint: String,
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var value by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = TextPrimary) },
        text = {
            Column {
                Text(hint, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                Spacer(Modifier.height(Space.md))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = Radius.sm,
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(value) }) {
                Text("Save", color = Accent, style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary, style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}

@Composable
private fun SetPinDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    val valid = pin.length in 4..8

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("Set admin PIN", style = MaterialTheme.typography.titleLarge, color = TextPrimary) },
        text = {
            Column {
                Text(
                    "Used to unlock protected settings. 4 to 8 digits.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(Space.md))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(8) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    shape = Radius.sm,
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(pin) }) {
                Text(
                    "Save",
                    color = if (valid) Accent else TextTertiary,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary, style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}

/** Monospace here is functional: the phrase has to be transcribed exactly. */
@Composable
private fun RecoveryPhraseDialog(secret: String, onDone: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var saved by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { },
        containerColor = Surface,
        title = { Text("Recovery phrase", style = MaterialTheme.typography.titleLarge, color = TextPrimary) },
        text = {
            Column {
                Text(
                    "Save this somewhere outside this device. It will not be shown again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(Space.md))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(Radius.sm)
                        .background(SurfaceRaised)
                        .border(1.dp, Border, Radius.sm)
                        .padding(Space.md)
                ) {
                    Text(
                        text = secret,
                        style = MonoText.copy(fontSize = 11.sp, lineHeight = 15.sp),
                        color = TextPrimary,
                        softWrap = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(Space.sm))
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = TextSecondary,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { clipboard.setText(AnnotatedString(secret)) }
                    )
                }
                Spacer(Modifier.height(Space.md))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { saved = !saved }
                ) {
                    Checkbox(
                        checked = saved,
                        onCheckedChange = { saved = it },
                        colors = CheckboxDefaults.colors(checkedColor = Accent)
                    )
                    Text(
                        "I have saved this phrase",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = saved, onClick = onDone) {
                Text(
                    "Done",
                    color = if (saved) Accent else TextTertiary,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    )
}

@Composable
private fun UnlockDialog(onDismiss: () -> Unit, onSubmit: (String) -> Boolean) {
    var value by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("Turn off lockdown", style = MaterialTheme.typography.titleLarge, color = TextPrimary) },
        text = {
            Column {
                Text(
                    "Enter the recovery phrase shown when lockdown was turned on.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(Space.md))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it; isError = false },
                    isError = isError,
                    shape = Radius.sm,
                    colors = fieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 88.dp)
                )
                if (isError) {
                    Spacer(Modifier.height(Space.sm))
                    Text(
                        "That phrase does not match.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Danger
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (!onSubmit(value.trim())) isError = true }) {
                Text("Unlock", color = Accent, style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary, style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Accent,
    unfocusedBorderColor = Border,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    cursorColor = Accent
)

// ============================================================================

private fun Context.hasPermission(permission: String): Boolean =
    androidx.core.content.ContextCompat.checkSelfPermission(this, permission) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED

private fun Context.ignoresBatteryOptimization(): Boolean {
    val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return pm.isIgnoringBatteryOptimizations(packageName)
}

private fun Context.requestIgnoreBatteryOptimization() {
    try {
        startActivity(
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
    }
}
