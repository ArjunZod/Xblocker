package com.antigravity.shieldx.ui.blocked

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.antigravity.shieldx.R
import com.antigravity.shieldx.learning.*
import com.antigravity.shieldx.ui.components.*
import com.antigravity.shieldx.ui.theme.*

/**
 * What replaces "this site can't be reached".
 *
 * The browser's error page is a dead end that invites a workaround. This is the
 * same moment turned into the only place in the app where the user earns
 * something: XP lands, a level bar moves, a meme lands, and a track is one tap
 * away in the music app.
 *
 * What it never does: name the site, name the category, count slips, or imply
 * anything about the person. Shame is what gets blockers uninstalled. The joke
 * is always aimed at the moment, never at them.
 */
class BlockInterceptActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val rewards = RewardEngine(this)
        val progress = LearningProgress(this)

        // The block already happened; this is the payout for surviving it.
        val xpGained = rewards.recordResist()
        val lesson = EnglishLessons.forMoment(progress.seenIds())
        progress.markSeen(lesson.id)
        progress.noteShown()
        rewards.recordWordLearned()

        val meme = MemeCards.forMoment(rewards.level())
        val song = SongRewards.forMoment()

        setContent {
            TarziTheme {
                RewardScreen(
                    meme = meme,
                    song = song,
                    lesson = lesson,
                    rewards = rewards,
                    xpGained = xpGained,
                    onPlaySong = { playInMusicApp(song, rewards) },
                    onClose = { finish() }
                )
            }
        }
    }

    /**
     * Hands the track to the TaRZI Music app. The blocker deliberately contains
     * no music engine; if the music app is not installed the row simply says so
     * rather than pretending it can play.
     */
    private fun playInMusicApp(song: SongReward, rewards: RewardEngine): Boolean {
        val pkg = listOf("com.antigravity.tarzi.music", "com.antigravity.tarzi.music.debug")
            .firstOrNull { runCatching { packageManager.getPackageInfo(it, 0) }.isSuccess }
            ?: return false

        return runCatching {
            startActivity(
                Intent("com.antigravity.tarzi.music.action.PLAY_QUERY").apply {
                    setPackage(pkg)
                    putExtra("query", song.query)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            rewards.recordSongPlayed()
            true
        }.getOrDefault(false)
    }
}

@Composable
private fun RewardScreen(
    meme: MemeCard,
    song: SongReward,
    lesson: Lesson,
    rewards: RewardEngine,
    xpGained: Int,
    onPlaySong: () -> Boolean,
    onClose: () -> Unit
) {
    val (into, cost) = rewards.levelProgress()
    val target = (into.toFloat() / cost.toFloat()).coerceIn(0f, 1f)

    // The bar animates up from where it was, so the XP visibly lands.
    var animate by remember { mutableStateOf(false) }
    val fill by animateFloatAsState(
        targetValue = if (animate) target else (target - xpGained.toFloat() / cost).coerceAtLeast(0f),
        animationSpec = tween(900),
        label = "xp"
    )
    LaunchedEffect(Unit) { animate = true }

    var songStarted by remember { mutableStateOf(false) }
    var songUnavailable by remember { mutableStateOf(false) }
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

        // Level + XP ---------------------------------------------------------
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
                text = "+$xpGained XP",
                style = MaterialTheme.typography.titleLarge,
                color = Success
            )
        }
        Spacer(Modifier.height(Space.sm))
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

        // The meme -----------------------------------------------------------
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

        // Reward track -------------------------------------------------------
        Column(
            Modifier
                .fillMaxWidth()
                .clip(Radius.md)
                .background(Surface)
                .border(1.dp, Border, Radius.md)
                .clickable(enabled = !songStarted) {
                    if (onPlaySong()) songStarted = true else songUnavailable = true
                }
                .padding(Space.lg)
        ) {
            StatusChip(
                text = if (songStarted) "PLAYING" else "REWARD TRACK",
                tone = if (songStarted) StatusTone.Positive else StatusTone.Neutral
            )
            Spacer(Modifier.height(Space.sm))
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                text = when {
                    songUnavailable -> "Install TaRZI Music to cash this in."
                    songStarted -> "Turn it up."
                    else -> "${song.why}  ·  Tap to play"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (songUnavailable) Warning else TextTertiary
            )
        }

        Spacer(Modifier.height(Space.lg))

        // Word / story -------------------------------------------------------
        Column(
            Modifier
                .fillMaxWidth()
                .clip(Radius.md)
                .background(Surface)
                .padding(Space.lg)
        ) {
            StatusChip(
                text = when (lesson.kind) {
                    LessonKind.Word -> "WORD BANKED"
                    LessonKind.Idiom -> "PHRASE BANKED"
                    LessonKind.Story -> "STORY UNLOCKED"
                },
                tone = StatusTone.Neutral
            )
            Spacer(Modifier.height(Space.sm))
            Text(
                text = lesson.headline,
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                text = lesson.meaning,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(Modifier.height(Space.md))
            Text(
                text = lesson.example,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary
            )

            Spacer(Modifier.height(Space.md))
            when (quizResult) {
                null -> if (!quizOpen) {
                    SecondaryButton(
                        text = "Go for +${RewardEngine.XP_CORRECT_ANSWER} XP",
                        onClick = { quizOpen = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = lesson.question,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(Space.sm))
                    lesson.options.forEachIndexed { index, option ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = Space.sm)
                                .clip(Radius.sm)
                                .background(SurfaceRaised)
                                .clickable {
                                    val right = index == lesson.correctIndex
                                    if (right) rewards.recordCorrectAnswer()
                                    quizResult = right
                                }
                                .padding(Space.md)
                        ) {
                            Text(option, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                        }
                    }
                }

                true -> Text(
                    text = "NAILED IT. +${RewardEngine.XP_CORRECT_ANSWER} XP",
                    style = MaterialTheme.typography.titleMedium,
                    color = Success
                )

                false -> Text(
                    text = "Not that one — it's \"${lesson.options[lesson.correctIndex]}\". Banked anyway.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Warning
                )
            }
        }

        Spacer(Modifier.height(Space.lg))

        // Daily challenges ---------------------------------------------------
        Column(
            Modifier
                .fillMaxWidth()
                .clip(Radius.md)
                .background(Surface)
                .padding(Space.lg)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "TODAY'S RUN",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary
                )
                Spacer(Modifier.weight(1f))
                if (rewards.streakDays() > 1) {
                    Icon(
                        painter = painterResource(R.drawable.ic_tz_flame),
                        contentDescription = null,
                        tint = AccentWarm,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${rewards.streakDays()} day streak",
                        style = MaterialTheme.typography.labelMedium,
                        color = AccentWarm
                    )
                }
            }
            Spacer(Modifier.height(Space.md))
            rewards.challenges().forEach { c ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(
                            if (c.complete) R.drawable.ic_tz_check else R.drawable.ic_tz_circle
                        ),
                        contentDescription = null,
                        tint = if (c.complete) Success else TextTertiary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(Space.sm))
                    Text(
                        text = c.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (c.complete) TextPrimary else TextSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${c.done}/${c.target}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTertiary
                    )
                }
            }
            if (dailyBonus > 0) {
                Spacer(Modifier.height(Space.sm))
                Text(
                    text = "DAILY CLEARED. +$dailyBonus XP",
                    style = MaterialTheme.typography.titleMedium,
                    color = Success
                )
            }
            Spacer(Modifier.height(Space.sm))
            Text(
                text = rewards.unlockedPackLabel(),
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary
            )
        }

        Spacer(Modifier.height(Space.xl))
        PrimaryButton(text = "Back to it", onClick = onClose)
        Spacer(Modifier.height(Space.section))
    }
}
