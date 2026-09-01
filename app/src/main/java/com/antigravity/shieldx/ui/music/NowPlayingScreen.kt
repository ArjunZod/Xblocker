package com.antigravity.shieldx.ui.music

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lyrics
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.antigravity.shieldx.core.model.PlaybackCommand
import com.antigravity.shieldx.core.model.PlaybackState
import com.antigravity.shieldx.core.model.Track
import com.antigravity.shieldx.core.security.SecurityManager
import com.antigravity.shieldx.music.LyricLine
import com.antigravity.shieldx.ui.components.StateMessage
import com.antigravity.shieldx.ui.theme.*
import kotlinx.coroutines.launch

private enum class NowPlayingTab { Track, Lyrics, Queue }

/**
 * Now Playing.
 *
 * Three views of the same playback state - the track, its lyrics, and the queue -
 * behind one segmented control, so none of them has to be crammed into the
 * others. Everything reads from [PlaybackState]; nothing here keeps its own idea
 * of whether audio is playing.
 */
@Composable
fun NowPlayingScreen(
    playbackState: PlaybackState,
    securityManager: SecurityManager,
    onDismiss: () -> Unit
) {
    val track = playbackState.currentTrack
    val scope = rememberCoroutineScope()

    var tab by remember { mutableStateOf(NowPlayingTab.Track) }
    var lyrics by remember(track?.id) { mutableStateOf<List<LyricLine>?>(null) }
    var lyricsLoading by remember(track?.id) { mutableStateOf(false) }

    // Fetch lyrics once per track, and only when the user asks to see them.
    LaunchedEffect(track?.id, tab) {
        if (track != null && tab == NowPlayingTab.Lyrics && lyrics == null && !lyricsLoading) {
            lyricsLoading = true
            lyrics = securityManager.lyricsService.getLyrics(track.title, track.artist)
            lyricsLoading = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        if (track == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Canvas),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Accent)
            }
            return@Dialog
        }
        Column(
            Modifier
                .fillMaxSize()
                .background(Canvas)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(top = 28.dp, bottom = Space.md)
        ) {
            // Header with Tab Bar --------------------------------------------
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.gutter, vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconCircle(Icons.Default.ExpandMore, "Close", onDismiss)
                Spacer(Modifier.weight(1f))
                TabBar(
                    current = tab,
                    onSelect = { tab = it },
                    modifier = Modifier.width(230.dp)
                )
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(40.dp))
            }

            // Body -----------------------------------------------------------
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (tab) {
                    NowPlayingTab.Track -> TrackView(track)
                    NowPlayingTab.Lyrics -> LyricsView(
                        lyrics = lyrics,
                        isLoading = lyricsLoading,
                        positionMs = playbackState.currentPositionMs
                    )
                    NowPlayingTab.Queue -> QueueView(
                        queue = playbackState.queue,
                        currentIndex = playbackState.queueIndex,
                        onPlayAt = { index ->
                            scope.launch { securityManager.musicController.playQueueItem(index) }
                        },
                        onRemove = { index ->
                            scope.launch { securityManager.musicController.removeQueueItem(index) }
                        },
                        onMove = { from, to ->
                            scope.launch { securityManager.musicController.moveQueueItem(from, to) }
                        },
                        onClearUpcoming = {
                            scope.launch { securityManager.musicController.clearUpcoming() }
                        }
                    )
                }
            }

            // Metadata & Actions --------------------------------------------
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.xxl),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                        maxLines = 1
                    )

                    playbackState.error?.let { error ->
                        Spacer(Modifier.height(Space.xs))
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = Danger,
                            maxLines = 1
                        )
                    }
                }

                var isLiked by remember(track.id) { mutableStateOf(false) }
                LaunchedEffect(track.id) {
                    isLiked = securityManager.musicLibraryRepository.isLiked(track.id)
                }

                IconButton(
                    onClick = {
                        scope.launch {
                            isLiked = securityManager.musicLibraryRepository.toggleLike(track)
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isLiked) "Unlike" else "Like",
                        tint = if (isLiked) Accent else TextTertiary
                    )
                }
            }

            Spacer(Modifier.height(Space.sm))

            Scrubber(
                positionMs = playbackState.currentPositionMs,
                durationMs = playbackState.durationMs,
                onSeek = { scope.launch { securityManager.musicController.seekTo(it) } }
            )

            Spacer(Modifier.height(Space.sm))

            TransportControls(
                isPlaying = playbackState.isPlaying,
                isBuffering = playbackState.isBuffering,
                onPrevious = { scope.launch { securityManager.musicController.previous() } },
                onNext = { scope.launch { securityManager.musicController.next() } },
                onToggle = {
                    scope.launch {
                        if (playbackState.isPlaying) {
                            securityManager.musicController.pause()
                        } else {
                            securityManager.musicController.resume()
                        }
                    }
                }
            )

            Spacer(Modifier.height(Space.xs))
        }
    }
}

