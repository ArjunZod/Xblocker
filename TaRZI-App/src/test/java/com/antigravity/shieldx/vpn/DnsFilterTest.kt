package com.antigravity.shieldx.vpn

import com.antigravity.shieldx.core.model.Category
import com.antigravity.shieldx.core.model.PolicyDecision
import com.antigravity.shieldx.data.local.entities.DomainRuleEntity
import com.antigravity.shieldx.policy.SafeSearchEnforcer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class DnsFilterTest {

    private lateinit var domainMatcher: DomainMatcher
    private lateinit var safeSearchEnforcer: SafeSearchEnforcer
    private lateinit var dnsFilter: DnsFilter

    @Before
    fun setUp() {
        domainMatcher = DomainMatcher()
        safeSearchEnforcer = SafeSearchEnforcer()
        dnsFilter = DnsFilter(domainMatcher, safeSearchEnforcer)

        domainMatcher.loadRules(
            listOf(
                DomainRuleEntity(
                    domain = "blocked-porn.com",
                    category = Category.PORNOGRAPHY,
                    action = PolicyDecision.BLOCK
                )
            )
        )
    }

    private fun constructSampleDnsQuery(domain: String, qType: Int = 1): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)

        dos.writeShort(0x1234) // Transaction ID
        dos.writeShort(0x0100) // Standard query
        dos.writeShort(1) // QDCOUNT
        dos.writeShort(0) // ANCOUNT
        dos.writeShort(0) // NSCOUNT
        dos.writeShort(0) // ARCOUNT

        val labels = domain.split('.')
        for (l in labels) {
            val bytes = l.toByteArray(Charsets.US_ASCII)
            dos.writeByte(bytes.size)
            dos.write(bytes)
        }
        dos.writeByte(0)
        dos.writeShort(qType) // QTYPE
        dos.writeShort(1) // QCLASS = IN

        dos.flush()
        return baos.toByteArray()
    }

    @Test
    fun testParseDnsQuery() {
        val queryBytes = constructSampleDnsQuery("example.com", 1)
        val query = dnsFilter.parseQuery(queryBytes, 0, queryBytes.size)

        assertNotNull(query)
        assertEquals(0x1234, query?.transactionId)
        assertEquals("example.com", query?.qName)
        assertEquals(1, query?.qType)
    }

    @Test
    fun testBlockedDomainEvaluation() {
        val queryBytes = constructSampleDnsQuery("blocked-porn.com", 1)
        val query = dnsFilter.parseQuery(queryBytes, 0, queryBytes.size)!!

        val result = dnsFilter.evaluate(query, safeSearchEnabled = true)
        assertEquals(PolicyDecision.BLOCK, result.action)
        assertEquals(Category.PORNOGRAPHY, result.category)
        assertNotNull(result.responseBytes)
    }

    @Test
    fun testSafeSearchRewriteEvaluation() {
        val queryBytes = constructSampleDnsQuery("google.com", 1)
        val query = dnsFilter.parseQuery(queryBytes, 0, queryBytes.size)!!

        val result = dnsFilter.evaluate(query, safeSearchEnabled = true)
        assertEquals(PolicyDecision.RESTRICT, result.action)
        assertNotNull(result.responseBytes)
    }

    @Test
    fun testAllowedDomainEvaluation() {
        val queryBytes = constructSampleDnsQuery("wikipedia.org", 1)
        val query = dnsFilter.parseQuery(queryBytes, 0, queryBytes.size)!!

        val result = dnsFilter.evaluate(query, safeSearchEnabled = true)
        assertEquals(PolicyDecision.ALLOW, result.action)
    }
}
