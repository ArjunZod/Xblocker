package com.antigravity.shieldx.policy

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SafeSearchEnforcerTest {

    private lateinit var enforcer: SafeSearchEnforcer

    @Before
    fun setUp() {
        enforcer = SafeSearchEnforcer()
    }

    @Test
    fun testGoogleDomainsRewriteToGoogleVip() {
        val googleCom = enforcer.getSafeSearchVipIpv4("google.com")
        val googleCoUk = enforcer.getSafeSearchVipIpv4("www.google.co.uk")
        val googleDe = enforcer.getSafeSearchVipIpv4("google.de")

        assertNotNull(googleCom)
        assertArrayEquals(SafeSearchEnforcer.GOOGLE_SAFESEARCH_IPV4, googleCom)
        assertArrayEquals(SafeSearchEnforcer.GOOGLE_SAFESEARCH_IPV4, googleCoUk)
        assertArrayEquals(SafeSearchEnforcer.GOOGLE_SAFESEARCH_IPV4, googleDe)
    }

    @Test
    fun testBingDomainsRewriteToBingVip() {
        val bing = enforcer.getSafeSearchVipIpv4("bing.com")
        val wwwBing = enforcer.getSafeSearchVipIpv4("www.bing.com")

        assertNotNull(bing)
        assertArrayEquals(SafeSearchEnforcer.BING_STRICT_IPV4, bing)
        assertArrayEquals(SafeSearchEnforcer.BING_STRICT_IPV4, wwwBing)
    }

    @Test
    fun testYouTubeDomainsRewriteToYouTubeVip() {
        val youtube = enforcer.getSafeSearchVipIpv4("youtube.com")
        val mYoutube = enforcer.getSafeSearchVipIpv4("m.youtube.com")

        assertNotNull(youtube)
        assertArrayEquals(SafeSearchEnforcer.YOUTUBE_RESTRICT_IPV4, youtube)
        assertArrayEquals(SafeSearchEnforcer.YOUTUBE_RESTRICT_IPV4, mYoutube)
    }

    @Test
    fun testDuckDuckGoRewritesToSafeDuckDuckGoVip() {
        val ddg = enforcer.getSafeSearchVipIpv4("duckduckgo.com")
        assertNotNull(ddg)
        assertArrayEquals(SafeSearchEnforcer.DUCKDUCKGO_SAFE_IPV4, ddg)
    }

    @Test
    fun testMetaSearchEnginesDoNotRedirectToGoogle() {
        // Critical Anti-Bug test: Ensuring meta search engines (Brave, Startpage, Ecosia)
        // are NOT routed to Google IP 216.239.38.120, which breaks TLS certificates
        val brave = enforcer.getSafeSearchVipIpv4("search.brave.com")
        val startpage = enforcer.getSafeSearchVipIpv4("startpage.com")
        val ecosia = enforcer.getSafeSearchVipIpv4("ecosia.org")
        val qwant = enforcer.getSafeSearchVipIpv4("qwant.com")

        assertNull(brave)
        assertNull(startpage)
        assertNull(ecosia)
        assertNull(qwant)
    }

    @Test
    fun testUnrelatedDomainsReturnNull() {
        val github = enforcer.getSafeSearchVipIpv4("github.com")
        val reddit = enforcer.getSafeSearchVipIpv4("reddit.com")
        val wikipedia = enforcer.getSafeSearchVipIpv4("en.wikipedia.org")

        assertNull(github)
        assertNull(reddit)
        assertNull(wikipedia)
    }
}
