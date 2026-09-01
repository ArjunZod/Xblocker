package com.antigravity.shieldx.music

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.services.youtube.PoTokenProvider
import org.schabi.newpipe.extractor.services.youtube.PoTokenResult
import java.util.concurrent.TimeUnit
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random

/**
 * Cold-start proof-of-origin token generation.
 *
 * Ported from Velune (github.com/nikhilvishwakarma00/Velune), which in turn
 * follows SmartTube's PoTokenService: it builds a structurally valid token from
 * an identifier and client state without running BotGuard or a WebView.
 *
 * NOTE ON LICENSING: Velune is GPL-3.0. This derivation carries that licence,
 * which matters if this app is ever distributed commercially or closed-source.
 *
 * NOTE ON RELIABILITY: this is a synthesised token, not a genuine BotGuard
 * attestation. YouTube accepts it in some contexts and rejects it in others,
 * so every caller must still handle resolution failure.
 */
@OptIn(ExperimentalEncodingApi::class)
object TarziPoTokenGenerator {

    private const val TOKEN_VERSION: Byte = 0x22
    private const val MAGIC_HEADER: Byte = 0x0A
    private const val INNER_TAG: Byte = 0x38
    private const val TIMESTAMP_TAG: Byte = 0x02

    fun generateColdStartToken(identifier: String, clientState: String = ""): String {
        val timestamp = System.currentTimeMillis()
        val identifierBytes = identifier.toByteArray(Charsets.UTF_8)
        val stateBytes = clientState.toByteArray(Charsets.UTF_8)

        val keyBytes = ByteArray(16).also { Random.nextBytes(it) }
        val encryptedId = xorEncrypt(identifierBytes, keyBytes)
        val timestampBytes = encodeLong(timestamp)

        val innerPayload = buildByteArray {
            append(INNER_TAG)
            appendVarInt(stateBytes.size)
            append(stateBytes)
            append(TIMESTAMP_TAG)
            appendVarInt(timestampBytes.size)
            append(timestampBytes)
        }

        val tokenPayload = buildByteArray {
            append(MAGIC_HEADER)
            appendVarInt(keyBytes.size)
            append(keyBytes)
            append(TOKEN_VERSION)
            appendVarInt(encryptedId.size)
            append(encryptedId)
            append(innerPayload)
        }

        return Base64.UrlSafe.encode(tokenPayload).trimEnd('=')
    }

    fun generateContentToken(identifier: String, videoId: String): String =
        generateColdStartToken(identifier, videoId)

    fun generateSessionToken(identifier: String): String =
        generateColdStartToken(identifier, "session")

    private fun xorEncrypt(data: ByteArray, key: ByteArray): ByteArray =
        ByteArray(data.size) { i ->
            (data[i].toInt() xor key[i % key.size].toInt()).toByte()
        }

    private fun encodeLong(value: Long): ByteArray {
        val buf = ByteArray(8)
        var v = value
        for (i in 0 until 8) {
            buf[i] = (v and 0xFF).toByte()
            v = v shr 8
        }
        var len = 8
        while (len > 1 && buf[len - 1] == 0.toByte()) len--
        return buf.copyOf(len)
    }

    private inline fun buildByteArray(block: ByteArrayBuilder.() -> Unit): ByteArray =
        ByteArrayBuilder().apply(block).toByteArray()

    private class ByteArrayBuilder {
        private val list = mutableListOf<Byte>()

        fun append(b: Byte) = list.add(b)

        fun append(bytes: ByteArray) {
            for (b in bytes) list.add(b)
        }

        fun appendVarInt(value: Int) {
            var v = value
            while (v >= 0x80) {
                list.add((v or 0x80).toByte())
                v = v shr 7
            }
            list.add(v.toByte())
        }

        fun toByteArray(): ByteArray = list.toByteArray()
    }
}

/**
 * Supplies NewPipeExtractor with per-client PoTokens so YouTube stops answering
 * "the page needs to be reloaded" and hands over real streaming data.
 */
class TarziPoTokenProvider : PoTokenProvider {

    companion object {
        private const val TAG = "TarziMusic"
        private val VISITOR_DATA_REGEX = Regex("Cg[A-Za-z0-9_-]{20,}")

        private val http = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        @Volatile
        private var cachedVisitorData: String? = null

        /**
         * visitorData identifies the anonymous session and is baked into the
         * token. Scraped from YouTube's service-worker bundle, which is where it
         * is exposed without an authenticated call.
         */
        fun visitorData(): String {
            cachedVisitorData?.let { return it }
            val fetched = try {
                val request = Request.Builder()
                    .url("https://www.youtube.com/sw.js_data")
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .addHeader("Referer", "https://www.youtube.com/")
                    .build()
                http.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    VISITOR_DATA_REGEX.find(body)?.value
                }
            } catch (e: Exception) {
                Log.w(TAG, "[VISITOR_DATA_FAIL] " + e.message)
                null
            }

            // A placeholder still yields a well-formed token; it is simply
            // less likely to be honoured than a real session identifier.
            val result = fetched ?: "dummy_visitor_data_for_token"
            cachedVisitorData = result
            Log.i(TAG, "[VISITOR_DATA] " + if (fetched != null) "fetched live" else "using placeholder")
            return result
        }
    }

    private fun tokenFor(videoId: String): PoTokenResult {
        val visitor = visitorData()
        val token = TarziPoTokenGenerator.generateContentToken(visitor, videoId)
        return PoTokenResult(visitor, token, token)
    }

    override fun getWebClientPoToken(videoId: String): PoTokenResult = tokenFor(videoId)

    override fun getWebEmbedClientPoToken(videoId: String): PoTokenResult = tokenFor(videoId)

    override fun getAndroidClientPoToken(videoId: String): PoTokenResult = tokenFor(videoId)

    override fun getIosClientPoToken(videoId: String): PoTokenResult = tokenFor(videoId)
}
