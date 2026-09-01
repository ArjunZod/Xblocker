package com.antigravity.shieldx.protection.controller

import android.content.Context
import com.antigravity.shieldx.core.model.*
import com.antigravity.shieldx.core.runtime.ProtectionController
import com.antigravity.shieldx.data.local.entities.PolicyEntity
import com.antigravity.shieldx.data.repository.PolicyRepository
import com.antigravity.shieldx.device.DeviceOwnerController
import com.antigravity.shieldx.device.LockdownController
import com.antigravity.shieldx.device.RestrictionController
import com.antigravity.shieldx.vpn.ProtectionVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProtectionControllerImpl(
    private val context: Context,
    private val policyRepository: PolicyRepository,
    private val deviceOwnerController: DeviceOwnerController,
    private val restrictionController: RestrictionController,
    private val lockdownController: LockdownController
) : ProtectionController {

    override fun capabilities(): Set<Capability> {
        return setOf(Capability.PROTECTION_MANAGEMENT)
    }

    override suspend fun execute(request: ToolRequest): ToolResult {
        return when (request.toolName.uppercase()) {
            "SET_PROTECTION_PROFILE" -> {
                val profileStr = request.parameters["profile"] as? String ?: "MAXIMUM"
                val profile = when (profileStr.uppercase()) {
                    "MAXIMUM" -> ProtectionProfile.MAXIMUM
                    "BALANCED" -> ProtectionProfile.BALANCED
                    "OFF" -> ProtectionProfile.OFF
                    else -> ProtectionProfile.MAXIMUM
                }
                val result = setProfile(profile)
                if (result.isSuccess) {
                    ToolResult(true, request.toolName, "Protection profile set to ${profile.name}")
                } else {
                    ToolResult(
                        false,
                        request.toolName,
                        result.exceptionOrNull()?.message ?: "Could not change the protection profile"
                    )
                }
            }
            "ENABLE_PROTECTION" -> {
                val enable = request.parameters["enabled"] as? Boolean ?: true
                if (enable) {
                    blockExplicitContent()
                    ToolResult(true, request.toolName, "Protection activated")
                } else {
                    val result = setProfile(ProtectionProfile.OFF)
                    if (result.isSuccess) {
                        ToolResult(true, request.toolName, "Protection paused")
                    } else {
                        ToolResult(
                            false,
                            request.toolName,
                            result.exceptionOrNull()?.message ?: "Protection is locked and cannot be paused"
                        )
                    }
                }
            }
            "GET_PROTECTION_STATUS" -> {
                val status = getStatus()
                ToolResult(true, request.toolName, "Protection status retrieved", status)
            }
            else -> ToolResult(false, request.toolName, "Unknown protection command: ${request.toolName}")
        }
    }

    override suspend fun setProfile(profile: ProtectionProfile): Result<Unit> = withContext(Dispatchers.IO) {
        // Lockdown means exactly this: no path, voice command, or UI toggle may
        // weaken protection while it is active. The only way through is the
        // dedicated unlock flow, which clears lockdown with the saved secret
        // before ever calling this function.
        if (lockdownController.isActive() && profile != ProtectionProfile.MAXIMUM) {
            return@withContext Result.failure(
                IllegalStateException(
                    "Protection is locked. Enter your lockdown recovery phrase to change this."
                )
            )
        }

        val current = policyRepository.getPolicy()
        val updated = when (profile) {
            ProtectionProfile.MAXIMUM -> current.copy(
                isEnabled = true,
                strictnessLevel = 4,
                blockAllAdult = true,
                safeSearchEnabled = true,
                aiClassificationEnabled = true,
                failClosedInLockdown = true
            )
            ProtectionProfile.BALANCED -> current.copy(
                isEnabled = true,
                strictnessLevel = 3,
                blockAllAdult = true,
                safeSearchEnabled = true,
                aiClassificationEnabled = false,
                failClosedInLockdown = true
            )
            ProtectionProfile.CUSTOM -> current.copy(isEnabled = true)
            ProtectionProfile.OFF -> current.copy(isEnabled = false)
        }

        policyRepository.updatePolicy(updated)

        if (updated.isEnabled) {
            ProtectionVpnService.startService(context)
            if (deviceOwnerController.isDeviceOwner()) {
                restrictionController.applyManagedRestrictions()
            }
        } else {
            ProtectionVpnService.stopService(context)
        }

        Result.success(Unit)
    }

    override suspend fun blockExplicitContent(): Result<Unit> {
        return setProfile(ProtectionProfile.MAXIMUM)
    }

    override suspend fun getStatus(): Map<String, Any> = withContext(Dispatchers.IO) {
        val policy = policyRepository.getPolicy()
        val isVpnRunning = ProtectionVpnService.isRunningFlow.value
        val isDeviceOwner = deviceOwnerController.isDeviceOwner()

        mapOf(
            "isEnabled" to policy.isEnabled,
            "strictnessLevel" to policy.strictnessLevel,
            "safeSearchEnabled" to policy.safeSearchEnabled,
            "isVpnRunning" to isVpnRunning,
            "isDeviceOwner" to isDeviceOwner,
            "deviceMode" to deviceOwnerController.getDeviceMode().name
        )
    }
}
