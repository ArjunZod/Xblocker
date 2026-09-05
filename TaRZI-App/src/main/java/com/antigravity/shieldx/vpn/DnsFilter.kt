package com.antigravity.shieldx.vpn

import com.antigravity.shieldx.core.model.BlockReason
import com.antigravity.shieldx.core.model.Category
import com.antigravity.shieldx.core.model.PolicyDecision
import com.antigravity.shieldx.policy.SafeSearchEnforcer
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer

/**
 * High-performance, crash-resilient DNS Packet Parser, Classifier, and Synthesizer.
 * Supports standard UDP DNS as well as TCP DNS (RFC 7766) and integrates with DnsDecisionCache.
 */
class DnsFilter(
    private val domainMatcher: DomainMatcher,
    private val safeSearchEnforcer: SafeSearchEnforcer,
    val decisionCache: DnsDecisionCache = DnsDecisionCache()
) {

    data class DnsQuery(
        val transactionId: Int,
        val flags: Int,
        val qName: String,
        val qType: Int, // 1 = A, 28 = AAAA, 5 = CNAME, etc.
        val qClass: Int,
        val rawDnsBytes: ByteArray,
        val isTcp: Boolean = false
    )

    data class FilterResult(
        val query: DnsQuery,
        val action: PolicyDecision,
        val category: Category,
        val reason: BlockReason?,
        val responseBytes: ByteArray? // If synthetic response is produced
    )

    companion object {
        const val QTYPE_A = 1
        const val QTYPE_AAAA = 28
        val SINKHOLE_IPV4 = byteArrayOf(0, 0, 0, 0)
        val SINKHOLE_IPV6 = ByteArray(16) { 0 }
    }

    /**
     * Parse raw DNS payload bytes with defensive bounds checking.
     * Supports both UDP payload and TCP payload (where [isTcp] is true).
     */
    fun parseQuery(dnsBytes: ByteArray, offset: Int, length: Int, isTcp: Boolean = false): DnsQuery? {
        if (length < 12 || offset + length > dnsBytes.size) return null

        return try {
            val buffer = ByteBuffer.wrap(dnsBytes, offset, length)

            val txId = buffer.short.toInt() and 0xFFFF
            val flags = buffer.short.toInt() and 0xFFFF
            val qdCount = buffer.short.toInt() and 0xFFFF

            if (qdCount < 1) return null // Must contain at least one question

            buffer.short // anCount
            buffer.short // nsCount
            buffer.short // arCount

            // Parse QNAME with strict RFC 1035 bounds
            val domainBuilder = StringBuilder()
            var totalDomainLength = 0

            while (buffer.hasRemaining()) {
                val labelLen = buffer.get().toInt() and 0xFF
                if (labelLen == 0) break // End of QNAME

                // Compression pointer in question section (uncommon, but skip 2nd byte if present)
                if ((labelLen and 0xC0) == 0xC0) {
                    if (buffer.hasRemaining()) buffer.get()
                    break
                }

                // RFC 1035: Label length maximum is 63 octets
                if (labelLen > 63 || buffer.remaining() < labelLen) return null

                totalDomainLength += labelLen + 1
                // RFC 1035: Total domain name maximum is 253 characters
                if (totalDomainLength > 253) return null

                val labelBytes = ByteArray(labelLen)
                buffer.get(labelBytes)

                // Verify label bytes are valid ASCII / non-null
                for (b in labelBytes) {
                    val c = b.toInt() and 0xFF
                    if (c == 0 || (c in 1..31) || c == 127) {
                        // Reject control characters or null bytes in domain label
                        return null
                    }
                }

                if (domainBuilder.isNotEmpty()) domainBuilder.append('.')
                domainBuilder.append(String(labelBytes, Charsets.US_ASCII))
            }

            if (buffer.remaining() < 4) return null
            val qType = buffer.short.toInt() and 0xFFFF
            val qClass = buffer.short.toInt() and 0xFFFF

            val raw = ByteArray(length)
            System.arraycopy(dnsBytes, offset, raw, 0, length)

            DnsQuery(
                transactionId = txId,
                flags = flags,
                qName = domainMatcher.normalizeDomain(domainBuilder.toString()),
                qType = qType,
                qClass = qClass,
                rawDnsBytes = raw,
                isTcp = isTcp
            )
        } catch (_: Exception) {
            null // Defensive: Malformed DNS packet never crashes
        }
    }

    /**
     * Evaluate DNS Query against Cache, Policy, Blocklist, and SafeSearch.
     */
    fun evaluate(query: DnsQuery, safeSearchEnabled: Boolean = true): FilterResult {
        val domain = query.qName

        // 1. Check in-memory TTL decision cache first
        decisionCache.get(domain, query.qType)?.let { cached ->
            return FilterResult(
                query = query,
                action = cached.decision,
                category = cached.category,
                reason = cached.reason,
                responseBytes = if (cached.responseBytes != null) {
                    // Patch query's transaction ID into the cached synthetic response header (first 2 bytes)
                    val patched = cached.responseBytes.clone()
                    if (patched.size >= 2) {
                        patched[0] = (query.transactionId ushr 8).toByte()
                        patched[1] = (query.transactionId and 0xFF).toByte()
                    }
                    patched
                } else null
            )
        }

        // 2. Encrypted-DNS bootstrap hostname check
        if (EncryptedDnsBlocker.isEncryptedDnsHostname(domain)) {
            val synthetic = buildSyntheticDnsResponse(
                query = query,
                ipV4 = SINKHOLE_IPV4,
                ipV6 = SINKHOLE_IPV6,
                ttl = 300
            )
            decisionCache.put(domain, query.qType, PolicyDecision.BLOCK, Category.OTHER_EXPLICIT, BlockReason.SAFESEARCH_ENFORCEMENT, synthetic, 300)
            return FilterResult(
                query = query,
                action = PolicyDecision.BLOCK,
                category = Category.OTHER_EXPLICIT,
                reason = BlockReason.SAFESEARCH_ENFORCEMENT,
                responseBytes = synthetic
            )
        }

        // 3. Check Domain Blocklist / Suffix Trie
        val match = domainMatcher.match(domain)
        if (match.isBlocked) {
            val synthetic = buildSyntheticDnsResponse(
                query = query,
                ipV4 = SINKHOLE_IPV4,
                ipV6 = SINKHOLE_IPV6,
                ttl = 300
            )
            decisionCache.put(domain, query.qType, PolicyDecision.BLOCK, match.category, BlockReason.KNOWN_ADULT_DOMAIN, synthetic, 300)
            return FilterResult(
                query = query,
                action = PolicyDecision.BLOCK,
                category = match.category,
                reason = BlockReason.KNOWN_ADULT_DOMAIN,
                responseBytes = synthetic
            )
        }

        // 4. Check SafeSearch Override
        if (safeSearchEnabled) {
            val override = safeSearchEnforcer.getOverride(domain, isIpv6Requested = query.qType == QTYPE_AAAA)
            if (override != null) {
                val ipV4 = override.targetIpV4
                val ipV6 = override.targetIpV6 ?: SINKHOLE_IPV6
                val synthetic = buildSyntheticDnsResponse(
                    query = query,
                    ipV4 = ipV4,
                    ipV6 = ipV6,
                    ttl = 3600
                )
                decisionCache.put(domain, query.qType, PolicyDecision.RESTRICT, Category.SAFE, BlockReason.SAFESEARCH_ENFORCEMENT, synthetic, 3600)
                return FilterResult(
                    query = query,
                    action = PolicyDecision.RESTRICT,
                    category = Category.SAFE,
                    reason = BlockReason.SAFESEARCH_ENFORCEMENT,
                    responseBytes = synthetic
                )
            }
        }

        // 5. ALLOW (Cache for 60 seconds)
        decisionCache.put(domain, query.qType, PolicyDecision.ALLOW, Category.SAFE, null, null, 60)
        return FilterResult(
            query = query,
            action = PolicyDecision.ALLOW,
            category = Category.SAFE,
            reason = null,
            responseBytes = null
        )
    }

    /**
     * Build standard DNS response packet for A or AAAA questions.
     */
    fun buildSyntheticDnsResponse(
        query: DnsQuery,
        ipV4: ByteArray,
        ipV6: ByteArray,
        ttl: Int = 300
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)

        // 1. Header (12 bytes)
        dos.writeShort(query.transactionId)
        dos.writeShort(0x8180) // QR=1, RD=1, RA=1 (Standard Response, No Error)
        dos.writeShort(1) // QDCOUNT = 1
        dos.writeShort(1) // ANCOUNT = 1
        dos.writeShort(0) // NSCOUNT = 0
        dos.writeShort(0) // ARCOUNT = 0

        // 2. Question Section (write original QNAME)
        val labels = query.qName.split('.')
        for (label in labels) {
            val bytes = label.toByteArray(Charsets.US_ASCII)
            dos.writeByte(bytes.size)
            dos.write(bytes)
        }
        dos.writeByte(0) // Null terminator
        dos.writeShort(query.qType)
        dos.writeShort(query.qClass)

        // 3. Answer Section
        dos.writeShort(0xC00C) // Name pointer to question at offset 12
        dos.writeShort(query.qType)
        dos.writeShort(query.qClass)
        dos.writeInt(ttl) // TTL

        when (query.qType) {
            QTYPE_A -> {
                dos.writeShort(4) // RDLENGTH = 4 bytes (IPv4)
                dos.write(ipV4)
            }
            QTYPE_AAAA -> {
                dos.writeShort(16) // RDLENGTH = 16 bytes (IPv6)
                dos.write(ipV6)
            }
            else -> {
                dos.writeShort(4)
                dos.write(ipV4)
            }
        }

        dos.flush()
        return baos.toByteArray()
    }

    /**
     * Helper to wrap DNS response in IPv4 or IPv6 + UDP packet headers.
     */
    fun wrapIpUdp(
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        dnsPayload: ByteArray,
        ipVersion: Int = if (srcIp.size == 16) 6 else 4
    ): ByteArray {
        return if (ipVersion == 6 || srcIp.size == 16) {
            wrapIpv6Udp(srcIp, dstIp, srcPort, dstPort, dnsPayload)
        } else {
            wrapIpv4Udp(srcIp, dstIp, srcPort, dstPort, dnsPayload)
        }
    }

    /**
     * Helper to wrap DNS response in IPv6 + UDP packet headers.
     */
    fun wrapIpv6Udp(
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        dnsPayload: ByteArray
    ): ByteArray {
        val udpLength = 8 + dnsPayload.size
        val totalLength = 40 + udpLength
        val buffer = ByteBuffer.allocate(totalLength)

        // IPv6 Header (40 bytes)
        buffer.putInt(0x60000000) // Version 6, Traffic Class 0, Flow Label 0
        buffer.putShort(udpLength.toShort()) // Payload Length (UDP header + data)
        buffer.put(VpnPacketParser.PROTOCOL_UDP.toByte()) // Next Header = UDP (17)
        buffer.put(64.toByte()) // Hop Limit = 64
        buffer.put(srcIp) // Source IPv6 (16 bytes)
        buffer.put(dstIp) // Dest IPv6 (16 bytes)

        // UDP Header (8 bytes)
        buffer.putShort(srcPort.toShort())
        buffer.putShort(dstPort.toShort())
        buffer.putShort(udpLength.toShort())
        buffer.putShort(0.toShort()) // Checksum

        // DNS Payload
        buffer.put(dnsPayload)

        return buffer.array()
    }

    /**
     * Helper to wrap DNS response in IPv4 + UDP packet headers.
     */
    fun wrapIpv4Udp(
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        dnsPayload: ByteArray
    ): ByteArray {
        val totalLength = 20 + 8 + dnsPayload.size
        val buffer = ByteBuffer.allocate(totalLength)

        // IPv4 Header (20 bytes)
        buffer.put(0x45.toByte()) // Version 4, IHL 5
        buffer.put(0x00.toByte()) // DSCP / ECN
        buffer.putShort(totalLength.toShort()) // Total Length
        buffer.putShort(0x0000.toShort()) // Identification
        buffer.putShort(0x4000.toShort()) // Don't Fragment
        buffer.put(64.toByte()) // TTL = 64
        buffer.put(VpnPacketParser.PROTOCOL_UDP.toByte()) // Protocol 17
        buffer.putShort(0.toShort()) // Header Checksum placeholder
        buffer.put(srcIp) // Source IP
        buffer.put(dstIp) // Dest IP

        // Compute IPv4 Header Checksum
        val ipChecksum = computeIpChecksum(buffer.array(), 0, 20)
        buffer.putShort(10, ipChecksum.toShort())

        // UDP Header (8 bytes)
        buffer.putShort(srcPort.toShort())
        buffer.putShort(dstPort.toShort())
        buffer.putShort((8 + dnsPayload.size).toShort())
        buffer.putShort(0.toShort()) // UDP Checksum optional in IPv4

        // DNS Payload
        buffer.put(dnsPayload)

        return buffer.array()
    }

    private fun computeIpChecksum(bytes: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        while (i < offset + length) {
            val high = bytes[i].toInt() and 0xFF
            val low = if (i + 1 < offset + length) bytes[i + 1].toInt() and 0xFF else 0
            val word = (high shl 8) or low
            sum += word
            if (sum > 0xFFFF) {
                sum = (sum and 0xFFFF) + 1
            }
            i += 2
        }
        return sum.inv() and 0xFFFF
    }
}
