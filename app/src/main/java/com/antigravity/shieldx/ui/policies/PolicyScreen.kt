package com.antigravity.shieldx.ui.policies

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.antigravity.shieldx.core.model.Category
import com.antigravity.shieldx.core.model.PolicyDecision
import com.antigravity.shieldx.core.security.SecurityManager
import com.antigravity.shieldx.ui.components.*
import com.antigravity.shieldx.ui.theme.*
import kotlinx.coroutines.launch

/**
 * The domain list. Custom entries are editable; the bundled list is shown for
 * reference but not individually deletable, since removing seeds one at a time
 * is not a workflow anyone actually wants.
 */
@Composable
fun PolicyScreen(
    securityManager: SecurityManager,
    onBack: () -> Unit = {}
) {
    val rules by securityManager.domainRepository.allRulesFlow
        .collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var showAdd by remember { mutableStateOf(false) }
    var showPin by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val isPinConfigured = remember { securityManager.adminRecoveryController.isPinSet() }

    val custom = rules.filter { it.isCustom }
    val seeded = rules.filterNot { it.isCustom }

    fun guarded(action: () -> Unit) {
        pendingAction = action
        showPin = true
    }

    if (showPin) {
        PinEntryDialog(
            title = "Change blocked domains",
            subtitle = "Enter your admin PIN to modify the domain list.",
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
            onAdd = { domain ->
                showAdd = false
                guarded {
                    scope.launch {
                        securityManager.domainRepository.addCustomRule(
                            domain, Category.PORNOGRAPHY, PolicyDecision.BLOCK
                        )
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
                title = "Blocked domains",
                subtitle = "${rules.size} rules active",
                onBack = onBack,
                trailing = {
                    SecondaryButton(text = "Add", onClick = { showAdd = true })
                }
            )
        }

        item { SectionHeader("Your domains") }

        if (custom.isEmpty()) {
            item {
                StateMessage(
                    title = "No custom domains",
                    description = "Add a domain to block it in addition to the built-in list.",
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
                            description = rule.category.name.lowercase().replace('_', ' '),
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

        item { SectionHeader("Built-in list") }
        item {
            Column(Modifier.padding(horizontal = Space.gutter)) {
                Text(
                    text = "${seeded.size} domains are blocked by default across pornography, " +
                        "cam services, adult dating, and encrypted DNS bypass endpoints.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }

        items(seeded.take(60), key = { it.domain }) { rule ->
            Text(
                text = rule.domain,
                style = MaterialTheme.typography.bodyMedium,
                color = TextTertiary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.gutter, vertical = 6.dp)
            )
        }

        if (seeded.size > 60) {
            item {
                Text(
                    text = "and ${seeded.size - 60} more",
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
private fun AddDomainDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var domain by remember { mutableStateOf("") }
    val valid = domain.contains('.') && !domain.contains(' ')

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("Add domain", style = MaterialTheme.typography.titleLarge, color = TextPrimary) },
        text = {
            Column {
                Text(
                    "Subdomains are blocked too.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(Space.md))
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
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onAdd(domain) }) {
                Text(
                    "Add",
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
