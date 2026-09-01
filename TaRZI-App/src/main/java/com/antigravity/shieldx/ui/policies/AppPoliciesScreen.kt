package com.antigravity.shieldx.ui.policies

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.antigravity.shieldx.core.model.AppPolicy
import com.antigravity.shieldx.core.security.SecurityManager
import com.antigravity.shieldx.ui.components.*
import com.antigravity.shieldx.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Per-app treatment. Each app is one row with its current policy on the right;
 * changing a policy is gated behind the admin PIN because it weakens coverage.
 */
@Composable
fun AppPoliciesScreen(
    securityManager: SecurityManager,
    onBack: () -> Unit = {}
) {
    val rules by securityManager.appPolicyRepository.allAppRulesFlow
        .collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var showPin by remember { mutableStateOf(false) }
    var pendingChange by remember { mutableStateOf<(() -> Unit)?>(null) }
    val isPinConfigured = remember { securityManager.adminRecoveryController.isPinSet() }

    if (showPin) {
        PinEntryDialog(
            title = "Change app policy",
            subtitle = "Enter your admin PIN to change how this app is treated.",
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
                title = "App control",
                subtitle = "How Tarzi treats each installed app",
                onBack = onBack
            )
        }

        if (rules.isEmpty()) {
            item {
                StateMessage(
                    title = "No apps listed",
                    description = "App policies will appear once the device has been scanned.",
                    icon = Icons.Default.Apps
                )
            }
        } else {
            val grouped = rules.groupBy { it.category }
            grouped.forEach { (category, categoryRules) ->
                item(key = "h_$category") {
                    SectionHeader(
                        category.lowercase().replaceFirstChar { it.uppercase() }
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
                                                else -> AppPolicy.RESTRICTED
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
    }
}
