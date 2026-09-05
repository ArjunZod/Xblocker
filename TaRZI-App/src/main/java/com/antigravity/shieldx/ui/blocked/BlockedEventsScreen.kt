package com.antigravity.shieldx.ui.blocked

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.antigravity.shieldx.core.model.Category
import com.antigravity.shieldx.core.security.SecurityManager
import com.antigravity.shieldx.ui.components.*
import com.antigravity.shieldx.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Real-time Blocked Activity & Security Audit Log.
 * Records intercepted domains, reasons, and timestamps with instant search and category filtering.
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

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf<Category?>(null) }

    var showPin by remember { mutableStateOf(false) }
    val isPinConfigured = remember { securityManager.adminRecoveryController.isPinSet() }

    val filteredEvents = remember(events, searchQuery, selectedCategoryFilter) {
        events.filter { event ->
            val matchesQuery = searchQuery.isBlank() ||
                    event.target.contains(searchQuery.trim(), ignoreCase = true) ||
                    event.reason.name.contains(searchQuery.trim(), ignoreCase = true)
            val matchesCat = selectedCategoryFilter == null || event.category == selectedCategoryFilter
            matchesQuery && matchesCat
        }
    }

    if (showPin) {
        PinEntryDialog(
            title = "Clear Activity History",
            subtitle = "Enter your admin PIN to delete all logged blocked events.",
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
                title = "Blocked Activity",
                subtitle = if (events.isEmpty()) null else "${events.size} total events logged",
                onBack = onBack,
                trailing = {
                    if (events.isNotEmpty()) {
                        SecondaryButton(text = "Clear", onClick = { showPin = true })
                    }
                }
            )
        }

        // Search Bar
        if (events.isNotEmpty()) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.gutter, vertical = Space.xs)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search blocked domain or reason...", color = TextTertiary) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = TextTertiary)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = TextTertiary,
                                    modifier = Modifier.clickable { searchQuery = "" }
                                )
                            }
                        },
                        singleLine = true,
                        shape = Radius.sm,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent,
                            unfocusedBorderColor = Border,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = Accent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        if (events.isEmpty()) {
            item {
                StateMessage(
                    title = "Nothing blocked yet",
                    description = "Requests that Xblocker intercepts and sinkholes will appear here.",
                    icon = Icons.Default.Shield
                )
            }
        } else if (filteredEvents.isEmpty()) {
            item {
                StateMessage(
                    title = "No matching events",
                    description = "No logged events match your current filter.",
                    icon = Icons.Default.Search
                )
            }
        } else {
            items(filteredEvents, key = { it.id }) { event ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.gutter, vertical = Space.md)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusChip(
                            text = event.category.name.lowercase().replace('_', ' ')
                                .replaceFirstChar { it.uppercase() },
                            tone = if (event.category.isSecurityThreat) StatusTone.Critical else StatusTone.Caution
                        )
                        Spacer(Modifier.width(Space.sm))
                        Text(
                            text = event.reason.name.lowercase().replace('_', ' '),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary
                        )
                    }
                }
                RowDivider(insetStart = Space.gutter)
            }
        }

        item { Spacer(Modifier.height(Space.section)) }
    }
}
