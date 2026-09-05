package com.antigravity.shieldx.ui.blocked

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.shieldx.learning.*
import com.antigravity.shieldx.ui.components.*
import com.antigravity.shieldx.ui.theme.*
import kotlinx.coroutines.delay

/**
 * What replaces "this site can't be reached".
 *
 * Turns the impulse moment into an educational and psychological reset:
 * - XP lands with animated level bar
 * - Interactive 4-4-4 Box Breathing coach provides a somatic circuit-breaker
 * - An English power word is learned
 * - Breaks the compulsion cycle with dignity and positive reinforcement.
 */
class BlockInterceptActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val rewards = RewardEngine(this)
        val progress = LearningProgress(this)

        // Record resist and fetch next educational micro-lesson
        val xpGained = rewards.recordResist()
        val lesson = EnglishLessons.forMoment(progress.seenIds())
        progress.markSeen(lesson.id)
        progress.noteShown()
        rewards.recordWordLearned()

        val meme = MemeCards.forMoment(rewards.level())

        setContent {
            TarziTheme {
                RewardScreen(
                    meme = meme,
                    lesson = lesson,
                    rewards = rewards,
                    xpGained = xpGained,
                    onClose = { finish() }
                )
            }
        }
    }
}

@Composable
private fun RewardScreen(
    meme: MemeCard,
    lesson: Lesson,
    rewards: RewardEngine,
    xpGained: Int,
    onClose: () -> Unit
) {
    val (into, cost) = rewards.levelProgress()
    val target = (into.toFloat() / cost.toFloat()).coerceIn(0f, 1f)

    var animate by remember { mutableStateOf(false) }
    val fill by animateFloatAsState(
        targetValue = if (animate) target else (target - xpGained.toFloat() / cost).coerceAtLeast(0f),
        animationSpec = tween(900),
        label = "xp"
    )
    LaunchedEffect(Unit) { animate = true }

    var quizOpen by remember { mutableStateOf(false) }
    var quizResult by remember { mutableStateOf<Boolean?>(null) }
    val dailyBonus = remember { rewards.claimDailyIfEarned() }

    Column(
        Modifier
            .fillMaxSize()
            .background(Canvas)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.gutter)
    ) {
        Spacer(Modifier.height(Space.lg))

        // Level + XP Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "LVL ${rewards.level()}",
                style = MaterialTheme.typography.titleLarge,
                color = Accent
            )
            Spacer(Modifier.width(Space.sm))
            Text(
                text = rewards.rank().uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = TextTertiary
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (xpGained > 0) "+$xpGained XP" else "DEFENSE ACTIVE",
                style = MaterialTheme.typography.titleLarge,
                color = Success
            )
        }
        Spacer(Modifier.height(Space.sm))

        // Level Progress Bar
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(Radius.full)
                .background(SurfaceRaised)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fill)
                    .height(10.dp)
                    .clip(Radius.full)
                    .background(Brush.horizontalGradient(listOf(Accent, AccentWarm)))
            )
        }
        Spacer(Modifier.height(Space.xs))
        Text(
            text = "$into / $cost to level ${rewards.level() + 1}",
            style = MaterialTheme.typography.bodySmall,
            color = TextTertiary
        )

        Spacer(Modifier.height(Space.xl))

        // The Humor / Meme Break
        Column(
            Modifier
                .fillMaxWidth()
                .clip(Radius.md)
                .background(Brush.verticalGradient(listOf(SurfaceHigh, Surface)))
                .border(1.dp, BorderStrong, Radius.md)
                .padding(Space.xl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(com.antigravity.shieldx.uikit.R.drawable.il_burst_block),
                contentDescription = null,
                modifier = Modifier.size(148.dp)
            )
            Spacer(Modifier.height(Space.sm))
            Text(
                text = meme.punch,
                style = MaterialTheme.typography.headlineLarge,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(Space.sm))
            Text(
                text = meme.sub,
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(Space.lg))

        // Interactive 4-4-4 Box Breathing Somatic Coach
        BoxBreathingCoach(
            onResetCompleted = {
                rewards.recordCorrectAnswer() // Bonus XP for mindful reset
            }
        )

        Spacer(Modifier.height(Space.lg))

        // English Micro-Lesson Card
        Column(
            Modifier
                .fillMaxWidth()
                .clip(Radius.md)
                .background(Surface)
                .border(1.dp, Border, Radius.md)
                .padding(Space.lg)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusChip(
                    text = when (lesson.kind) {
                        LessonKind.Word -> "POWER WORD"
                        LessonKind.Idiom -> "IDIOM"
                        LessonKind.Story -> "PERSPECTIVE"
                    },
                    tone = StatusTone.Neutral
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "+20 XP",
                    style = MaterialTheme.typography.labelSmall,
                    color = Accent
                )
            }
            Spacer(Modifier.height(Space.sm))
            Text(
                text = lesson.headline,
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                text = lesson.meaning,
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )
            Spacer(Modifier.height(Space.md))
            Text(
                text = "\"${lesson.example}\"",
                style = MaterialTheme.typography.bodyMedium,
                color = TextTertiary
            )

            Spacer(Modifier.height(Space.md))

            // Interactive Quiz
            if (!quizOpen && quizResult == null) {
                GhostButton(
                    text = "Test yourself (+15 XP)",
                    onClick = { quizOpen = true },
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (quizOpen) {
                Text(
                    text = lesson.question,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Spacer(Modifier.height(Space.sm))
                lesson.options.forEachIndexed { index, option ->
                    val correct = index == lesson.correctIndex
                    GhostButton(
                        text = option,
                        onClick = {
                            quizOpen = false
                            quizResult = correct
                            if (correct) rewards.recordCorrectAnswer()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(Space.xs))
                }
            } else if (quizResult == true) {
                Text(
                    text = "Correct! +15 XP added.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Success
                )
            } else {
                Text(
                    text = "Nice try! Review the meaning above.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTertiary
                )
            }
        }

        if (dailyBonus > 0) {
            Spacer(Modifier.height(Space.md))
            Text(
                text = "Daily Challenge complete! +$dailyBonus XP",
                style = MaterialTheme.typography.bodyMedium,
                color = Success,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(Space.xl))

        PrimaryButton(
            text = "Back to Safety",
            onClick = onClose,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(Space.section))
    }
}

/**
 * Interactive Box Breathing Coach:
 * Cycles through Inhale (4s) -> Hold (4s) -> Exhale (4s) -> Rest (4s)
 * with a smoothly expanding/contracting sphere to ground the user.
 */
@Composable
private fun BoxBreathingCoach(
    onResetCompleted: () -> Unit
) {
    var isActive by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf("Ready to reset?") }
    var stepSeconds by remember { mutableIntStateOf(4) }
    var cyclesCompleted by remember { mutableIntStateOf(0) }

    // Breathing scale animation
    var targetScale by remember { mutableFloatStateOf(1f) }
    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(durationMillis = 3800, easing = LinearEasing),
        label = "breathScale"
    )

    LaunchedEffect(isActive) {
        if (!isActive) {
            targetScale = 1f
            phase = "Tap circle to start 30s Mind Reset"
            return@LaunchedEffect
        }

        while (isActive && cyclesCompleted < 2) {
            // Inhale (4s)
            phase = "Inhale slowly..."
            targetScale = 1.35f
            for (sec in 4 downTo 1) {
                stepSeconds = sec
                delay(1000)
            }

            // Hold (4s)
            phase = "Hold..."
            for (sec in 4 downTo 1) {
                stepSeconds = sec
                delay(1000)
            }

            // Exhale (4s)
            phase = "Exhale gently..."
            targetScale = 1.0f
            for (sec in 4 downTo 1) {
                stepSeconds = sec
                delay(1000)
            }

            // Rest (4s)
            phase = "Rest & clear mind..."
            for (sec in 4 downTo 1) {
                stepSeconds = sec
                delay(1000)
            }

            cyclesCompleted++
        }

        if (cyclesCompleted >= 2) {
            phase = "Reset Complete! Mind cleared."
            isActive = false
            onResetCompleted()
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(Radius.md)
            .background(Surface)
            .border(1.dp, Border, Radius.md)
            .padding(Space.lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusChip(
                text = "SOMATIC RESET",
                tone = StatusTone.Positive
            )
            if (isActive) {
                Text(
                    text = "${stepSeconds}s",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Accent
                )
            }
        }

        Spacer(Modifier.height(Space.md))

        // Animated Breathing Sphere
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(120.dp)
                .clickable {
                    if (!isActive) {
                        isActive = true
                        cyclesCompleted = 0
                    }
                }
        ) {
            // Glow halo
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .scale(animatedScale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                if (isActive) Accent.copy(alpha = 0.35f) else SurfaceRaised,
                                Canvas.copy(alpha = 0.1f)
                            )
                        )
                    )
            )
            // Core breathing ball
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(70.dp)
                    .scale(animatedScale)
                    .clip(CircleShape)
                    .background(if (isActive) Accent else SurfaceHigh)
                    .border(1.dp, BorderStrong, CircleShape)
            ) {
                Text(
                    text = if (isActive) "$stepSeconds" else "START",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) Canvas else TextPrimary
                )
            }
        }

        Spacer(Modifier.height(Space.md))

        Text(
            text = phase,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            text = "Dopamine craving peaks within 90 seconds. A few deep breaths break the reflex.",
            style = MaterialTheme.typography.bodySmall,
            color = TextTertiary,
            textAlign = TextAlign.Center
        )
    }
}
