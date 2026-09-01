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
import com.antigravity.shieldx.assistant.voice.VoiceAssistantManager
import com.antigravity.shieldx.core.model.PlaybackCommand
import com.antigravity.shieldx.core.model.ProtectionProfile
import com.antigravity.shieldx.core.security.SecurityManager
import com.antigravity.shieldx.ui.assistant.VoiceModeScreen
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
    onNavigateToAssistant: () -> Unit,
    onNavigateToMusic: () -> Unit,
    onNavigateToProtection: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isVpnRunning by ProtectionVpnService.isRunningFlow.collectAsState()
    val policy by securityManager.policyRepository.currentPolicyFlow.collectAsState(initial = null)
    val playback by securityManager.musicController.playbackStateFlow.collectAsState()
    val recent by securityManager.playHistoryRepository.recentFlow
        .collectAsState(initial = emptyList())

    val isProtected = policy?.isEnabled == true && isVpnRunning

    var showVoiceMode by remember { mutableStateOf(false) }
    var voiceManagerRef by remember { mutableStateOf<VoiceAssistantManager?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showVoiceMode = true
        }
    }

    DisposableEffect(context) {
        val vm = VoiceAssistantManager(
            context = context,
            onSpeechRecognized = {
                // Navigate to assistant with speech
                onNavigateToAssistant()
            }
        )
        voiceManagerRef = vm
        onDispose {
            vm.destroy()
        }
    }

    fun startVoiceMode() {
        val vm = voiceManagerRef ?: return
        if (!vm.hasRecordPermission()) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            showVoiceMode = true
        }
    }

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
                    subtitle = "How can I help you today?"
                )
            }

            // 1. Primary AI Assistant Prompt Bar
            item {
                Column(Modifier.padding(horizontal = Space.gutter)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(Radius.md)
                            .background(Surface)
                            .border(1.dp, Border, Radius.md)
                            .clickable { onNavigateToAssistant() }
                            .padding(Space.lg)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Ask TaRZI anything...",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TextTertiary
                                )
                                Spacer(Modifier.height(Space.xs))
                                Text(
                                    text = "Tap to chat or use live voice",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Accent)
                                    .clickable { startVoiceMode() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Start Live Voice",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 2. Intelligent Quick Actions & Suggestions
            item {
                SuggestionChipRow(
                    suggestions = listOf(
                        "Play relaxing jazz",
                        "How much RAM is free?",
                        "Check device protection",
                        "Turn on flashlight",
                        "Show stored memory"
                    ),
                    onSuggestionClick = { onNavigateToAssistant() }
                )
            }

            // 3. Current Playback Widget (if active)
            playback.currentTrack?.let { track ->
                item {
                    Column(Modifier.padding(horizontal = Space.gutter)) {
                        SectionHeader(title = "Now Playing", modifier = Modifier.padding(horizontal = 0.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(Radius.md)
                                .background(Surface)
                                .border(1.dp, Border, Radius.md)
                                .clickable { onNavigateToMusic() }
                                .padding(Space.md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!track.thumbnailUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = track.thumbnailUrl,
                                    contentDescription = track.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(Radius.sm)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(Radius.sm)
                                        .background(SurfaceRaised),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = TextSecondary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.width(Space.md))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    maxLines = 1
                                )
                                Text(
                                    text = track.artist,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    maxLines = 1
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(SurfaceRaised)
                                    .clickable {
                                        scope.launch {
                                            if (playback.isPlaying) {
                                                securityManager.musicController.pause()
                                            } else {
                                                securityManager.musicController.resume()
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (playback.isPlaying) "Pause" else "Play",
                                    tint = TextPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 4. Recently Played Music Row
            if (recent.isNotEmpty()) {
                item {
                    Column {
                        SectionHeader(title = "Recent Music")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = Space.gutter),
                            horizontalArrangement = Arrangement.spacedBy(Space.md)
                        ) {
                            items(recent.take(6), key = { it.trackId }) { entry ->
                                Column(
                                    modifier = Modifier
                                        .width(110.dp)
                                        .clickable {
                                            scope.launch {
                                                securityManager.musicController.play(
                                                    PlaybackCommand.PlayTrack(entry.toTrack())
                                                )
                                                onNavigateToMusic()
                                            }
                                        }
                                ) {
                                    if (!entry.thumbnailUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = entry.thumbnailUrl,
                                            contentDescription = entry.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(110.dp)
                                                .clip(Radius.md)
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(110.dp)
                                                .clip(Radius.md)
                                                .background(SurfaceRaised),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.MusicNote,
                                                contentDescription = null,
                                                tint = TextSecondary,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(Space.xs))
                                    Text(
                                        text = entry.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextPrimary,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = entry.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 5. Protection Status Glance
            item {
                Column(Modifier.padding(horizontal = Space.gutter)) {
                    SectionHeader(title = "Device Protection", modifier = Modifier.padding(horizontal = 0.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(Radius.md)
                            .background(Surface)
                            .border(1.dp, Border, Radius.md)
                            .clickable { onNavigateToProtection() }
                            .padding(Space.lg),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusChip(
                                    text = if (isProtected) "Active Protection" else "Protection Off",
                                    tone = if (isProtected) StatusTone.Positive else StatusTone.Caution
                                )
                            }
                            Spacer(Modifier.height(Space.xs))
                            Text(
                                text = if (isProtected) "SafeSearch and content filtering are actively running." else "Tap to enable on-device content filtering.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(Radius.sm)
                                .background(if (isProtected) SurfaceRaised else Accent)
                                .clickable { toggleProtection() }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = if (isProtected) "Turn Off" else "Enable",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isProtected) TextSecondary else Color.White
                            )
                        }
                    }
                }
            }
        }

        // Live Voice Dialog
        if (showVoiceMode) {
            voiceManagerRef?.let { vm ->
                VoiceModeScreen(
                    voiceManager = vm,
                    lastReply = "",
                    isThinking = false,
                    onClose = { showVoiceMode = false },
                    onSwitchToKeyboard = {
                        showVoiceMode = false
                        onNavigateToAssistant()
                    }
                )
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
