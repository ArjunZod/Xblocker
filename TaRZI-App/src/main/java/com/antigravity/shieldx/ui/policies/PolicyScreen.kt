package com.antigravity.shieldx.ui.policies

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.antigravity.shieldx.core.model.Category
import com.antigravity.shieldx.core.model.PolicyDecision
import com.antigravity.shieldx.core.security.SecurityManager
import com.antigravity.shieldx.ui.components.*
import com.antigravity.shieldx.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Domain Management Screen.
 * Manage custom blocklists and allowlist overrides with instant search,
 * category selection, and admin PIN protection.
 */
@Composable
fun PolicyScreen(
    securityManager: SecurityManager,
    onBack: () -> Unit = {}
) {
    val rules by securityManager.domainRepository.allRulesFlow
        .collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var showPin by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val isPinConfigured = remember { securityManager.adminRecoveryController.isPinSet() }

    val filteredRules = remember(rules, searchQuery) {
        if (searchQuery.isBlank()) rules
        else rules.filter { it.domain.contains(searchQuery.trim().lowercase()) }
    }

    val custom = filteredRules.filter { it.isCustom }
    val seeded = filteredRules.filterNot { it.isCustom }

    fun guarded(action: () -> Unit) {
        pendingAction = action
        showPin = true
    }

    if (showPin) {
        PinEntryDialog(
            title = "Modify domain rules",
            subtitle = "Enter your admin PIN to modify custom domain rules.",
            isPinConfigured = isPinConfigured,
            onDismiss = { showPin = false; pendingAction = null },
            onVerify = { securityManager.adminRecoveryController.verifyPin(it) },
            onSuccess = {
                showPin = false
                pendingAction?.invoke()
                pendingAction = null
            }
        )
    }

    if (showAdd) {
        AddDomainDialog(
            onDismiss = { showAdd = false },
            onAdd = { domain, category, decision ->
                showAdd = false
                guarded {
                    scope.launch {
                        securityManager.domainRepository.addCustomRule(domain, category, decision)
                        securityManager.domainMatcher
                            .loadRules(securityManager.domainRepository.getAllRules())
                    }
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
                title = "Domain Rules",
                subtitle = "${rules.size} total rules active",
                onBack = onBack,
                trailing = {
                    SecondaryButton(text = "Add Rule", onClick = { showAdd = true })
                }
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
                    placeholder = { Text("Search domains...", color = TextTertiary) },
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

        item { SectionHeader("Custom Rules (${custom.size})") }

        if (custom.isEmpty()) {
            item {
                StateMessage(
                    title = if (searchQuery.isEmpty()) "No custom domains" else "No matching custom rules",
                    description = if (searchQuery.isEmpty()) "Add domains to block or create allowlist overrides." else "Try another search term.",
                    icon = Icons.Default.Add
                )
            }
        } else {
            item {
                Grouped {
                    custom.forEachIndexed { index, rule ->
                        if (index > 0) RowDivider()
                        SettingRow(
                            title = rule.domain,
                            description = "${rule.action.name} • ${rule.category.displayName}",
                            trailing = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Remove ${rule.domain}",
                                    tint = TextTertiary,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable {
                                            guarded {
                                                scope.launch {
                                                    securityManager.domainRepository.removeRule(rule)
                                                    securityManager.domainMatcher.loadRules(
                                                        securityManager.domainRepository.getAllRules()
                                                    )
                                                }
                                            }
                                        }
                                )
                            }
                        )
                    }
                }
            }
        }

        item { SectionHeader("Built-in System Threat Rules (${seeded.size})") }
        item {
            Column(Modifier.padding(horizontal = Space.gutter)) {
                Text(
                    text = "System-level verified domains across adult, malware, phishing, and encrypted DNS bypass resolvers.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }

        items(seeded.take(50), key = { it.domain }) { rule ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.gutter, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = rule.domain,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
                Text(
                    text = rule.category.name.lowercase().replace('_', ' '),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }
        }

        if (seeded.size > 50) {
            item {
                Text(
                    text = "and ${seeded.size - 50} more rules in active trie",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTertiary,
                    modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.md)
                )
            }
        }

        item { Spacer(Modifier.height(Space.section)) }
    }
}

@Composable
private fun AddDomainDialog(
    onDismiss: () -> Unit,
    onAdd: (domain: String, category: Category, decision: PolicyDecision) -> Unit
) {
    var domain by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(Category.PORNOGRAPHY) }
    var selectedDecision by remember { mutableStateOf(PolicyDecision.BLOCK) }

    val valid = domain.contains('.') && !domain.contains(' ') && domain.length >= 4

    val categoryOptions = listOf(
        Category.PORNOGRAPHY,
        Category.GAMBLING,
        Category.MALWARE,
        Category.PHISHING,
        Category.SOCIAL_MEDIA,
        Category.SHORT_VIDEO,
        Category.PIRACY,
        Category.TRACKING,
        Category.SAFE
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("Add Domain Rule", style = MaterialTheme.typography.titleLarge, color = TextPrimary) },
        text = {
            Column {
                Text(
                    "Enter domain (subdomains are automatically included):",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(Space.sm))
                OutlinedTextField(
                    value = domain,
                    onValueChange = { domain = it.trim().lowercase() },
                    placeholder = { Text("example.com", color = TextTertiary) },
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

                Spacer(Modifier.height(Space.md))
                Text("Action:", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Spacer(Modifier.height(Space.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    FilterChip(
                        selected = selectedDecision == PolicyDecision.BLOCK,
                        onClick = { selectedDecision = PolicyDecision.BLOCK },
                        label = { Text("BLOCK") }
                    )
                    FilterChip(
                        selected = selectedDecision == PolicyDecision.ALLOW,
                        onClick = { selectedDecision = PolicyDecision.ALLOW },
                        label = { Text("ALLOW (Override)") }
                    )
                }

                Spacer(Modifier.height(Space.md))
                Text("Category:", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Spacer(Modifier.height(Space.xs))
                // Quick Category Selection Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    categoryOptions.take(3).forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat.name.take(7)) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onAdd(domain, selectedCategory, selectedDecision) }
            ) {
                Text(
                    "Save Rule",
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
