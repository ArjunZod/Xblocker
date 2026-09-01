package com.antigravity.shieldx.music

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class LyricLine(
    val timestampMs: Long,
    val text: String
)

/**
 * Synced lyrics from lrclib.
 *
 * The hard part is not the API, it is the metadata. YouTube hands us titles like
 * "The Weeknd - Blinding Lights (Official Video)" and channel names like
 * "TheWeekndVEVO", and lrclib answers 404 for both. Cleaning those to
 * "Blinding Lights" / "The Weeknd" is the difference between lyrics always
 * failing and lyrics working - which is exactly why they never appeared before.
 */
class LyricsService {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val cache = HashMap<String, List<LyricLine>>()

    companion object {
        private const val TAG = "TarziLyrics"
        private const val API = "https://lrclib.net/api"
        private const val UA = "TarziMusic/1.0 (https://github.com/tarzi)"

        /** Suffixes YouTube uploaders append that are never part of a song title. */
        private val NOISE = Regex(
            "\\((?:[^)]*(?:official|video|audio|lyric|lyrics|hd|4k|mv|visualizer|" +
                "remaster(?:ed)?|explicit|clean|live|performance|version)[^)]*)\\)" +
                "|\\[(?:[^\\]]*(?:official|video|audio|lyric|lyrics|hd|4k|mv|visualizer|" +
                "remaster(?:ed)?|explicit|clean)[^\\]]*)\\]",
            RegexOption.IGNORE_CASE
        )

        private val TRAILING_TAGS = Regex(
            "\\s*[-–|]\\s*(?:official\\s*)?(?:music\\s*)?(?:video|audio|lyrics?|visualizer|hd|4k)\\s*$",
            RegexOption.IGNORE_CASE
        )

        /**
         * The word boundary matters more than it looks: without it, "ft" matches
         * inside "Daft Punk" and the artist is truncated to "Da".
         */
        private val FEATURING = Regex(
            "\\s*(?:\\(|\\[)?\\s*\\b(?:feat|ft|featuring|with)\\b\\.?\\s+[^)\\]]*(?:\\)|\\])?",
            RegexOption.IGNORE_CASE
        )

        /** Channel suffixes that are not part of the artist's name. */
        private val ARTIST_NOISE = Regex(
            "\\s*(?:-\\s*topic|vevo|official|music|records|tv)\\s*$",
            RegexOption.IGNORE_CASE
        )

        /**
         * Clean a YouTube title into a probable song title, and pull out the
         * artist when the uploader used the common "Artist - Title" form.
         */
        fun normalize(rawTitle: String, rawArtist: String): Pair<String, String> {
            var title = rawTitle
                .replace(NOISE, " ")
                .replace(TRAILING_TAGS, "")
                .replace(FEATURING, "")
                .replace(Regex("\\s+"), " ")
                .trim()
                .trim('-', '–', '|', ' ')

            var artist = rawArtist
                .replace(ARTIST_NOISE, "")
                .replace(FEATURING, "")
                .trim()

            // "Artist - Title": prefer the embedded artist, since a channel name
            // is far more often wrong than the title's own prefix.
            val separator = Regex("\\s+[-–]\\s+")
            val parts = title.split(separator, limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                artist = parts[0].trim()
                title = parts[1].trim()
            }

            // "TheWeeknd" -> "The Weeknd". Channel names drop the spaces.
            if (artist.isNotBlank() && !artist.contains(' ') && artist.length > 3) {
                artist = artist.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")
            }

            return title.trim() to artist.trim()
        }
    }

    /**
     * Look up lyrics, trying the exact endpoint first and falling back to
     * lrclib's fuzzier search when the precise match misses.
     */
    suspend fun getLyrics(trackTitle: String, artistName: String): List<LyricLine> =
        withContext(Dispatchers.IO) {
            val (title, artist) = normalize(trackTitle, artistName)
            if (title.isBlank()) return@withContext emptyList()

            val key = "$title|$artist".lowercase()
            cache[key]?.let { return@withContext it }

            Log.i(TAG, "[LOOKUP] '$trackTitle' / '$artistName' -> '$title' / '$artist'")

            val result = exactMatch(title, artist)
                ?: searchMatch(title, artist)
                ?: emptyList()

            if (result.isNotEmpty()) {
                cache[key] = result
                Log.i(TAG, "[FOUND] ${result.size} lines for '$title'")
            } else {
                Log.i(TAG, "[NOT_FOUND] no lyrics for '$title' / '$artist'")
            }
            result
        }

    private fun exactMatch(title: String, artist: String): List<LyricLine>? {
        if (artist.isBlank()) return null
        return try {
            val url = "$API/get?track_name=${encode(title)}&artist_name=${encode(artist)}"
            request(url)?.let { extractLines(it) }
        } catch (e: Exception) {
            Log.w(TAG, "[EXACT_FAIL] ${e.message}")
            null
        }
    }

    /** lrclib's search tolerates a wrong artist, which the exact endpoint will not. */
    private fun searchMatch(title: String, artist: String): List<LyricLine>? {
        return try {
            val query = if (artist.isBlank()) title else "$title $artist"
            val url = "$API/search?q=${encode(query)}"
            val body = requestRaw(url) ?: return null
            val results = gson.fromJson(body, JsonArray::class.java) ?: return null

            // Prefer a hit that actually carries synced timings.
            val best = results.firstOrNull {
                val obj = it.asJsonObject
                !obj.get("syncedLyrics")?.asString.isNullOrBlank()
            }?.asJsonObject ?: results.firstOrNull()?.asJsonObject ?: return null

            extractLines(best)
        } catch (e: Exception) {
            Log.w(TAG, "[SEARCH_FAIL] ${e.message}")
            null
        }
    }

    private fun extractLines(json: JsonObject): List<LyricLine>? {
        val synced = json.get("syncedLyrics")?.takeIf { !it.isJsonNull }?.asString
        if (!synced.isNullOrBlank()) return parseLrc(synced)

        val plain = json.get("plainLyrics")?.takeIf { !it.isJsonNull }?.asString
        if (plain.isNullOrBlank()) return null

        // Unsynced lyrics get no fake timings: a zero timestamp tells the UI to
        // render them as a static block instead of pretending to follow along.
        return plain.lines()
            .filter { it.isNotBlank() }
            .map { LyricLine(0L, it.trim()) }
    }

    private fun request(url: String): JsonObject? {
        val body = requestRaw(url) ?: return null
        return gson.fromJson(body, JsonObject::class.java)
    }

    private fun requestRaw(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", UA)
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    /** Parse `[mm:ss.xx] text` lines into timed entries. */
    fun parseLrc(lrcText: String): List<LyricLine> {
        val regex = Regex("\\[(\\d{1,2}):(\\d{2})[.:](\\d{2,3})\\](.*)")
        val lines = mutableListOf<LyricLine>()

        for (raw in lrcText.lines()) {
            val match = regex.find(raw.trim()) ?: continue
            val minutes = match.groupValues[1].toLongOrNull() ?: 0L
            val seconds = match.groupValues[2].toLongOrNull() ?: 0L
            val fractionText = match.groupValues[3]
            val fraction = fractionText.toLongOrNull() ?: 0L
            // Two digits are centiseconds, three are milliseconds.
            val millis = if (fractionText.length == 2) fraction * 10 else fraction

            val text = match.groupValues[4].trim()
            lines.add(LyricLine(minutes * 60_000 + seconds * 1_000 + millis, text))
        }
        return lines.sortedBy { it.timestampMs }
    }
}
