package com.antigravity.shieldx.music

import com.antigravity.shieldx.core.model.RepeatMode
import com.antigravity.shieldx.core.model.Track

data class Artist(
    val id: String,
    val name: String,
    val thumbnailUrl: String = ""
)

data class Album(
    val id: String,
    val title: String,
    val artist: String,
    val year: String = "",
    val thumbnailUrl: String = "",
    val tracks: List<Track> = emptyList()
)

data class Playlist(
    val id: String,
    val title: String,
    val description: String = "",
    val author: String = "TaRZI Music",
    val thumbnailUrl: String = "",
    val trackCount: Int = 0,
    val tracks: List<Track> = emptyList()
)

data class ExploreSection(
    val title: String,
    val subtitle: String = "",
    val items: List<Track> = emptyList(),
    val playlists: List<Playlist> = emptyList()
)

data class FullPlaybackState(
    val isPlaying: Boolean = false,
    val currentTrack: Track? = null,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isBuffering: Boolean = false,
    val queue: List<Track> = emptyList(),
    val queueIndex: Int = 0,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val isLiked: Boolean = false,
    val lyrics: List<LyricLine> = emptyList(),
    val error: String? = null
)