// ============================================================================

@Composable
private fun TrackView(track: Track) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(horizontal = Space.xxl),
        contentAlignment = Alignment.Center
    ) {
        if (track.thumbnailUrl.isNotBlank()) {
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxHeight(0.76f)
                    .aspectRatio(1f)
                    .clip(Radius.md)
                    .background(SurfaceRaised)
            )
        } else {
            Box(
                Modifier
                    .fillMaxHeight(0.82f)
                    .aspectRatio(1f)
                    .clip(Radius.md)
                    .background(SurfaceRaised),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
    }
}

/**
 * Synced lyrics with the active line emphasised.
 *
 * A zero timestamp on every line means lrclib only had unsynced lyrics, so the
 * view renders them as a plain readable block rather than pretending to follow
 * along with the music.
 */
@Composable
private fun LyricsView(
    lyrics: List<LyricLine>?,
    isLoading: Boolean,
    positionMs: Long
) {
    val listState = rememberLazyListState()

    val isSynced = lyrics != null && lyrics.size > 1 && lyrics.any { it.timestampMs > 0 }

    val activeIndex = remember(positionMs, lyrics) {
        if (!isSynced || lyrics == null) -1
        else lyrics.indexOfLast { it.timestampMs <= positionMs }.coerceAtLeast(0)
    }

    // Keep the current line near the upper third, where the eye expects it.
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            listState.animateScrollToItem(activeIndex.coerceAtLeast(0), scrollOffset = -160)
        }
    }

    when {
        isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = TextTertiary
            )
        }

        lyrics.isNullOrEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            StateMessage(
                title = "No lyrics found",
                description = "Lyrics are not available for this track.",
                icon = Icons.Default.Lyrics
            )
        }

        else -> LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // Generous top and bottom padding so lines are never clipped by the
            // header or read as running underneath the track metadata below.
            contentPadding = PaddingValues(
                start = Space.xxl,
                end = Space.xxl,
                top = Space.xxl,
                bottom = Space.section
            ),
            verticalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            itemsIndexed(lyrics) { index, line ->
                val isActive = isSynced && index == activeIndex
                val color by animateColorAsState(
                    targetValue = when {
                        !isSynced -> TextSecondary
                        isActive -> TextPrimary
                        index < activeIndex -> TextTertiary
                        else -> TextSecondary
                    },
                    animationSpec = tween(Motion.standard),
                    label = "lyricColor"
                )
                val scale by animateFloatAsState(
                    targetValue = if (isActive) 1f else 0.97f,
                    animationSpec = tween(Motion.standard),
                    label = "lyricScale"
                )

                Text(
                    text = line.text,
                    style = if (isActive) {
                        MaterialTheme.typography.headlineMedium
                    } else {
                        MaterialTheme.typography.titleLarge
                    },
                    color = color,
                    modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
                )
            }
        }
    }
}

