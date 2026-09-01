package com.antigravity.shieldx.ui.assistant

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.antigravity.shieldx.assistant.voice.VoiceAssistantManager
import com.antigravity.shieldx.assistant.voice.VoiceState
import com.antigravity.shieldx.ui.theme.*

/**
 * Premium full-screen Live Voice Mode for TaRZI.
 * Seamlessly integrates live audio reactivity, true barge-in interruption,
 * and live streaming transcriptions.
 */
@Composable
fun VoiceModeScreen(
    voiceManager: VoiceAssistantManager,
    lastReply: String,
    isThinking: Boolean,
    onClose: () -> Unit,
    onSwitchToKeyboard: () -> Unit = onClose
) {
    val voiceState by voiceManager.voiceStateFlow.collectAsState()
    var isManuallyMuted by remember { mutableStateOf(false) }

    // Start continuous conversation loop on screen entry, cleanup on dispose
    DisposableEffect(Unit) {
        voiceManager.setContinuousMode(true)
        if (voiceManager.hasRecordPermission()) {
            voiceManager.startListening()
        }
        onDispose {
            voiceManager.setContinuousMode(false)
            voiceManager.stopListening()
            voiceManager.stopSpeaking()
            voiceManager.resetToIdle()
        }
    }

    val phase = when {
        isThinking -> VoicePhase.Thinking
        voiceState is VoiceState.Listening -> VoicePhase.Listening
        voiceState is VoiceState.Speaking -> VoicePhase.Speaking
        voiceState is VoiceState.Processing -> VoicePhase.Thinking
        voiceState is VoiceState.Error -> VoicePhase.Error
        else -> VoicePhase.Idle
    }

    val amplitude = (voiceState as? VoiceState.Listening)?.rmsDb ?: 0f

    // Natural barge-in / speech auto-resume
    LaunchedEffect(voiceState, isThinking, isManuallyMuted) {
        if (!isManuallyMuted && voiceState is VoiceState.Idle && !isThinking) {
            kotlinx.coroutines.delay(250)
            if (voiceManager.hasRecordPermission()) {
                voiceManager.startListening()
            }
        }
    }

    // Active transcript / spoken feedback
    val caption = when {
        voiceState is VoiceState.Listening -> ""
        voiceState is VoiceState.Processing ->
            (voiceState as VoiceState.Processing).partialText
        isThinking -> ""
        lastReply.isNotBlank() -> lastReply
        else -> ""
    }

    val statusWord = when {
        isManuallyMuted -> "Muted · Tap to speak"
        phase == VoicePhase.Listening -> "Listening"
        phase == VoicePhase.Thinking -> "Thinking"
        phase == VoicePhase.Speaking -> "Speaking"
        phase == VoicePhase.Error -> "Reconnecting..."
        else -> "Tarzi ready"
    }

    fun interruptOrToggle() {
        if (voiceState is VoiceState.Speaking) {
            // Natural Barge-In: instantly halt speech and start listening
            voiceManager.stopSpeaking()
            voiceManager.resetToIdle()
            voiceManager.startListening()
        } else if (phase == VoicePhase.Listening) {
            isManuallyMuted = true
            voiceManager.stopListening()
        } else {
            isManuallyMuted = false
            voiceManager.stopSpeaking()
            voiceManager.resetToIdle()
            voiceManager.startListening()
        }
    }

    Dialog(
        onDismissRequest = {
            voiceManager.setContinuousMode(false)
            voiceManager.stopListening()
            voiceManager.stopSpeaking()
            onClose()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            dismissOnBackPress = true
        )
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Canvas,
                            Color(0xFF0F0F14),
                            Canvas
                        )
                    )
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    interruptOrToggle()
                }
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = Space.section)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top minimal title & indicator
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Space.lg),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TaRZI Live",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextSecondary
                    )

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(SurfaceRaised)
                            .clickable { onSwitchToKeyboard() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Keyboard,
                            contentDescription = "Switch to text keyboard",
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Center visualizer section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Space.lg),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .sizeIn(maxWidth = 240.dp, maxHeight = 240.dp)
                            .fillMaxWidth(0.70f)
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .clickable { interruptOrToggle() },
                        contentAlignment = Alignment.Center
                    ) {
                        VoiceVisualizer(
                            phase = phase,
                            amplitude = amplitude,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(Modifier.height(Space.xl))

                    AnimatedContent(
                        targetState = statusWord,
                        transitionSpec = {
                            fadeIn(tween(Motion.standard)) togetherWith fadeOut(tween(Motion.fast))
                        },
                        label = "status"
                    ) { word ->
                        Text(
                            text = word,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isManuallyMuted) Warning else if (phase == VoicePhase.Listening) Accent else TextSecondary
                        )
                    }

                    Spacer(Modifier.height(Space.md))

                    // Dynamic transcript subtitle
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        if (caption.isNotBlank()) {
                            Text(
                                text = caption,
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextPrimary,
                                textAlign = TextAlign.Center,
                                maxLines = 4
                            )
                        }
                    }
                }

                // Bottom intuitive action controls
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = Space.xl),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Close / End call button
                    CircleControl(
                        icon = Icons.Default.Close,
                        contentDescription = "Close Live Voice Mode",
                        background = Danger.copy(alpha = 0.15f),
                        tint = Danger,
                        onClick = {
                            voiceManager.setContinuousMode(false)
                            voiceManager.stopListening()
                            voiceManager.stopSpeaking()
                            onClose()
                        }
                    )

                    Spacer(Modifier.width(Space.xxl))

                    // Primary Mic Toggle
                    val isListening = phase == VoicePhase.Listening
                    CircleControl(
                        icon = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = if (isListening) "Mute mic" else "Unmute mic",
                        background = if (isListening) Accent else SurfaceRaised,
                        tint = if (isListening) Color.White else TextPrimary,
                        size = 64.dp,
                        onClick = { interruptOrToggle() }
                    )
                }
            }
        }
    }
}

@Composable
private fun CircleControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    background: Color,
    tint: Color,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 52.dp
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size * 0.44f)
        )
    }
}
