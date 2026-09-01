package com.antigravity.shieldx.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Encrypted DNS was the single hole that made the whole content filter
 * decorative: Chrome's default DoH never touched the plaintext DNS this VPN
 * inspects, so blocked domains resolved anyway. These lock the fix in place.
 */
class EncryptedDnsBlockerTest {

    private fun ip(a: Int, b: Int, c: Int, d: Int) =
        byteArrayOf(a.toByte(), b.toByte(), c.toByte(), d.toByte())

    @Test
    fun `recognises the resolvers browsers auto-upgrade to`() {
        assertTrue("Cloudflare", EncryptedDnsBlocker.isEncryptedDnsEndpoint(ip(1, 1, 1, 1)))
        assertTrue("Google", EncryptedDnsBlocker.isEncryptedDnsEndpoint(ip(8, 8, 8, 8)))
        assertTrue("Google secondary", EncryptedDnsBlocker.isEncryptedDnsEndpoint(ip(8, 8, 4, 4)))
        assertTrue("OpenDNS", EncryptedDnsBlocker.isEncryptedDnsEndpoint(ip(208, 67, 222, 222)))
        assertTrue("AdGuard", EncryptedDnsBlocker.isEncryptedDnsEndpoint(ip(94, 140, 14, 14)))
    }

    @Test
    fun `leaves ordinary traffic alone`() {
        assertFalse(EncryptedDnsBlocker.isEncryptedDnsEndpoint(ip(142, 250, 190, 46)))
        assertFalse(EncryptedDnsBlocker.isEncryptedDnsEndpoint(ip(10, 254, 1, 1)))
        assertFalse(EncryptedDnsBlocker.isEncryptedDnsEndpoint(ip(192, 168, 1, 1)))
    }

    @Test
    fun `does not cut off the family-filtering resolver we rely on`() {
        // 1.1.1.3 is Cloudflare Family, this app's own upstream. Blocking it
        // would break the filter it is meant to enforce.
        assertFalse(EncryptedDnsBlocker.isEncryptedDnsEndpoint(ip(1, 1, 1, 3)))
    }

    @Test
    fun `drops DNS-over-TLS regardless of destination`() {
        assertTrue(
            EncryptedDnsBlocker.shouldDrop(
                ip(203, 0, 113, 5),
                EncryptedDnsBlocker.PORT_DOT,
                VpnPacketParser.PROTOCOL_TCP
            )
        )
    }

    @Test
    fun `drops DoH on 443 only for known resolvers`() {
        assertTrue(
            "Cloudflare DoH must be dropped",
            EncryptedDnsBlocker.shouldDrop(ip(1, 1, 1, 1), 443, VpnPacketParser.PROTOCOL_TCP)
        )
        assertFalse(
            "ordinary HTTPS must pass",
            EncryptedDnsBlocker.shouldDrop(ip(142, 250, 190, 46), 443, VpnPacketParser.PROTOCOL_TCP)
        )
    }

    @Test
    fun `blocks DoH bootstrap hostnames`() {
        listOf(
            "dns.google",
            "cloudflare-dns.com",
            "mozilla.cloudflare-dns.com",
            "dns.quad9.net",
            "doh.opendns.com",
            "dns.nextdns.io"
        ).forEach {
            assertTrue("should block $it", EncryptedDnsBlocker.isEncryptedDnsHostname(it))
        }
    }

    @Test
    fun `hostname matching is case and trailing-dot tolerant`() {
        assertTrue(EncryptedDnsBlocker.isEncryptedDnsHostname("DNS.Google"))
        assertTrue(EncryptedDnsBlocker.isEncryptedDnsHostname("cloudflare-dns.com."))
    }

    @Test
    fun `catches per-account resolver subdomains`() {
        assertTrue(EncryptedDnsBlocker.isEncryptedDnsHostname("abc123.dns.nextdns.io"))
        assertTrue(EncryptedDnsBlocker.isEncryptedDnsHostname("myprofile.zero.dns0.eu"))
    }

    @Test
    fun `ordinary hostnames are untouched`() {
        listOf("google.com", "youtube.com", "wikipedia.org", "mydns.example.com").forEach {
            assertFalse("should not block $it", EncryptedDnsBlocker.isEncryptedDnsHostname(it))
        }
    }

    @Test
    fun `every routed endpoint is one the blocker recognises`() {
        val routes = EncryptedDnsBlocker.routableEndpoints()
        assertTrue("expected resolver routes", routes.isNotEmpty())
        routes.forEach { addr ->
            val parts = addr.split(".").map { it.toInt() }
            assertTrue(
                "routed $addr but blocker does not match it",
                EncryptedDnsBlocker.isEncryptedDnsEndpoint(ip(parts[0], parts[1], parts[2], parts[3]))
            )
        }
    }
}