@Composable
private fun QueueView(
    queue: List<Track>,
    currentIndex: Int,
    onPlayAt: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onClearUpcoming: () -> Unit
) {
    if (queue.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            StateMessage(
                title = "Queue is empty",
                description = "Tracks you play next will appear here.",
                icon = Icons.AutoMirrored.Filled.QueueMusic
            )
        }
        return
    }

    val hasUpcoming = currentIndex < queue.size - 1

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Space.md)
    ) {
        if (hasUpcoming) {
            item(key = "queue_actions") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.xxl, vertical = Space.sm),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "Clear upcoming",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary,
                        modifier = Modifier
                            .clip(Radius.full)
                            .clickable(onClick = onClearUpcoming)
                            .padding(horizontal = Space.md, vertical = 6.dp)
                    )
                }
            }
        }

        itemsIndexed(queue, key = { _, t -> t.id }) { index, item ->
            val isCurrent = index == currentIndex

            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onPlayAt(index) }
                    .padding(start = Space.xxl, end = Space.md)
                    .padding(vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.thumbnailUrl.isNotBlank()) {
                    AsyncImage(
                        model = item.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(Radius.sm)
                            .background(SurfaceRaised)
                    )
                }

                Spacer(Modifier.width(Space.md))

                Column(Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isCurrent) Accent else TextPrimary,
                        maxLines = 1
                    )
                    Text(
                        text = if (isCurrent) "Playing now" else item.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isCurrent) Accent else TextSecondary,
                        maxLines = 1
                    )
                }

                // Reorder controls, disabled at the ends rather than hidden so
                // rows keep a stable width as the list changes.
                QueueIconButton(
                    icon = Icons.Default.KeyboardArrowUp,
                    description = "Move up",
                    enabled = index > 0,
                    onClick = { onMove(index, index - 1) }
                )
                QueueIconButton(
                    icon = Icons.Default.KeyboardArrowDown,
                    description = "Move down",
                    enabled = index < queue.size - 1,
                    onClick = { onMove(index, index + 1) }
                )
                QueueIconButton(
                    icon = Icons.Default.Close,
                    description = "Remove from queue",
                    enabled = true,
                    onClick = { onRemove(index) }
                )
            }
        }
    }
}

@Composable
private fun QueueIconButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) TextTertiary else TextTertiary.copy(alpha = 0.3f),
            modifier = Modifier.size(18.dp)
        )
    }
}

// ============================================================================

@Composable
private fun Scrubber(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    // Dragging is tracked separately so the thumb follows the finger instead of
    // being yanked back by position updates arriving from the player.
    var isDragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    val fraction = if (isDragging) {
        dragValue
    } else if (durationMs > 0) {
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else 0f

    Column(Modifier.padding(horizontal = Space.xxl)) {
        Slider(
            value = fraction,
            onValueChange = { isDragging = true; dragValue = it },
            onValueChangeFinished = {
                onSeek((dragValue * durationMs).toLong())
                isDragging = false
            },
            colors = SliderDefaults.colors(
                thumbColor = TextPrimary,
                activeTrackColor = TextPrimary,
                inactiveTrackColor = SurfaceHigh
            )
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = formatTime((fraction * durationMs).toLong()),
                style = NumericStyle,
                color = TextTertiary
            )
            Text(text = formatTime(durationMs), style = NumericStyle, color = TextTertiary)
        }
    }
}

@Composable
private fun TransportControls(
    isPlaying: Boolean,
    isBuffering: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggle: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconCircle(Icons.Default.SkipPrevious, "Previous", onPrevious, size = 46.dp)
        Spacer(Modifier.width(Space.xl))

        Box(
            Modifier
                .size(62.dp)
                .clip(CircleShape)
                .background(TextPrimary)
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center
        ) {
            if (isBuffering) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = Canvas
                )
            } else {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Canvas,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(Modifier.width(Space.xl))
        IconCircle(Icons.Default.SkipNext, "Next", onNext, size = 46.dp)
    }
}

@Composable
private fun TabBar(
    current: NowPlayingTab,
    onSelect: (NowPlayingTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .clip(Radius.full)
            .background(SurfaceRaised)
            .border(1.dp, Border, Radius.full)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(Space.xs)
    ) {
        NowPlayingTab.entries.forEach { entry ->
            val selected = entry == current
            Box(
                Modifier
                    .weight(1f)
                    .clip(Radius.full)
                    .background(if (selected) SurfaceHigh else Color.Transparent)
                    .clickable { onSelect(entry) }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) TextPrimary else TextTertiary
                )
            }
        }
    }
}

@Composable
private fun IconCircle(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 40.dp
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = TextPrimary,
            modifier = Modifier.size(size * 0.55f)
        )
    }
}


private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}
