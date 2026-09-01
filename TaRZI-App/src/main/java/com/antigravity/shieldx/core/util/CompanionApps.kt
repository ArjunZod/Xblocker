package com.antigravity.shieldx.core.util

import android.content.Context
import android.content.Intent

/**
 * Music and the assistant ship as their own apps now. This is how the
 * protection app hands off to them — and how it tells the difference between
 * "not installed" and "installed but not open", which are different problems
 * for the user.
 */
enum class CompanionApp(val label: String, private val candidates: List<String>) {

    Music(
        label = "TaRZI Music",
        candidates = listOf("com.antigravity.tarzi.music", "com.antigravity.tarzi.music.debug")
    ),
    Assistant(
        label = "TaRZI Assistant",
        candidates = listOf("com.antigravity.tarzi.agent", "com.antigravity.tarzi.agent.debug")
    );

    /** The installed package, preferring a release build over a debug one. */
    fun installedPackage(context: Context): String? {
        val pm = context.packageManager
        return candidates.firstOrNull { pkg ->
            runCatching { pm.getPackageInfo(pkg, 0) }.isSuccess
        }
    }

    fun isInstalled(context: Context): Boolean = installedPackage(context) != null

    /** Returns false when the app isn't installed, so callers can say so. */
    fun launch(context: Context): Boolean {
        val pkg = installedPackage(context) ?: return false
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }

    /** Asks the music app to play something by name. */
    fun playInMusicApp(context: Context, query: String): Boolean {
        val pkg = installedPackage(context) ?: return false
        context.startActivity(
            Intent("com.antigravity.tarzi.music.action.PLAY_QUERY").apply {
                setPackage(pkg)
                putExtra("query", query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        return true
    }
}
