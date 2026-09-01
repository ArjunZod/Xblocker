package com.antigravity.shieldx.ui.blocked

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.antigravity.shieldx.core.security.SecurityManager
import com.antigravity.shieldx.ui.components.*
import com.antigravity.shieldx.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * A log, presented as a log: newest first, one line of what and when, with the
 * reason as supporting text. Clearing it is destructive, so it stays behind the
 * admin PIN.
 */
@Composable
fun BlockedEventsScreen(
    securityManager: SecurityManager,
    onBack: () -> Unit = {}
) {
    val events by securityManager.auditRepository.recentBlockedEventsFlow
        .collectAsState(initial = emptyList())
    val formatter = remember { SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()) }
    val scope = rememberCoroutineScope()

    var showPin by remember { mutableStateOf(false) }
    val isPinConfigured = remember { securityManager.adminRecoveryController.isPinSet() }

    if (showPin) {
        PinEntryDialog(
            title = "Clear history",
            subtitle = "Enter your admin PIN to delete the blocked activity log.",
            isPinConfigured = isPinConfigured,
            onDismiss = { showPin = false },
            onVerify = { securityManager.adminRecoveryController.verifyPin(it) },
            onSuccess = {
                showPin = false
                scope.launch { securityManager.auditRepository.clearHistory() }
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
                title = "Blocked activity",
                subtitle = if (events.isEmpty()) null else "${events.size} recent events",
                onBack = onBack,
                trailing = {
                    if (events.isNotEmpty()) {
                        SecondaryButton(text = "Clear", onClick = { showPin = true })
                    }
                }
            )
        }

        if (events.isEmpty()) {
            item {
                StateMessage(
                    title = "Nothing blocked yet",
                    description = "Requests that Tarzi blocks will appear here.",
                    icon = Icons.Default.Shield
                )
            }
        } else {
            items(events, key = { it.id }) { event ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.gutter, vertical = Space.md)
                ) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            text = event.target,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(Space.sm))
                        Text(
                            text = formatter.format(event.timestamp),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = event.category.name.lowercase().replace('_', ' ')
                            .replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                RowDivider(insetStart = Space.gutter)
            }
        }
    }
}
