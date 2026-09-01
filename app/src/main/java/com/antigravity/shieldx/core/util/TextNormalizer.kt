package com.antigravity.shieldx.core.util

import java.net.URLDecoder
import java.text.Normalizer
import java.util.regex.Pattern

/**
 * Text normalizer that strips obfuscation, homoglyphs, leetspeak, repeated characters,
 * zero-width spaces, and URL encoding.
 */
object TextNormalizer {

    private val ZERO_WIDTH_PATTERN = Pattern.compile("[\u200B\u200C\u200D\u200E\u200F\uFEFF\u00AD]+")
    private val REPEATED_CHARS_PATTERN = Pattern.compile("(.)\\1{2,}") // 3 or more repeated chars -> 1

    private val HOMOGLYPH_MAP = mapOf(
        'а' to 'a', 'ä' to 'a', 'à' to 'a', 'á' to 'a', 'â' to 'a', 'ã' to 'a', 'å' to 'a', 'α' to 'a', '@' to 'a', '4' to 'a',
        'Ь' to 'b', 'в' to 'b', 'ß' to 'b', '8' to 'b',
        'с' to 'c', 'ç' to 'c', 'ć' to 'c', 'č' to 'c', '©' to 'c',
        'ԁ' to 'd', 'ð' to 'd',
        'е' to 'e', 'ё' to 'e', 'è' to 'e', 'é' to 'e', 'ê' to 'e', 'ë' to 'e', 'ε' to 'e', '3' to 'e', '€' to 'e',
        'ƒ' to 'f',
        'ɡ' to 'g', '9' to 'g',
        'һ' to 'h', '#' to 'h',
        'і' to 'i', 'ї' to 'i', 'í' to 'i', 'ì' to 'i', 'î' to 'i', 'ï' to 'i', 'ι' to 'i', '1' to 'i', '!' to 'i', '|' to 'i',
        'ј' to 'j',
        'к' to 'k',
        'ӏ' to 'l', 'ł' to 'l',
        'м' to 'm',
        'п' to 'n', 'ñ' to 'n',
        'о' to 'o', 'ö' to 'o', 'ò' to 'o', 'ó' to 'o', 'ô' to 'o', 'õ' to 'o', 'ø' to 'o', 'ο' to 'o', '0' to 'o',
        'р' to 'p', 'ρ' to 'p',
        'г' to 'r', '®' to 'r',
        'ѕ' to 's', 'ş' to 's', 'š' to 's', '$' to 's', '5' to 's',
        'т' to 't', 'τ' to 't', '+' to 't', '7' to 't',
        'υ' to 'u', 'ü' to 'u', 'ù' to 'u', 'ú' to 'u', 'û' to 'u', 'µ' to 'u',
        'ν' to 'v',
        'ѡ' to 'w',
        'х' to 'x', 'χ' to 'x', '×' to 'x',
        'у' to 'y', 'ÿ' to 'y', 'ý' to 'y', '¥' to 'y',
        'z' to 'z', '2' to 'z'
    )

    /**
     * Fully normalize input string into clean lowercase ASCII base form.
     */
    fun normalize(input: String): String {
        if (input.isEmpty()) return ""

        // 1. URL percent-decoding
        var text = try {
            URLDecoder.decode(input, Charsets.UTF_8.name())
        } catch (_: Exception) {
            input
        }

        // 2. Strip Zero-Width Characters
        text = ZERO_WIDTH_PATTERN.matcher(text).replaceAll("")

        // 3. Unicode NFKC Normalization
        text = Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase()

        // 4. Homoglyph and Leetspeak Replacement
        val sb = StringBuilder(text.length)
        for (ch in text) {
            sb.append(HOMOGLYPH_MAP[ch] ?: ch)
        }
        text = sb.toString()

        // 5. Collapse Repeated Characters (e.g. "pooooorrrrn" -> "porn")
        text = REPEATED_CHARS_PATTERN.matcher(text).replaceAll("$1")

        return text
    }

    /**
     * Strip all non-alphanumeric characters for compact keyword scanning.
     */
    fun stripPunctuationAndSpacing(input: String): String {
        val normalized = normalize(input)
        return normalized.filter { it.isLetterOrDigit() }
    }
}
