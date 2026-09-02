package com.antigravity.shieldx.ui.blocked

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.antigravity.shieldx.learning.EnglishLessons
import com.antigravity.shieldx.learning.LearningProgress
import com.antigravity.shieldx.ui.theme.*
import kotlinx.coroutines.delay

/**
 * The pause between deciding to switch the filter off and it actually going off.
 *
 * The point is not to trap anyone - it is the user's device, and the admin PIN
 * still turns everything off immediately. The point is that the decision to
 * disable is almost always made in about four seconds, and this puts a short,
 * mildly useful obstacle in the middle of those four seconds.
 *
 * It asks a question about a word the user has already been shown, so it is
 * answerable and takes a few seconds, not a puzzle designed to defeat them.
 * Getting it wrong costs nothing but another question.
 */
@Composable
fun DisableGate(
    onDismiss: () -> Unit,
    onConfirmed: () -> Unit
) {
    val context = LocalContext.current
    val progress = remember { LearningProgress(context) }

    var attempt by remember { mutableIntStateOf(0) }
    val lesson = remember(attempt) {
        EnglishLessons.quizFrom(progress.seenIds(), System.currentTimeMillis() + attempt * 977L)
    }
    var wrong by remember(attempt) { mutableStateOf(false) }

    // A short wait before the answer can be submitted at all. Most of the value
    // of this screen is the few seconds it costs, not the question itself.
    var secondsLeft by remember { mutableIntStateOf(5) }
    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1000)
            secondsLeft -= 1
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = {
            Text(
                text = "Before you turn it off",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
        },
        text = {
            Column {
                Text(
                    text = "One question from what you've picked up. Get it right and " +
                        "filtering goes off.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(Space.lg))

                Text(
                    text = lesson.question,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Spacer(Modifier.height(Space.md))

                lesson.options.forEachIndexed { index, option ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = Space.sm)
                            .clip(Radius.sm)
                            .background(SurfaceRaised)
                            .border(1.dp, Border, Radius.sm)
                            .clickable(enabled = secondsLeft == 0) {
                                if (index == lesson.correctIndex) {
                                    progress.markAnsweredCorrectly()
                                    onConfirmed()
                                } else {
                                    wrong = true
                                }
                            }
                            .padding(Space.md)
                    ) {
                        Text(
                            text = option,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (secondsLeft == 0) TextPrimary else TextTertiary
                        )
                    }
                }

                if (secondsLeft > 0) {
                    Text(
                        text = "Answers unlock in ${secondsLeft}s",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTertiary
                    )
                }

                if (wrong) {
                    Text(
                        text = "Not that one. Here's another.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Warning
                    )
                    LaunchedEffect(wrong) {
                        delay(900)
                        attempt += 1
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Keep it on",
                    color = Accent,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    )
}
