package com.antigravity.shieldx.vpn

import com.antigravity.shieldx.core.model.Category
import com.antigravity.shieldx.core.model.MatchType
import com.antigravity.shieldx.core.model.PolicyDecision
import com.antigravity.shieldx.data.local.entities.DomainRuleEntity
import java.net.IDN
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * High-performance Domain Matching Engine.
 * Supports exact domains, subdomain hierarchy matching, TLD matching, and wildcard rules.
 * Thread-safe for multi-threaded VPN packet/DNS processing.
 */
class DomainMatcher {

    private val lock = ReentrantReadWriteLock()
    private val exactAllowlist = ConcurrentHashMap<String, DomainRuleEntity>()
    private val exactBlocklist = ConcurrentHashMap<String, DomainRuleEntity>()
    private val blockedTlds = ConcurrentHashMap.newKeySet<String>()
    private val rootTrie = SuffixTrieNode()

    init {
        // Default adult-specific TLDs
        listOf("xxx", "porn", "adult", "sex", "sexy", "cam", "webcam", "dating", "tube").forEach {
            blockedTlds.add(it.lowercase())
        }
    }

    data class MatchResult(
        val isBlocked: Boolean,
        val category: Category,
        val matchedRule: String,
        val decision: PolicyDecision
    )

    /**
     * Load or replace all rules into in-memory fast matching structures.
     */
    fun loadRules(rules: List<DomainRuleEntity>) {
        lock.write {
            exactAllowlist.clear()
            exactBlocklist.clear()
            rootTrie.clear()

            for (rule in rules) {
                val normalizedDomain = normalizeDomain(rule.domain)
                if (normalizedDomain.isEmpty()) continue

                if (rule.action == PolicyDecision.ALLOW) {
                    exactAllowlist[normalizedDomain] = rule
                } else {
                    exactBlocklist[normalizedDomain] = rule
                    // Insert reversed domain labels into Suffix Trie for subdomain matching
                    val labels = normalizedDomain.split('.').reversed()
                    rootTrie.insert(labels, rule)
                }
            }
        }
    }

    /**
     * Match a requested hostname against allowlist, blocklist, suffix trie, and TLD rules.
     */
    fun match(hostname: String): MatchResult {
        val normalized = normalizeDomain(hostname)
        if (normalized.isEmpty()) {
            return MatchResult(false, Category.SAFE, "", PolicyDecision.ALLOW)
        }

        lock.read {
            // 1. Check Allowlist Overrides (Highest Precedence)
            if (exactAllowlist.containsKey(normalized)) {
                return MatchResult(false, Category.SAFE, normalized, PolicyDecision.ALLOW)
            }

            // 2. Check Exact Blocklist
            exactBlocklist[normalized]?.let { rule ->
                return MatchResult(true, rule.category, normalized, PolicyDecision.BLOCK)
            }

            // 3. Check Suffix / Subdomain Trie (e.g., m.pornhub.com -> pornhub.com)
            val labels = normalized.split('.').reversed()
            val trieMatch = rootTrie.findLongestPrefix(labels)
            if (trieMatch != null) {
                return MatchResult(true, trieMatch.category, trieMatch.domain, PolicyDecision.BLOCK)
            }

            // 4. Check Adult TLDs
            val lastDot = normalized.lastIndexOf('.')
            if (lastDot != -1 && lastDot < normalized.length - 1) {
                val tld = normalized.substring(lastDot + 1)
                if (blockedTlds.contains(tld)) {
                    return MatchResult(true, Category.PORNOGRAPHY, ".$tld", PolicyDecision.BLOCK)
                }
            }

            return MatchResult(false, Category.SAFE, "", PolicyDecision.ALLOW)
        }
    }

    /**
     * Normalize domain name: lowercase, trim, IDN punycode decode, strip trailing dot and port.
     */
    fun normalizeDomain(rawDomain: String): String {
        var domain = rawDomain.trim().lowercase()
        // Strip scheme if accidentally present
        if (domain.startsWith("http://")) domain = domain.removePrefix("http://")
        if (domain.startsWith("https://")) domain = domain.removePrefix("https://")
        // Strip path / query
        val slashIndex = domain.indexOf('/')
        if (slashIndex != -1) domain = domain.substring(0, slashIndex)
        // Strip port
        val colonIndex = domain.indexOf(':')
        if (colonIndex != -1) domain = domain.substring(0, colonIndex)
        // Strip trailing dot
        domain = domain.trimEnd('.')
        // IDN Punycode conversion
        return try {
            IDN.toASCII(domain)
        } catch (e: Exception) {
            domain
        }
    }

    /**
     * Suffix Trie Node for sub-millisecond reverse-domain label hierarchy lookups.
     */
    private class SuffixTrieNode {
        private val children = HashMap<String, SuffixTrieNode>()
        var rule: DomainRuleEntity? = null

        fun clear() {
            children.clear()
            rule = null
        }

        fun insert(labels: List<String>, ruleEntity: DomainRuleEntity, index: Int = 0) {
            if (index >= labels.size) {
                this.rule = ruleEntity
                return
            }
            val label = labels[index]
            val child = children.getOrPut(label) { SuffixTrieNode() }
            child.insert(labels, ruleEntity, index + 1)
        }

        fun findLongestPrefix(labels: List<String>, index: Int = 0): DomainRuleEntity? {
            if (this.rule != null) {
                return this.rule
            }
            if (index >= labels.size) {
                return null
            }
            val label = labels[index]
            val child = children[label] ?: return null
            return child.findLongestPrefix(labels, index + 1) ?: this.rule
        }
    }
}
