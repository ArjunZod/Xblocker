package com.antigravity.shieldx.ui.assistant

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.shieldx.assistant.TarziBrain
import com.antigravity.shieldx.assistant.voice.VoiceAssistantManager
import com.antigravity.shieldx.core.model.ToolRequest
import com.antigravity.shieldx.core.security.SecurityManager
import com.antigravity.shieldx.ui.components.*
import com.antigravity.shieldx.ui.theme.*
import kotlinx.coroutines.launch

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: String, // "USER" or "ASSISTANT"
    val text: String,
    val toolRequests: List<ToolRequest> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Premium Conversation Mode for TaRZI AI Assistant.
 * Built for calm, spacious, intelligent dialogue inspired by Gemini and Claude.
 */
@Composable
fun AssistantChatScreen(
    securityManager: SecurityManager,
    onNavigateToMusic: () -> Unit,
    onNavigateToProtection: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val messages = remember { mutableStateListOf<ChatMessage>() }
    var inputText by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }
    var pendingConfirmation by remember { mutableStateOf<ToolRequest?>(null) }
    var confirmationPrompt by remember { mutableStateOf("") }
    var voiceManagerRef by remember { mutableStateOf<VoiceAssistantManager?>(null) }

    val brain = remember { TarziBrain(securityManager) }
    val listState = rememberLazyListState()

    var showVoiceMode by remember { mutableStateOf(false) }
    var lastSpokenReply by remember { mutableStateOf("") }

    // Seed welcome greeting if empty
    LaunchedEffect(Unit) {
        if (messages.isEmpty()) {
            messages.add(
                ChatMessage(
                    sender = "ASSISTANT",
                    text = "Hello! I am Tarzi, your personal AI assistant. How can I help you today?"
                )
            )
        }
    }

    // Automatically scroll to bottom when messages are added
    LaunchedEffect(messages.size, isThinking) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    fun handleUserQuery(query: String) {
        if (query.isBlank()) return
        focusManager.clearFocus()
        keyboardController?.hide()
        messages.add(ChatMessage(sender = "USER", text = query))
        inputText = ""
        isThinking = true

        scope.launch {
            val response = brain.process(query)
            isThinking = false

            response.pendingConfirmation?.let { pending ->
                pendingConfirmation = pending
                confirmationPrompt = response.confirmationPrompt
                    ?: "Confirm ${pending.toolName.lowercase().replace('_', ' ')}?"
                voiceManagerRef?.speak(confirmationPrompt)
                return@launch
            }

            messages.add(
                ChatMessage(
                    sender = "ASSISTANT",
                    text = response.displayText,
                    toolRequests = response.toolRequests
                )
            )
            lastSpokenReply = response.spokenReply

            // Automatically scroll to latest message
            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }

    // Voice assistant integration
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
            onSpeechRecognized = { transcript ->
                if (transcript.isNotBlank()) {
                    handleUserQuery(transcript)
                }
            }
        )
        voiceManagerRef = vm
        securityManager.voiceAssistantManager = vm
        onDispose {
            vm.destroy()
            if (securityManager.voiceAssistantManager === vm) {
                securityManager.voiceAssistantManager = null
            }
        }
    }

    fun startLiveVoice() {
        val vm = voiceManagerRef ?: return
        if (!vm.hasRecordPermission()) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            showVoiceMode = true
        }
    }

    val suggestions = remember {
        listOf(
            "Play some chill music",
            "How much RAM is free?",
            "What can you do?",
            "Turn on flashlight",
            "What is my battery level?"
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Canvas)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.gutter, vertical = Space.md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "TaRZI Assistant",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Text(
                        text = "On-Device Intelligence & Controls",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Surface)
                            .border(1.dp, Border, CircleShape)
                            .clickable { onNavigateToMusic() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "Music",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Surface)
                            .border(1.dp, Border, CircleShape)
                            .clickable { onNavigateToProtection() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Protection",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Messages Stream
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = Space.gutter, vertical = Space.md),
                verticalArrangement = Arrangement.spacedBy(Space.lg)
            ) {
                items(messages, key = { it.id }) { msg ->
                    if (msg.sender == "USER") {
                        // User message: sleek right-aligned bubble
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .wrapContentWidth(Alignment.End)
                                    .clip(RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp))
                                    .background(SurfaceRaised)
                                    .border(1.dp, Border, RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp))
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = msg.text,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TextPrimary
                                )
                            }
                        }
                    } else {
                        // Assistant message: open left-aligned response with rich tool cards
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(Accent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "T",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = Color.White
                                    )
                                }
                                Spacer(Modifier.width(Space.sm))
                                Text(
                                    text = "Tarzi",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextSecondary
                                )
                            }

                            Text(
                                text = msg.text,
                                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 23.sp),
                                color = TextPrimary,
                                modifier = Modifier.padding(start = 2.dp)
                            )

                            // Render attached tool results if any
                            if (msg.toolRequests.isNotEmpty()) {
                                Spacer(Modifier.height(Space.sm))
                                msg.toolRequests.forEach { tool ->
                                    ActionExecutionCard(
                                        toolName = tool.toolName,
                                        summary = tool.parameters.entries.joinToString(", ") { "${it.key}: ${it.value}" }.ifBlank { "Executed successfully" },
                                        statusTone = StatusTone.Positive
                                    )
                                    Spacer(Modifier.height(Space.xs))
                                }
                            }
                        }
                    }
                }

                // Thinking / typing indicator
                if (isThinking) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 2.dp, top = Space.xs)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Accent)
                            )
                            Spacer(Modifier.width(Space.sm))
                            Text(
                                text = "Tarzi is reasoning...",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextTertiary
                            )
                        }
                    }
                }

                // Confirmation card if pending
                if (pendingConfirmation != null) {
                    item {
                        ConfirmationPromptCard(
                            prompt = confirmationPrompt,
                            onConfirm = {
                                val pending = pendingConfirmation ?: return@ConfirmationPromptCard
                                pendingConfirmation = null
                                scope.launch {
                                    val res = brain.executeConfirmedTool(pending)
                                    messages.add(
                                        ChatMessage(
                                            sender = "ASSISTANT",
                                            text = res.displayText,
                                            toolRequests = res.toolRequests
                                        )
                                    )
                                }
                            },
                            onCancel = {
                                pendingConfirmation = null
                                messages.add(
                                    ChatMessage(
                                        sender = "ASSISTANT",
                                        text = "Action cancelled."
                                    )
                                )
                            }
                        )
                    }
                }
            }

            // Suggestion chips
            if (inputText.isBlank() && messages.size <= 2 && !isThinking) {
                SuggestionChipRow(
                    suggestions = suggestions,
                    onSuggestionClick = { handleUserQuery(it) },
                    modifier = Modifier.padding(bottom = Space.sm)
                )
            }

            // Adaptive Input Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.gutter)
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(Radius.full)
                        .background(Surface)
                        .border(1.dp, Border, Radius.full)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left action / multimodal trigger
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(SurfaceRaised)
                            .clickable {
                                handleUserQuery("Read my current screen")
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add context",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(Modifier.width(Space.sm))

                    // Text Input
                    BasicTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary),
                        cursorBrush = SolidColor(Accent),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { handleUserQuery(inputText) }),
                        decorationBox = { innerTextField ->
                            if (inputText.isEmpty()) {
                                Text(
                                    text = "Ask Tarzi anything...",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TextTertiary
                                )
                            }
                            innerTextField()
                        }
                    )

                    Spacer(Modifier.width(Space.sm))

                    // Dynamic Send or Live Voice launcher
                    if (inputText.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Accent)
                                .clickable { handleUserQuery(inputText) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send message",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Accent)
                                .clickable { startLiveVoice() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Start Live Voice Mode",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // Live Voice Full Screen Dialog
        if (showVoiceMode) {
            voiceManagerRef?.let { vm ->
                VoiceModeScreen(
                    voiceManager = vm,
                    lastReply = lastSpokenReply,
                    isThinking = isThinking,
                    onClose = { showVoiceMode = false },
                    onSwitchToKeyboard = { showVoiceMode = false }
                )
            }
        }
    }
}
