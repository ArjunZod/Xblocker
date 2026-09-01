package com.antigravity.shieldx.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The palette.
 *
 * The greys are very slightly warm rather than dead neutral. Pure neutral grey
 * on an OLED panel reads as flat and cheap; a few points of warmth makes the
 * surfaces feel like material instead of switched-off pixels, without anyone
 * consciously noticing a tint.
 *
 * Surfaces step in small increments so depth comes from stacking rather than
 * from borders and shadows. One accent marks what is interactive. Semantic
 * colours only ever report real state.
 */

// Canvas and surfaces --------------------------------------------------------

/** The page. Not pure black - true black makes every edge a hard cut. */
val Canvas = Color(0xFF0B0B0D)

/** Cards and grouped rows. */
val Surface = Color(0xFF141417)

/** Input fields, inner wells, pressed rows. */
val SurfaceRaised = Color(0xFF1C1C21)

/** The highest step, for a control sitting on a raised surface. */
val SurfaceHigh = Color(0xFF26262C)

/** Hairlines. Close enough to the surface to separate without drawing a box. */
val Border = Color(0xFF232329)

/** For the single element that currently needs emphasis. */
val BorderStrong = Color(0xFF35353D)

// Text -----------------------------------------------------------------------

/** Warm near-white. Pure #FFF vibrates against a dark ground. */
val TextPrimary = Color(0xFFF2F1EF)

val TextSecondary = Color(0xFF9C9BA4)

val TextTertiary = Color(0xFF67666F)

// Accent ---------------------------------------------------------------------

/**
 * A soft indigo-violet. The previous flat blue read as a stock Material default;
 * this sits closer to the products the user pointed at, and stays legible
 * against the warm greys without glowing.
 */
val Accent = Color(0xFF7C7AF5)
val AccentPressed = Color(0xFF6462E0)
val AccentMuted = Color(0x267C7AF5)

/** Second voice, used only in the voice visualiser gradient. */
val AccentWarm = Color(0xFFE8875A)

// Semantic -------------------------------------------------------------------

val Success = Color(0xFF3DBE7C)
val Warning = Color(0xFFE0A22B)
val Danger = Color(0xFFE8615D)

val SuccessMuted = Color(0x263DBE7C)
val WarningMuted = Color(0x26E0A22B)
val DangerMuted = Color(0x26E8615D)

// Scheme ---------------------------------------------------------------------

val TarziColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = AccentMuted,
    onPrimaryContainer = Accent,
    secondary = TextSecondary,
    onSecondary = Canvas,
    background = Canvas,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceRaised,
    onSurfaceVariant = TextSecondary,
    outline = Border,
    outlineVariant = BorderStrong,
    error = Danger,
    onError = Color.White,
    scrim = Color(0xE6000000)
)
