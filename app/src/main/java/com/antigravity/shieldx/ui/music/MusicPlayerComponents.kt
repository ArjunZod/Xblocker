package com.antigravity.shieldx.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.antigravity.shieldx.core.model.PlaybackState
import com.antigravity.shieldx.core.model.Track
import com.antigravity.shieldx.core.security.SecurityManager
import com.antigravity.shieldx.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Playback surfaces. Both read the same [PlaybackState], so what the UI claims
 * and what the player is doing cannot drift apart.
 */

/**
 * A slim bar above the navigation. Deliberately quiet: artwork, title, one
 * control. Everything else lives in the full player.
 */
@Composable
fun MiniPlayerBar(
    playbackState: PlaybackState,
    securityManager: SecurityManager,
    onExpandClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val track = playbackState.currentTrack ?: return
    val scope = rememberCoroutineScope()

    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Border)
        )

        // A hairline progress read, not a decorative bar.
        if (playbackState.durationMs > 0) {
            LinearProgressIndicator(
                progress = {
                    (playbackState.currentPositionMs.toFloat() /
                        playbackState.durationMs.toFloat()).coerceIn(0f, 1f)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = Accent,
                trackColor = Surface,
                drawStopIndicator = {}
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface)
                .clickable(onClick = onExpandClick)
                .padding(horizontal = Space.md, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Artwork(
                url = track.thumbnailUrl,
                modifier = Modifier
                    .size(40.dp)
                    .clip(Radius.sm)
            )

            Spacer(Modifier.width(Space.md))

            Column(Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (playbackState.isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = TextSecondary
                    )
                } else {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                        tint = TextPrimary,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable {
                                scope.launch {
                                    if (playbackState.isPlaying) {
                                        securityManager.musicController.pause()
                                    } else {
                                        securityManager.musicController.resume()
                                    }
                                }
                            }
                            .padding(4.dp)
                    )
                }

                Spacer(Modifier.width(Space.xs))

                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss mini player",
                    tint = TextTertiary,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable {
                            scope.launch {
                                securityManager.musicController.stop()
                            }
                        }
                        .padding(4.dp)
                )
            }
        }
    }
}

/**
 * Full-screen playback. Artwork carries the screen; controls sit beneath it in
 * one row, with the scrubber the only other interactive element.
 */
@Composable
fun FullPlayerBottomSheet(
    playbackState: PlaybackState,
    securityManager: SecurityManager,
    onDismiss: () -> Unit
) {
    val track = playbackState.currentTrack ?: return
    val scope = rememberCoroutineScope()

    // Drag state is held separately so the thumb follows the finger instead of
    // fighting position updates streaming in from the player.
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableFloatStateOf(0f) }

    val progress = if (isScrubbing) {
        scrubPosition
    } else if (playbackState.durationMs > 0) {
        (playbackState.currentPositionMs.toFloat() / playbackState.durationMs.toFloat())
            .coerceIn(0f, 1f)
    } else 0f

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Canvas)
                .statusBarsPadding()
                .padding(horizontal = Space.xxl)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = Space.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    tint = TextSecondary,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDismiss)
                        .padding(8.dp)
                )
            }

            Spacer(Modifier.weight(1f))

            Artwork(
                url = track.thumbnailUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(Radius.md)
            )

            Spacer(Modifier.height(Space.section))

            Text(
                text = track.title,
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                maxLines = 2,
                textAlign = TextAlign.Start
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                maxLines = 1
            )

            playbackState.error?.let { error ->
                Spacer(Modifier.height(Space.md))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Danger
                )
            }

            Spacer(Modifier.height(Space.xl))

            Slider(
                value = progress,
                onValueChange = {
                    isScrubbing = true
                    scrubPosition = it
                },
                onValueChangeFinished = {
                    scope.launch {
                        securityManager.musicController.seekTo(
                            (scrubPosition * playbackState.durationMs).toLong()
                        )
                        isScrubbing = false
                    }
                },
                colors = SliderDefaults.colors(
                    thumbColor = TextPrimary,
                    activeTrackColor = TextPrimary,
                    inactiveTrackColor = SurfaceRaised
                )
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime((progress * playbackState.durationMs).toLong()),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
                Text(
                    text = formatTime(playbackState.durationMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }

            Spacer(Modifier.height(Space.xl))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.SkipPrevious,
                    contentDescription = "Previous",
                    tint = TextPrimary,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable { scope.launch { securityManager.musicController.previous() } }
                        .padding(10.dp)
                )

                Spacer(Modifier.width(Space.xl))

                Box(
                    Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(TextPrimary)
                        .clickable {
                            scope.launch {
                                if (playbackState.isPlaying) {
                                    securityManager.musicController.pause()
                                } else {
                                    securityManager.musicController.resume()
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (playbackState.isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = Canvas
                        )
                    } else {
                        Icon(
                            imageVector = if (playbackState.isPlaying) {
                                Icons.Default.Pause
                            } else {
                                Icons.Default.PlayArrow
                            },
                            contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                            tint = Canvas,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                Spacer(Modifier.width(Space.xl))

                Icon(
                    Icons.Default.SkipNext,
                    contentDescription = "Next",
                    tint = TextPrimary,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable { scope.launch { securityManager.musicController.next() } }
                        .padding(10.dp)
                )
            }

            Spacer(Modifier.weight(1f))
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun Artwork(url: String, modifier: Modifier = Modifier) {
    if (url.isNotBlank()) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.background(SurfaceRaised)
        )
    } else {
        Box(modifier.background(SurfaceRaised), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
