package com.antigravity.shieldx.policy

import java.net.InetAddress

/**
 * Enforces SafeSearch at the DNS layer across major search engines and media platforms.
 * Returns CNAME overrides or explicit IP redirections only for providers with verified,
 * official SafeSearch VIP endpoints that do not cause TLS certificate mismatches.
 *
 * Official Coverage:
 * - Google (all ccTLDs)   -> forcesafesearch.google.com (216.239.38.120)
 * - YouTube               -> restrict.youtube.com (216.239.38.119)
 * - Bing (all subdomains) -> strict.bing.com (204.79.197.220)
 * - DuckDuckGo            -> safe.duckduckgo.com (52.142.124.215)
 * - Yahoo / Yahoo Japan   -> safe.search.yahoo.com (87.248.98.8)
 * - Yandex (all domains)  -> familysearch.yandex.ru (213.180.193.56)
 */
class SafeSearchEnforcer {

    data class SafeSearchOverride(
        val originalDomain: String,
        val targetCname: String,
        val targetIpV4: ByteArray,
        val targetIpV6: ByteArray?
    )

    companion object {
        // Google SafeSearch
        private val GOOGLE_SAFE_SEARCH_IPV4 = byteArrayOf(216.toByte(), 239.toByte(), 38.toByte(), 120.toByte())
        private val GOOGLE_SAFE_SEARCH_IPV6 = InetAddress.getByName("2001:4860:4802:32::78").address

        // YouTube Restricted Mode
        private val YOUTUBE_RESTRICT_IPV4 = byteArrayOf(216.toByte(), 239.toByte(), 38.toByte(), 119.toByte())
        private val YOUTUBE_RESTRICT_IPV6 = InetAddress.getByName("2001:4860:4802:32::77").address

        // Bing Strict
        private val BING_STRICT_IPV4 = byteArrayOf(204.toByte(), 79.toByte(), 197.toByte(), 220.toByte())
        private val BING_STRICT_IPV6: ByteArray? = null

        // DuckDuckGo Safe
        private val DUCKDUCKGO_SAFE_IPV4 = byteArrayOf(52.toByte(), 142.toByte(), 124.toByte(), 215.toByte())
        private val DUCKDUCKGO_SAFE_IPV6: ByteArray? = null

        // Yahoo Safe (safe.search.yahoo.com)
        private val YAHOO_SAFE_IPV4 = byteArrayOf(87.toByte(), 248.toByte(), 98.toByte(), 8.toByte())
        private val YAHOO_SAFE_IPV6: ByteArray? = null

        // Yandex Family (familysearch.yandex.ru)
        private val YANDEX_FAMILY_IPV4 = byteArrayOf(213.toByte(), 180.toByte(), 193.toByte(), 56.toByte())
        private val YANDEX_FAMILY_IPV6: ByteArray? = null

        /**
         * Google ccTLD suffixes. Covers google.co.in, google.co.uk, google.com.au, etc.
         */
        private val GOOGLE_CCTLDS = setOf(
            "com", "co.in", "co.uk", "co.jp", "co.kr", "co.id", "co.za",
            "com.au", "com.br", "com.mx", "com.ar", "com.sg", "com.hk",
            "com.tw", "com.ph", "com.pk", "com.eg", "com.ng", "com.tr",
            "com.my", "com.bd", "com.sa", "com.co", "com.pe", "com.ve",
            "de", "fr", "it", "es", "nl", "be", "pt", "pl", "se", "no",
            "dk", "fi", "at", "ch", "ie", "ru", "ca", "cl", "ro", "cz",
            "hu", "bg", "gr", "hr", "sk", "si", "lt", "lv", "ee", "rs",
            "ua", "kz", "by", "az", "ge", "am", "md", "kg", "tj", "uz",
            "ae", "il", "vn", "th", "la", "mm", "np", "lk", "mv",
            "ke", "tz", "ug", "gh", "cm", "sn", "ci", "dz", "ma", "tn"
        )

        /**
         * Search engines that lack official SafeSearch VIP endpoints.
         * Never rewrite these to Google IP to avoid SSL certificate errors.
         */
        private val UNENFORCEABLE_SEARCH_ENGINES = setOf(
            "search.brave.com",
            "ecosia.org",
            "qwant.com",
            "startpage.com"
        )
    }

