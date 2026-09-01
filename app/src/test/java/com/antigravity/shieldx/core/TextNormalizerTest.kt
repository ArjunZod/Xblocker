package com.antigravity.shieldx.core

import com.antigravity.shieldx.core.util.TextNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextNormalizerTest {

    @Test
    fun testBasicNormalization() {
        val input = "Hello World"
        val normalized = TextNormalizer.normalize(input)
        assertEquals("hello world", normalized)
    }

    @Test
    fun testHomoglyphReplacement() {
        // Cyrillic 'р' (U+0440) and 'о' (U+043E)
        val cyrillicPorn = "\u0440\u043Ern"
        val normalized = TextNormalizer.normalize(cyrillicPorn)
        assertEquals("porn", normalized)
    }

    @Test
    fun testLeetspeakSubstitution() {
        val leet = "p0rn"
        val normalized = TextNormalizer.normalize(leet)
        assertEquals("porn", normalized)

        val leet2 = "h3nt4i"
        val normalized2 = TextNormalizer.normalize(leet2)
        assertEquals("hentai", normalized2)
    }

    @Test
    fun testRepeatedCharactersCollapsing() {
        val repeated = "pooooorrrrn"
        val normalized = TextNormalizer.normalize(repeated)
        assertEquals("porn", normalized)

        val repeatedX = "xxxxxxxxx"
        val normalizedX = TextNormalizer.normalize(repeatedX)
        assertEquals("x", normalizedX)
    }

    @Test
    fun testUrlPercentDecoding() {
        val encoded = "sex%20video"
        val normalized = TextNormalizer.normalize(encoded)
        assertEquals("sex video", normalized)
    }

    @Test
    fun testZeroWidthSpaceRemoval() {
        val zeroWidth = "p\u200B\u200Co\u200Dr\uFEFFn"
        val normalized = TextNormalizer.normalize(zeroWidth)
        assertEquals("porn", normalized)
    }

    @Test
    fun testStripPunctuationAndSpacing() {
        val spaced = "p.o_r-n"
        val stripped = TextNormalizer.stripPunctuationAndSpacing(spaced)
        assertEquals("porn", stripped)
    }
}
