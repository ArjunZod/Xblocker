package com.antigravity.shieldx.vpn

import java.nio.ByteBuffer

/**
 * High-performance, crash-resilient binary packet parser for IPv4 and IPv6 TUN packets.
 * Strictly verifies bounds on headers, offsets, lengths, and handles both UDP and TCP DNS frames,
 * including instant TCP RST synthesis for fast DoH/DoT rejection without 30-second client timeouts.
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
        val tcpDnsLength: Int = 0,
        val tcpSeq: Long = 0L,
        val tcpAck: Long = 0L,
        val tcpFlags: Int = 0
    )

    companion object {
        const val PROTOCOL_TCP = 6
        const val PROTOCOL_UDP = 17
        const val PORT_DNS = 53
        const val PORT_DOT = 853
        const val PORT_DOQ = 784

        fun computeIpChecksum(data: ByteArray, offset: Int, length: Int): Int {
            var sum = 0
            var i = offset
            while (i < offset + length - 1) {
                val word = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
                sum += word
                i += 2
            }
            if (i < offset + length) {
                sum += (data[i].toInt() and 0xFF) shl 8
            }
            while ((sum shr 16) > 0) {
                sum = (sum and 0xFFFF) + (sum shr 16)
            }
            return sum.inv() and 0xFFFF
        }

        fun computeTcpChecksum(
            tcpData: ByteArray,
            tcpOffset: Int,
            tcpLength: Int,
            srcIp: ByteArray,
            dstIp: ByteArray
        ): Int {
            var sum = 0
            // Pseudo-header: Src IP + Dst IP + Zero (1 byte) + Protocol (1 byte) + TCP Length (2 bytes)
            for (i in 0 until 4 step 2) {
                sum += ((srcIp[i].toInt() and 0xFF) shl 8) or (srcIp[i + 1].toInt() and 0xFF)
                sum += ((dstIp[i].toInt() and 0xFF) shl 8) or (dstIp[i + 1].toInt() and 0xFF)
            }
            sum += PROTOCOL_TCP
            sum += tcpLength

            var i = tcpOffset
            while (i < tcpOffset + tcpLength - 1) {
                if (i != tcpOffset + 16) { // Skip checksum field itself
                    sum += ((tcpData[i].toInt() and 0xFF) shl 8) or (tcpData[i + 1].toInt() and 0xFF)
                }
                i += 2
            }
            if (i < tcpOffset + tcpLength) {
                sum += (tcpData[i].toInt() and 0xFF) shl 8
            }

            while ((sum shr 16) > 0) {
                sum = (sum and 0xFFFF) + (sum shr 16)
            }
            return sum.inv() and 0xFFFF
        }
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

        var tcpSeq = 0L
        var tcpAck = 0L
        var tcpFlags = 0

        val (payloadOffset, payloadLength, isTcpDns, tcpDnsLen) = when (protocol) {
            PROTOCOL_UDP -> {
                val udpLen = buffer.getShort(transportOffset + 4).toInt() and 0xFFFF
                if (udpLen < 8 || transportOffset + udpLen > offset + totalIpLen) return null
                Quad(transportOffset + 8, udpLen - 8, false, 0)
            }
            PROTOCOL_TCP -> {
                val dataOffset = ((buffer.get(transportOffset + 12).toInt() and 0xF0) ushr 4) * 4
                if (dataOffset < 20 || transportOffset + dataOffset > offset + totalIpLen) return null
                
                tcpSeq = buffer.getInt(transportOffset + 4).toLong() and 0xFFFFFFFFL
                tcpAck = buffer.getInt(transportOffset + 8).toLong() and 0xFFFFFFFFL
                tcpFlags = buffer.get(transportOffset + 13).toInt() and 0xFF

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
            tcpDnsLength = tcpDnsLen,
            tcpSeq = tcpSeq,
            tcpAck = tcpAck,
            tcpFlags = tcpFlags
        )
    }

    private fun parseIpv6(buffer: ByteBuffer, offset: Int, length: Int): ParsedPacket? {
        if (length < 40 + 8) return null
        val payloadLen = buffer.getShort(offset + 4).toInt() and 0xFFFF
        if (40 + payloadLen > length) return null

        val nextHeader = buffer.get(offset + 6).toInt() and 0xFF
        val srcIp = ByteArray(16)
        val dstIp = ByteArray(16)

        for (i in 0 until 16) {
            srcIp[i] = buffer.get(offset + 8 + i)
            dstIp[i] = buffer.get(offset + 24 + i)
        }

        val transportOffset = offset + 40
        val srcPort = buffer.getShort(transportOffset).toInt() and 0xFFFF
        val dstPort = buffer.getShort(transportOffset + 2).toInt() and 0xFFFF

        var tcpSeq = 0L
        var tcpAck = 0L
        var tcpFlags = 0

        val (payloadOffset, payloadLength, isTcpDns, tcpDnsLen) = when (nextHeader) {
            PROTOCOL_UDP -> {
                val udpLen = buffer.getShort(transportOffset + 4).toInt() and 0xFFFF
                if (udpLen < 8 || transportOffset + udpLen > offset + 40 + payloadLen) return null
                Quad(transportOffset + 8, udpLen - 8, false, 0)
            }
            PROTOCOL_TCP -> {
                val dataOffset = ((buffer.get(transportOffset + 12).toInt() and 0xF0) ushr 4) * 4
                if (dataOffset < 20 || transportOffset + dataOffset > offset + 40 + payloadLen) return null
                
                tcpSeq = buffer.getInt(transportOffset + 4).toLong() and 0xFFFFFFFFL
                tcpAck = buffer.getInt(transportOffset + 8).toLong() and 0xFFFFFFFFL
                tcpFlags = buffer.get(transportOffset + 13).toInt() and 0xFF

                val tcpPayloadOffset = transportOffset + dataOffset
                val tcpPayloadLen = payloadLen - dataOffset
                if ((dstPort == PORT_DNS || srcPort == PORT_DNS) && tcpPayloadLen >= 2) {
                    val dnsMsgLen = buffer.getShort(tcpPayloadOffset).toInt() and 0xFFFF
                    Quad(tcpPayloadOffset + 2, tcpPayloadLen - 2, true, dnsMsgLen)
                } else {
                    Quad(tcpPayloadOffset, tcpPayloadLen, false, 0)
                }
            }
            else -> Quad(transportOffset, payloadLen, false, 0)
        }

        if (payloadOffset < 0 || payloadLength < 0 || payloadOffset + payloadLength > offset + length) {
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
            payloadLength = payloadLength,
            isTcpDns = isTcpDns,
            tcpDnsLength = tcpDnsLen,
            tcpSeq = tcpSeq,
            tcpAck = tcpAck,
            tcpFlags = tcpFlags
        )
    }

    /**
     * Synthesizes an immediate TCP RST packet.
     * Tells clients to instantly abort without hanging for 15-30 seconds.
     */
    fun buildTcpReset(packet: ParsedPacket): ByteArray? {
        if (packet.protocol != PROTOCOL_TCP || packet.ipVersion != 4) return null
        val totalLength = 40 // 20 IP + 20 TCP
        val buf = ByteBuffer.allocate(totalLength)

        // IPv4 Header
        buf.put(0x45.toByte())
        buf.put(0x00.toByte())
        buf.putShort(totalLength.toShort())
        buf.putShort(0.toShort())
        buf.putShort(0x4000.toShort()) // Don't Fragment
        buf.put(64.toByte()) // TTL
        buf.put(PROTOCOL_TCP.toByte())
        buf.putShort(0.toShort()) // Checksum placeholder
        buf.put(packet.destIp) // Src IP = incoming Dst IP
        buf.put(packet.sourceIp) // Dst IP = incoming Src IP

        val ipChecksum = computeIpChecksum(buf.array(), 0, 20)
        buf.putShort(10, ipChecksum.toShort())

        // TCP Header (offset 20)
        buf.putShort(packet.destPort.toShort())
        buf.putShort(packet.sourcePort.toShort())

        val isAck = (packet.tcpFlags and 0x10) != 0
        val isSyn = (packet.tcpFlags and 0x02) != 0

        if (isAck) {
            buf.putInt(packet.tcpAck.toInt())
            buf.putInt(0)
            buf.put(0x50.toByte()) // Data Offset 5 (20 bytes)
            buf.put(0x04.toByte()) // RST
        } else {
            buf.putInt(0)
            val ackNum = packet.tcpSeq + if (isSyn) 1 else packet.payloadLength.coerceAtLeast(1)
            buf.putInt(ackNum.toInt())
            buf.put(0x50.toByte()) // Data Offset 5 (20 bytes)
            buf.put(0x14.toByte()) // RST | ACK
        }

        buf.putShort(0.toShort()) // Window Size = 0
        buf.putShort(0.toShort()) // Checksum placeholder
        buf.putShort(0.toShort()) // Urgent Pointer

        val tcpChecksum = computeTcpChecksum(buf.array(), 20, 20, packet.destIp, packet.sourceIp)
        buf.putShort(36, tcpChecksum.toShort())

        return buf.array()
    }

    private data class Quad(
        val first: Int,
        val second: Int,
        val third: Boolean,
        val fourth: Int
    )
}
