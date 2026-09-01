package com.antigravity.shieldx.core.security

import android.content.Context
import com.antigravity.shieldx.assistant.AgentGraph
import com.antigravity.shieldx.core.runtime.EventBus
import com.antigravity.shieldx.core.runtime.MusicController
import com.antigravity.shieldx.core.runtime.ProtectionController
import com.antigravity.shieldx.core.runtime.ServiceRegistry
import com.antigravity.shieldx.data.local.AppDatabase
import com.antigravity.shieldx.music.MusicGraph
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
    private val music = MusicGraph(appContext, protection.policyEngine, database.playHistoryDao())
    private val agent = AgentGraph(
        context = appContext,
        configStore = protection.configRepository,
        auditSink = protection.auditRepository,
        protectionInsights = protection.auditRepository,
        memoryDao = database.memoryDao(),
        automationDao = database.automationDao(),
        networkProfileDao = database.networkProfileDao(),
        eventBus = eventBus,
        protectionController = protection.protectionController,
        musicController = music.musicController
    )

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

    // --- Music subsystem (delegated to MusicGraph) ---
    val innerTubeClient get() = music.innerTubeClient
    val lavalinkNodeManager get() = music.lavalinkNodeManager
    val musicResolver get() = music.musicResolver
    val musicSearchEngine get() = music.musicSearchEngine
    val playHistoryRepository get() = music.playHistoryRepository
    val musicLibraryRepository get() = music.musicLibraryRepository
    val lyricsService get() = music.lyricsService
    val musicController: MusicController get() = music.musicController
    /** Full Music-owned dependency set, for Music's own screens. */
    val musicGraph: MusicGraph get() = music

    // --- Assistant subsystem (delegated to AgentGraph) ---
    var voiceAssistantManager: com.antigravity.shieldx.assistant.voice.VoiceAssistantManager?
        get() = agent.voiceAssistantManager
        set(value) { agent.voiceAssistantManager = value }
    /** Full agent-owned dependency set, for the assistant's own screens/services. */
    val agentGraph: AgentGraph get() = agent
    val commandRegistry get() = agent.commandRegistry
    val confirmationManager get() = agent.confirmationManager
    val intentEngine get() = agent.intentEngine
    val assistantPlanner get() = agent.assistantPlanner
    val deterministicExecutor get() = agent.deterministicExecutor
    val memoryManager get() = agent.memoryManager
    val contextEngine get() = agent.contextEngine
    val automationEngine get() = agent.automationEngine
    val toolHandlers get() = agent.toolHandlers
    val systemControlTools get() = agent.systemControlTools

    init {
        // Register in ServiceRegistry
        ServiceRegistry.register(ProtectionController::class.java, protectionController)
        ServiceRegistry.register(MusicController::class.java, musicController)
        ServiceRegistry.register(EventBus::class.java, eventBus)
        ServiceRegistry.register(AgentGraph::class.java, agent)
        ServiceRegistry.register(com.antigravity.shieldx.assistant.memory.MemoryManager::class.java, memoryManager)
        ServiceRegistry.register(com.antigravity.shieldx.assistant.automation.AutomationEngine::class.java, automationEngine)

        // Register tools across all 8 families
        toolHandlers.registerAll()

        // Screen control: tap, scroll, read, navigate - works in any app
        systemControlTools.registerAll()

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
