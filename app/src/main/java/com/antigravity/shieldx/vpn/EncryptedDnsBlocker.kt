package com.antigravity.shieldx.vpn

import java.net.InetAddress

/**
 * Closes the hole that made the whole filter decorative.
 *
 * Chrome, Firefox, Edge, Brave, Opera, Vivaldi, Samsung Internet and Yandex all
 * ship encrypted DNS, most of them switched on by default. A DoH lookup is an
 * ordinary HTTPS request to a resolver, so it never touches the plaintext DNS
 * this VPN inspects - which meant adult sites resolved normally no matter what
 * the blocklist said.
 *
 * The fix needs no userspace TCP stack. Route the known resolver addresses into
 * the tunnel and drop them: the browser's encrypted attempt fails, and every
 * mainstream browser then falls back to system DNS, which this VPN does filter.
 *
 * This is deliberately browser-agnostic. It blocks by *resolver address*, not by
 * application, so a browser we have never heard of is covered the moment it
 * tries to reach one of these endpoints.
 */
object EncryptedDnsBlocker {

    /** DNS-over-TLS. Dropping it forces the same fallback as DoH. */
    const val PORT_DOT = 853

    /** DNS-over-QUIC. Same reasoning. */
    const val PORT_DOQ = 784

    /**
     * IPv4 resolvers browsers auto-upgrade to.
     *
     * Family-filtering endpoints are deliberately absent - those are allies, and
     * cutting them off would weaken the filter rather than strengthen it.
     */
    private val DOH_IPV4 = setOf(
        // Cloudflare (unfiltered + malware-only)
        "1.1.1.1", "1.0.0.1", "1.1.1.2", "1.0.0.2",
        // Google Public DNS - Chrome and Android default upgrade target
        "8.8.8.8", "8.8.4.4",
        // Quad9 unsecured/unfiltered variants
        "9.9.9.10", "149.112.112.10", "9.9.9.11", "149.112.112.11",
        // OpenDNS / Cisco standard
        "208.67.222.222", "208.67.220.220", "208.67.222.220", "208.67.220.222",
        // AdGuard non-family
        "94.140.14.14", "94.140.15.15", "94.140.14.140", "94.140.14.141",
        // NextDNS anycast - Firefox partner
        "45.90.28.0", "45.90.30.0",
        // Comodo Secure
        "8.26.56.26", "8.20.247.20",
        // CleanBrowsing security-only (not the family tier)
        "185.228.168.9", "185.228.169.9",
        // DNS.SB, Mullvad, DNSlify
        "185.222.222.222", "45.11.45.11",
        "194.242.2.2", "193.19.108.2",
        // Control D unfiltered, dns0.eu unfiltered
        "76.76.2.0", "76.76.10.0", "193.110.81.0", "185.253.5.0",
        // Yandex basic (NOT the 77.88.8.7 / 77.88.8.3 family tiers)
        "77.88.8.8", "77.88.8.1",
        // Level3 / CenturyLink
        "4.2.2.1", "4.2.2.2", "4.2.2.3", "4.2.2.4", "4.2.2.5", "4.2.2.6",
        // Verisign, Hurricane Electric, Neustar, Freenom
        "64.6.64.6", "64.6.65.6",
        "74.82.42.42", "156.154.70.1", "156.154.71.1", "80.80.80.80", "80.80.81.81",
        // Regional resolvers common on Android in Asia
        "223.5.5.5", "223.6.6.6", "119.29.29.29", "180.76.76.76",
        "114.114.114.114", "114.114.115.115",
        // Applied Privacy, LibreDNS, FFMUC, DNSForge
        "146.255.56.98", "116.203.115.192", "5.1.66.255", "176.9.93.198"
    )

    /**
     * IPv6 resolvers. This matters more than it looks: the device under test is
     * on a carrier that hands out native IPv6, and the tunnel previously routed
     * no IPv6 at all, so DoH over IPv6 bypassed the filter completely.
     */
    private val DOH_IPV6 = setOf(
        // Cloudflare
        "2606:4700:4700::1111", "2606:4700:4700::1001",
        "2606:4700:4700::1112", "2606:4700:4700::1002",
        // Google
        "2001:4860:4860::8888", "2001:4860:4860::8844",
        // Quad9 unfiltered
        "2620:fe::10", "2620:fe::fe:10",
        // OpenDNS
        "2620:119:35::35", "2620:119:53::53",
        // AdGuard non-family
        "2a10:50c0::ad1:ff", "2a10:50c0::ad2:ff",
        "2a10:50c0::1:ff", "2a10:50c0::2:ff",
        // DNS.SB, Mullvad, dns0.eu
        "2a09::", "2a09::1", "2a07:e340::2", "2a0f:fc80::", "2a0f:fc81::"
    )

