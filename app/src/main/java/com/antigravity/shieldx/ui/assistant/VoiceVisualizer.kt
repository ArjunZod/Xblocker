package com.antigravity.shieldx.ui.assistant

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * State machine for TaRZI Live Voice Visualizer.
 */
enum class VoicePhase {
    Idle,
    Listening,
    Thinking,
    Speaking,
    Interrupted,
    Error
}

/**
 * State-of-the-art organic fluid audio-reactive visualizer for TaRZI Live Voice Mode.
 * Reacts to actual microphone amplitude and speech synthesis dynamics.
 */
@Composable
fun VoiceVisualizer(
    phase: VoicePhase,
    amplitude: Float,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "voice_organic")

    // Four incommensurate phase timers so the fluid shape evolves continuously
    val t1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(6500, easing = LinearEasing)),
        label = "t1"
    )
    val t2 by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(9800, easing = LinearEasing)),
        label = "t2"
    )
    val t3 by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing)),
        label = "t3"
    )
    val t4 by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(13400, easing = LinearEasing)),
        label = "t4"
    )

    // Gentle organic breathing pulse
    val breath by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )

    // Dynamic energy smoothed with spring physics
    val energy by animateFloatAsState(
        targetValue = when (phase) {
            VoicePhase.Idle -> 0.05f
            VoicePhase.Listening -> 0.15f + amplitude.coerceIn(0f, 1f) * 0.85f
            VoicePhase.Thinking -> 0.35f
            VoicePhase.Speaking -> 0.45f + amplitude.coerceIn(0f, 1f) * 0.55f
            VoicePhase.Interrupted -> 0.90f
            VoicePhase.Error -> 0.20f
        },
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 220f),
        label = "energy"
    )

    val palette = when (phase) {
        VoicePhase.Idle -> listOf(Color(0xFF4A4A58), Color(0xFF2C2C35))
        VoicePhase.Listening -> listOf(Color(0xFF7C7AF5), Color(0xFFE8875A), Color(0xFF6366F1))
        VoicePhase.Thinking -> listOf(Color(0xFF6366F1), Color(0xFF8B5CF6), Color(0xFF3B82F6))
        VoicePhase.Speaking -> listOf(Color(0xFFE8875A), Color(0xFF7C7AF5), Color(0xFFF43F5E))
        VoicePhase.Interrupted -> listOf(Color(0xFFF43F5E), Color(0xFFE8875A))
        VoicePhase.Error -> listOf(Color(0xFFE8615D), Color(0xFF7C2D12))
    }

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val minDim = minOf(size.width, size.height)
        val baseRadius = minDim * 0.32f * breath

        // Outer ambient glow aura
        drawBlob(
            center = center,
            baseRadius = baseRadius * 1.35f,
            energy = energy * 0.7f,
            t1 = t1, t2 = t2, t3 = t3, t4 = t4,
            brush = Brush.radialGradient(
                colors = listOf(palette[0].copy(alpha = 0.18f), Color.Transparent),
                center = center,
                radius = baseRadius * 1.6f
            )
        )

        // Secondary mid harmonic layer
        drawBlob(
            center = center,
            baseRadius = baseRadius * 1.15f,
            energy = energy * 0.9f,
            t1 = t1 + 1.2f, t2 = t2 + 0.8f, t3 = t3 + 2.1f, t4 = t4 + 0.5f,
            brush = Brush.linearGradient(
                colors = palette.map { it.copy(alpha = 0.35f) },
                start = Offset(0f, size.height),
                end = Offset(size.width, 0f)
            )
        )

        // Core primary fluid body
        drawBlob(
            center = center,
            baseRadius = baseRadius,
            energy = energy,
            t1 = t1 + 2.5f, t2 = t2 + 1.8f, t3 = t3 + 0.9f, t4 = t4 + 3.1f,
            brush = Brush.linearGradient(
                colors = palette,
                start = Offset(size.width * 0.15f, size.height * 0.15f),
                end = Offset(size.width * 0.85f, size.height * 0.85f)
            )
        )

        // Crisp inner specular edge
        drawBlob(
            center = center,
            baseRadius = baseRadius * 0.98f,
            energy = energy,
            t1 = t1 + 2.5f, t2 = t2 + 1.8f, t3 = t3 + 0.9f, t4 = t4 + 3.1f,
            brush = Brush.linearGradient(
                colors = listOf(Color.White.copy(alpha = 0.5f), Color.Transparent, palette[0].copy(alpha = 0.6f))
            ),
            stroke = true
        )
    }
}

/**
 * Builds smooth closed organic fluid contours using dense harmonic deformation.
 */
private fun DrawScope.drawBlob(
    center: Offset,
    baseRadius: Float,
    energy: Float,
    t1: Float,
    t2: Float,
    t3: Float,
    t4: Float,
    brush: Brush,
    stroke: Boolean = false
) {
    val points = 96
    val path = Path()
    val wobble = baseRadius * (0.05f + energy * 0.35f)

    val coords = ArrayList<Offset>(points)
    for (i in 0 until points) {
        val angle = (i.toFloat() / points) * 2f * PI.toFloat()

        val deform =
            sin(angle * 3f + t1) * 0.50f +
            sin(angle * 5f - t2) * 0.30f +
            sin(angle * 2f + t3) * 0.35f +
            sin(angle * 7f + t4) * 0.15f

        val radius = baseRadius + deform * wobble
        coords.add(
            Offset(
                x = center.x + cos(angle) * radius,
                y = center.y + sin(angle) * radius
            )
        )
    }

    path.moveTo(
        (coords[0].x + coords[points - 1].x) / 2f,
        (coords[0].y + coords[points - 1].y) / 2f
    )
    for (i in 0 until points) {
        val current = coords[i]
        val next = coords[(i + 1) % points]
        path.quadraticTo(
            current.x, current.y,
            (current.x + next.x) / 2f, (current.y + next.y) / 2f
        )
    }
    path.close()

    drawPath(
        path = path,
        brush = brush,
        style = if (stroke) Stroke(width = 1.5f) else Fill
    )
}
