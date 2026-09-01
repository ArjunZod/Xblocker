package com.antigravity.shieldx.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.antigravity.shieldx.core.model.MusicSource
import com.antigravity.shieldx.core.model.PlaybackCommand
import com.antigravity.shieldx.core.model.Track
import com.antigravity.shieldx.music.MusicGraph
import com.antigravity.shieldx.music.ExploreSection
import com.antigravity.shieldx.ui.components.*
import com.antigravity.shieldx.ui.theme.*
import kotlinx.coroutines.launch

/**
 * A music screen where the artwork is the interface.
 *
 * No outlines around album art, no metadata competing with the cover, and no
 * fabricated catalogue: sections come from the live extractor, and when it
 * returns nothing the screen says so rather than showing invented songs.
 */
@Composable
fun MusicHomeScreen(
    musicGraph: MusicGraph,
    onOpenFullPlayer: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }

    var sections by remember { mutableStateOf<List<ExploreSection>>(emptyList()) }
    var isLoadingSections by remember { mutableStateOf(true) }

    // Recently played comes from the database, so it survives restarts and
    // updates the moment a track actually starts playing.
    val recent by musicGraph.playHistoryRepository.recentFlow
        .collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        isLoadingSections = true
        sections = try {
            musicGraph.innerTubeClient.getExploreSections()
                .filter { it.items.isNotEmpty() }
        } catch (_: Exception) {
            emptyList()
        }
        isLoadingSections = false
    }

    fun search(q: String) {
        if (q.isBlank()) {
            results = emptyList()
            hasSearched = false
            return
        }
        isSearching = true
        hasSearched = true
        scope.launch {
            results = try {
                musicGraph.musicSearchEngine.search(q)
            } catch (_: Exception) {
                emptyList()
            }
            isSearching = false
        }
    }

    fun play(track: Track) {
        scope.launch {
            onOpenFullPlayer()
            musicGraph.musicController.play(PlaybackCommand.PlayTrack(track))
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Canvas),
        contentPadding = ContentBottomPadding,
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {

        item { ScreenHeader(title = "Music") }

        item {
            SearchField(
                value = query,
                onValueChange = { query = it },
                onSearch = { search(query) },
                onClear = {
                    query = ""
                    results = emptyList()
                    hasSearched = false
                }
            )
        }

        // Search results replace browsing while a query is active.
        if (hasSearched) {
            when {
                isSearching -> item { LoadingRow() }

                results.isEmpty() -> item {
                    StateMessage(
                        title = "No results",
                        description = "Nothing matched \"$query\". Try a different search.",
                        icon = Icons.Default.Search
                    )
                }

                else -> {
                    item { SectionHeader("Results") }
                    items(results, key = { "${it.source.name}_${it.id}_${it.title}" }) { track ->
                        TrackRow(track = track, onClick = { play(track) })
                    }
                }
            }
        } else {
            // Recently played first: it is the section a returning user wants,
            // and it renders from local data so the screen has content even
            // when the catalogue request fails.
            if (recent.isNotEmpty()) {
                item(key = "h_recent") { SectionHeader("Recently played") }
                item(key = "r_recent") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = Space.gutter),
                        horizontalArrangement = Arrangement.spacedBy(Space.md)
                    ) {
                        items(recent, key = { it.trackId }) { entry ->
                            val track = entry.toTrack()
                            AlbumCard(track = track, onClick = { play(track) })
                        }
                    }
                }
            }

            when {
                isLoadingSections -> item { LoadingRow() }

                sections.isEmpty() && recent.isEmpty() -> item {
                    StateMessage(
                        title = "Nothing to show yet",
                        description = "Search for a song, artist, or album to start listening.",
                        icon = Icons.Default.MusicNote
                    )
                }

                sections.isEmpty() -> item {
                    // History is present but the catalogue call failed. Say so
                    // rather than silently showing a short page.
                    StateMessage(
                        title = "Could not load recommendations",
                        description = "Check your connection, or search for something specific.",
                        icon = Icons.Default.MusicNote
                    )
                }

                else -> sections.forEach { section ->
                    item(key = "h_" + section.title) { SectionHeader(section.title) }
                    item(key = "r_" + section.title) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = Space.gutter),
                            horizontalArrangement = Arrangement.spacedBy(Space.md)
                        ) {
                            items(section.items, key = { it.id }) { track ->
                                AlbumCard(track = track, onClick = { play(track) })
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(Space.section)) }
    }
}

// ============================================================================

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit
) {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter)
            .clip(Radius.full)
            .background(SurfaceRaised)
            .border(1.dp, Border, Radius.full)
            .padding(horizontal = Space.lg, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            tint = TextTertiary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(Space.md))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = "Songs, artists, albums",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextTertiary
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary),
                cursorBrush = SolidColor(Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    onSearch()
                }),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (value.isNotEmpty()) {
            Spacer(Modifier.width(Space.sm))
            Icon(
                Icons.Default.Close,
                contentDescription = "Clear",
                tint = TextTertiary,
                modifier = Modifier
                    .size(18.dp)
                    .clickable {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        onClear()
                    }
            )
        }
    }
}

/** Artwork first, title and artist beneath. No border competing with the art. */
@Composable
private fun AlbumCard(track: Track, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(150.dp)
            .clickable(onClick = onClick)
    ) {
        Artwork(
            url = track.thumbnailUrl,
            modifier = Modifier
                .size(150.dp)
                .clip(Radius.sm)
        )
        Spacer(Modifier.height(Space.sm))
        Text(
            text = track.title,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            maxLines = 1
        )
        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            maxLines = 1
        )
    }
}

@Composable
private fun TrackRow(track: Track, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Space.gutter, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(
            url = track.thumbnailUrl,
            modifier = Modifier
                .size(52.dp)
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
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SourceBadge(source = track.source)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 1
                )
            }
        }
        if (track.durationSeconds > 0) {
            Spacer(Modifier.width(Space.sm))
            Text(
                text = formatDuration(track.durationSeconds),
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary
            )
        }
    }
}

@Composable
fun SourceBadge(source: MusicSource, modifier: Modifier = Modifier) {
    val (label, bg, fg) = when (source) {
        MusicSource.YOUTUBE_MUSIC -> Triple("YouTube", androidx.compose.ui.graphics.Color(0xFFE50914).copy(alpha = 0.18f), androidx.compose.ui.graphics.Color(0xFFFF4B4B))
        MusicSource.SPOTIFY -> Triple("Spotify", androidx.compose.ui.graphics.Color(0xFF1DB954).copy(alpha = 0.18f), androidx.compose.ui.graphics.Color(0xFF1ED760))
        MusicSource.DEEZER -> Triple("Deezer", androidx.compose.ui.graphics.Color(0xFFA238FF).copy(alpha = 0.18f), androidx.compose.ui.graphics.Color(0xFFC084FC))
        MusicSource.LAVALINK -> Triple("Lavalink", androidx.compose.ui.graphics.Color(0xFF3B82F6).copy(alpha = 0.18f), androidx.compose.ui.graphics.Color(0xFF60A5FA))
        MusicSource.CUSTOM_URL -> Triple("Stream", androidx.compose.ui.graphics.Color(0xFF10B981).copy(alpha = 0.18f), androidx.compose.ui.graphics.Color(0xFF34D399))
    }

    Box(
        modifier = modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp),
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            ),
            color = fg
        )
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
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun LoadingRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Space.section),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = TextTertiary
        )
    }
}

private fun formatDuration(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}
