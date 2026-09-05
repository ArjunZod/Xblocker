# ShieldX / Xblocker Proguard & R8 Configuration

# Room Persistence
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Serialization & Core Models
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers enum * { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.antigravity.shieldx.core.model.** { *; }
-keep class com.antigravity.shieldx.data.local.entities.** { *; }

# Backup & Threat Intelligence
-keep class com.antigravity.shieldx.backup.** { *; }
-keep class com.antigravity.shieldx.policy.ThreatIntelManager** { *; }
-keep class com.antigravity.shieldx.vpn.DnsDecisionCache** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Compose Runtime
-keepclassmembers class * extends androidx.compose.runtime.State { *; }

# Device Admin, VPN & Broadcast Receivers
-keep class com.antigravity.shieldx.device.ShieldXDeviceAdminReceiver { *; }
-keep class com.antigravity.shieldx.vpn.ProtectionVpnService { *; }
-keep class com.antigravity.shieldx.apps.PackageChangeReceiver { *; }
