package com.antigravity.shieldx.ui.blocked

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.antigravity.shieldx.learning.EnglishLessons
import com.antigravity.shieldx.learning.Lesson
import com.antigravity.shieldx.learning.LessonKind
import com.antigravity.shieldx.learning.LearningProgress
import com.antigravity.shieldx.ui.components.*
import com.antigravity.shieldx.ui.theme.*

/**
 * What replaces "this site can't be reached".
 *
 * The browser's error page is a dead end that invites a workaround. This is the
 * same moment used differently: the request is still blocked, but the screen
 * hands back something worth the interruption and then gets out of the way.
 *
 * What it deliberately does not do: name the site, say what category it was,
 * count slips, or say anything about willpower. Shame is what makes people
 * uninstall a blocker. The site is simply not mentioned.
 */
class BlockInterceptActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val progress = LearningProgress(this)
        val lesson = EnglishLessons.forMoment(progress.seenIds())
        progress.markSeen(lesson.id)
        progress.noteShown()

        setContent {
            TarziTheme {
                BlockInterceptScreen(
                    lesson = lesson,
                    learnedCount = progress.learnedCount(),
                    dayNumber = progress.daysSinceStart(),
                    onClose = { finish() }
                )
            }
        }
    }
}

@Composable
fun BlockInterceptScreen(
    lesson: Lesson,
    learnedCount: Int,
    dayNumber: Int,
    onClose: () -> Unit
) {
    var revealed by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Canvas)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.gutter)
    ) {
        Spacer(Modifier.height(Space.section))

        // No site name, no category, no judgement - just a door closing quietly.
        Text(
            text = "Not this one.",
            style = MaterialTheme.typography.headlineLarge,
            color = TextPrimary
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            text = "Here's thirty seconds of English instead.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary
        )

        Spacer(Modifier.height(Space.xxl))

        // The lesson ------------------------------------------------------
        Column(
            Modifier
                .fillMaxWidth()
                .clip(Radius.md)
                .background(Surface)
                .padding(Space.xl)
        ) {
            StatusChip(
                text = when (lesson.kind) {
                    LessonKind.Word -> "Word"
                    LessonKind.Idiom -> "Phrase"
                    LessonKind.Story -> "Short read"
                },
                tone = StatusTone.Neutral
            )
            Spacer(Modifier.height(Space.md))
            Text(
                text = lesson.headline,
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )
            Spacer(Modifier.height(Space.sm))
            Text(
                text = lesson.meaning,
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )
            Spacer(Modifier.height(Space.lg))
            Text(
                text = lesson.example,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary
            )
        }

        Spacer(Modifier.height(Space.lg))

        // Optional self-check. Nothing depends on getting it right.
        if (!revealed) {
            SecondaryButton(
                text = "Quick check",
                onClick = { revealed = true },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(Radius.md)
                    .background(SurfaceRaised)
                    .padding(Space.lg)
            ) {
                Text(
                    text = lesson.question,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Spacer(Modifier.height(Space.sm))
                Text(
                    text = lesson.options[lesson.correctIndex],
                    style = MaterialTheme.typography.bodyLarge,
                    color = Success
                )
            }
        }

        Spacer(Modifier.height(Space.section))

        // What they have accumulated. This number never goes down.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (learnedCount == 1) "1 learned" else "$learnedCount learned",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
                Text(
                    text = "Day $dayNumber",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTertiary
                )
            }
        }

        Spacer(Modifier.height(Space.lg))
        PrimaryButton(text = "Close", onClick = onClose)
        Spacer(Modifier.height(Space.section))
    }
}
