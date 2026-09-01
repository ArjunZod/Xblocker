package com.antigravity.shieldx.music

import android.util.Log
import com.antigravity.shieldx.core.model.Track
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * High-performance InnerTube & YouTube Music client.
 * Implements multi-tier stream resolution with public proxy fallback and resilient audio caching.
 */
class InnerTubeClient {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6000, TimeUnit.MILLISECONDS)
        .readTimeout(10000, TimeUnit.MILLISECONDS)
        .followRedirects(true)
        .build()

    private val gson = Gson()
    private val streamUrlCache = ConcurrentHashMap<String, Pair<String, Long>>()

    companion object {
        private const val TAG = "TaRZIMusic"
        private const val INNERTUBE_API = "https://www.youtube.com/youtubei/v1"
        private const val YTM_API = "https://music.youtube.com/youtubei/v1"

        private val STREAM_RESOLVER_ENDPOINTS = listOf(
            "https://pipedapi.kavin.rocks/streams/",
            "https://api.piped.privacydev.net/streams/",
            "https://piped-api.lunar.icu/streams/",
            "https://yewtu.be/api/v1/videos/",
            "https://inv.nadeko.net/api/v1/videos/",
            "https://invidious.nerdvpn.de/api/v1/videos/"
        )

    }

    /**
     * Search YouTube Music catalog for songs, artists, or albums.
     */
    suspend fun search(query: String, filter: String = "songs"): List<Track> = withContext(Dispatchers.IO) {
        Log.i(TAG, "[SEARCH] Query: '$query', filter: '$filter'")

        // NewPipeExtractor tracks YouTube's changes; prefer it over our own parsing.
        val viaExtractor = NewPipeStreamResolver.searchSongs(query)
        if (viaExtractor.isNotEmpty()) {
            Log.i(TAG, "[SEARCH_SUCCESS] Extractor returned ${viaExtractor.size} tracks for '$query'")
            return@withContext viaExtractor
        }

        try {
            val params = when (filter.lowercase()) {
                "songs" -> "EgWKAQIIAWoQEAMQBBAJEAoQCxAEEAUQEQ%3D%3D"
                "albums" -> "EgWKAQIBAWoQEAMQBBAJEAoQCxAEEAUQEQ%3D%3D"
                else -> "EgWKAQIIAWoQEAMQBBAJEAoQCxAEEAUQEQ%3D%3D"
            }

            val requestBody = JsonObject().apply {
                val context = JsonObject().apply {
                    val client = JsonObject().apply {
                        addProperty("clientName", "WEB_REMIX")
                        addProperty("clientVersion", "1.20240902.01.00")
                        addProperty("hl", "en")
                        addProperty("gl", "US")
                    }
                    add("client", client)
                }
                add("context", context)
                addProperty("query", query)
                addProperty("params", params)
            }

            val request = Request.Builder()
                .url("$YTM_API/search")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .addHeader("Referer", "https://music.youtube.com/")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (!body.isNullOrEmpty()) {
                    val parsed = parseSearchResults(body)
                    if (parsed.isNotEmpty()) {
                        Log.i(TAG, "[SEARCH_SUCCESS] Found ${parsed.size} tracks for '$query'")
                        return@withContext parsed
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "[SEARCH_FAIL] Network search exception: ${e.message}")
        }

        Log.w(TAG, "[SEARCH_EMPTY] No results for '$query'")
        emptyList()
    }

    /**
     * Fetch Explore feeds from YouTube Music catalog (Trending, Focus, Workout).
     */
    /**
     * The browse shelves.
     *
     * Each shelf is one search, and they are fetched concurrently - run serially
     * this took as many round trips as there are shelves, which is why the Music
     * screen used to sit empty for several seconds on open.
     *
     * A shelf that returns nothing is dropped rather than padded with filler, so
     * the screen never shows a heading above songs that do not match it.
     */
    suspend fun getExploreSections(): List<ExploreSection> = withContext(Dispatchers.IO) {
        val shelves = listOf(
            "Trending now" to "Top songs this week",
            "Top English" to "Billboard hot hits english songs",
            "Top Hindi" to "Bollywood top hindi songs this week",
            "Top Telugu" to "Telugu top hit songs this week",
            "Top Punjabi" to "Punjabi top hits this week",
            "Focus" to "Lofi chill beats to study",
            "Workout" to "High energy workout songs"
        )

        val deferred = shelves.map { (title, query) ->
            async {
                val items = runCatching { search(query).take(10) }.getOrDefault(emptyList())
                if (items.isEmpty()) null else ExploreSection(title = title, items = items)
            }
        }

        deferred.awaitAll().filterNotNull()
    }

    /**
     * Resolve playable direct audio stream URL for a given track.
     */
    suspend fun resolveStreamUrl(track: Track): String = withContext(Dispatchers.IO) {
        val videoId = track.id
        Log.i(TAG, "[TRACK_SELECTED] Resolving stream for ID: '$videoId', title: '${track.title}'")

        // An already-direct media URL (not a watch page) can be used as-is.
        if (track.streamUrl.startsWith("http") && !track.streamUrl.contains("youtube.com/watch")) {
            return@withContext track.streamUrl
        }

        if (videoId.isBlank()) return@withContext ""

        val cached = streamUrlCache[videoId]
        if (cached != null && cached.second > System.currentTimeMillis()) {
            Log.i(TAG, "[STREAM_CACHE_HIT] Valid cached stream for '$videoId'")
            return@withContext cached.first
        }

        // Tier 1: NewPipeExtractor. Handles the signature cipher and the
        // throttling n-parameter, which is why raw InnerTube calls fail now.
        val extracted = NewPipeStreamResolver.resolveAudioUrl(videoId)
        if (!extracted.isNullOrBlank()) {
            // These URLs are time-limited; keep the cache well inside that window.
            streamUrlCache[videoId] = extracted to (System.currentTimeMillis() + 2 * 3600 * 1000L)
            return@withContext extracted
        }

        // Tier 2: public proxy instances, if any happen to be alive.
        for (baseUrl in STREAM_RESOLVER_ENDPOINTS) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl$videoId")
                    .addHeader("User-Agent", "Mozilla/5.0")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrEmpty()) {
                            val json = gson.fromJson(body, JsonObject::class.java)

                            json.getAsJsonArray("audioStreams")?.takeIf { it.size() > 0 }?.let { arr ->
                                val url = arr[0].asJsonObject.get("url")?.asString
                                if (!url.isNullOrEmpty()) {
                                    Log.i(TAG, "[MEDIA_URI_RESOLVED] Proxy stream for '$videoId'")
                                    streamUrlCache[videoId] =
                                        url to (System.currentTimeMillis() + 2 * 3600 * 1000L)
                                    return@withContext url
                                }
                            }

                            json.getAsJsonArray("adaptiveFormats")?.forEach { fmt ->
                                val mime = fmt.asJsonObject.get("type")?.asString ?: ""
                                if (mime.startsWith("audio/")) {
                                    val url = fmt.asJsonObject.get("url")?.asString
                                    if (!url.isNullOrEmpty()) {
                                        streamUrlCache[videoId] =
                                            url to (System.currentTimeMillis() + 2 * 3600 * 1000L)
                                        return@withContext url
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        // No substitute audio. Playing an unrelated track would be worse than
        // reporting failure, because it hides the problem from the user.
        Log.w(TAG, "[STREAM_UNAVAILABLE] Could not resolve any stream for '$videoId'")
        ""
    }

    private fun resolveInnerTubeDirect(videoId: String, clientName: String, clientVersion: String): String? {
        return try {
            val requestBody = JsonObject().apply {
                val context = JsonObject().apply {
                    val client = JsonObject().apply {
                        addProperty("clientName", clientName)
                        addProperty("clientVersion", clientVersion)
                        addProperty("hl", "en")
                        addProperty("gl", "US")
                    }
                    add("client", client)
                }
                add("context", context)
                addProperty("videoId", videoId)
            }

            val request = Request.Builder()
                .url("$INNERTUBE_API/player")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .addHeader("X-YouTube-Client-Name", if (clientName == "IOS") "5" else "1")
                .addHeader("X-YouTube-Client-Version", clientVersion)
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null

            val body = response.body?.string() ?: return null
            val root = gson.fromJson(body, JsonObject::class.java)
            val streamingData = root.getAsJsonObject("streamingData") ?: return null

            val adaptiveFormats = streamingData.getAsJsonArray("adaptiveFormats")
            if (adaptiveFormats != null) {
                for (fmt in adaptiveFormats) {
                    val fmtObj = fmt.asJsonObject
                    val mimeType = fmtObj.get("mimeType")?.asString ?: ""
                    if (mimeType.startsWith("audio/")) {
                        val directUrl = fmtObj.get("url")?.asString
                        if (!directUrl.isNullOrEmpty()) {
                            return directUrl
                        }
                    }
                }
            }

            val formats = streamingData.getAsJsonArray("formats")
            if (formats != null) {
                for (fmt in formats) {
                    val directUrl = fmt.asJsonObject.get("url")?.asString
                    if (!directUrl.isNullOrEmpty()) {
                        return directUrl
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun parseSearchResults(jsonString: String): List<Track> {
        val tracks = mutableListOf<Track>()
        try {
            val root = gson.fromJson(jsonString, JsonObject::class.java)
            val contents = root.getAsJsonObject("contents")
                ?.getAsJsonObject("tabbedSearchResultsRenderer")
                ?.getAsJsonArray("tabs")?.get(0)?.asJsonObject
                ?.getAsJsonObject("tabRenderer")
                ?.getAsJsonObject("content")
                ?.getAsJsonObject("sectionListRenderer")
                ?.getAsJsonArray("contents") ?: return emptyList()

            for (section in contents) {
                val itemSection = section.asJsonObject.getAsJsonObject("musicShelfRenderer") ?: continue
                val items = itemSection.getAsJsonArray("contents") ?: continue

                for (item in items) {
                    val renderer = item.asJsonObject.getAsJsonObject("musicResponsiveListItemRenderer") ?: continue
                    val flexColumns = renderer.getAsJsonArray("flexColumns") ?: continue

                    var title = "Unknown Title"
                    var artist = "Unknown Artist"
                    var videoId = ""
                    var isExplicit = false

                    val playNav = renderer.getAsJsonObject("playlistItemData")
                    videoId = playNav?.get("videoId")?.asString ?: ""

                    if (flexColumns.size() > 0) {
                        val runs = flexColumns.get(0).asJsonObject
                            .getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                            ?.getAsJsonObject("text")
                            ?.getAsJsonArray("runs")
                        if (runs != null && runs.size() > 0) {
                            title = runs.get(0).asJsonObject.get("text")?.asString ?: title
                        }
                    }

                    if (flexColumns.size() > 1) {
                        val runs = flexColumns.get(1).asJsonObject
                            .getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                            ?.getAsJsonObject("text")
                            ?.getAsJsonArray("runs")
                        if (runs != null && runs.size() > 0) {
                            artist = runs.get(0).asJsonObject.get("text")?.asString ?: artist
                        }
                    }

                    val badges = renderer.getAsJsonArray("badges")
                    if (badges != null && badges.size() > 0) {
                        isExplicit = true
                    }

                    val thumbUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

                    if (videoId.isNotEmpty()) {
                        tracks.add(
                            Track(
                                id = videoId,
                                title = title,
                                artist = artist,
                                thumbnailUrl = thumbUrl,
                                streamUrl = "https://www.youtube.com/watch?v=$videoId",
                                isExplicit = isExplicit
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "[PARSE_ERROR] Failed parsing search results: ${e.message}")
        }
        return tracks
    }

    private fun fetchRealCategoryTracks(query: String): List<Track> {
        return listOf(
            Track("4NRXx6U8ABQ", "Blinding Lights", "The Weeknd", "After Hours", 200, "https://i.ytimg.com/vi/4NRXx6U8ABQ/hqdefault.jpg"),
            Track("34Na4j8AVgA", "Starboy", "The Weeknd ft. Daft Punk", "Starboy", 230, "https://i.ytimg.com/vi/34Na4j8AVgA/hqdefault.jpg"),
            Track("XXYlFuWEuKI", "Save Your Tears", "The Weeknd", "After Hours", 215, "https://i.ytimg.com/vi/XXYlFuWEuKI/hqdefault.jpg")
        )
    }

    private fun getCuratedExploreItems(category: String): List<Track> {
        return when (category) {
            "trending" -> listOf(
                Track("4NRXx6U8ABQ", "Blinding Lights", "The Weeknd", "After Hours", 200, "https://i.ytimg.com/vi/4NRXx6U8ABQ/hqdefault.jpg"),
                Track("34Na4j8AVgA", "Starboy", "The Weeknd ft. Daft Punk", "Starboy", 230, "https://i.ytimg.com/vi/34Na4j8AVgA/hqdefault.jpg"),
                Track("XXYlFuWEuKI", "Save Your Tears", "The Weeknd", "After Hours", 215, "https://i.ytimg.com/vi/XXYlFuWEuKI/hqdefault.jpg")
            )
            "focus" -> listOf(
                Track("fc_1", "Deep Horizon", "Lofi Haven", "Focus Sessions", 230, "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=500"),
                Track("fc_2", "Coffee Shop Rain", "Study Sound", "Lofi Cafe", 195, "https://images.unsplash.com/photo-1447752875215-b2761acb3c5d?w=500")
            )
            else -> listOf(
                Track("wo_1", "Overdrive 140", "Apex Club", "Sprint Energy", 210, "https://images.unsplash.com/photo-1534438327276-14e5300c3a48?w=500"),
                Track("wo_2", "Heavy Bassline", "Rumble", "Bass Arena", 185, "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=500")
            )
        }
    }
}
