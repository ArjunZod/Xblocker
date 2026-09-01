package com.antigravity.shieldx.vpn

import java.nio.ByteBuffer

/**
 * High-performance binary packet parser for IPv4 and IPv6 TUN packets.
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
        val payloadLength: Int
    )

    companion object {
        const val PROTOCOL_TCP = 6
        const val PROTOCOL_UDP = 17
        const val PORT_DNS = 53
    }

    /**
     * Parse raw IP packet buffer from TUN interface.
     */
    fun parse(buffer: ByteBuffer, length: Int): ParsedPacket? {
        if (length < 20) return null
        val position = buffer.position()
        val firstByte = buffer.get(position).toInt() and 0xFF
        val version = (firstByte ushr 4) and 0x0F

        return when (version) {
            4 -> parseIpv4(buffer, position, length)
            6 -> parseIpv6(buffer, position, length)
            else -> null
        }
    }

    private fun parseIpv4(buffer: ByteBuffer, offset: Int, length: Int): ParsedPacket? {
        val firstByte = buffer.get(offset).toInt() and 0xFF
        val headerLength = (firstByte and 0x0F) * 4
        if (length < headerLength + 8) return null // Need at least IP + UDP/TCP header

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

        val (payloadOffset, payloadLength) = when (protocol) {
            PROTOCOL_UDP -> {
                val udpLen = buffer.getShort(transportOffset + 4).toInt() and 0xFFFF
                Pair(transportOffset + 8, udpLen - 8)
            }
            PROTOCOL_TCP -> {
                val dataOffset = ((buffer.get(transportOffset + 12).toInt() and 0xF0) ushr 4) * 4
                val totalIpLen = buffer.getShort(offset + 2).toInt() and 0xFFFF
                Pair(transportOffset + dataOffset, totalIpLen - headerLength - dataOffset)
            }
            else -> Pair(transportOffset, length - headerLength)
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
            payloadLength = payloadLength
        )
    }

    private fun parseIpv6(buffer: ByteBuffer, offset: Int, length: Int): ParsedPacket? {
        if (length < 40 + 8) return null
        val nextHeader = buffer.get(offset + 6).toInt() and 0xFF
        val payloadLen = buffer.getShort(offset + 4).toInt() and 0xFFFF

        val srcIp = ByteArray(16)
        val dstIp = ByteArray(16)
        for (i in 0 until 16) {
            srcIp[i] = buffer.get(offset + 8 + i)
            dstIp[i] = buffer.get(offset + 24 + i)
        }

        val transportOffset = offset + 40
        val srcPort = (buffer.getShort(transportOffset).toInt() and 0xFFFF)
        val dstPort = (buffer.getShort(transportOffset + 2).toInt() and 0xFFFF)

        val (payloadOffset, payLength) = when (nextHeader) {
            PROTOCOL_UDP -> {
                val udpLen = buffer.getShort(transportOffset + 4).toInt() and 0xFFFF
                Pair(transportOffset + 8, udpLen - 8)
            }
            PROTOCOL_TCP -> {
                val dataOffset = ((buffer.get(transportOffset + 12).toInt() and 0xF0) ushr 4) * 4
                Pair(transportOffset + dataOffset, payloadLen - dataOffset)
            }
            else -> Pair(transportOffset, length - 40)
        }

        return ParsedPacket(
            ipVersion = 6,
            protocol = nextHeader,
            sourceIp = srcIp,
            destIp = dstIp,
            sourcePort = srcPort,
            destPort = dstPort,
            payloadOffset = payloadOffset,
            payloadLength = payLength
        )
    }
}
