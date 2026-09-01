package com.antigravity.shieldx.music

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import java.util.concurrent.TimeUnit

/**
 * Proves the music fix end to end: that NewPipeExtractor returns a genuinely
 * playable audio URL where the old hand-rolled InnerTube calls returned nothing.
 *
 * This talks to the network on purpose - it is the only way to verify the claim.
 * If the network is unavailable the test skips rather than failing the build.
 */
class StreamResolutionLiveTest {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private inner class TestDownloader : Downloader() {
        override fun execute(request: Request): Response {
            val builder = okhttp3.Request.Builder()
                .method(request.httpMethod(), request.dataToSend()?.toRequestBody())
                .url(request.url())
            request.headers().forEach { (name, values) ->
                builder.removeHeader(name)
                values.forEach { builder.addHeader(name, it) }
            }
            builder.header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:129.0) Gecko/20100101 Firefox/129.0"
            )
            val r = http.newCall(builder.build()).execute()
            return Response(r.code, r.message, r.headers.toMultimap(), r.body?.string(), r.request.url.toString())
        }
    }

    @Test
    fun `resolves a real playable audio stream for a known video`() {
        try {
            NewPipe.init(TestDownloader(), Localization.DEFAULT)
            YoutubeStreamExtractor.setPoTokenProvider(TarziPoTokenProvider())
            YoutubeStreamExtractor.setFetchIosClient(true)

            // "Blinding Lights" - one of the tracks the app previously claimed to
            // play while actually emitting a SoundHelix demo MP3.
            val extractor = ServiceList.YouTube
                .getStreamExtractor("https://www.youtube.com/watch?v=4NRXx6U8ABQ")
            extractor.fetchPage()

            val streams = extractor.audioStreams.orEmpty()
            println("[TEST] title      = " + extractor.name)
            println("[TEST] uploader   = " + extractor.uploaderName)
            println("[TEST] audioCount = " + streams.size)

            assertTrue("expected at least one audio stream", streams.isNotEmpty())

            val best = streams.maxByOrNull { it.averageBitrate } ?: streams.first()
            val url = best.content
            println("[TEST] bitrate    = " + best.averageBitrate)
            println("[TEST] format     = " + best.format?.name)
            println("[TEST] url        = " + url?.take(110))

            assertTrue("stream url must be populated", !url.isNullOrBlank())
            assertTrue("stream url must be http(s)", url!!.startsWith("http"))
            assertTrue(
                "resolved url must not be substitute demo audio",
                !url.contains("soundhelix", ignoreCase = true)
            )

            // A HEAD request confirms the URL is genuinely fetchable, which is
            // the part signature deobfuscation exists to make true.
            val head = http.newCall(
                okhttp3.Request.Builder().url(url).head().build()
            ).execute()
            println("[TEST] HEAD       = " + head.code + " " + head.header("content-type"))
            assertTrue("stream must be reachable, got HTTP " + head.code, head.isSuccessful)
        } catch (e: java.io.IOException) {
            assumeNoException("network unavailable; skipping live extraction test", e)
        }
    }
}
