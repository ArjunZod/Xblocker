package com.antigravity.shieldx.assistant

import android.app.Application
import com.antigravity.shieldx.assistant.bridge.PrefsConfigStore
import com.antigravity.shieldx.assistant.bridge.RemoteMusicController
import com.antigravity.shieldx.assistant.bridge.RoomAuditSink
import com.antigravity.shieldx.assistant.bridge.UnavailableProtectionController
import com.antigravity.shieldx.assistant.bridge.UnavailableProtectionInsights
import com.antigravity.shieldx.assistant.data.AgentDatabase
import com.antigravity.shieldx.core.runtime.EventBus
import com.antigravity.shieldx.core.runtime.ServiceRegistry

/**
 * Composition root for the standalone TaRZI Assistant app.
 *
 * Same AgentGraph the all-in-one build wires; only the provided services differ
 * — its own config and audit stores, and a music controller that drives the
 * separately installed TaRZI Music app instead of an in-process engine.
 */
class AgentApp : Application() {

    lateinit var graph: AgentGraph
        private set

    lateinit var remoteMusic: RemoteMusicController
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        val db = AgentDatabase.getInstance(this)
        remoteMusic = RemoteMusicController(this).also { it.connect() }

        graph = AgentGraph(
            context = this,
            configStore = PrefsConfigStore(this),
            auditSink = RoomAuditSink(db.auditDao()),
            protectionInsights = UnavailableProtectionInsights,
            memoryDao = db.memoryDao(),
            automationDao = db.automationDao(),
            networkProfileDao = db.networkProfileDao(),
            eventBus = EventBus(),
            protectionController = UnavailableProtectionController,
            musicController = remoteMusic
        )

        graph.toolHandlers.registerAll()
        graph.systemControlTools.registerAll()

        ServiceRegistry.register(AgentGraph::class.java, graph)
    }

    companion object {
        @Volatile
        lateinit var instance: AgentApp
            private set
    }
}
