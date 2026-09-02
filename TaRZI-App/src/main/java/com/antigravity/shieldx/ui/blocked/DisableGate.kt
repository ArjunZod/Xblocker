package com.antigravity.shieldx.ui.blocked

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.antigravity.shieldx.R
import com.antigravity.shieldx.learning.EnglishLessons
import com.antigravity.shieldx.learning.LearningProgress
import com.antigravity.shieldx.learning.RewardEngine
import com.antigravity.shieldx.ui.components.PrimaryButton
import com.antigravity.shieldx.ui.theme.*
import kotlinx.coroutines.delay

/**
 * The pause between deciding to switch the filter off and it actually going off.
 *
 * Full screen rather than a dialog, because a dialog reads as a system nag to
 * dismiss, and this needs to carry the same weight as the reward screen - it is
 * the other half of the same loop.
 *
 * It is not a trap. It is the user's device, the admin PIN still turns
 * everything off immediately, and a wrong answer only costs another question.
 * What it does is show the size of what is being put down - level, XP, streak -
 * during the few seconds when that is the relevant information.
 */
@Composable
fun DisableGate(
    onDismiss: () -> Unit,
    onConfirmed: () -> Unit
) {
    val context = LocalContext.current
    val progress = remember { LearningProgress(context) }
    val rewards = remember { RewardEngine(context) }

    var attempt by remember { mutableIntStateOf(0) }
    val lesson = remember(attempt) {
        EnglishLessons.quizFrom(progress.seenIds(), System.currentTimeMillis() + attempt * 977L)
    }
    var wrong by remember(attempt) { mutableStateOf(false) }

    var secondsLeft by remember { mutableIntStateOf(5) }
    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1000)
            secondsLeft -= 1
        }
    }

    BackHandler(onBack = onDismiss)

    Column(
        Modifier
            .fillMaxSize()
            .background(Canvas)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.gutter)
    ) {
        Spacer(Modifier.height(Space.section))

        Icon(
            painter = painterResource(R.drawable.ic_tz_stop),
            contentDescription = null,
            tint = AccentWarm,
            modifier = Modifier.size(44.dp)
        )
        Spacer(Modifier.height(Space.lg))

        Text(
            text = "HOLD UP",
            style = MaterialTheme.typography.headlineLarge,
            color = TextPrimary
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            text = "You've banked this one already. Prove it and the filter comes down.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary
        )

        Spacer(Modifier.height(Space.xl))

        // What is on the table right now.
        Row(
            Modifier
                .fillMaxWidth()
                .clip(Radius.md)
                .background(Brush.horizontalGradient(listOf(SurfaceHigh, Surface)))
                .border(1.dp, BorderStrong, Radius.md)
                .padding(Space.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "LVL ${rewards.level()}  ·  ${rewards.rank().uppercase()}",
                    style = MaterialTheme.typography.titleLarge,
                    color = Accent
                )
                Text(
                    text = "${rewards.xp()} XP  ·  ${rewards.totalResists()} shut down",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            if (rewards.streakDays() > 1) {
                Icon(
                    painter = painterResource(R.drawable.ic_tz_flame),
                    contentDescription = null,
                    tint = AccentWarm,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "${rewards.streakDays()}",
                    style = MaterialTheme.typography.titleLarge,
                    color = AccentWarm
                )
            }
        }

        Spacer(Modifier.height(Space.xl))

        Text(
            text = lesson.question,
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary
        )
        Spacer(Modifier.height(Space.md))

        lesson.options.forEachIndexed { index, option ->
            val enabled = secondsLeft == 0
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = Space.sm)
                    .clip(Radius.sm)
                    .background(SurfaceRaised)
                    .border(1.dp, if (enabled) Border else SurfaceRaised, Radius.sm)
                    .clickable(enabled = enabled) {
                        if (index == lesson.correctIndex) {
                            progress.markAnsweredCorrectly()
                            onConfirmed()
                        } else {
                            wrong = true
                        }
                    }
                    .padding(Space.lg)
            ) {
                Text(
                    text = option,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) TextPrimary else TextTertiary
                )
            }
        }

        if (secondsLeft > 0) {
            Text(
                text = "Unlocking in ${secondsLeft}s",
                style = MaterialTheme.typography.bodyMedium,
                color = TextTertiary
            )
        }

        if (wrong) {
            Text(
                text = "Nope. Next one, go.",
                style = MaterialTheme.typography.titleMedium,
                color = Warning
            )
            LaunchedEffect(wrong) {
                delay(900)
                attempt += 1
            }
        }

        Spacer(Modifier.height(Space.section))

        PrimaryButton(text = "NAH, KEEP IT ON", onClick = onDismiss)

        Spacer(Modifier.height(Space.md))
        Text(
            text = "Filtering stays on. Nothing lost.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(Space.section))
    }
}
