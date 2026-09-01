package com.antigravity.shieldx.core.security

import android.content.Context
import com.antigravity.shieldx.classifier.ClassificationManager
import com.antigravity.shieldx.classifier.LocalContentClassifier
import com.antigravity.shieldx.classifier.RuleClassifier
import com.antigravity.shieldx.classifier.UrlClassifier
import com.antigravity.shieldx.classifier.ai.AICostController
import com.antigravity.shieldx.classifier.ai.AIResponseValidator
import com.antigravity.shieldx.classifier.ai.GeminiClassifier
import com.antigravity.shieldx.core.runtime.ProtectionController
import com.antigravity.shieldx.data.local.AppDatabase
import com.antigravity.shieldx.data.repository.AppPolicyRepository
import com.antigravity.shieldx.data.repository.AuditRepository
import com.antigravity.shieldx.data.repository.DomainRepository
import com.antigravity.shieldx.data.repository.PolicyRepository
import com.antigravity.shieldx.device.AdminRecoveryController
import com.antigravity.shieldx.device.DeviceOwnerController
import com.antigravity.shieldx.device.LockdownController
import com.antigravity.shieldx.device.RestrictionController
import com.antigravity.shieldx.policy.BlocklistManager
import com.antigravity.shieldx.policy.PolicyEngine
import com.antigravity.shieldx.policy.SafeSearchEnforcer
import com.antigravity.shieldx.protection.controller.ProtectionControllerImpl
import com.antigravity.shieldx.tamper.IntegrityMonitor
import com.antigravity.shieldx.tamper.SecurityStateMachine
import com.antigravity.shieldx.tamper.TamperMonitor
import com.antigravity.shieldx.vpn.DomainMatcher

/**
 * Everything TaRZI-App's content-blocking core owns. Split out of
 * SecurityManager so the composition root stops being one 200-line class that
 * hand-wires all three subsystems at once.
 */
class ProtectionGraph(context: Context, database: AppDatabase) {

    val policyRepository = PolicyRepository(database)
    val domainRepository = DomainRepository(context, database)
    val appPolicyRepository = AppPolicyRepository(context, database)
    val auditRepository = AuditRepository(database)
    val configRepository = com.antigravity.shieldx.data.repository.ConfigRepository(database)

    val domainMatcher = DomainMatcher()
    val safeSearchEnforcer = SafeSearchEnforcer()
    val ruleClassifier = RuleClassifier()
    val urlClassifier = UrlClassifier(ruleClassifier)
    val localContentClassifier = LocalContentClassifier(ruleClassifier, urlClassifier)

    val aiResponseValidator = AIResponseValidator()
    val aiCostController = AICostController(database)
    val geminiClassifier = GeminiClassifier(aiCostController, aiResponseValidator)

    val classificationManager = ClassificationManager(
        domainMatcher = domainMatcher,
        ruleClassifier = ruleClassifier,
        urlClassifier = urlClassifier,
        localContentClassifier = localContentClassifier,
        geminiClassifier = geminiClassifier,
        policyRepository = policyRepository
    )

    val stateMachine = SecurityStateMachine(auditRepository)
    val integrityMonitor = IntegrityMonitor()
    val deviceOwnerController = DeviceOwnerController(context)
    val restrictionController = RestrictionController(context, deviceOwnerController)
    val adminRecoveryController = AdminRecoveryController(context)
    val lockdownController = LockdownController(context)
    val tamperMonitor = TamperMonitor(stateMachine, deviceOwnerController, integrityMonitor)

    val policyEngine = PolicyEngine(policyRepository, domainRepository, auditRepository, stateMachine)
    val blocklistManager = BlocklistManager(context, domainRepository)

    val protectionController: ProtectionController = ProtectionControllerImpl(
        context = context,
        policyRepository = policyRepository,
        deviceOwnerController = deviceOwnerController,
        restrictionController = restrictionController,
        lockdownController = lockdownController
    )
}
