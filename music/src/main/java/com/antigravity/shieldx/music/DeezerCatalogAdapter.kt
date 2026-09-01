package com.antigravity.shieldx.music

import android.util.Log
import com.antigravity.shieldx.core.model.MusicSource
import com.antigravity.shieldx.core.model.Track
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Deezer Catalog Adapter for rich metadata, high-resolution album art, and track search.
 */
class DeezerCatalogAdapter : MusicSourceAdapter {

    override val source: MusicSource = MusicSource.DEEZER
    override val priority: Int = 3

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    companion object {
        private const val TAG = "TaRZI_Deezer_Adapter"
        private const val BASE_URL = "https://api.deezer.com"
    }

    override suspend fun search(
        query: String,
        filter: SearchFilter,
        limit: Int
    ): List<Track> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        try {
            val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
            val endpoint = when (filter) {
                SearchFilter.ALBUMS -> "$BASE_URL/search/album?q=$encodedQuery&limit=$limit"
                SearchFilter.ARTISTS -> "$BASE_URL/search/artist?q=$encodedQuery&limit=$limit"
                else -> "$BASE_URL/search?q=$encodedQuery&limit=$limit"
            }

            val request = Request.Builder()
                .url(endpoint)
                .addHeader("User-Agent", "TaRZI-Music/1.0")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val body = response.body?.string() ?: return@withContext emptyList()
            val root = gson.fromJson(body, JsonObject::class.java)
            val dataArray = root.getAsJsonArray("data") ?: return@withContext emptyList()

            val tracks = mutableListOf<Track>()
            for (element in dataArray) {
                if (!element.isJsonObject) continue
                val item = element.asJsonObject

                val id = item.get("id")?.asString ?: continue
                val title = item.get("title")?.asString ?: item.get("title_short")?.asString ?: "Unknown"
                val artistObj = item.getAsJsonObject("artist")
                val artistName = artistObj?.get("name")?.asString ?: "Unknown Artist"
                val albumObj = item.getAsJsonObject("album")
                val albumTitle = albumObj?.get("title")?.asString ?: ""
                val artwork = albumObj?.get("cover_big")?.asString
                    ?: albumObj?.get("cover_medium")?.asString
                    ?: artistObj?.get("picture_big")?.asString
                    ?: ""
                val durationSec = item.get("duration")?.asLong ?: 0L
                val isExplicit = item.get("explicit_lyrics")?.asBoolean ?: false
                val link = item.get("link")?.asString ?: "https://www.deezer.com/track/$id"
                val previewUrl = item.get("preview")?.asString ?: ""

                tracks.add(
                    Track(
                        id = "deezer:$id",
                        title = title,
                        artist = artistName,
                        album = albumTitle,
                        durationSeconds = durationSec,
                        thumbnailUrl = artwork,
                        streamUrl = previewUrl,
                        isExplicit = isExplicit,
                        source = MusicSource.DEEZER,
                        sourceId = id,
                        canonicalUrl = link,
                        isPlayable = false, // Metadata catalog; playable equivalent resolved via resolver
                        metadata = mapOf(
                            "preview_url" to previewUrl,
                            "deezer_id" to id
                        )
                    )
                )
            }

            Log.d(TAG, "Fetched ${tracks.size} tracks from Deezer for '$query'")
            tracks
        } catch (e: Exception) {
            Log.e(TAG, "Deezer search failed for '$query': ${e.message}")
            emptyList()
        }
    }
}
