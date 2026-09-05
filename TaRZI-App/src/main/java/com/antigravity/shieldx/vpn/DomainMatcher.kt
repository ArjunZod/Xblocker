package com.antigravity.shieldx.vpn

import com.antigravity.shieldx.core.model.Category
import com.antigravity.shieldx.core.model.PolicyDecision
import com.antigravity.shieldx.core.util.TextNormalizer
import com.antigravity.shieldx.data.local.entities.DomainRuleEntity
import java.net.IDN
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * High-performance Domain Matching Engine.
 * Supports exact domains, suffix/subdomain hierarchy, IDN punycode, Unicode NFKC,
 * homoglyph mapping, TLD matching, and category-aware filtering.
 */
class DomainMatcher {

    private val lock = ReentrantReadWriteLock()
    private val exactAllowlist = ConcurrentHashMap<String, DomainRuleEntity>()
    private val exactBlocklist = ConcurrentHashMap<String, DomainRuleEntity>()
    private val blockedTlds = ConcurrentHashMap<String, Category>()
    private val rootTrie = SuffixTrieNode()

    // Configured active categories to block (defaults to all security & adult categories)
    private val activeBlockedCategories = ConcurrentHashMap.newKeySet<Category>()

    init {
        // Default adult TLDs
        listOf("xxx", "porn", "adult", "sex", "sexy", "cam", "webcam", "dating", "tube").forEach {
            blockedTlds[it.lowercase()] = Category.PORNOGRAPHY
        }
        // Default active categories
        activeBlockedCategories.addAll(
            listOf(
                Category.PORNOGRAPHY,
                Category.NUDITY,
                Category.SEXUAL_SERVICES,
                Category.ADULT_DATING,
                Category.CAM,
                Category.EXPLICIT_STREAMING,
                Category.ADULT_SOCIAL,
                Category.ADULT_FORUM,
                Category.ADULT_SEARCH,
                Category.NSFW_MEDIA,
                Category.OTHER_EXPLICIT,
                Category.MALWARE,
                Category.PHISHING,
                Category.SCAM,
                Category.CRYPTOMINING
            )
        )
    }

    data class MatchResult(
        val isBlocked: Boolean,
        val category: Category,
        val matchedRule: String,
        val decision: PolicyDecision
    )

    /**
     * Configure which categories should be actively blocked.
     */
    fun setActiveCategories(categories: Set<Category>) {
        lock.write {
            activeBlockedCategories.clear()
            activeBlockedCategories.addAll(categories)
        }
    }

    fun isCategoryActive(category: Category): Boolean {
        return activeBlockedCategories.contains(category)
    }

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
                if (activeBlockedCategories.contains(rule.category)) {
                    return MatchResult(true, rule.category, normalized, PolicyDecision.BLOCK)
                }
            }

            // 3. Check Suffix / Subdomain Trie (e.g., m.pornhub.com -> pornhub.com)
            val labels = normalized.split('.').reversed()
            val trieMatch = rootTrie.findLongestPrefix(labels)
            if (trieMatch != null && activeBlockedCategories.contains(trieMatch.category)) {
                return MatchResult(true, trieMatch.category, trieMatch.domain, PolicyDecision.BLOCK)
            }

            // 4. Check Blocked TLDs
            val lastDot = normalized.lastIndexOf('.')
            if (lastDot != -1 && lastDot < normalized.length - 1) {
                val tld = normalized.substring(lastDot + 1)
                blockedTlds[tld]?.let { tldCategory ->
                    if (activeBlockedCategories.contains(tldCategory)) {
                        return MatchResult(true, tldCategory, ".$tld", PolicyDecision.BLOCK)
                    }
                }
            }

            return MatchResult(false, Category.SAFE, "", PolicyDecision.ALLOW)
        }
    }

    /**
     * Defensive domain normalization:
     * 1. Remove null bytes, control characters, leading/trailing whitespace
     * 2. Unicode NFKC normalization
     * 3. Cyrillic/Greek homoglyph resolution to ASCII base characters
     * 4. Strip schemes (http://, https://)
     * 5. Strip paths, queries, and ports
     * 6. Collapse repeated dots and strip trailing dot
     * 7. IDN Punycode conversion
     */
    fun normalizeDomain(rawDomain: String): String {
        if (rawDomain.isBlank()) return ""

        var domain = rawDomain.trim()
            .replace("\u0000", "") // Null bytes
            .replace(Regex("[\\s\\p{Cntrl}]+"), "") // Whitespace and control chars

        // Unicode NFKC normalization
        domain = Normalizer.normalize(domain, Normalizer.Form.NFKC)

        // Homoglyph conversion (e.g. Cyrillic 'р' -> 'p')
        domain = TextNormalizer.normalize(domain)

        domain = domain.lowercase()

        // Strip scheme if present
        if (domain.startsWith("http://")) domain = domain.removePrefix("http://")
        if (domain.startsWith("https://")) domain = domain.removePrefix("https://")

        // Strip path / query
        val slashIndex = domain.indexOf('/')
        if (slashIndex != -1) domain = domain.substring(0, slashIndex)

        // Strip port
        val colonIndex = domain.indexOf(':')
        if (colonIndex != -1) domain = domain.substring(0, colonIndex)

        // Collapse repeated dots and trim edges
        domain = domain.replace(Regex("\\.+"), ".").trim('.')

        if (domain.isEmpty()) return ""

        // IDN Punycode conversion
        return try {
            IDN.toASCII(domain)
        } catch (_: Exception) {
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
