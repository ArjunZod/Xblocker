package com.antigravity.shieldx.music

import android.app.Application
import com.antigravity.shieldx.core.runtime.ContentPolicyGate
import com.antigravity.shieldx.core.runtime.MusicController
import com.antigravity.shieldx.core.runtime.ServiceRegistry

/**
 * Composition root for the standalone TaRZI Music app.
 *
 * When Music runs on its own there is no protection engine to consult, so the
 * explicit-content gate is open. The all-in-one build supplies a real
 * ContentPolicyGate backed by the policy engine instead.
 */
object OpenContentPolicyGate : ContentPolicyGate {
    override suspend fun isExplicitContentBlocked(): Boolean = false
}

class MusicApp : Application() {

    lateinit var graph: MusicGraph
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        graph = MusicGraph(
            context = this,
            policyGate = OpenContentPolicyGate,
            playHistoryDao = MusicDatabase.getInstance(this).playHistoryDao()
        )

        ServiceRegistry.register(MusicController::class.java, graph.musicController)
    }

    companion object {
        @Volatile
        lateinit var instance: MusicApp
            private set
    }
}
