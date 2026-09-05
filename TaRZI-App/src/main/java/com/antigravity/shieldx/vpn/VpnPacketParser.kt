package com.antigravity.shieldx.vpn

import java.nio.ByteBuffer

/**
 * High-performance, crash-resilient binary packet parser for IPv4 and IPv6 TUN packets.
 * Strictly verifies bounds on headers, offsets, lengths, and handles both UDP and TCP DNS frames.
 */
class VpnPacketParser {

    data class ParsedPacket(
        val ipVersion: Int,
        val protocol: Int, // 17 = UDP, 6 = TCP
        val sourceIp: ByteArray,
        val destIp: ByteArray,
        val sourcePort: Int,
        val destPort: Int,
        val payloadOffset: Int,
        val payloadLength: Int,
        val isTcpDns: Boolean = false,
        val tcpDnsLength: Int = 0
    )

    companion object {
        const val PROTOCOL_TCP = 6
        const val PROTOCOL_UDP = 17
        const val PORT_DNS = 53
        const val PORT_DOT = 853
        const val PORT_DOQ = 784
    }

    /**
     * Parse raw IP packet buffer from TUN interface with defensive bounds checking.
     */
    fun parse(buffer: ByteBuffer, length: Int): ParsedPacket? {
        if (length < 20 || buffer.remaining() < 20) return null
        val position = buffer.position()
        val firstByte = buffer.get(position).toInt() and 0xFF
        val version = (firstByte ushr 4) and 0x0F

        return try {
            when (version) {
                4 -> parseIpv4(buffer, position, length)
                6 -> parseIpv6(buffer, position, length)
                else -> null
            }
        } catch (_: Exception) {
            null // Defensive: Malformed packets never crash the parser
        }
    }

    private fun parseIpv4(buffer: ByteBuffer, offset: Int, length: Int): ParsedPacket? {
        val firstByte = buffer.get(offset).toInt() and 0xFF
        val headerLength = (firstByte and 0x0F) * 4
        if (headerLength < 20 || length < headerLength + 8) return null

        val totalIpLen = buffer.getShort(offset + 2).toInt() and 0xFFFF
        if (totalIpLen < headerLength || totalIpLen > length) return null

        val protocol = buffer.get(offset + 9).toInt() and 0xFF
        val srcIp = ByteArray(4)
        val dstIp = ByteArray(4)

        for (i in 0 until 4) {
            srcIp[i] = buffer.get(offset + 12 + i)
            dstIp[i] = buffer.get(offset + 16 + i)
        }

        val transportOffset = offset + headerLength
        val srcPort = (buffer.getShort(transportOffset).toInt() and 0xFFFF)
        val dstPort = (buffer.getShort(transportOffset + 2).toInt() and 0xFFFF)

        val (payloadOffset, payloadLength, isTcpDns, tcpDnsLen) = when (protocol) {
            PROTOCOL_UDP -> {
                val udpLen = buffer.getShort(transportOffset + 4).toInt() and 0xFFFF
                if (udpLen < 8 || transportOffset + udpLen > offset + totalIpLen) return null
                Quad(transportOffset + 8, udpLen - 8, false, 0)
            }
            PROTOCOL_TCP -> {
                val dataOffset = ((buffer.get(transportOffset + 12).toInt() and 0xF0) ushr 4) * 4
                if (dataOffset < 20 || transportOffset + dataOffset > offset + totalIpLen) return null
                val tcpPayloadOffset = transportOffset + dataOffset
                val tcpPayloadLen = totalIpLen - headerLength - dataOffset

                // Check for TCP DNS (port 53) RFC 7766: 2-byte prefix length
                if ((dstPort == PORT_DNS || srcPort == PORT_DNS) && tcpPayloadLen >= 2) {
                    val dnsMsgLen = buffer.getShort(tcpPayloadOffset).toInt() and 0xFFFF
                    Quad(tcpPayloadOffset + 2, tcpPayloadLen - 2, true, dnsMsgLen)
                } else {
                    Quad(tcpPayloadOffset, tcpPayloadLen, false, 0)
                }
            }
            else -> Quad(transportOffset, length - headerLength, false, 0)
        }

        if (payloadOffset < 0 || payloadLength < 0 || payloadOffset + payloadLength > offset + length) {
            return null
        }

        return ParsedPacket(
            ipVersion = 4,
            protocol = protocol,
            sourceIp = srcIp,
            destIp = dstIp,
            sourcePort = srcPort,
            destPort = dstPort,
            payloadOffset = payloadOffset,
            payloadLength = payloadLength,
            isTcpDns = isTcpDns,
            tcpDnsLength = tcpDnsLen
        )
    }

    private fun parseIpv6(buffer: ByteBuffer, offset: Int, length: Int): ParsedPacket? {
        if (length < 40 + 8) return null
        val nextHeader = buffer.get(offset + 6).toInt() and 0xFF
        val payloadLen = buffer.getShort(offset + 4).toInt() and 0xFFFF
        if (40 + payloadLen > length) return null

        val srcIp = ByteArray(16)
        val dstIp = ByteArray(16)
        for (i in 0 until 16) {
            srcIp[i] = buffer.get(offset + 8 + i)
            dstIp[i] = buffer.get(offset + 24 + i)
        }

        val transportOffset = offset + 40
        val srcPort = (buffer.getShort(transportOffset).toInt() and 0xFFFF)
        val dstPort = (buffer.getShort(transportOffset + 2).toInt() and 0xFFFF)

        val (payloadOffset, payLength, isTcpDns, tcpDnsLen) = when (nextHeader) {
            PROTOCOL_UDP -> {
                val udpLen = buffer.getShort(transportOffset + 4).toInt() and 0xFFFF
                if (udpLen < 8 || transportOffset + udpLen > offset + 40 + payloadLen) return null
                Quad(transportOffset + 8, udpLen - 8, false, 0)
            }
            PROTOCOL_TCP -> {
                val dataOffset = ((buffer.get(transportOffset + 12).toInt() and 0xF0) ushr 4) * 4
                if (dataOffset < 20 || transportOffset + dataOffset > offset + 40 + payloadLen) return null
                val tcpPayloadOffset = transportOffset + dataOffset
                val tcpPayloadLen = payloadLen - dataOffset

                if ((dstPort == PORT_DNS || srcPort == PORT_DNS) && tcpPayloadLen >= 2) {
                    val dnsMsgLen = buffer.getShort(tcpPayloadOffset).toInt() and 0xFFFF
                    Quad(tcpPayloadOffset + 2, tcpPayloadLen - 2, true, dnsMsgLen)
                } else {
                    Quad(tcpPayloadOffset, tcpPayloadLen, false, 0)
                }
            }
            else -> Quad(transportOffset, length - 40, false, 0)
        }

        if (payloadOffset < 0 || payLength < 0 || payloadOffset + payLength > offset + length) {
            return null
        }

        return ParsedPacket(
            ipVersion = 6,
            protocol = nextHeader,
            sourceIp = srcIp,
            destIp = dstIp,
            sourcePort = srcPort,
            destPort = dstPort,
            payloadOffset = payloadOffset,
            payloadLength = payLength,
            isTcpDns = isTcpDns,
            tcpDnsLength = tcpDnsLen
        )
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