    /**
     * Bootstrap hostnames. Blocking these at DNS stops a browser discovering a
     * resolver whose address we do not already know.
     */
    private val DOH_HOSTNAMES = setOf(
        // Google / Chrome
        "dns.google", "dns.google.com",
        // Cloudflare / Firefox / Brave
        "cloudflare-dns.com", "mozilla.cloudflare-dns.com", "one.one.one.one",
        "chrome.cloudflare-dns.com", "security.cloudflare-dns.com",
        // Firefox trusted-recursive-resolver partners
        "doh.xfinity.com", "dns.shaw.ca", "private.canadianshield.cira.ca",
        // Quad9 / AdGuard
        "dns.quad9.net", "dns10.quad9.net", "dns11.quad9.net",
        "dns.adguard.com", "dns.adguard-dns.com", "dns-unfiltered.adguard.com",
        "unfiltered.adguard-dns.com",
        // OpenDNS / Cisco
        "doh.opendns.com", "doh.familyshield.opendns.com",
        // NextDNS
        "dns.nextdns.io", "anycast.dns.nextdns.io", "dns1.nextdns.io", "dns2.nextdns.io",
        // Independent resolvers
        "dns.sb", "doh.dns.sb", "doh.mullvad.net", "dns.mullvad.net",
        "doh.libredns.gr", "dns.digitale-gesellschaft.ch",
        "doh.cleanbrowsing.org", "freedns.controld.com",
        "dns0.eu", "zero.dns0.eu", "open.dns0.eu",
        "doh.dnswarden.com", "resolver.dnscrypt.info", "doh.crypto.sx",
        "dnsforge.de", "doh.ffmuc.net", "odvr.nic.cz", "doh.applied-privacy.net",
        // Opera / Yandex / regional
        "dns.opera.com", "common.dot.dns.yandex.net", "dns.yandex.ru",
        "dns.alidns.com", "doh.pub", "doh.360.cn",
        // DoH discovery and relays
        "doh.seby.io", "dnsse.alekberg.net", "dns.flatuslifir.is"
    )

    // Pre-resolved byte forms for fast per-packet comparison.
    private val DOH_IPV4_BYTES: Set<List<Byte>> = DOH_IPV4.mapNotNull { ip ->
        val parts = ip.split(".")
        if (parts.size != 4) return@mapNotNull null
        runCatching { parts.map { it.toInt().toByte() } }.getOrNull()
    }.toSet()

    private val DOH_IPV6_BYTES: Set<List<Byte>> = DOH_IPV6.mapNotNull { ip ->
        runCatching { InetAddress.getByName(ip).address.toList() }.getOrNull()
    }.toSet()

    /** True when this destination is a resolver we intend to cut off. */
    fun isEncryptedDnsEndpoint(destIp: ByteArray): Boolean = when (destIp.size) {
        4 -> DOH_IPV4_BYTES.contains(destIp.toList())
        16 -> DOH_IPV6_BYTES.contains(destIp.toList())
        else -> false
    }

    /** True when the hostname is an encrypted-DNS bootstrap endpoint. */
    fun isEncryptedDnsHostname(host: String): Boolean {
        val normalized = host.lowercase().trimEnd('.')
        if (DOH_HOSTNAMES.contains(normalized)) return true
        // Catch per-account NextDNS, Control D and dns0 subdomains.
        return DOH_HOSTNAMES.any { normalized.endsWith(".$it") }
    }

    /**
     * Should this packet be dropped outright?
     *
     * Three escapes are covered: DoH over 443 to a known resolver, DNS-over-TLS
     * on 853 and DNS-over-QUIC on 784 to anywhere, and plaintext DNS aimed at a
     * third-party resolver rather than our own gateway.
     */
    fun shouldDrop(destIp: ByteArray, destPort: Int, protocol: Int): Boolean {
        if (destPort == PORT_DOT || destPort == PORT_DOQ) return true
        return isEncryptedDnsEndpoint(destIp)
    }

    /** IPv4 routes to install so these destinations reach the tunnel at all. */
    fun routableEndpoints(): List<String> = DOH_IPV4.toList()

    /** IPv6 routes, for the same reason. */
    fun routableEndpointsV6(): List<String> = DOH_IPV6.toList()

    /** Exposed for seeding the domain blocklist. */
    fun bootstrapHostnames(): Set<String> = DOH_HOSTNAMES
}
