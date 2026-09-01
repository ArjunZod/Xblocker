package com.antigravity.shieldx.music

import android.util.Log
import com.antigravity.shieldx.core.model.MusicSource
import com.antigravity.shieldx.core.model.Track
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max

/**
 * Multi-Source Music Search & Discovery Engine.
 *
 * Implements:
 * - YouTube Music, Spotify, and Deezer multi-source search
 * - Platform badge preservation
 * - Intelligent relevance ranking
 * - In-memory LRU search caching
 */
class TaRziMusicSearchEngine(
    private val adapters: List<MusicSourceAdapter>
) {

    private val searchCache = ConcurrentHashMap<String, Pair<List<Track>, Long>>()

    companion object {
        private const val TAG = "TaRZISearchEngine"
        private const val CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes
    }

    /**
     * Search across all registered source adapters with ranking and deduplication.
     */
    suspend fun search(
        query: String,
        filter: SearchFilter = SearchFilter.SONGS,
        limit: Int = 30
    ): List<Track> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        val cacheKey = "${filter.name}:${trimmed.lowercase()}"
        val cached = searchCache[cacheKey]
        if (cached != null && (System.currentTimeMillis() - cached.second) < CACHE_TTL_MS) {
            Log.d(TAG, "Search cache hit for '$trimmed' (${cached.first.size} results)")
            return@withContext cached.first
        }

        // Run all enabled adapters concurrently (YouTube Music, Spotify, Deezer)
        val enabledAdapters = adapters.filter { it.isEnabled }
        val deferredList = enabledAdapters.map { adapter ->
            async {
                try {
                    adapter.search(trimmed, filter, limit = 15)
                } catch (e: Exception) {
                    Log.w(TAG, "Adapter ${adapter.source} failed: ${e.message}")
                    emptyList()
                }
            }
        }

        val allResults = mutableListOf<Track>()
        for (deferred in deferredList) {
            allResults.addAll(deferred.await())
        }

        // Deduplicate same-source exact ID spam while keeping distinct platforms
        val deduplicated = deduplicateTracks(allResults)

        // Rank by relevance to query with fair multi-platform distribution
        val ranked = rankTracks(deduplicated, trimmed).take(limit)

        // Cache results
        searchCache[cacheKey] = Pair(ranked, System.currentTimeMillis())

        Log.i(TAG, "Search completed for '$trimmed': ${ranked.size} unique ranked results across ${enabledAdapters.size} sources")
        ranked
    }

    /**
     * Deduplicates exact duplicate source IDs while preserving platform diversity.
     */
    private fun deduplicateTracks(tracks: List<Track>): List<Track> {
        val seen = HashSet<String>()
        val unique = mutableListOf<Track>()

        for (track in tracks) {
            val key = "${track.source}_${track.id}"
            if (seen.add(key)) {
                unique.add(track)
            }
        }
        return unique
    }

    /**
     * Ranks tracks by string similarity to the query, prioritizing exact title and artist matches.
     */
    private fun rankTracks(tracks: List<Track>, query: String): List<Track> {
        val qLower = query.lowercase().trim()

        return tracks.sortedWith(
            compareByDescending<Track> { track ->
                var score = 0
                val titleLower = track.title.lowercase()
                val artistLower = track.artist.lowercase()

                // Exact match bonuses
                if (titleLower == qLower) score += 100
                if (artistLower == qLower) score += 80
                if (titleLower.startsWith(qLower)) score += 50
                if (titleLower.contains(qLower)) score += 30
                if (artistLower.contains(qLower)) score += 25

                // Levenshtein similarity
                val sim = similarity(titleLower, qLower)
                score += (sim * 30).toInt()

                // High-resolution artwork bonus
                if (track.thumbnailUrl.isNotBlank()) score += 10

                // Source priority
                when (track.source) {
                    MusicSource.YOUTUBE_MUSIC -> score += 15
                    MusicSource.SPOTIFY -> score += 12
                    MusicSource.DEEZER -> score += 10
                    else -> {}
                }

                score
            }.thenBy { it.title.length }
        )
    }

    private fun similarity(s1: String, s2: String): Double {
        val longer = if (s1.length >= s2.length) s1 else s2
        val shorter = if (s1.length < s2.length) s1 else s2
        if (longer.isEmpty()) return 1.0
        val dist = levenshtein(longer, shorter)
        return (longer.length - dist).toDouble() / longer.length.toDouble()
    }

    private fun levenshtein(s: String, t: String): Int {
        val m = s.length
        val n = t.length
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (s[i - 1] == t[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[m][n]
    }
}
