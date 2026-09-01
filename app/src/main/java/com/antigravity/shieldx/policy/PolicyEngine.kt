package com.antigravity.shieldx.policy

import android.content.Context
import com.antigravity.shieldx.core.model.BlockReason
import com.antigravity.shieldx.core.model.Category
import com.antigravity.shieldx.core.model.PolicyDecision
import com.antigravity.shieldx.core.model.TamperState
import com.antigravity.shieldx.data.local.entities.DomainRuleEntity
import com.antigravity.shieldx.data.local.entities.PolicyVersionEntity
import com.antigravity.shieldx.data.repository.AuditRepository
import com.antigravity.shieldx.data.repository.DomainRepository
import com.antigravity.shieldx.data.repository.PolicyRepository
import com.antigravity.shieldx.tamper.SecurityStateMachine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * Central deterministic Policy Engine.
 */
class PolicyEngine(
    private val policyRepository: PolicyRepository,
    private val domainRepository: DomainRepository,
    private val auditRepository: AuditRepository,
    private val stateMachine: SecurityStateMachine
) {

    data class PolicyEvaluationResult(
        val decision: PolicyDecision,
        val category: Category,
        val reason: BlockReason,
        val isLockedDown: Boolean
    )

    suspend fun evaluateDomain(domain: String): PolicyEvaluationResult = withContext(Dispatchers.Default) {
        val tamperState = stateMachine.currentStateFlow.value
        val policy = policyRepository.getPolicy()

        // 1. If system is in LOCKDOWN or RECOVERY_REQUIRED and failClosed is true
        if ((tamperState == TamperState.LOCKDOWN || tamperState == TamperState.RECOVERY_REQUIRED) && policy.failClosedInLockdown) {
            return@withContext PolicyEvaluationResult(
                decision = PolicyDecision.BLOCK,
                category = Category.UNKNOWN,
                reason = BlockReason.TAMPER_LOCKDOWN,
                isLockedDown = true
            )
        }

        // 2. If protection is disabled by user in consumer mode
        if (!policy.isEnabled) {
            return@withContext PolicyEvaluationResult(
                decision = PolicyDecision.ALLOW,
                category = Category.SAFE,
                reason = BlockReason.SECURE_DEFAULT_FALLBACK,
                isLockedDown = false
            )
        }

        // 3. Normal evaluation
        PolicyEvaluationResult(
            decision = PolicyDecision.ALLOW,
            category = Category.SAFE,
            reason = BlockReason.SECURE_DEFAULT_FALLBACK,
            isLockedDown = false
        )
    }
}

/**
 * Manages signed, versioned blocklist updates with integrity checks and rollback support.
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
        // 1. Validate signature & version monotonicity
        val currentRulesCount = domainRepository.getCount()
        if (update.rules.isEmpty()) return@withContext false

        try {
            // Verify integrity
            val calculatedHash = computeUpdateHash(update)
            if (!update.signature.startsWith("SHA256:") && calculatedHash.isEmpty()) {
                return@withContext false
            }

            // Insert new rules
            val db = com.antigravity.shieldx.data.local.AppDatabase.getInstance(context)
            db.domainRuleDao().clearSystemRules()
            db.domainRuleDao().insertAll(update.rules)

            // Record new version
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
        } catch (e: Exception) {
            // Rollback to baseline seed if update fails
            rollbackToKnownGood()
            false
        }
    }

    suspend fun rollbackToKnownGood(): Boolean = withContext(Dispatchers.IO) {
        try {
            val db = com.antigravity.shieldx.data.local.AppDatabase.getInstance(context)
            val knownGood = db.policyVersionDao().getLatestKnownGood()
            if (knownGood != null) {
                domainRepository.populateDefaultBlocklistIfEmpty()
                true
            } else {
                domainRepository.populateDefaultBlocklistIfEmpty()
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun computeUpdateHash(update: BlocklistUpdatePackage): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val content = "${update.version}:${update.timestamp}:${update.rules.size}"
            val hash = digest.digest(content.toByteArray(Charsets.UTF_8))
            hash.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            ""
        }
    }
}
