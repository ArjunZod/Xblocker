package com.antigravity.shieldx.music

import com.antigravity.shieldx.core.model.MusicSource
import com.antigravity.shieldx.core.model.Track

enum class SearchFilter {
    ALL,
    SONGS,
    ALBUMS,
    ARTISTS,
    PLAYLISTS
}

/**
 * Adapter interface for heterogeneous music catalog providers.
 * All providers normalize their results into [Track].
 */
interface MusicSourceAdapter {
    val source: MusicSource
    val isEnabled: Boolean get() = true
    val priority: Int get() = 10

    /**
     * Search the provider's catalog for tracks matching [query].
     */
    suspend fun search(query: String, filter: SearchFilter = SearchFilter.SONGS, limit: Int = 20): List<Track>

    /**
     * Fetch recommendations / radio tracks based on [seed].
     */
    suspend fun getRecommendations(seed: String, limit: Int = 10): List<Track> = emptyList()
}
