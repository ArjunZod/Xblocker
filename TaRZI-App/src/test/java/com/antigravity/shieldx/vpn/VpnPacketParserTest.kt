package com.antigravity.shieldx.vpn

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer

class VpnPacketParserTest {

    private lateinit var parser: VpnPacketParser

    @Before
    fun setUp() {
        parser = VpnPacketParser()
    }

    private fun constructSampleUdpDnsPacket(): ByteArray {
        val totalLength = 20 + 8 + 12 // IP(20) + UDP(8) + DNS Header(12)
        val buf = ByteBuffer.allocate(totalLength)

        // IPv4 Header
        buf.put(0x45.toByte())
        buf.put(0x00.toByte())
        buf.putShort(totalLength.toShort())
        buf.putShort(0x1234.toShort())
        buf.putShort(0x4000.toShort())
        buf.put(64.toByte())
        buf.put(VpnPacketParser.PROTOCOL_UDP.toByte())
        buf.putShort(0.toShort()) // checksum placeholder
        buf.put(byteArrayOf(10, 254, 1, 2.toByte())) // Src
        buf.put(byteArrayOf(10, 254, 1, 1.toByte())) // Dst

        // UDP Header
        buf.putShort(54321.toShort()) // Src Port
        buf.putShort(53.toShort())    // Dst Port
        buf.putShort(20.toShort())    // Length = 8 + 12
        buf.putShort(0.toShort())

        // Dummy DNS Header
        buf.putShort(0xABCD.toShort()) // TID
        buf.putShort(0x0100.toShort()) // Flags
        buf.putShort(1.toShort())      // QDCOUNT
        buf.putShort(0.toShort())
        buf.putShort(0.toShort())
        buf.putShort(0.toShort())

        return buf.array()
    }

    @Test
    fun testParseUdpDnsPacket() {
        val raw = constructSampleUdpDnsPacket()
        val parsed = parser.parse(ByteBuffer.wrap(raw), raw.size)

        assertNotNull(parsed)
        assertEquals(4, parsed?.ipVersion)
        assertEquals(VpnPacketParser.PROTOCOL_UDP, parsed?.protocol)
        assertEquals(54321, parsed?.sourcePort)
        assertEquals(53, parsed?.destPort)
        assertEquals(28, parsed?.payloadOffset)
        assertEquals(12, parsed?.payloadLength)
    }

    @Test
    fun testBuildTcpResetGeneratesValidRst() {
        val samplePacket = VpnPacketParser.ParsedPacket(
            ipVersion = 4,
            protocol = VpnPacketParser.PROTOCOL_TCP,
            sourceIp = byteArrayOf(10, 0, 0, 2),
            destIp = byteArrayOf(1, 1, 1, 1),
            sourcePort = 43210,
            destPort = 853, // DoT port
            payloadOffset = 40,
            payloadLength = 0,
            isTcpDns = false,
            tcpDnsLength = 0,
            tcpSeq = 1000L,
            tcpAck = 0L,
            tcpFlags = 0x02 // SYN
        )

        val rstBytes = parser.buildTcpReset(samplePacket)
        assertNotNull(rstBytes)
        assertEquals(40, rstBytes?.size)

        val buf = ByteBuffer.wrap(rstBytes!!)
        // Check IP Header
        assertEquals(0x45.toByte(), buf.get(0))
        assertEquals(VpnPacketParser.PROTOCOL_TCP.toByte(), buf.get(9))
        // Verify source IP is 1.1.1.1 and dest is 10.0.0.2
        assertEquals(1.toByte(), buf.get(12))
        assertEquals(10.toByte(), buf.get(16))

        // Check TCP Header
        assertEquals(853.toShort(), buf.getShort(20))   // Src Port = 853
        assertEquals(43210.toShort(), buf.getShort(22)) // Dst Port = 43210
        val flags = buf.get(33).toInt() and 0xFF
        assertTrue("Flags must contain RST bit", (flags and 0x04) != 0)
    }
}
