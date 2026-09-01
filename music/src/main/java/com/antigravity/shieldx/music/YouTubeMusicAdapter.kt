package com.antigravity.shieldx.music

import android.util.Log
import com.antigravity.shieldx.core.model.MusicSource
import com.antigravity.shieldx.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Primary search adapter powered by YouTube Music & NewPipeExtractor.
 */
class YouTubeMusicAdapter(
    private val innerTubeClient: InnerTubeClient
) : MusicSourceAdapter {

    override val source: MusicSource = MusicSource.YOUTUBE_MUSIC
    override val priority: Int = 1

    companion object {
        private const val TAG = "TaRZI_YTM_Adapter"
    }

    override suspend fun search(
        query: String,
        filter: SearchFilter,
        limit: Int
    ): List<Track> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        try {
            val ytmFilter = when (filter) {
                SearchFilter.SONGS -> "songs"
                SearchFilter.ALBUMS -> "albums"
                SearchFilter.ARTISTS -> "artists"
                SearchFilter.PLAYLISTS -> "playlists"
                SearchFilter.ALL -> "all"
            }

            val rawResults = innerTubeClient.search(query, ytmFilter)
            val normalized = rawResults.map { track ->
                track.copy(
                    source = MusicSource.YOUTUBE_MUSIC,
                    sourceId = track.id,
                    canonicalUrl = "https://music.youtube.com/watch?v=${track.id}",
                    isPlayable = true
                )
            }.take(limit)

            Log.d(TAG, "Fetched ${normalized.size} tracks from YouTube Music for '$query'")
            normalized
        } catch (e: Exception) {
            Log.e(TAG, "Search failed for '$query': ${e.message}")
            emptyList()
        }
    }

    override suspend fun getRecommendations(seed: String, limit: Int): List<Track> = withContext(Dispatchers.IO) {
        if (seed.isBlank()) return@withContext emptyList()
        try {
            search("$seed radio", SearchFilter.SONGS, limit)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
