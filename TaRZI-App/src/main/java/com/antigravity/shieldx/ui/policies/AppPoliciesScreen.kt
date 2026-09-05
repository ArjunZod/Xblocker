package com.antigravity.shieldx.ui.policies

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.antigravity.shieldx.core.model.AppPolicy
import com.antigravity.shieldx.core.security.SecurityManager
import com.antigravity.shieldx.ui.components.*
import com.antigravity.shieldx.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Application-level policy management screen for Xblocker.
 * Allows fine-grained restriction, blocking, or whitelisting of installed applications
 * with instant search, category organization, and admin PIN authorization.
 */
@Composable
fun AppPoliciesScreen(
    securityManager: SecurityManager,
    onBack: () -> Unit = {}
) {
    val rules by securityManager.appPolicyRepository.allAppRulesFlow
        .collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf<AppPolicy?>(null) } // null = All

    var showPin by remember { mutableStateOf(false) }
    var pendingChange by remember { mutableStateOf<(() -> Unit)?>(null) }
    val isPinConfigured = remember { securityManager.adminRecoveryController.isPinSet() }

    val filteredRules = remember(rules, searchQuery, selectedFilter) {
        rules.filter { rule ->
            val matchesQuery = searchQuery.isBlank() ||
                    rule.appLabel.contains(searchQuery.trim(), ignoreCase = true) ||
                    rule.packageName.contains(searchQuery.trim(), ignoreCase = true)
            val matchesFilter = selectedFilter == null || rule.policy == selectedFilter
            matchesQuery && matchesFilter
        }
    }

    if (showPin) {
        PinEntryDialog(
            title = "Change App Policy",
            subtitle = "Enter your admin PIN to modify how this application is managed.",
            isPinConfigured = isPinConfigured,
            onDismiss = { showPin = false; pendingChange = null },
            onVerify = { securityManager.adminRecoveryController.verifyPin(it) },
            onSuccess = {
                showPin = false
                pendingChange?.invoke()
                pendingChange = null
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
                title = "Application Control",
                subtitle = "How Xblocker manages installed applications",
                onBack = onBack
            )
        }

        // Search Bar
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.gutter, vertical = Space.xs)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search apps or package name...", color = TextTertiary) },
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

        // Filter Chips
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.gutter, vertical = Space.xs),
                horizontalArrangement = Arrangement.spacedBy(Space.xs)
            ) {
                FilterChip(
                    selected = selectedFilter == null,
                    onClick = { selectedFilter = null },
                    label = { Text("All (${rules.size})") }
                )
                FilterChip(
                    selected = selectedFilter == AppPolicy.RESTRICTED,
                    onClick = { selectedFilter = if (selectedFilter == AppPolicy.RESTRICTED) null else AppPolicy.RESTRICTED },
                    label = { Text("Restricted") }
                )
                FilterChip(
                    selected = selectedFilter == AppPolicy.BLOCKED,
                    onClick = { selectedFilter = if (selectedFilter == AppPolicy.BLOCKED) null else AppPolicy.BLOCKED },
                    label = { Text("Blocked") }
                )
                FilterChip(
                    selected = selectedFilter == AppPolicy.ALLOWED,
                    onClick = { selectedFilter = if (selectedFilter == AppPolicy.ALLOWED) null else AppPolicy.ALLOWED },
                    label = { Text("Allowed") }
                )
            }
        }

        if (filteredRules.isEmpty()) {
            item {
                StateMessage(
                    title = if (rules.isEmpty()) "No apps listed" else "No matching apps",
                    description = if (rules.isEmpty()) "App policies will appear once the device has been scanned." else "Try adjusting your search or filter.",
                    icon = Icons.Default.Apps
                )
            }
        } else {
            val grouped = filteredRules.groupBy { it.category }
            grouped.forEach { (category, categoryRules) ->
                item(key = "h_$category") {
                    SectionHeader(
                        category.lowercase().replaceFirstChar { it.uppercase() } + " (${categoryRules.size})"
                    )
                }
                item(key = "g_$category") {
                    Grouped {
                        categoryRules.forEachIndexed { index, rule ->
                            if (index > 0) RowDivider()
                            SettingRow(
                                title = rule.appLabel,
                                description = rule.packageName,
                                onClick = {
                                    pendingChange = {
                                        scope.launch {
                                            val next = when (rule.policy) {
                                                AppPolicy.BLOCKED -> AppPolicy.RESTRICTED
                                                AppPolicy.RESTRICTED -> AppPolicy.ALLOWED
                                                else -> AppPolicy.BLOCKED
                                            }
                                            securityManager.appPolicyRepository.updateAppPolicy(
                                                packageName = rule.packageName,
                                                appLabel = rule.appLabel,
                                                category = rule.category,
                                                policy = next,
                                                reason = rule.reason
                                            )
                                        }
                                    }
                                    showPin = true
                                },
                                trailing = {
                                    StatusChip(
                                        text = rule.policy.name.lowercase()
                                            .replaceFirstChar { it.uppercase() },
                                        tone = when (rule.policy) {
                                            AppPolicy.BLOCKED -> StatusTone.Critical
                                            AppPolicy.RESTRICTED -> StatusTone.Caution
                                            AppPolicy.ALLOWED -> StatusTone.Positive
                                            else -> StatusTone.Neutral
                                        }
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(Space.section)) }
    }
}
