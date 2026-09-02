package com.antigravity.shieldx.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Which skin to wear. "System" is the default because a phone-wide setting is
 * usually already the right answer, and someone who wants to override it can.
 */
enum class ThemeMode { System, Light, Dark }

/**
 * Reads and writes the choice. Deliberately a plain preference rather than a
 * database row: the theme has to be known before the first frame, and waiting
 * on a database to draw anything would show a flash of the wrong colour.
 */
class ThemePreference(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("tarzi_theme", Context.MODE_PRIVATE)

    var mode: ThemeMode
        get() = runCatching { ThemeMode.valueOf(prefs.getString(KEY, ThemeMode.System.name)!!) }
            .getOrDefault(ThemeMode.System)
        set(value) {
            prefs.edit().putString(KEY, value.name).apply()
        }

    private companion object {
        const val KEY = "theme_mode"
    }
}

/** Lets any screen offer a theme switch without threading state through. */
val LocalThemeController = staticCompositionLocalOf<ThemeController> {
    ThemeController(ThemeMode.System) {}
}

class ThemeController(val mode: ThemeMode, val setMode: (ThemeMode) -> Unit)

/**
 * Wraps an app in the palette. Screens keep using the same token names; only
 * the values behind them change.
 */
@Composable
fun TarziTheme(
    mode: ThemeMode = ThemeMode.System,
    onModeChange: (ThemeMode) -> Unit = {},
    content: @Composable () -> Unit
) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val colors = if (dark) TarziDarkColors else TarziLightColors

    CompositionLocalProvider(
        LocalTarziColors provides colors,
        LocalThemeController provides ThemeController(mode, onModeChange)
    ) {
        MaterialTheme(
            colorScheme = if (dark) darkScheme(colors) else lightScheme(colors),
            typography = TarziTypography,
            content = content
        )
    }
}

/**
 * The variant an app uses at its root: remembers the stored choice and rewrites
 * it when the user picks another, so the switch takes effect immediately.
 */
@Composable
fun TarziThemeRoot(
    context: Context,
    content: @Composable () -> Unit
) {
    val store = remember(context) { ThemePreference(context) }
    var mode by remember { mutableStateOf(store.mode) }

    TarziTheme(
        mode = mode,
        onModeChange = {
            store.mode = it
            mode = it
        },
        content = content
    )
}
