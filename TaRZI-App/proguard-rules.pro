# ShieldX Proguard / R8 Configuration

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Gson & Model serialization
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers enum * { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Compose
-keepclassmembers class * extends androidx.compose.runtime.State { *; }

# Device Admin & VPN
-keep class com.antigravity.shieldx.device.ShieldXDeviceAdminReceiver { *; }
-keep class com.antigravity.shieldx.vpn.ProtectionVpnService { *; }
-keep class com.antigravity.shieldx.apps.PackageChangeReceiver { *; }

# Rhino JS Engine / Third-party dependencies
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn org.mozilla.javascript.**

