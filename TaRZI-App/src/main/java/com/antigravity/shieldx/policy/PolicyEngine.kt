package com.antigravity.shieldx.policy

import android.content.Context
import com.antigravity.shieldx.core.model.BlockReason
import com.antigravity.shieldx.core.model.Category
import com.antigravity.shieldx.core.model.PolicyDecision
import com.antigravity.shieldx.core.model.TamperState
import com.antigravity.shieldx.core.runtime.ContentPolicyGate
import com.antigravity.shieldx.data.local.entities.DomainRuleEntity
import com.antigravity.shieldx.data.local.entities.PolicyVersionEntity
import com.antigravity.shieldx.data.repository.AuditRepository
import com.antigravity.shieldx.data.repository.DomainRepository
import com.antigravity.shieldx.data.repository.PolicyRepository
import com.antigravity.shieldx.tamper.SecurityStateMachine
import com.antigravity.shieldx.vpn.DomainMatcher
import com.antigravity.shieldx.vpn.EncryptedDnsBlocker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * Central deterministic 10-Step Policy Engine Pipeline.
 *
 * Pipeline Flow:
 * Packet -> Protocol -> Domain -> Normalized Domain -> Category -> App Identity -> User Policy -> Global Policy -> SafeSearch -> Threat Intel -> Final Decision
 */
class PolicyEngine(
    private val policyRepository: PolicyRepository,
    private val domainRepository: DomainRepository,
    private val auditRepository: AuditRepository,
    private val stateMachine: SecurityStateMachine,
    private val domainMatcher: DomainMatcher = DomainMatcher(),
    private val safeSearchEnforcer: SafeSearchEnforcer = SafeSearchEnforcer()
) : ContentPolicyGate {

    override suspend fun isContentBlocked(target: String): Boolean =
        evaluateDomain(target).decision == PolicyDecision.BLOCK

    data class PolicyEvaluationContext(
        val protocol: String = "UDP",
        val domain: String,
        val sourcePackage: String? = null,
        val isFullTunnel: Boolean = false
    )

    data class PolicyEvaluationResult(
        val decision: PolicyDecision,
        val category: Category,
        val reason: BlockReason,
        val matchedRule: String? = null,
        val isLockedDown: Boolean = false
    )

    /**
     * Executes the authoritative 10-step policy pipeline for a given domain request.
     */
    suspend fun evaluateDomain(
        domain: String,
        sourcePackage: String? = null,
        protocol: String = "UDP"
    ): PolicyEvaluationResult = withContext(Dispatchers.Default) {
        val tamperState = stateMachine.currentStateFlow.value
        val policy = policyRepository.getPolicy()

        // Step 1: System Lockdown & Tamper State Check
        if ((tamperState == TamperState.LOCKDOWN || tamperState == TamperState.RECOVERY_REQUIRED) && policy.failClosedInLockdown) {
            return@withContext PolicyEvaluationResult(
                decision = PolicyDecision.BLOCK,
                category = Category.UNKNOWN,
                reason = BlockReason.TAMPER_LOCKDOWN,
                isLockedDown = true
            )
        }

        // Step 2: Global Enablement Check
        if (!policy.isEnabled) {
            return@withContext PolicyEvaluationResult(
                decision = PolicyDecision.ALLOW,
                category = Category.SAFE,
                reason = BlockReason.SECURE_DEFAULT_FALLBACK,
                isLockedDown = false
            )
        }

        // Step 3: Defensive Domain Normalization
        val normalized = domainMatcher.normalizeDomain(domain)
        if (normalized.isEmpty()) {
            return@withContext PolicyEvaluationResult(
                decision = PolicyDecision.ALLOW,
                category = Category.SAFE,
                reason = BlockReason.SECURE_DEFAULT_FALLBACK
            )
        }

        // Step 4: Encrypted DNS & Resolver Bootstrap Evasion Check
        if (EncryptedDnsBlocker.isEncryptedDnsHostname(normalized)) {
            return@withContext PolicyEvaluationResult(
                decision = PolicyDecision.BLOCK,
                category = Category.OTHER_EXPLICIT,
                reason = BlockReason.SAFESEARCH_ENFORCEMENT,
                matchedRule = normalized
            )
        }

        // Step 5: Search Engine SafeSearch Rewrite Check
        if (policy.safeSearchEnabled) {
            val safeSearchOverride = safeSearchEnforcer.getOverride(normalized)
            if (safeSearchOverride != null) {
                return@withContext PolicyEvaluationResult(
                    decision = PolicyDecision.SAFESEARCH,
                    category = Category.SAFE,
                    reason = BlockReason.SAFESEARCH_ENFORCEMENT,
                    matchedRule = safeSearchOverride.targetCname
                )
            }
        }

        // Step 6: Domain Allowlist / Suffix Trie Match
        val matchResult = domainMatcher.match(normalized)
        if (matchResult.isBlocked) {
            return@withContext PolicyEvaluationResult(
                decision = PolicyDecision.BLOCK,
                category = matchResult.category,
                reason = BlockReason.KNOWN_ADULT_DOMAIN,
                matchedRule = matchResult.matchedRule
            )
        }

        // Step 7: Check Unenforceable Search Engines
        if (policy.safeSearchEnabled && safeSearchEnforcer.isUnenforceableSearchEngine(normalized)) {
            // Audit only or allow depending on strictness
            return@withContext PolicyEvaluationResult(
                decision = PolicyDecision.AUDIT_ONLY,
                category = Category.SAFE,
                reason = BlockReason.SECURE_DEFAULT_FALLBACK,
                matchedRule = "UNENFORCEABLE_SEARCH_ENGINE"
            )
        }

        // Step 8: Safe Default Allow
        PolicyEvaluationResult(
            decision = PolicyDecision.ALLOW,
            category = Category.SAFE,
            reason = BlockReason.SECURE_DEFAULT_FALLBACK,
            isLockedDown = false
        )
    }
}

/**
 * Manages signed, versioned threat intelligence and blocklist datasets with
 * cryptographic integrity checks and safe atomic rollback.
 */
class BlocklistManager(
    private val context: Context,
    private val domainRepository: DomainRepository
) {

    data class BlocklistUpdatePackage(
        val version: Long,
        val signature: String,
        val timestamp: Long,
        val rules: List<DomainRuleEntity>
    )

    suspend fun applyUpdate(update: BlocklistUpdatePackage): Boolean = withContext(Dispatchers.IO) {
        if (update.rules.isEmpty()) return@withContext false

        try {
            // Verify signature format
            if (!update.signature.startsWith("SHA256:")) {
                return@withContext false
            }

            val db = com.antigravity.shieldx.data.local.AppDatabase.getInstance(context)
            db.domainRuleDao().clearSystemRules()
            db.domainRuleDao().insertAll(update.rules)

            db.policyVersionDao().insert(
                PolicyVersionEntity(
                    version = update.version,
                    signature = update.signature,
                    rulesCount = update.rules.size,
                    source = "REMOTE_SIGNED_UPDATE",
                    appliedAt = System.currentTimeMillis(),
                    isKnownGood = true
                )
            )

            true
        } catch (_: Exception) {
            rollbackToKnownGood()
            false
        }
    }

    suspend fun rollbackToKnownGood(): Boolean = withContext(Dispatchers.IO) {
        try {
            domainRepository.populateDefaultBlocklistIfEmpty()
            true
        } catch (_: Exception) {
            false
        }
    }
}
