package com.antigravity.shieldx.core.security

import android.content.Context
import com.antigravity.shieldx.core.runtime.EventBus
import com.antigravity.shieldx.core.runtime.ProtectionController
import com.antigravity.shieldx.core.runtime.ServiceRegistry
import com.antigravity.shieldx.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * App-wide composition root. Wires the three subsystem graphs together and
 * exposes their pieces under the same property names screens/services already
 * use, so this decomposition doesn't require touching call sites elsewhere.
 */
class SecurityManager private constructor(context: Context) {

    val appContext: Context = context.applicationContext
    val database: AppDatabase = AppDatabase.getInstance(appContext)
    val eventBus: EventBus = EventBus()

    private val protection = ProtectionGraph(appContext, database)
    // --- Protection subsystem (delegated to ProtectionGraph) ---
    val policyRepository get() = protection.policyRepository
    val domainRepository get() = protection.domainRepository
    val appPolicyRepository get() = protection.appPolicyRepository
    val auditRepository get() = protection.auditRepository
    val configRepository get() = protection.configRepository
    val domainMatcher get() = protection.domainMatcher
    val safeSearchEnforcer get() = protection.safeSearchEnforcer
    val ruleClassifier get() = protection.ruleClassifier
    val urlClassifier get() = protection.urlClassifier
    val localContentClassifier get() = protection.localContentClassifier
    val aiResponseValidator get() = protection.aiResponseValidator
    val aiCostController get() = protection.aiCostController
    val geminiClassifier get() = protection.geminiClassifier
    val classificationManager get() = protection.classificationManager
    val stateMachine get() = protection.stateMachine
    val integrityMonitor get() = protection.integrityMonitor
    val deviceOwnerController get() = protection.deviceOwnerController
    val restrictionController get() = protection.restrictionController
    val adminRecoveryController get() = protection.adminRecoveryController
    val lockdownController get() = protection.lockdownController
    val tamperMonitor get() = protection.tamperMonitor
    val policyEngine get() = protection.policyEngine
    val blocklistManager get() = protection.blocklistManager
    val protectionController: ProtectionController get() = protection.protectionController

    init {
        // Register in ServiceRegistry
        ServiceRegistry.register(ProtectionController::class.java, protectionController)
        ServiceRegistry.register(EventBus::class.java, eventBus)
        // Background seeding & initializations
        CoroutineScope(Dispatchers.IO).launch {
            val key = configRepository.get("gemini_api_key")
            geminiClassifier.setApiKeyProvider { key }

            domainRepository.populateDefaultBlocklistIfEmpty()
            appPolicyRepository.populateDefaultAppPoliciesIfEmpty()

            val rules = domainRepository.getAllRules()
            domainMatcher.loadRules(rules)

            tamperMonitor.startMonitoring()
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: SecurityManager? = null

        fun getInstance(context: Context): SecurityManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SecurityManager(context).also { INSTANCE = it }
            }
        }
    }
}
