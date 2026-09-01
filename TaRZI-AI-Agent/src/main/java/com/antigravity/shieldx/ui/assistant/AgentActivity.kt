package com.antigravity.shieldx.ui.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.antigravity.shieldx.assistant.AgentApp
import com.antigravity.shieldx.assistant.AgentGraph
import com.antigravity.shieldx.ui.components.Grouped
import com.antigravity.shieldx.ui.components.PrimaryButton
import com.antigravity.shieldx.ui.components.ScreenHeader
import com.antigravity.shieldx.ui.components.SecondaryButton
import com.antigravity.shieldx.ui.components.SectionHeader
import com.antigravity.shieldx.ui.components.StatusChip
import com.antigravity.shieldx.ui.components.StatusTone
import com.antigravity.shieldx.ui.theme.Accent
import com.antigravity.shieldx.ui.theme.Border
import com.antigravity.shieldx.ui.theme.Canvas
import com.antigravity.shieldx.ui.theme.Radius
import com.antigravity.shieldx.ui.theme.Space
import com.antigravity.shieldx.ui.theme.TarziTheme
import com.antigravity.shieldx.ui.theme.TextPrimary
import com.antigravity.shieldx.ui.theme.TextSecondary
import com.antigravity.shieldx.ui.theme.TextTertiary
import kotlinx.coroutines.launch

/**
 * The standalone TaRZI Assistant app.
 *
 * It hosts the same AssistantChatScreen the all-in-one build uses, plus the
 * key entry that used to live on the app's control screen — without a key the
 * assistant would have no backend to reason with.
 */
class AgentActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            TarziTheme {
                var showSettings by remember { mutableStateOf(false) }
                val graph = AgentApp.instance.graph

                Box(Modifier.fillMaxSize()) {
                    AssistantChatScreen(
                        agent = graph,
                        onNavigateToMusic = { openMusicApp() },
                        onNavigateToProtection = { showSettings = true }
                    )

                    if (showSettings) {
                        AgentSettingsScreen(
                            agent = graph,
                            onDismiss = { showSettings = false }
                        )
                    }
                }
            }
        }
    }

    /** The music app is a separate product now; hand off rather than embed. */
    private fun openMusicApp() {
        val pm = packageManager
        val pkg = listOf("com.antigravity.tarzi.music", "com.antigravity.tarzi.music.debug")
            .firstOrNull { runCatching { pm.getPackageInfo(it, 0) }.isSuccess }
        pkg?.let { pm.getLaunchIntentForPackage(it) }?.let { startActivity(it) }
    }
}

/**
 * Keys and status. The assistant is only as capable as the backend it can
 * reach, so this screen reports plainly whether it can reach one.
 */
@Composable
private fun AgentSettingsScreen(agent: AgentGraph, onDismiss: () -> Unit) {

    val scope = rememberCoroutineScope()
    var deepSeek by remember { mutableStateOf("") }
    var gemini by remember { mutableStateOf("") }
    var picovoice by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        deepSeek = agent.configStore.get("deepseek_api_key").orEmpty()
        gemini = agent.configStore.get("gemini_api_key").orEmpty()
        picovoice = agent.configStore.get("picovoice_access_key").orEmpty()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Canvas)
            .verticalScroll(rememberScrollState())
    ) {
        ScreenHeader(
            title = "Assistant settings",
            subtitle = "Keys are stored on this device only.",
            onBack = onDismiss
        )

        SectionHeader("Reasoning backend")
        Grouped {
            KeyField("DeepSeek API key", deepSeek) { deepSeek = it; saved = false }
            KeyField("Gemini API key (optional, image input)", gemini) { gemini = it; saved = false }
        }

        SectionHeader("Voice")
        Grouped {
            KeyField("Picovoice access key (optional wake word)", picovoice) { picovoice = it; saved = false }
        }

        Spacer(Modifier.height(Space.lg))
        Box(Modifier.padding(horizontal = Space.gutter)) {
            PrimaryButton(
                text = if (saved) "Saved" else "Save keys",
                onClick = {
                    scope.launch {
                        agent.configStore.set("deepseek_api_key", deepSeek.trim())
                        agent.configStore.set("gemini_api_key", gemini.trim())
                        agent.configStore.set("picovoice_access_key", picovoice.trim())
                        saved = true
                    }
                }
            )
        }

        Spacer(Modifier.height(Space.section))
    }
}

@Composable
private fun KeyField(label: String, value: String, onChange: (String) -> Unit) {
    Column(Modifier.padding(Space.lg)) {
        Row {
            Text(label, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Spacer(Modifier.fillMaxWidth(0.02f))
            StatusChip(
                text = if (value.isBlank()) "Not set" else "Set",
                tone = if (value.isBlank()) StatusTone.Neutral else StatusTone.Positive
            )
        }
        Spacer(Modifier.height(Space.sm))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            placeholder = { Text("Paste key", color = TextTertiary) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            shape = Radius.sm,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent,
                unfocusedBorderColor = Border,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextSecondary,
                cursorColor = Accent
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
