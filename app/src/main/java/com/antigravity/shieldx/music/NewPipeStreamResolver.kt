package com.antigravity.shieldx.music

import android.util.Log
import com.antigravity.shieldx.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Resolves real, playable YouTube audio streams.
 *
 * This replaces the hand-rolled InnerTube calls, which cannot work any more:
 * YouTube now returns an encrypted `signatureCipher` instead of a plain URL and
 * requires a PoToken on most clients, so a raw `/player` request yields nothing
 * playable. NewPipeExtractor carries the deobfuscation logic (via Rhino) and is
 * kept current with YouTube's changes, which is not something worth reimplementing.
 */
object NewPipeStreamResolver {

    private const val TAG = "TarziMusic"

    private val initialized = AtomicBoolean(false)

    private val httpClient = OkHttpClient.Builder()
        .retryOnConnectionFailure(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build()

    /** Safe to call repeatedly; the extractor only needs wiring up once. */
    private fun ensureInitialized() {
        if (initialized.getAndSet(true)) return
        try {
            NewPipe.init(OkHttpDownloader(), Localization.DEFAULT)

            // Without a PoToken YouTube answers "the page needs to be reloaded"
            // instead of returning streaming data.
            YoutubeStreamExtractor.setPoTokenProvider(TarziPoTokenProvider())

            // The iOS client exposes formats the others withhold, so keep it in
            // the rotation as an extra chance at a resolvable stream.
            YoutubeStreamExtractor.setFetchIosClient(true)

            Log.i(TAG, "[EXTRACTOR_INIT] NewPipeExtractor ready with PoToken provider")
        } catch (e: Throwable) {
            initialized.set(false)
            Log.e(TAG, "[EXTRACTOR_INIT_FAIL] " + e.message)
        }
    }

    // ==========================================================
    // Stream resolution
    // ==========================================================

    private val streamCache = java.util.concurrent.ConcurrentHashMap<String, Pair<String, Long>>()
    private const val STREAM_CACHE_TTL_MS = 60 * 60 * 1000L // 1 hour

    /**
     * Resolve a directly playable audio URL for [videoId].
     * Returns null when the video is unavailable, private, or geo-blocked -
     * callers must handle that rather than substituting unrelated audio.
     */
    suspend fun resolveAudioUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext null

        val cached = streamCache[videoId]
        if (cached != null && (System.currentTimeMillis() - cached.second) < STREAM_CACHE_TTL_MS) {
            Log.i(TAG, "[STREAM_CACHE_HIT] videoId=$videoId (0ms instant playback)")
            return@withContext cached.first
        }

        ensureInitialized()

        try {
            val extractor = ServiceList.YouTube
                .getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
            extractor.fetchPage()

            val audioStreams: List<AudioStream> = extractor.audioStreams.orEmpty()
            if (audioStreams.isEmpty()) {
                Log.w(TAG, "[NO_AUDIO_STREAMS] videoId=$videoId")
                return@withContext null
            }

            // Highest bitrate wins; these are audio-only so the cost is modest.
            val best = audioStreams.maxByOrNull { it.averageBitrate.takeIf { b -> b > 0 } ?: it.bitrate }
                ?: audioStreams.first()

            val url = best.content
            if (url.isNullOrBlank()) {
                Log.w(TAG, "[EMPTY_STREAM_URL] videoId=$videoId")
                return@withContext null
            }

            streamCache[videoId] = Pair(url, System.currentTimeMillis())

            Log.i(
                TAG,
                "[STREAM_RESOLVED] videoId=$videoId format=${best.format?.name} bitrate=${best.averageBitrate}"
            )
            url
        } catch (e: Throwable) {
            Log.w(TAG, "[STREAM_RESOLVE_FAIL] videoId=$videoId - ${e.message}")
            null
        }
    }

    /** Duration and canonical metadata for a video, when the caller needs it. */
    suspend fun resolveTrackDetails(videoId: String): Track? = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext null
        ensureInitialized()
        try {
            val extractor = ServiceList.YouTube
                .getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
            extractor.fetchPage()
            Track(
                id = videoId,
                title = extractor.name.orEmpty(),
                artist = extractor.uploaderName.orEmpty(),
                durationSeconds = extractor.length,
                thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                streamUrl = ""
            )
        } catch (e: Throwable) {
            Log.w(TAG, "[DETAILS_FAIL] videoId=$videoId - ${e.message}")
            null
        }
    }

    // ==========================================================
    // Search
    // ==========================================================

    /**
     * Search YouTube Music for songs. Returns an empty list on failure so
     * callers can show an honest empty state instead of fabricated results.
     */
    suspend fun searchSongs(query: String, limit: Int = 20): List<Track> =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext emptyList()
            ensureInitialized()

            try {
                val handler = YoutubeSearchQueryHandlerFactory.getInstance().fromQuery(
                    query,
                    listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS),
                    ""
                )
                val extractor: SearchExtractor = ServiceList.YouTube.getSearchExtractor(handler)
                extractor.fetchPage()

                val items = extractor.initialPage.items.orEmpty()
                val tracks = items
                    .filterIsInstance<StreamInfoItem>()
                    .mapNotNull { item ->
                        val id = extractVideoId(item.url) ?: return@mapNotNull null
                        Track(
                            id = id,
                            title = item.name.orEmpty(),
                            artist = item.uploaderName.orEmpty(),
                            durationSeconds = item.duration.coerceAtLeast(0L),
                            thumbnailUrl = item.thumbnails
                                ?.lastOrNull()?.url
                                ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg",
                            streamUrl = ""
                        )
                    }
                    .take(limit)

                Log.i(TAG, "[SEARCH_OK] '$query' -> ${tracks.size} tracks")
                tracks
            } catch (e: Throwable) {
                Log.w(TAG, "[SEARCH_FAIL] '$query' - ${e.message}")
                emptyList()
            }
        }

    /** Pull the 11-character video id out of any YouTube URL form. */
    private fun extractVideoId(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return Regex("(?:v=|/shorts/|youtu\\.be/|/embed/)([A-Za-z0-9_-]{11})")
            .find(url)?.groupValues?.get(1)
            ?: url.takeIf { it.length == 11 && !it.contains('/') }
    }

    // ==========================================================
    // Downloader
    // ==========================================================

    /** Bridges NewPipe's abstract Downloader onto the OkHttp stack. */
    private class OkHttpDownloader : Downloader() {

        override fun execute(request: Request): Response {
            val builder = okhttp3.Request.Builder()
                .method(request.httpMethod(), request.dataToSend()?.toRequestBody())
                .url(request.url())

            var hasUserAgent = false
            request.headers().forEach { (name, values) ->
                if (name.equals("User-Agent", ignoreCase = true) && values.isNotEmpty()) {
                    hasUserAgent = true
                }
                builder.removeHeader(name)
                values.forEach { value -> builder.addHeader(name, value) }
            }
            if (!hasUserAgent) {
                builder.addHeader("User-Agent", USER_AGENT)
            }

            val response = httpClient.newCall(builder.build()).execute()

            // YouTube serves a consent/captcha wall on 429; surfacing it as the
            // dedicated exception lets the extractor report it meaningfully.
            if (response.code == 429) {
                response.close()
                throw ReCaptchaException("reCaptcha challenge requested", request.url())
            }

            val body = response.body?.string()
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                body,
                response.request.url.toString()
            )
        }

        companion object {
            private const val USER_AGENT =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:129.0) Gecko/20100101 Firefox/129.0"
        }
    }
}
