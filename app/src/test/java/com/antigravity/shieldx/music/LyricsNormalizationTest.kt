package com.antigravity.shieldx.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lyrics never appeared because YouTube's metadata never matched lrclib's
 * index. These lock in the cleaning that makes the lookup succeed.
 */
class LyricsNormalizationTest {

    @Test
    fun `strips official video suffix and lifts artist out of the title`() {
        val (title, artist) = LyricsService.normalize(
            "The Weeknd - Blinding Lights (Official Video)",
            "TheWeekndVEVO"
        )
        assertEquals("Blinding Lights", title)
        assertEquals("The Weeknd", artist)
    }

    @Test
    fun `splits a run-together channel name into words`() {
        val (_, artist) = LyricsService.normalize("Some Song", "ArcticMonkeys")
        assertEquals("Arctic Monkeys", artist)
    }

    @Test
    fun `removes the Topic suffix Auto-generated channels carry`() {
        val (_, artist) = LyricsService.normalize("Song Name", "Daft Punk - Topic")
        assertEquals("Daft Punk", artist)
    }

    @Test
    fun `drops featuring credits that break exact matching`() {
        val (title, _) = LyricsService.normalize(
            "Starboy (feat. Daft Punk) [Official Music Video]",
            "TheWeekndVEVO"
        )
        assertEquals("Starboy", title)
    }

    @Test
    fun `handles bracketed lyric tags`() {
        val (title, _) = LyricsService.normalize("Levitating [Official Audio]", "Dua Lipa")
        assertEquals("Levitating", title)
    }

    @Test
    fun `leaves already-clean metadata untouched`() {
        val (title, artist) = LyricsService.normalize("Blinding Lights", "The Weeknd")
        assertEquals("Blinding Lights", title)
        assertEquals("The Weeknd", artist)
    }

    @Test
    fun `parses centisecond and millisecond LRC timestamps`() {
        val service = LyricsService()
        val lines = service.parseLrc(
            """
            [00:13.13] Yeah
            [00:15.500] I've been tryna call
            [01:02.75] I said, ooh
            """.trimIndent()
        )

        assertEquals(3, lines.size)
        assertEquals(13_130L, lines[0].timestampMs)
        assertEquals(15_500L, lines[1].timestampMs)
        assertEquals(62_750L, lines[2].timestampMs)
        assertEquals("Yeah", lines[0].text)
    }

    @Test
    fun `LRC lines come back in chronological order`() {
        val service = LyricsService()
        val lines = service.parseLrc("[00:30.00] second\n[00:10.00] first")
        assertTrue(lines[0].timestampMs < lines[1].timestampMs)
        assertEquals("first", lines[0].text)
    }
}
