package com.antigravity.shieldx.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.antigravity.shieldx.core.model.AppPolicy
import com.antigravity.shieldx.core.model.BlockReason
import com.antigravity.shieldx.core.model.Category
import com.antigravity.shieldx.core.model.DeviceMode
import com.antigravity.shieldx.core.model.MatchType
import com.antigravity.shieldx.core.model.PolicyDecision
import com.antigravity.shieldx.core.model.TamperState

@Entity(tableName = "policies")
data class PolicyEntity(
    @PrimaryKey val id: String = "default_policy",
    val name: String = "System Adult Protection Policy",
    val isEnabled: Boolean = true,
    val strictnessLevel: Int = 3, // 1: Relaxed, 2: Moderate, 3: Aggressive (Default), 4: Strict Lockdown
    val blockAllAdult: Boolean = true,
    val safeSearchEnabled: Boolean = true,
    val aiClassificationEnabled: Boolean = false,
    val aiConfidenceThreshold: Float = 0.70f,
    val failClosedInLockdown: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "domain_rules",
    indices = [
        Index(value = ["domain"], unique = true),
        Index(value = ["category"]),
        Index(value = ["action"])
    ]
)
data class DomainRuleEntity(
    @PrimaryKey val domain: String,
    val category: Category = Category.PORNOGRAPHY,
    val matchType: MatchType = MatchType.SUBDOMAIN,
    val action: PolicyDecision = PolicyDecision.BLOCK,
    val isCustom: Boolean = false,
    val source: String = "SYSTEM_SEED",
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "app_rules",
    indices = [
        Index(value = ["packageName"], unique = true),
        Index(value = ["policy"])
    ]
)
data class AppRuleEntity(
    @PrimaryKey val packageName: String,
    val appLabel: String,
    val category: String, // BROWSER, SOCIAL, ADULT, UTILITY, SYSTEM
    val policy: AppPolicy = AppPolicy.RESTRICTED,
    val reason: String = "Monitored package for adult protection",
    val isCustom: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "classification_cache",
    indices = [
        Index(value = ["contentHash"], unique = true),
        Index(value = ["cachedAt"])
    ]
)
data class ClassificationCacheEntity(
    @PrimaryKey val contentHash: String,
    val target: String,
    val decision: PolicyDecision,
    val category: Category,
    val confidence: Float,
    val cachedAt: Long = System.currentTimeMillis(),
    val ttlMillis: Long = 86400000L // 24 hours
)

@Entity(
    tableName = "blocked_events",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["category"]),
        Index(value = ["target"])
    ]
)
data class BlockedEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val target: String, // Domain, URL fragment, or Package name (redacted of sensitive query)
    val category: Category,
    val reason: BlockReason,
    val appPackageName: String? = null,
    val deviceMode: DeviceMode = DeviceMode.NORMAL_CONSUMER,
    val details: String = ""
)

@Entity(
    tableName = "tamper_events",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["severity"])
    ]
)
data class TamperEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: String,
    val severity: String, // LOW, MEDIUM, HIGH, CRITICAL
    val description: String,
    val resultingState: TamperState
)

@Entity(tableName = "device_state")
data class DeviceStateEntity(
    @PrimaryKey val key: String,
    val value: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "model_decisions",
    indices = [
        Index(value = ["inputHash"]),
        Index(value = ["timestamp"])
    ]
)
data class ModelDecisionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val inputHash: String,
    val decision: PolicyDecision,
    val category: Category,
    val confidence: Float,
    val latencyMs: Long,
    val reasonCode: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "policy_versions")
data class PolicyVersionEntity(
    @PrimaryKey val version: Long,
    val signature: String,
    val rulesCount: Int,
    val source: String,
    val appliedAt: Long = System.currentTimeMillis(),
    val isKnownGood: Boolean = true
)

@Entity(tableName = "configurations")
data class ConfigurationEntity(
    @PrimaryKey val configKey: String,
    val configValue: String,
    val updatedAt: Long = System.currentTimeMillis()
)

// === TaRZI Super-App Entities ===

@Entity(
    tableName = "audit_events",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["category"])
    ]
)
data class AuditEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val category: String, // SECURITY, ASSISTANT, PLAYBACK, AUTOMATION, PERMISSION
    val action: String,
    val actor: String = "USER",
    val details: String,
    val status: String = "SUCCESS" // SUCCESS, DENIED, FAILED, CONFIRMED
)

@Entity(tableName = "user_preferences")
data class UserPreferenceEntity(
    @PrimaryKey val key: String,
    val value: String,
    val updatedAt: Long = System.currentTimeMillis()
)