    /**
     * Check if a requested query domain matches an official search engine that requires SafeSearch rewrite.
     */
    fun getOverride(domain: String, isIpv6Requested: Boolean = false): SafeSearchOverride? {
        val host = domain.trim().lowercase()

        // 1. Google SafeSearch (*.google.*, google.com, etc.)
        if (isGoogleSearchHost(host)) {
            return SafeSearchOverride(
                originalDomain = host,
                targetCname = "forcesafesearch.google.com",
                targetIpV4 = GOOGLE_SAFE_SEARCH_IPV4,
                targetIpV6 = if (isIpv6Requested) GOOGLE_SAFE_SEARCH_IPV6 else null
            )
        }

        // 2. YouTube Restricted Mode
        if (isYouTubeHost(host)) {
            return SafeSearchOverride(
                originalDomain = host,
                targetCname = "restrict.youtube.com",
                targetIpV4 = YOUTUBE_RESTRICT_IPV4,
                targetIpV6 = if (isIpv6Requested) YOUTUBE_RESTRICT_IPV6 else null
            )
        }

        // 3. Bing Strict SafeSearch
        if (host == "bing.com" || host.endsWith(".bing.com")) {
            return SafeSearchOverride(
                originalDomain = host,
                targetCname = "strict.bing.com",
                targetIpV4 = BING_STRICT_IPV4,
                targetIpV6 = BING_STRICT_IPV6
            )
        }

        // 4. DuckDuckGo SafeSearch
        if (host == "duckduckgo.com" || host.endsWith(".duckduckgo.com")) {
            return SafeSearchOverride(
                originalDomain = host,
                targetCname = "safe.duckduckgo.com",
                targetIpV4 = DUCKDUCKGO_SAFE_IPV4,
                targetIpV6 = DUCKDUCKGO_SAFE_IPV6
            )
        }

        // 5. Yahoo Search (search.yahoo.com, *.search.yahoo.*, yahoo.co.jp, etc.)
        if (isYahooSearchHost(host)) {
            return SafeSearchOverride(
                originalDomain = host,
                targetCname = "safe.search.yahoo.com",
                targetIpV4 = YAHOO_SAFE_IPV4,
                targetIpV6 = YAHOO_SAFE_IPV6
            )
        }

        // 6. Yandex Search (yandex.ru, yandex.com, yandex.com.tr, ya.ru, etc.)
        if (isYandexHost(host)) {
            return SafeSearchOverride(
                originalDomain = host,
                targetCname = "familysearch.yandex.ru",
                targetIpV4 = YANDEX_FAMILY_IPV4,
                targetIpV6 = YANDEX_FAMILY_IPV6
            )
        }

        return null
    }

    /**
     * Checks if this host is a search engine without official SafeSearch VIPs.
     * The policy engine can decide to block or allow+audit based on user configuration.
     */
    fun isUnenforceableSearchEngine(domain: String): Boolean {
        val host = domain.trim().lowercase()
        return UNENFORCEABLE_SEARCH_ENGINES.any { host == it || host.endsWith(".$it") } ||
                host.contains("searx") || host.contains("searxng")
    }

    private fun isGoogleSearchHost(host: String): Boolean {
        if (host == "google.com" || host == "www.google.com" || host.startsWith("google.")) return true
        if (host.contains(".google.")) {
            val parts = host.split('.')
            if (parts.size >= 2 && parts[parts.size - 2] == "google") return true
            if (parts.size >= 3 && parts[parts.size - 3] == "google") return true
        }
        for (tld in GOOGLE_CCTLDS) {
            if (host == "google.$tld" || host == "www.google.$tld") return true
        }
        return false
    }

    private fun isYouTubeHost(host: String): Boolean {
        return host == "youtube.com" ||
                host == "www.youtube.com" ||
                host == "m.youtube.com" ||
                host == "youtubei.googleapis.com" ||
                host == "youtube.googleapis.com" ||
                host == "www.youtube-nocookie.com" ||
                host == "youtu.be" ||
                host == "youtube-nocookie.com" ||
                host.endsWith(".youtube.com") ||
                host.endsWith(".youtu.be")
    }

    private fun isYahooSearchHost(host: String): Boolean {
        if (host == "search.yahoo.com" || host.endsWith(".search.yahoo.com")) return true
        if (host == "yahoo.com" || host == "www.yahoo.com") return true
        if (host == "search.yahoo.co.jp" || host.endsWith(".search.yahoo.co.jp")) return true
        if (host == "yahoo.co.jp" || host == "www.yahoo.co.jp") return true
        if (host.matches(Regex("""(www\.)?yahoo\.[a-z]{2,3}(\.[a-z]{2})?"""))) return true
        return false
    }

    private fun isYandexHost(host: String): Boolean {
        return host == "yandex.ru" || host == "www.yandex.ru" ||
                host == "yandex.com" || host == "www.yandex.com" ||
                host == "yandex.com.tr" || host == "www.yandex.com.tr" ||
                host == "yandex.kz" || host == "yandex.by" ||
                host == "yandex.uz" || host == "yandex.ua" ||
                host == "ya.ru" || host == "www.ya.ru" ||
                host.endsWith(".yandex.ru") ||
                host.endsWith(".yandex.com") ||
                host.endsWith(".yandex.com.tr")
    }
}
