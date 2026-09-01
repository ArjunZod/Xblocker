package com.antigravity.shieldx.core.security

import android.content.Context
import com.antigravity.shieldx.assistant.automation.AutomationEngine
import com.antigravity.shieldx.assistant.commands.CommandRegistry
import com.antigravity.shieldx.assistant.commands.handlers.SystemControlTools
import com.antigravity.shieldx.assistant.commands.handlers.ToolHandlers
import com.antigravity.shieldx.assistant.executor.ConfirmationManager
import com.antigravity.shieldx.assistant.executor.DeterministicExecutor
import com.antigravity.shieldx.assistant.intent.IntentEngine
import com.antigravity.shieldx.assistant.memory.ContextEngine
import com.antigravity.shieldx.assistant.memory.MemoryManager
import com.antigravity.shieldx.assistant.planning.AssistantPlanner
import com.antigravity.shieldx.classifier.ClassificationManager
import com.antigravity.shieldx.classifier.LocalContentClassifier
import com.antigravity.shieldx.classifier.RuleClassifier
import com.antigravity.shieldx.classifier.UrlClassifier
import com.antigravity.shieldx.classifier.ai.AICostController
import com.antigravity.shieldx.classifier.ai.AIResponseValidator
import com.antigravity.shieldx.classifier.ai.GeminiClassifier
import com.antigravity.shieldx.core.runtime.EventBus
import com.antigravity.shieldx.core.runtime.MusicController
import com.antigravity.shieldx.core.runtime.ProtectionController
import com.antigravity.shieldx.core.runtime.ServiceRegistry
import com.antigravity.shieldx.data.local.AppDatabase
import com.antigravity.shieldx.data.repository.AppPolicyRepository
import com.antigravity.shieldx.data.repository.AuditRepository
import com.antigravity.shieldx.data.repository.ConfigRepository
import com.antigravity.shieldx.data.repository.DomainRepository
import com.antigravity.shieldx.data.repository.PolicyRepository
import com.antigravity.shieldx.device.AdminRecoveryController
import com.antigravity.shieldx.device.DeviceOwnerController
import com.antigravity.shieldx.device.LockdownController
import com.antigravity.shieldx.device.RestrictionController
import com.antigravity.shieldx.music.InnerTubeClient
import com.antigravity.shieldx.music.LyricsService
import com.antigravity.shieldx.music.MusicControllerImpl
import com.antigravity.shieldx.policy.BlocklistManager
import com.antigravity.shieldx.policy.PolicyEngine
import com.antigravity.shieldx.policy.SafeSearchEnforcer
import com.antigravity.shieldx.protection.controller.ProtectionControllerImpl
import com.antigravity.shieldx.tamper.IntegrityMonitor
import com.antigravity.shieldx.tamper.SecurityStateMachine
import com.antigravity.shieldx.tamper.TamperMonitor
import com.antigravity.shieldx.vpn.DomainMatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Primary coordinator managing all TaRZI Super-App subsystems.
 */
class SecurityManager private constructor(context: Context) {

    val appContext: Context = context.applicationContext
    val database: AppDatabase = AppDatabase.getInstance(appContext)
    val eventBus: EventBus = EventBus()

    // Data Repositories
    val policyRepository = PolicyRepository(database)
    val domainRepository = DomainRepository(appContext, database)
    val appPolicyRepository = AppPolicyRepository(appContext, database)
    val auditRepository = AuditRepository(database)
    val configRepository = ConfigRepository(database)

    // Protection Subsystem
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
    val deviceOwnerController = DeviceOwnerController(appContext)
    val restrictionController = RestrictionController(appContext, deviceOwnerController)
    val adminRecoveryController = AdminRecoveryController(appContext)
    val lockdownController = LockdownController(appContext)
    val tamperMonitor = TamperMonitor(stateMachine, deviceOwnerController, integrityMonitor)

    val policyEngine = PolicyEngine(policyRepository, domainRepository, auditRepository, stateMachine)
    val blocklistManager = BlocklistManager(appContext, domainRepository)

    val protectionController: ProtectionController = ProtectionControllerImpl(
        context = appContext,
        policyRepository = policyRepository,
        deviceOwnerController = deviceOwnerController,
        restrictionController = restrictionController,
        lockdownController = lockdownController
    )

    // Music Subsystem
    val innerTubeClient = InnerTubeClient()
    val lavalinkNodeManager = com.antigravity.shieldx.music.LavalinkNodeManager()
    val musicResolver = com.antigravity.shieldx.music.MusicIdentifierResolver(lavalinkNodeManager, innerTubeClient)
    val musicSearchEngine = com.antigravity.shieldx.music.TaRziMusicSearchEngine(
        listOf(
            com.antigravity.shieldx.music.YouTubeMusicAdapter(innerTubeClient),
            com.antigravity.shieldx.music.SpotifyCatalogAdapter(),
            com.antigravity.shieldx.music.DeezerCatalogAdapter()
        )
    )
    val playHistoryRepository = com.antigravity.shieldx.music.PlayHistoryRepository(
        database.playHistoryDao()
    )
    val musicLibraryRepository = com.antigravity.shieldx.music.MusicLibraryRepository(
        appContext,
        playHistoryRepository
    )
    val lyricsService = LyricsService()
    val musicController: MusicController = MusicControllerImpl(
        context = appContext,
        searchEngine = musicSearchEngine,
        resolver = musicResolver,
        lyricsService = lyricsService,
        policyGate = policyEngine,
        playHistoryRepository = playHistoryRepository,
        libraryRepository = musicLibraryRepository
    )

    // Assistant Subsystem
    var voiceAssistantManager: com.antigravity.shieldx.assistant.voice.VoiceAssistantManager? = null
    val commandRegistry = CommandRegistry()
    val confirmationManager = ConfirmationManager()
    val intentEngine = IntentEngine()
    val assistantPlanner = AssistantPlanner(intentEngine, commandRegistry)
    val deterministicExecutor = DeterministicExecutor(
        context = appContext,
        commandRegistry = commandRegistry,
        confirmationManager = confirmationManager,
        database = database,
        eventBus = eventBus
    )

    // Memory & Automation
    val memoryManager = MemoryManager(database)
    val contextEngine = ContextEngine()
    val automationEngine = AutomationEngine(database, deterministicExecutor)

    val toolHandlers = ToolHandlers(
        context = appContext,
        commandRegistry = commandRegistry,
        protectionController = protectionController,
        musicController = musicController,
        database = database
    )

    /** Screen-control tools, backed by the accessibility service. */
    val systemControlTools = SystemControlTools(
        context = appContext,
        commandRegistry = commandRegistry
    )

    init {
        // Register in ServiceRegistry
        ServiceRegistry.register(ProtectionController::class.java, protectionController)
        ServiceRegistry.register(MusicController::class.java, musicController)
        ServiceRegistry.register(EventBus::class.java, eventBus)
        ServiceRegistry.register(MemoryManager::class.java, memoryManager)
        ServiceRegistry.register(AutomationEngine::class.java, automationEngine)

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
