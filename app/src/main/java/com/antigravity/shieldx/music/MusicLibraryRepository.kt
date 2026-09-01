package com.antigravity.shieldx.music

import android.content.Context
import com.antigravity.shieldx.core.model.MusicSource
import com.antigravity.shieldx.core.model.Track
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Manages the user's personal music library:
 * - Liked / Saved Songs
 * - User Playlists (Create, Rename, Delete, Add Track, Remove Track)
 * - Recently Played History
 * - Offline cached metadata
 */
class MusicLibraryRepository(
    private val context: Context,
    private val playHistoryRepository: PlayHistoryRepository? = null
) {

    private val prefs = context.getSharedPreferences("tarzi_music_library", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _likedTracksFlow = MutableStateFlow<List<Track>>(emptyList())
    val likedTracksFlow: Flow<List<Track>> = _likedTracksFlow.asStateFlow()

    private val _playlistsFlow = MutableStateFlow<List<Playlist>>(emptyList())
    val playlistsFlow: Flow<List<Playlist>> = _playlistsFlow.asStateFlow()

    val recentlyPlayedFlow: Flow<List<Track>>? = playHistoryRepository?.recentFlow?.map { list ->
        list.map { it.toTrack() }
    }

    init {
        loadLibrary()
    }

    private fun loadLibrary() {
        try {
            val likedJson = prefs.getString("liked_tracks", "[]") ?: "[]"
            val trackListType = object : TypeToken<List<Track>>() {}.type
            val liked: List<Track> = gson.fromJson(likedJson, trackListType) ?: emptyList()
            _likedTracksFlow.value = liked

            val playlistsJson = prefs.getString("user_playlists", "[]") ?: "[]"
            val playlistListType = object : TypeToken<List<Playlist>>() {}.type
            val playlists: List<Playlist> = gson.fromJson(playlistsJson, playlistListType) ?: emptyList()
            _playlistsFlow.value = playlists
        } catch (_: Exception) {
            _likedTracksFlow.value = emptyList()
            _playlistsFlow.value = emptyList()
        }
    }

    suspend fun isLiked(trackId: String): Boolean = withContext(Dispatchers.IO) {
        _likedTracksFlow.value.any { it.id == trackId }
    }

    suspend fun toggleLike(track: Track): Boolean = withContext(Dispatchers.IO) {
        val current = _likedTracksFlow.value.toMutableList()
        val exists = current.any { it.id == track.id }
        val nowLiked = if (exists) {
            current.removeAll { it.id == track.id }
            false
        } else {
            current.add(0, track)
            true
        }

        _likedTracksFlow.value = current
        saveLikedTracks(current)
        nowLiked
    }

    suspend fun createPlaylist(name: String, description: String = ""): Playlist = withContext(Dispatchers.IO) {
        val current = _playlistsFlow.value.toMutableList()
        val id = "pl_" + System.currentTimeMillis()
        val newPlaylist = Playlist(
            id = id,
            title = name.trim(),
            description = description.trim(),
            author = "You",
            trackCount = 0,
            tracks = emptyList()
        )
        current.add(0, newPlaylist)
        _playlistsFlow.value = current
        savePlaylists(current)
        newPlaylist
    }

    suspend fun addTrackToPlaylist(playlistId: String, track: Track): Boolean = withContext(Dispatchers.IO) {
        val current = _playlistsFlow.value.toMutableList()
        val index = current.indexOfFirst { it.id == playlistId }
        if (index < 0) return@withContext false

        val pl = current[index]
        if (pl.tracks.any { it.id == track.id }) return@withContext true // already added

        val updatedTracks = pl.tracks + track
        val updatedPl = pl.copy(
            tracks = updatedTracks,
            trackCount = updatedTracks.size,
            thumbnailUrl = pl.thumbnailUrl.ifBlank { track.thumbnailUrl }
        )
        current[index] = updatedPl
        _playlistsFlow.value = current
        savePlaylists(current)
        true
    }

    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String): Boolean = withContext(Dispatchers.IO) {
        val current = _playlistsFlow.value.toMutableList()
        val index = current.indexOfFirst { it.id == playlistId }
        if (index < 0) return@withContext false

        val pl = current[index]
        val updatedTracks = pl.tracks.filter { it.id != trackId }
        val updatedPl = pl.copy(
            tracks = updatedTracks,
            trackCount = updatedTracks.size
        )
        current[index] = updatedPl
        _playlistsFlow.value = current
        savePlaylists(current)
        true
    }

    suspend fun deletePlaylist(playlistId: String): Boolean = withContext(Dispatchers.IO) {
        val current = _playlistsFlow.value.toMutableList()
        val removed = current.removeAll { it.id == playlistId }
        if (removed) {
            _playlistsFlow.value = current
            savePlaylists(current)
        }
        removed
    }

    private fun saveLikedTracks(tracks: List<Track>) {
        val json = gson.toJson(tracks)
        prefs.edit().putString("liked_tracks", json).apply()
    }

    private fun savePlaylists(playlists: List<Playlist>) {
        val json = gson.toJson(playlists)
        prefs.edit().putString("user_playlists", json).apply()
    }
}
