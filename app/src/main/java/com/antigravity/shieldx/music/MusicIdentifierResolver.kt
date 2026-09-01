package com.antigravity.shieldx.music

import android.util.Log
import com.antigravity.shieldx.core.model.MusicSource
import com.antigravity.shieldx.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Resolves heterogeneous [Track] items and public music URLs into directly playable media streams.
 * Coordinates between Lavalink v4 nodes and on-device Velune/NewPipe extraction with instant fallback.
 */
class MusicIdentifierResolver(
    private val lavalinkNodeManager: LavalinkNodeManager,
    private val innerTubeClient: InnerTubeClient
) {

    companion object {
        private const val TAG = "TaRZI_Resolver"
    }

    /**
     * Resolves a [Track] into a real playable audio stream URL.
     * Returns a pair of (ResolvedTrack, StreamUrl), or null if the track cannot be played.
     */
    suspend fun resolveStream(track: Track): Pair<Track, String>? = withContext(Dispatchers.IO) {
        Log.i(TAG, "Resolving playable stream for '${track.title}' by '${track.artist}' (source=${track.source})")

        // 1. Direct stream URL already present and valid
        if (track.streamUrl.isNotBlank() && track.streamUrl.startsWith("http")) {
            return@withContext Pair(track, track.streamUrl)
        }

        var videoId = track.id

        // 2. If the track is from Spotify or Deezer, resolve equivalent track
        if (track.source == MusicSource.SPOTIFY || track.source == MusicSource.DEEZER || !track.isPlayable) {
            val query = "${track.title} ${track.artist}"
            Log.d(TAG, "Searching playable equivalent on YouTube Music for '$query'")

            // Try Lavalink first for metadata-to-audio matching with a 1.5s timeout
            val lavalinkTrack = withTimeoutOrNull(1500) {
                try {
                    lavalinkNodeManager.loadTracks("ytsearch:$query")
                } catch (_: Exception) { null }
            }

            if (lavalinkTrack != null && lavalinkTrack.uri.isNotBlank()) {
                val extractedId = extractYouTubeId(lavalinkTrack.uri)
                if (!extractedId.isNullOrBlank()) {
                    videoId = extractedId
                }
            } else {
                // Fallback to InnerTube search
                val candidates = innerTubeClient.search(query)
                val match = candidates.firstOrNull()
                if (match != null) {
                    videoId = match.id
                } else {
                    Log.w(TAG, "No playable equivalent found for '$query'")
                    return@withContext null
                }
            }
        }

        // Clean video ID if formatted as URI
        if (videoId.startsWith("spotify:") || videoId.startsWith("deezer:")) {
            val match = innerTubeClient.search("${track.title} ${track.artist}").firstOrNull()
            if (match != null) videoId = match.id else return@withContext null
        }

        // 3. Resolve direct Opus audio stream via NewPipe / InnerTube (Velune player backend)
        val directStreamUrl = NewPipeStreamResolver.resolveAudioUrl(videoId)
            ?: innerTubeClient.resolveStreamUrl(track.copy(id = videoId))

        if (directStreamUrl.isNullOrBlank()) {
            Log.e(TAG, "Stream resolution failed completely for videoId=$videoId")
            return@withContext null
        }

        val resolvedTrack = track.copy(
            id = videoId,
            streamUrl = directStreamUrl,
            isPlayable = true
        )

        Log.i(TAG, "Stream resolved successfully for '${resolvedTrack.title}' (videoId=$videoId)")
        Pair(resolvedTrack, directStreamUrl)
    }

    /**
     * Detects if [input] is a URL and resolves it to a playable Track.
     */
    suspend fun resolveFromUrl(url: String): Pair<Track, String>? = withContext(Dispatchers.IO) {
        val trimmed = url.trim()
        when {
            trimmed.contains("youtube.com") || trimmed.contains("youtu.be") -> {
                val videoId = extractYouTubeId(trimmed) ?: return@withContext null
                val streamUrl = NewPipeStreamResolver.resolveAudioUrl(videoId) ?: return@withContext null
                val track = Track(
                    id = videoId,
                    title = "YouTube Track",
                    artist = "YouTube",
                    streamUrl = streamUrl,
                    canonicalUrl = trimmed,
                    source = MusicSource.YOUTUBE_MUSIC,
                    isPlayable = true
                )
                Pair(track, streamUrl)
            }
            else -> {
                // Try Lavalink URL loader with fallback
                val lavalinkResult = withTimeoutOrNull(2000) {
                    lavalinkNodeManager.loadTracks(trimmed)
                }

                if (lavalinkResult != null) {
                    val track = Track(
                        id = lavalinkResult.identifier,
                        title = lavalinkResult.title,
                        artist = lavalinkResult.author,
                        durationSeconds = lavalinkResult.length / 1000,
                        thumbnailUrl = lavalinkResult.artworkUrl ?: "",
                        canonicalUrl = lavalinkResult.uri,
                        source = MusicSource.LAVALINK,
                        isPlayable = true
                    )
                    resolveStream(track)
                } else null
            }
        }
    }

    private fun extractYouTubeId(url: String): String? {
        val patterns = listOf(
            Regex("(?:v=|/v/|youtu\\.be/|/embed/|/shorts/)([a-zA-Z0-9_-]{11})"),
            Regex("^([a-zA-Z0-9_-]{11})$")
        )
        for (p in patterns) {
            val match = p.find(url)
            if (match != null) return match.groupValues[1]
        }
        return null
    }
}
