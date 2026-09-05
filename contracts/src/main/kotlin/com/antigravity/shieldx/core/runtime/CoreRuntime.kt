package com.antigravity.shieldx.core.runtime

import com.antigravity.shieldx.core.model.Capability
import com.antigravity.shieldx.core.model.ProtectionProfile
import com.antigravity.shieldx.core.model.ToolRequest
import com.antigravity.shieldx.core.model.ToolResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Base ShieldX Subsystem Controller contract.
 */
interface TaRZIController {
    suspend fun execute(request: ToolRequest): ToolResult
    fun capabilities(): Set<Capability>
}

/**
 * Protection Subsystem Controller contract.
 */
interface ProtectionController : TaRZIController {
    suspend fun setProfile(profile: ProtectionProfile): Result<Unit>
    suspend fun blockExplicitContent(): Result<Unit>
    suspend fun getStatus(): Map<String, Any>
}

/**
 * Gate content checks against before allowing network access.
 */
interface ContentPolicyGate {
    suspend fun isContentBlocked(target: String): Boolean
}

/**
 * Typed EventBus for decoupled cross-subsystem messaging.
 */
sealed class TaRZIEvent {
    data class ProtectionStateChanged(val isEnabled: Boolean, val profileName: String) : TaRZIEvent()
    data class ContentBlocked(val target: String, val category: String, val reason: String) : TaRZIEvent()
    data class PolicyRuleChanged(val ruleCount: Int, val version: Long) : TaRZIEvent()
    data class NetworkProfileChanged(val ssid: String, val profileName: String) : TaRZIEvent()
    data class TamperAlert(val severity: String, val description: String) : TaRZIEvent()
}

class EventBus {
    private val _events = MutableSharedFlow<TaRZIEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<TaRZIEvent> = _events.asSharedFlow()

    suspend fun publish(event: TaRZIEvent) {
        _events.emit(event)
    }

    fun tryPublish(event: TaRZIEvent): Boolean {
        return _events.tryEmit(event)
    }
}

/**
 * Application-scoped service registry for accessing registered subsystem controllers.
 */
object ServiceRegistry {
    private val services = ConcurrentHashMap<Class<*>, Any>()

    fun <T : Any> register(serviceClass: Class<T>, instance: T) {
        services[serviceClass] = instance
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(serviceClass: Class<T>): T {
        return services[serviceClass] as? T
            ?: throw IllegalStateException("Service ${serviceClass.name} is not registered in ServiceRegistry")
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getOrNull(serviceClass: Class<T>): T? {
        return services[serviceClass] as? T
    }
}
