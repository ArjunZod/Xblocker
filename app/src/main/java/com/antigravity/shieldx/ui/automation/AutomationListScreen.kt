package com.antigravity.shieldx.ui.automation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.antigravity.shieldx.core.model.ToolRequest
import com.antigravity.shieldx.core.security.SecurityManager
import com.antigravity.shieldx.ui.components.*
import com.antigravity.shieldx.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Routines: a trigger and the actions it runs. Each routine is one row stating
 * both, because "when X, do Y" is the whole mental model and it fits on a line.
 */
@Composable
fun AutomationListScreen(
    securityManager: SecurityManager,
    onBack: () -> Unit = {}
) {
    val routines by securityManager.automationEngine.activeAutomationsFlow
        .collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var showCreate by remember { mutableStateOf(false) }

    if (showCreate) {
        CreateRoutineDialog(
            onDismiss = { showCreate = false },
            onCreate = { name, trigger, payload ->
                showCreate = false
                scope.launch {
                    securityManager.automationEngine.createAutomation(
                        name = name,
                        triggerType = trigger,
                        triggerPayload = payload,
                        actions = listOf(
                            ToolRequest("SET_PROTECTION_PROFILE", mapOf("profile" to "MAXIMUM"))
                        )
                    )
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Canvas),
        contentPadding = ContentBottomPadding
    ) {
        item {
            ScreenHeader(
                title = "Routines",
                subtitle = if (routines.isEmpty()) null else "${routines.size} active",
                onBack = onBack,
                trailing = { SecondaryButton(text = "New", onClick = { showCreate = true }) }
            )
        }

        if (routines.isEmpty()) {
            item {
                StateMessage(
                    title = "No routines yet",
                    description = "Create a routine to run actions automatically, " +
                        "like turning on protection when you join a Wi-Fi network.",
                    icon = Icons.Default.Bolt,
                    action = {
                        PrimaryButton(
                            text = "Create routine",
                            onClick = { showCreate = true },
                            modifier = Modifier.widthIn(max = 240.dp)
                        )
                    }
                )
            }
        } else {
            item {
                Grouped {
                    routines.forEachIndexed { index, routine ->
                        if (index > 0) RowDivider()
                        SettingRow(
                            title = routine.name,
                            description = describeTrigger(routine.triggerType, routine.triggerPayload),
                            trailing = {
                                StatusChip(
                                    text = if (routine.isEnabled) "On" else "Off",
                                    tone = if (routine.isEnabled) StatusTone.Positive else StatusTone.Neutral
                                )
                            }
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(Space.section)) }
    }
}

private fun describeTrigger(type: String, payload: String): String = when (type) {
    "WIFI_CONNECTED" -> "When connected to $payload"
    "TIME_SCHEDULE" -> "Every day at $payload"
    "BLUETOOTH" -> "When $payload connects"
    "BATTERY" -> "When battery hits $payload"
    "BOOT" -> "When the device starts"
    else -> "$type · $payload"
}

@Composable
private fun CreateRoutineDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, trigger: String, payload: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var trigger by remember { mutableStateOf("WIFI_CONNECTED") }
    var payload by remember { mutableStateOf("") }

    val triggers = listOf(
        "WIFI_CONNECTED" to "Wi-Fi network",
        "TIME_SCHEDULE" to "Time of day",
        "BOOT" to "Device start"
    )
    val valid = name.isNotBlank() && (trigger == "BOOT" || payload.isNotBlank())

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("New routine", style = MaterialTheme.typography.titleLarge, color = TextPrimary) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("Name", color = TextTertiary) },
                    singleLine = true,
                    shape = Radius.sm,
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(Space.md))
                Text("Trigger", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                Spacer(Modifier.height(Space.sm))

                triggers.forEach { (value, label) ->
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = trigger == value,
                            onClick = { trigger = value },
                            colors = RadioButtonDefaults.colors(selectedColor = Accent)
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary
                        )
                    }
                }

                if (trigger != "BOOT") {
                    Spacer(Modifier.height(Space.sm))
                    OutlinedTextField(
                        value = payload,
                        onValueChange = { payload = it },
                        placeholder = {
                            Text(
                                if (trigger == "TIME_SCHEDULE") "08:00" else "Network name",
                                color = TextTertiary
                            )
                        },
                        singleLine = true,
                        shape = Radius.sm,
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onCreate(name, trigger, payload) }) {
                Text(
                    "Create",
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

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Accent,
    unfocusedBorderColor = Border,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    cursorColor = Accent
)
