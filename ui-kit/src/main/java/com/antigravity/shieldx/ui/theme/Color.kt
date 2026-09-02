package com.antigravity.shieldx.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The palette, in two skins.
 *
 * Every colour the app draws with is a token here rather than a literal at the
 * call site, so light and dark are two values of the same name instead of two
 * copies of every screen. The token names are unchanged from when this was
 * dark-only - they are now composable properties reading from the current
 * scheme, which is what let the whole app gain a light theme without touching
 * the screens.
 *
 * Light is not dark inverted. Surfaces stay warm and slightly off-white,
 * because pure #FFF against a phone's brightness is harsh, and the accent
 * darkens a little so it still passes contrast on a pale ground.
 */
data class TarziColors(
    val canvas: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val surfaceHigh: Color,
    val border: Color,
    val borderStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val accentPressed: Color,
    val accentMuted: Color,
    val accentWarm: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val successMuted: Color,
    val warningMuted: Color,
    val dangerMuted: Color,
    val isLight: Boolean
)

/**
 * Dark. The greys are very slightly warm rather than dead neutral - pure
 * neutral grey on an OLED panel reads as flat and cheap.
 */
val TarziDarkColors = TarziColors(
    canvas = Color(0xFF0B0B0D),
    surface = Color(0xFF141417),
    surfaceRaised = Color(0xFF1C1C21),
    surfaceHigh = Color(0xFF26262C),
    border = Color(0xFF232329),
    borderStrong = Color(0xFF35353D),
    textPrimary = Color(0xFFF2F1EF),
    textSecondary = Color(0xFF9C9BA4),
    textTertiary = Color(0xFF67666F),
    accent = Color(0xFF7C7AF5),
    accentPressed = Color(0xFF6462E0),
    accentMuted = Color(0x267C7AF5),
    accentWarm = Color(0xFFE8875A),
    success = Color(0xFF3DBE7C),
    warning = Color(0xFFE0A22B),
    danger = Color(0xFFE8615D),
    successMuted = Color(0x263DBE7C),
    warningMuted = Color(0x26E0A22B),
    dangerMuted = Color(0x26E8615D),
    isLight = false
)

/**
 * Light. Warm paper rather than white, with the accent pulled darker so it
 * still holds contrast against a pale surface.
 */
val TarziLightColors = TarziColors(
    canvas = Color(0xFFFAF8F5),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFF3F0EB),
    surfaceHigh = Color(0xFFE9E5DE),
    border = Color(0xFFE4E0D8),
    borderStrong = Color(0xFFCFC9BE),
    textPrimary = Color(0xFF16151A),
    textSecondary = Color(0xFF5D5B66),
    textTertiary = Color(0xFF8B8894),
    accent = Color(0xFF5B4BE8),
    accentPressed = Color(0xFF4839C9),
    accentMuted = Color(0x1A5B4BE8),
    accentWarm = Color(0xFFD9682F),
    success = Color(0xFF1F9D5F),
    warning = Color(0xFFB57A10),
    danger = Color(0xFFCF3F3B),
    successMuted = Color(0x1A1F9D5F),
    warningMuted = Color(0x1AB57A10),
    dangerMuted = Color(0x1ACF3F3B),
    isLight = true
)

val LocalTarziColors = staticCompositionLocalOf { TarziDarkColors }

// The token names the screens already use. Each now reads from whichever
// scheme is in force, so a screen written for dark works in light unchanged.

val Canvas: Color @Composable @ReadOnlyComposable get() = LocalTarziColors.current.canvas
val Surface: Color @Composable @ReadOnlyComposable get() = LocalTarziColors.current.surface
val SurfaceRaised: Color @Composable @ReadOnlyComposable get() = LocalTarziColors.current.surfaceRaised
val SurfaceHigh: Color @Composable @ReadOnlyComposable get() = LocalTarziColors.current.surfaceHigh
val Border: Color @Composable @ReadOnlyComposable get() = LocalTarziColors.current.border
val BorderStrong: Color @Composable @ReadOnlyComposable get() = LocalTarziColors.current.borderStrong
val TextPrimary: Color @Composable @ReadOnlyComposable get() = LocalTarziColors.current.textPrimary
val TextSecondary: Color @Composable @ReadOnlyComposable get() = LocalTarziColors.current.textSecondary
val TextTertiary: Color @Composable @ReadOnlyComposable get() = LocalTarziColors.current.textTertiary
val Accent: Color @Composable @ReadOnlyComposable get() = LocalTarziColors.current.accent
val AccentPressed: Color @Composable @ReadOnlyComposable get() = LocalTarziColors.current.accentPressed
val AccentMuted: Color @Composable @ReadOnlyComposable get() = LocalTarziColors.current.accentMuted
val AccentWarm: Color @Composable @ReadOnlyComposable get() = LocalTarziColors.current.accentWarm
val Success: Color @Composable @ReadOnlyComposable get() = LocalTarziColors.current.success
val Warning: Color @Composable @ReadOnlyComposable get() = LocalTarziColors.current.warning
val Danger: Color @Composable @ReadOnlyComposable get() = LocalTarziColors.current.danger
val SuccessMuted: Color @Composable @ReadOnlyComposable get() = LocalTarziColors.current.successMuted
val WarningMuted: Color @Composable @ReadOnlyComposable get() = LocalTarziColors.current.warningMuted
val DangerMuted: Color @Composable @ReadOnlyComposable get() = LocalTarziColors.current.dangerMuted

internal fun darkScheme(c: TarziColors) = darkColorScheme(
    primary = c.accent,
    onPrimary = Color.White,
    primaryContainer = c.accentMuted,
    onPrimaryContainer = c.accent,
    secondary = c.textSecondary,
    onSecondary = c.canvas,
    background = c.canvas,
    onBackground = c.textPrimary,
    surface = c.surface,
    onSurface = c.textPrimary,
    surfaceVariant = c.surfaceRaised,
    onSurfaceVariant = c.textSecondary,
    outline = c.border,
    outlineVariant = c.borderStrong,
    error = c.danger,
    onError = Color.White,
    scrim = Color(0xE6000000)
)

internal fun lightScheme(c: TarziColors) = lightColorScheme(
    primary = c.accent,
    onPrimary = Color.White,
    primaryContainer = c.accentMuted,
    onPrimaryContainer = c.accent,
    secondary = c.textSecondary,
    onSecondary = Color.White,
    background = c.canvas,
    onBackground = c.textPrimary,
    surface = c.surface,
    onSurface = c.textPrimary,
    surfaceVariant = c.surfaceRaised,
    onSurfaceVariant = c.textSecondary,
    outline = c.border,
    outlineVariant = c.borderStrong,
    error = c.danger,
    onError = Color.White,
    scrim = Color(0x99000000)
)
