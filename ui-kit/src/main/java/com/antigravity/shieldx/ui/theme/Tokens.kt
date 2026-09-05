package com.antigravity.shieldx.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * The single source of spacing, shape, and motion for the whole app.
 *
 * Everything on screen picks from these values. Nothing hardcodes its own
 * padding or corner radius, because that is how a UI drifts into looking like a
 * pile of unrelated components rather than one product.
 */

/** 4dp base scale. Use the named steps, not raw numbers. */
object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val section = 32.dp

    /** Horizontal page gutter. Every screen uses this so edges line up. */
    val gutter = 20.dp
}

/**
 * Cohesive radius system for consistent card, modal, and control surfaces.
 */
object Radius {
    val sm = RoundedCornerShape(10.dp)
    val md = RoundedCornerShape(14.dp)
    val lg = RoundedCornerShape(18.dp)
    val full = RoundedCornerShape(percent = 50)
}

/** Motion is for orientation and feedback only, never decoration. */
object Motion {
    const val fast = 120
    const val standard = 220
    const val slow = 320
}
