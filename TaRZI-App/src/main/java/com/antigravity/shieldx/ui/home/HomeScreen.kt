package com.antigravity.shieldx.ui.home

import android.Manifest
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.antigravity.shieldx.core.model.ProtectionProfile
import com.antigravity.shieldx.core.security.SecurityManager
import com.antigravity.shieldx.ui.components.*
import com.antigravity.shieldx.ui.theme.*
import com.antigravity.shieldx.vpn.ProtectionVpnService
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Assistant-Centric Home Screen for TaRZI.
 * The AI Assistant is the primary interaction surface; music, protection, and
 * utilities support it.
 */
@Composable
fun HomeScreen(
    securityManager: SecurityManager,
    onNavigateToProtection: () -> Unit,
    onNavigateToLogs: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isVpnRunning by ProtectionVpnService.isRunningFlow.collectAsState()
    val policy by securityManager.policyRepository.currentPolicyFlow.collectAsState(initial = null)
    val isProtected = policy?.isEnabled == true && isVpnRunning

    val vpnConsent = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            scope.launch { securityManager.protectionController.setProfile(ProtectionProfile.MAXIMUM) }
        }
    }

    fun toggleProtection() {
        if (isProtected) {
            scope.launch { securityManager.protectionController.setProfile(ProtectionProfile.OFF) }
        } else {
            val intent = VpnService.prepare(context)
            if (intent != null) {
                vpnConsent.launch(intent)
            } else {
                scope.launch { securityManager.protectionController.setProfile(ProtectionProfile.MAXIMUM) }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Canvas)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Canvas),
            contentPadding = ContentBottomPadding,
            verticalArrangement = Arrangement.spacedBy(Space.lg)
        ) {
            item {
                ScreenHeader(
                    title = greeting(),
                    subtitle = null
                )
            }

            // 1. Primary AI Assistant Prompt Bar
            item {
                Column(Modifier.padding(horizontal = Space.gutter)) {
                    Text(
                        text = if (isProtected) "Filtering is on" else "Filtering is off",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        text = if (isProtected) {
                            "Adult content is filtered across your browsers and apps."
                        } else {
                            "Turn filtering on to block adult content device-wide."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(Space.lg))
                    PrimaryButton(
                        text = if (isProtected) "Turn off filtering" else "Turn on filtering",
                        onClick = { toggleProtection() }
                    )
                }
            }

            item {
                Column {
                    SectionHeader(title = "Activity")
                    Grouped {
                        SettingRow(
                            title = "Blocked activity",
                            description = "See what has been blocked and why",
                            onClick = onNavigateToLogs
                        )
                    }
                }
            }

            item {
                Column {
                    SectionHeader(title = "Protection")
                    Grouped {
                        SettingRow(
                            title = "Filtering settings",
                            description = "SafeSearch, blocked domains and app rules",
                            onClick = onNavigateToProtection
                        )
                    }
                }
            }
        }

    }
}

private fun greeting(): String {
    return when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 4..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Good night"
    }
}
