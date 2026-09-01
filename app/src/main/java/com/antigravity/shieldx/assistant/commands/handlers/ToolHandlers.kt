package com.antigravity.shieldx.assistant.commands.handlers

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import com.antigravity.shieldx.assistant.commands.CommandRegistry
import com.antigravity.shieldx.assistant.commands.ContactResolver
import com.antigravity.shieldx.assistant.commands.ToolDefinition
import com.antigravity.shieldx.core.model.*
import com.antigravity.shieldx.core.runtime.MusicController
import com.antigravity.shieldx.core.runtime.ProtectionController
import com.antigravity.shieldx.data.local.AppDatabase
import com.antigravity.shieldx.data.local.entities.MemoryItemEntity
import com.antigravity.shieldx.data.local.entities.NetworkProfileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Registers all real on-device and external system tools.
 */
class ToolHandlers(
    private val context: Context,
    private val commandRegistry: CommandRegistry,
    private val protectionController: ProtectionController,
    private val musicController: MusicController,
    private val database: AppDatabase
) {

    fun registerAll() {
        registerProtectionTools()
        registerMusicTools()
        registerDeviceTools()
        registerAppAndExternalTools()
        registerMemoryTools()
        registerPeopleTools()
        registerPlanningTools()
        registerNetworkTools()
        registerCommerceTools()
    }

    private fun registerProtectionTools() {
        commandRegistry.registerTool(
            ToolDefinition(
                name = "SET_PROTECTION_PROFILE",
                description = "Set system protection profile (MAXIMUM, BALANCED, OFF)",
                riskLevel = RiskLevel.L3_HIGH_SECURITY,
                confirmation = ConfirmationPolicy.REQUIRED,
                handler = { req ->
                    val profileStr = req.parameters["profile"] as? String ?: "MAXIMUM"
                    val profile = when (profileStr.uppercase()) {
                        "MAXIMUM" -> ProtectionProfile.MAXIMUM
                        "BALANCED" -> ProtectionProfile.BALANCED
                        "OFF" -> ProtectionProfile.OFF
                        else -> ProtectionProfile.MAXIMUM
                    }
                    protectionController.setProfile(profile)
                    ToolResult(true, req.toolName, "ShieldX protection profile set to ${profile.name}")
                }
            )
        )

        commandRegistry.registerTool(
            ToolDefinition(
                name = "ENABLE_PROTECTION",
                description = "Enable or disable system-level content protection",
                riskLevel = RiskLevel.L3_HIGH_SECURITY,
                confirmation = ConfirmationPolicy.REQUIRED,
                handler = { req ->
                    val enable = req.parameters["enabled"] as? Boolean ?: true
                    if (enable) {
                        protectionController.blockExplicitContent()
                        ToolResult(true, req.toolName, "System protection is now ACTIVE")
                    } else {
                        protectionController.setProfile(ProtectionProfile.OFF)
                        ToolResult(true, req.toolName, "System protection has been PAUSED")
                    }
                }
            )
        )

        commandRegistry.registerTool(
            ToolDefinition(
                name = "GET_PROTECTION_STATUS",
                description = "Query active protection state, VPN status, and blocks today",
                riskLevel = RiskLevel.L0_READ_ONLY,
                handler = { req ->
                    val status = protectionController.getStatus()
                    val blocks = database.blockedEventDao().getBlockedCountSince(0)
                    ToolResult(
                        true,
                        req.toolName,
                        "Protection is active. $blocks threats blocked so far.",
                        status
                    )
                }
            )
        )

        commandRegistry.registerTool(
            ToolDefinition(
                name = "SHOW_BLOCKED_EVENTS",
                description = "View recently intercepted explicit content events",
                riskLevel = RiskLevel.L0_READ_ONLY,
                handler = { req ->
                    val events = database.blockedEventDao().getRecentEvents(10)
                    ToolResult(
                        true,
                        req.toolName,
                        "Retrieved ${events.size} recent blocked events.",
                        mapOf("events" to events)
                    )
                }
            )
        )
    }

    private fun registerMusicTools() {
        commandRegistry.registerTool(
            ToolDefinition(
                name = "PLAY",
                description = "Play a song, artist, album, or search query",
                riskLevel = RiskLevel.L1_LOW_IMPACT,
                handler = { req ->
                    val query = (req.parameters["query"] ?: req.parameters["song"] ?: req.parameters["title"]) as? String
                    if (!query.isNullOrBlank()) {
                        val res = musicController.searchAndPlay(query)
                        if (res.isSuccess) {
                            ToolResult(true, req.toolName, "Playing \"$query\"")
                        } else {
                            ToolResult(false, req.toolName, "Could not play \"$query\": ${res.exceptionOrNull()?.message}")
                        }
                    } else {
                        musicController.resume()
                        ToolResult(true, req.toolName, "Resumed music playback")
                    }
                }
            )
        )

        commandRegistry.registerTool(
            ToolDefinition(
                name = "PAUSE",
                description = "Pause audio playback",
                riskLevel = RiskLevel.L1_LOW_IMPACT,
                handler = { req ->
                    musicController.pause()
                    ToolResult(true, req.toolName, "Paused music")
                }
            )
        )

        commandRegistry.registerTool(
            ToolDefinition(
                name = "RESUME",
                description = "Resume audio playback",
                riskLevel = RiskLevel.L1_LOW_IMPACT,
                handler = { req ->
                    musicController.resume()
                    ToolResult(true, req.toolName, "Resumed music")
                }
            )
        )

        commandRegistry.registerTool(
            ToolDefinition(
                name = "NEXT",
                description = "Skip to next track in queue",
                riskLevel = RiskLevel.L1_LOW_IMPACT,
                handler = { req ->
                    musicController.next()
                    ToolResult(true, req.toolName, "Skipped to next track")
                }
            )
        )

        commandRegistry.registerTool(
            ToolDefinition(
                name = "PREVIOUS",
                description = "Go back to previous track",
                riskLevel = RiskLevel.L1_LOW_IMPACT,
                handler = { req ->
                    musicController.previous()
                    ToolResult(true, req.toolName, "Playing previous track")
                }
            )
        )

        commandRegistry.registerTool(
            ToolDefinition(
                name = "SHUFFLE",
                description = "Toggle queue shuffle mode",
                riskLevel = RiskLevel.L1_LOW_IMPACT,
                handler = { req ->
                    val res = musicController.execute(req)
                    res
                }
            )
        )

        commandRegistry.registerTool(
            ToolDefinition(
                name = "REPEAT",
                description = "Toggle repeat mode (off, one, all)",
                riskLevel = RiskLevel.L1_LOW_IMPACT,
                handler = { req ->
                    val res = musicController.execute(req)
                    res
                }
            )
        )

        commandRegistry.registerTool(
            ToolDefinition(
                name = "LIKE",
                description = "Save or like the currently playing song",
                riskLevel = RiskLevel.L1_LOW_IMPACT,
                handler = { req ->
                    val res = musicController.execute(req)
                    res
                }
            )
        )

        commandRegistry.registerTool(
            ToolDefinition(
                name = "PLAY_LIKED",
                description = "Play user's liked songs playlist",
                riskLevel = RiskLevel.L1_LOW_IMPACT,
                handler = { req ->
                    val res = musicController.execute(req)
                    res
                }
            )
        )

        commandRegistry.registerTool(
            ToolDefinition(
                name = "WHAT_IS_PLAYING",
                description = "Get the name and artist of the currently playing track",
                riskLevel = RiskLevel.L0_READ_ONLY,
                handler = { req ->
                    val res = musicController.execute(req)
                    res
                }
            )
        )
    }

    private fun registerDeviceTools() {
        commandRegistry.registerTool(
            ToolDefinition(
                name = "GET_BATTERY",
                description = "Get battery percentage and charging state",
                riskLevel = RiskLevel.L0_READ_ONLY,
                handler = { req ->
                    val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                    val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    val isCharging = bm.isCharging
                    ToolResult(
                        true,
                        req.toolName,
                        "Battery is at $level%${if (isCharging) " and currently charging" else ""}.",
                        mapOf("level" to level, "isCharging" to isCharging)
                    )
                }
            )
        )

        commandRegistry.registerTool(
            ToolDefinition(
                name = "GET_STORAGE",
                description = "Get available and total internal storage",
                riskLevel = RiskLevel.L0_READ_ONLY,
                handler = { req ->
                    val path = Environment.getDataDirectory()
                    val stat = StatFs(path.path)
                    val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
                    val totalBytes = stat.blockCountLong * stat.blockSizeLong
                    val availGb = String.format("%.1f", availableBytes.toDouble() / (1024 * 1024 * 1024))
                    val totalGb = String.format("%.1f", totalBytes.toDouble() / (1024 * 1024 * 1024))

                    ToolResult(
                        true,
                        req.toolName,
                        "Storage status: $availGb GB available out of $totalGb GB total.",
                        mapOf("availableGb" to availGb, "totalGb" to totalGb)
                    )
                }
            )
        )

        commandRegistry.registerTool(
            ToolDefinition(
                name = "SET_FLASHLIGHT",
                description = "Toggle camera torch on/off",
                riskLevel = RiskLevel.L1_LOW_IMPACT,
                handler = { req ->
                    val enable = req.parameters["enable"] as? Boolean ?: true
                    try {
                        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                        val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                            val chars = cameraManager.getCameraCharacteristics(id)
                            chars.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                        } ?: cameraManager.cameraIdList.firstOrNull()

                        if (cameraId != null) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                cameraManager.setTorchMode(cameraId, enable)
                                ToolResult(true, req.toolName, "Flashlight turned ${if (enable) "ON" else "OFF"}")
                            } else {
                                ToolResult(false, req.toolName, "Flashlight control requires Android 6.0+")
                            }
                        } else {
                            ToolResult(false, req.toolName, "No camera flash hardware detected.")
                        }
                    } catch (e: Exception) {
                        ToolResult(false, req.toolName, "Flashlight error: ${e.message}")
                    }
                }
            )
        )

        commandRegistry.registerTool(
            ToolDefinition(
                name = "SET_VOLUME",
                description = "Set media volume percentage (0 to 100)",
                riskLevel = RiskLevel.L1_LOW_IMPACT,
                handler = { req ->
                    val percent = (req.parameters["levelPercent"] as? Number)?.toInt() ?: 50
                    try {
                        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        val targetVol = (maxVol * (percent.coerceIn(0, 100) / 100f)).toInt()
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, AudioManager.FLAG_SHOW_UI)
                        ToolResult(true, req.toolName, "Volume set to $percent%")
                    } catch (e: Exception) {
                        ToolResult(false, req.toolName, "Could not set volume: ${e.message}")
                    }
                }
            )
        )

        commandRegistry.registerTool(
            ToolDefinition(
                name = "OPEN_SETTINGS",
                description = "Open device settings",
                riskLevel = RiskLevel.L1_LOW_IMPACT,
                handler = { req ->
                    val intent = Intent(Settings.ACTION_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    ToolResult(true, req.toolName, "Opened device settings")
                }
            )
        )
    }

    private fun registerAppAndExternalTools() {
        commandRegistry.registerTool(
            ToolDefinition(
                name = "OPEN_APP",
                description = "Launch any external app installed on the device (e.g. YouTube, WhatsApp, Camera, Chrome, Instagram, Maps, Spotify)",
                riskLevel = RiskLevel.L1_LOW_IMPACT,
                handler = { req ->
                    val appName = req.parameters["appName"] as? String ?: "app"
                    val lowerApp = appName.lowercase().trim()

                    // Known package mappings
                    val targetPackage = when {
                        lowerApp.contains("youtube") -> "com.google.android.youtube"
                        lowerApp.contains("whatsapp") -> "com.whatsapp"
                        lowerApp.contains("chrome") -> "com.android.chrome"
                        lowerApp.contains("camera") -> null // use MediaStore
                        lowerApp.contains("instagram") -> "com.instagram.android"
                        lowerApp.contains("maps") -> "com.google.android.apps.maps"
                        lowerApp.contains("spotify") -> "com.spotify.music"
                        lowerApp.contains("telegram") -> "org.telegram.messenger"
                        lowerApp.contains("twitter") || lowerApp.contains(" x") || lowerApp == "x" -> "com.twitter.android"
                        lowerApp.contains("gmail") || lowerApp.contains("email") -> "com.google.android.gm"
                        lowerApp.contains("gallery") || lowerApp.contains("photos") -> "com.google.android.apps.photos"
                        lowerApp.contains("calculator") -> "com.google.android.calculator"
                        else -> null
                    }

                    try {
                        val pm = context.packageManager
                        var launchIntent: Intent? = null

                        if (lowerApp.contains("camera")) {
                            launchIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                        } else if (targetPackage != null) {
                            launchIntent = pm.getLaunchIntentForPackage(targetPackage)
                        }

                        // If not found in known list, dynamically scan installed apps
                        if (launchIntent == null) {
                            val installed = pm.getInstalledApplications(0)
                            for (appInfo in installed) {
                                val label = pm.getApplicationLabel(appInfo).toString().lowercase()
                                if (label.contains(lowerApp) || appInfo.packageName.lowercase().contains(lowerApp)) {
                                    launchIntent = pm.getLaunchIntentForPackage(appInfo.packageName)
                                    if (launchIntent != null) break
                                }
                            }
                        }

                        if (launchIntent != null) {
                            launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            context.startActivity(launchIntent)
                            ToolResult(true, req.toolName, "Opening $appName")
                        } else {
                            // Fallback: Open in web or play store
                            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$appName")).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(webIntent)
                            ToolResult(true, req.toolName, "$appName is not directly installed. Searching for it.")
                        }
                    } catch (e: Exception) {
                        ToolResult(false, req.toolName, "Error launching $appName: ${e.message}")
                    }
                }
            )
        )

        commandRegistry.registerTool(
            ToolDefinition(
                name = "SEARCH_WEB",
                description = "Perform a Google search in browser",
                riskLevel = RiskLevel.L0_READ_ONLY,
                handler = { req ->
                    val query = req.parameters["query"] as? String ?: ""
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                        ToolResult(true, req.toolName, "Searching web for '$query'")
                    } catch (e: Exception) {
                        ToolResult(false, req.toolName, "Search error: ${e.message}")
                    }
                }
            )
        )
    }

    private fun registerPeopleTools() {
        commandRegistry.registerTool(
            ToolDefinition(
                name = "CALL_CONTACT",
                description = "Initiate phone call to a contact name or phone number",
                riskLevel = RiskLevel.L2_MODERATE_IMPACT,
                confirmation = ConfirmationPolicy.NONE,
                handler = { req ->
                    val spoken = (req.parameters["name"] as? String).orEmpty().trim()

                    if (spoken.isBlank()) {
                        ToolResult(false, req.toolName, "Who should I call?")
                    } else {
                        // Resolve the spoken name to an actual number. Handing the
                        // dialer a raw name like "mom" is what made every call
                        // silently do nothing while reporting success.
                        when (val resolved = ContactResolver.resolve(context, spoken)) {

                            is ContactResolver.Result.PermissionMissing ->
                                ToolResult(
                                    false,
                                    req.toolName,
                                    "I need access to your contacts to look up $spoken. " +
                                        "Grant Contacts permission in Settings."
                                )

                            is ContactResolver.Result.NotFound ->
                                ToolResult(
                                    false,
                                    req.toolName,
                                    "I could not find anyone called $spoken in your contacts."
                                )

                            is ContactResolver.Result.Ambiguous -> {
                                val names = resolved.candidates.take(4)
                                    .joinToString(", ") { it.name }
                                ToolResult(
                                    false,
                                    req.toolName,
                                    "I found several matches for $spoken: $names. Which one?"
                                )
                            }

                            is ContactResolver.Result.Found -> {
                                val match = resolved.match
                                val hasCallPhone = androidx.core.content.ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.CALL_PHONE
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                                try {
                                    val action = if (hasCallPhone) Intent.ACTION_CALL else Intent.ACTION_DIAL
                                    val intent = Intent(
                                        action,
                                        Uri.parse("tel:" + Uri.encode(match.number))
                                    ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }

                                    val simIndex = (req.parameters["sim"] as? Number)?.toInt()
                                        ?: (req.parameters["sim_slot"] as? Number)?.toInt()

                                    val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager
                                    if (simIndex != null && telecomManager != null && androidx.core.content.ContextCompat.checkSelfPermission(
                                            context,
                                            android.Manifest.permission.READ_PHONE_STATE
                                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                    ) {
                                        try {
                                            val accounts = telecomManager.callCapablePhoneAccounts
                                            if (accounts != null && simIndex in 1..accounts.size) {
                                                intent.putExtra(android.telecom.TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, accounts[simIndex - 1])
                                            }
                                        } catch (_: Exception) {}
                                    }

                                    context.startActivity(intent)

                                    if (hasCallPhone) {
                                        val simSuffix = if (simIndex != null) " on SIM $simIndex" else ""
                                        ToolResult(true, req.toolName, "Calling ${match.name}$simSuffix")
                                    } else {
                                        ToolResult(
                                            true,
                                            req.toolName,
                                            "Opening dialer for ${match.name}. (Grant Phone permission in App Settings to call directly without opening the dialer)."
                                        )
                                    }
                                } catch (e: Exception) {
                                    ToolResult(
                                        false,
                                        req.toolName,
                                        "Could not place call: ${e.message}"
                                    )
                                }
                            }
                        }
                    }
                }
            )
        )

        commandRegistry.registerTool(
            ToolDefinition(
                name = "SEND_MESSAGE",
                description = "Send message via SMS or WhatsApp",
                riskLevel = RiskLevel.L1_LOW_IMPACT,
                handler = { req ->
                    val recipient = req.parameters["name"] as? String ?: "Contact"
                    val message = req.parameters["message"] as? String ?: ""
                    val useWhatsApp = req.parameters["useWhatsApp"] as? Boolean ?: false

                    try {
                        if (useWhatsApp) {
                            val url = "https://api.whatsapp.com/send?text=${Uri.encode(message)}"
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                            ToolResult(true, req.toolName, "Opening WhatsApp for $recipient")
                        } else {
                            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).apply {
                                putExtra("sms_body", message)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                            ToolResult(true, req.toolName, "Drafting message for $recipient: '$message'")
                        }
                    } catch (e: Exception) {
                        ToolResult(false, req.toolName, "Messaging error: ${e.message}")
                    }
                }
            )
        )
    }

    private fun registerPlanningTools() {
        commandRegistry.registerTool(
            ToolDefinition(
                name = "SET_ALARM",
                description = "Set an alarm clock on device",
                riskLevel = RiskLevel.L1_LOW_IMPACT,
                handler = { req ->
                    val hour = (req.parameters["hour"] as? Number)?.toInt() ?: 7
                    val minute = (req.parameters["minute"] as? Number)?.toInt() ?: 0
                    val message = req.parameters["message"] as? String ?: "Alarm"

                    try {
                        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                            putExtra(AlarmClock.EXTRA_HOUR, hour)
                            putExtra(AlarmClock.EXTRA_MINUTES, minute)
                            putExtra(AlarmClock.EXTRA_MESSAGE, message)
                            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                        val formattedTime = String.format("%02d:%02d", hour, minute)
                        ToolResult(true, req.toolName, "Alarm set for $formattedTime ($message)")
                    } catch (e: Exception) {
                        ToolResult(false, req.toolName, "Could not set alarm: ${e.message}")
                    }
                }
            )
        )

        // A reminder is an alarm that carries its reason, which is what people
        // actually mean by "remind me to call Mom at 8".
        commandRegistry.registerTool(
            ToolDefinition(
                name = "CREATE_REMINDER",
                description = "Create a reminder at a given time",
                riskLevel = RiskLevel.L1_LOW_IMPACT,
                handler = { req ->
                    val what = (req.parameters["text"] as? String)?.trim().orEmpty()
                        .ifBlank { "Reminder" }
                    val hour = (req.parameters["hour"] as? Number)?.toInt() ?: 9
                    val minute = (req.parameters["minute"] as? Number)?.toInt() ?: 0

                    try {
                        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                            putExtra(AlarmClock.EXTRA_HOUR, hour)
                            putExtra(AlarmClock.EXTRA_MINUTES, minute)
                            putExtra(AlarmClock.EXTRA_MESSAGE, what)
                            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                        val at = String.format("%02d:%02d", hour, minute)
                        ToolResult(true, req.toolName, "Reminder set for $at: $what")
                    } catch (e: Exception) {
                        ToolResult(false, req.toolName, "Could not set that reminder: ${e.message}")
                    }
                }
            )
        )

        commandRegistry.registerTool(
            ToolDefinition(
                name = "SET_TIMER",
                description = "Set a countdown timer on device",
                riskLevel = RiskLevel.L1_LOW_IMPACT,
                handler = { req ->
                    val seconds = (req.parameters["seconds"] as? Number)?.toInt() ?: 300
                    val message = req.parameters["message"] as? String ?: "Timer"

                    try {
                        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                            putExtra(AlarmClock.EXTRA_MESSAGE, message)
                            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                        val min = seconds / 60
                        ToolResult(true, req.toolName, "Timer set for ${if (min > 0) "$min minute(s)" else "$seconds second(s)"}")
                    } catch (e: Exception) {
                        ToolResult(false, req.toolName, "Could not set timer: ${e.message}")
                    }
                }
            )
        )
    }

    private fun registerMemoryTools() {
        commandRegistry.registerTool(
            ToolDefinition(
                name = "MEMORY_WRITE",
                description = "Save a user-approved fact or preference to persistent memory",
                riskLevel = RiskLevel.L1_LOW_IMPACT,
                handler = { req ->
                    val key = req.parameters["key"] as? String ?: "fact"
                    val value = req.parameters["value"] as? String ?: ""
                    withContext(Dispatchers.IO) {
                        database.memoryDao().insertOrUpdate(
                            MemoryItemEntity(
                                key = key.trim().lowercase(),
                                value = value.trim(),
                                source = "USER_EXPLICIT",
                                consent = true
                            )
                        )
                    }
                    ToolResult(true, req.toolName, "Remembered: your $key is $value.")
                }
            )
        )

        commandRegistry.registerTool(
            ToolDefinition(
                name = "MEMORY_READ",
                description = "Recall a fact or preference from persistent memory",
                riskLevel = RiskLevel.L0_READ_ONLY,
                handler = { req ->
                    val key = req.parameters["key"] as? String ?: ""
                    val item = withContext(Dispatchers.IO) {
                        database.memoryDao().getMemory(key.trim().lowercase())
                    }
                    if (item != null) {
                        ToolResult(true, req.toolName, "Your ${item.key} is ${item.value}.", mapOf("value" to item.value))
                    } else {
                        ToolResult(false, req.toolName, "I don't have a memory saved for '$key'.")
                    }
                }
            )
        )
    }

    private fun registerNetworkTools() {
        // Binds the Wi-Fi the device is on to a named profile, so "connect to my
        // home setup" applies that network's saved protection level.
        commandRegistry.registerTool(
            ToolDefinition(
                name = "SET_NETWORK_PROFILE",
                description = "Apply a named network profile (for example home or work) to the current Wi-Fi",
                riskLevel = RiskLevel.L2_MODERATE_IMPACT,
                handler = { req ->
                    val profileName = (req.parameters["profileName"] as? String)?.trim().orEmpty()
                        .ifBlank { "home" }
                    val protectionLevel = (req.parameters["protectionLevel"] as? String)?.uppercase()
                        ?: "MAXIMUM"

                    val ssid = try {
                        val wm = context.applicationContext
                            .getSystemService(Context.WIFI_SERVICE) as WifiManager
                        wm.connectionInfo.ssid.replace("\"", "")
                    } catch (_: Exception) {
                        ""
                    }

                    if (ssid.isBlank() || ssid.contains("unknown", ignoreCase = true)) {
                        ToolResult(
                            false,
                            req.toolName,
                            "I need to be on a Wi-Fi network to save a $profileName profile"
                        )
                    } else {
                        withContext(Dispatchers.IO) {
                            database.networkProfileDao().insertOrUpdate(
                                NetworkProfileEntity(
                                    ssid = ssid,
                                    profileName = profileName,
                                    protectionLevel = protectionLevel,
                                    isHomeNetwork = profileName.equals("home", ignoreCase = true)
                                )
                            )
                        }
                        protectionController.setProfile(
                            when (protectionLevel) {
                                "BALANCED" -> ProtectionProfile.BALANCED
                                "OFF" -> ProtectionProfile.OFF
                                else -> ProtectionProfile.MAXIMUM
                            }
                        )
                        ToolResult(
                            success = true,
                            toolName = req.toolName,
                            resultSummary = "Applied the $profileName profile to $ssid",
                            data = mapOf("ssid" to ssid, "profileName" to profileName)
                        )
                    }
                }
            )
        )

        commandRegistry.registerTool(
            ToolDefinition(
                name = "GET_WIFI",
                description = "Query current Wi-Fi network and security profile",
                riskLevel = RiskLevel.L0_READ_ONLY,
                handler = { req ->
                    val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    val info = wm.connectionInfo
                    val ssid = info.ssid.replace("\"", "")
                    ToolResult(
                        true,
                        req.toolName,
                        "Connected to Wi-Fi network: $ssid",
                        mapOf("ssid" to ssid)
                    )
                }
            )
        )
    }

    private fun registerCommerceTools() {
        commandRegistry.registerTool(
            ToolDefinition(
                name = "SEARCH_PRODUCTS",
                description = "Search products and compare prices",
                riskLevel = RiskLevel.L0_READ_ONLY,
                handler = { req ->
                    val query = req.parameters["query"] as? String ?: ""
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=buy+${Uri.encode(query)}")).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    ToolResult(true, req.toolName, "Searching products for '$query'")
                }
            )
        )

        registerGeneralTools()
    }

    private fun registerGeneralTools() {
        commandRegistry.registerTool(
            ToolDefinition(
                name = "GENERAL_QUERY",
                description = "Handle conversational inquiries, greetings and assistant questions",
                riskLevel = RiskLevel.L0_READ_ONLY,
                confirmation = ConfirmationPolicy.NONE,
                handler = { req ->
                    val query = (req.parameters["query"] as? String).orEmpty().lowercase()

                    // These are the offline replies, used only when no language
                    // model is reachable. They stay short and plain on purpose:
                    // a canned line that performs enthusiasm ("All systems
                    // operational, boss") reads far worse than one that simply
                    // answers, and it misleads the user into thinking a model is
                    // running when none is.
                    val reply = when {
                        query.contains("how are you") || query.contains("how you doing") ->
                            "Running fine. What do you need?"

                        query.contains("who are you") || query.contains("your name") ->
                            "I am Tarzi. I can control your phone, play music, and manage " +
                                "your content filtering."

                        query.contains("what can you do") || query.contains("help") ->
                            "Right now I can call your contacts, open apps, play music, " +
                                "control the flashlight, volume and alarms, act on what is on " +
                                "your screen, and manage protection. Ask me to do any of those."

                        Regex("^(hi|hey|hello|yo)\\b").containsMatchIn(query) ->
                            "Hey. What can I do?"

                        query.contains("thank") ->
                            "Any time."

                        else ->
                            // The honest answer. Without a model there is no
                            // general conversation, and pretending otherwise is
                            // what makes the assistant feel hollow.
                            "I do not have a language model connected, so I can only run " +
                                "commands right now. Add a Gemini API key in Settings and I " +
                                "can actually talk with you."
                    }
                    ToolResult(true, req.toolName, reply)
                }
            )
        )
    }
}
