package com.antigravity.shieldx.assistant.system

import android.animation.ValueAnimator
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * A draggable Tarzi orb that floats above every other app.
 *
 * It is the visible half of the always-on assistant: it shows what Tarzi is
 * doing and gives the user a tap target when they would rather not speak the
 * wake word out loud.
 */
class TarziOverlayService : Service() {

    companion object {
        const val ACTION_SHOW = "com.antigravity.shieldx.tarzi.OVERLAY_SHOW"
        const val ACTION_HIDE = "com.antigravity.shieldx.tarzi.OVERLAY_HIDE"

        /** Overlay drawing needs an explicit, user-granted special permission. */
        fun canDrawOverlay(context: Context): Boolean = Settings.canDrawOverlays(context)

        fun show(context: Context) {
            context.startService(
                Intent(context, TarziOverlayService::class.java).apply { action = ACTION_SHOW }
            )
        }

        fun hide(context: Context) {
            context.startService(
                Intent(context, TarziOverlayService::class.java).apply { action = ACTION_HIDE }
            )
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var windowManager: WindowManager? = null
    private var orbView: OrbView? = null
    private var orbParams: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> {
                removeOrb()
                stopSelf()
            }

            else -> addOrb()
        }
        return START_STICKY
    }

    private fun addOrb() {
        if (orbView != null) return
        if (!canDrawOverlay(this)) {
            stopSelf()
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            ORB_SIZE_PX,
            ORB_SIZE_PX,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - ORB_SIZE_PX
            y = resources.displayMetrics.heightPixels / 3
        }

        val view = OrbView(this)
        view.setOnTapListener {
            // Tapping the orb is the silent equivalent of saying the wake word.
            TarziVoiceService.listenNow(this)
        }

        try {
            windowManager?.addView(view, params)
            orbView = view
            orbParams = params
        } catch (_: Exception) {
            stopSelf()
            return
        }

        // Mirror assistant state into the orb.
        scope.launch {
            TarziVoiceService.stateFlow.collectLatest { state ->
                orbView?.setState(state)
            }
        }
    }

    private fun removeOrb() {
        try {
            orbView?.let { windowManager?.removeView(it) }
        } catch (_: Exception) {
        }
        orbView = null
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOrb()
    }

    // ==========================================================
    // The orb itself
    // ==========================================================

    private inner class OrbView(context: Context) : View(context) {

        private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }

        private var state = TarziState.WAITING
        private var pulse = 0f
        private var onTap: (() -> Unit)? = null

        private var dragStartX = 0f
        private var dragStartY = 0f
        private var initialX = 0
        private var initialY = 0
        private var isDragging = false

        private val pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1600
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                pulse = it.animatedValue as Float
                invalidate()
            }
            start()
        }

        fun setOnTapListener(listener: () -> Unit) {
            onTap = listener
        }

        fun setState(newState: TarziState) {
            state = newState
            invalidate()
        }

        /** Each state gets its own colour so the orb is readable at a glance. */
        private fun accentColor(): Int = when (state) {
            TarziState.OFFLINE -> Color.parseColor("#64748B")
            TarziState.WAITING -> Color.parseColor("#00E5FF")
            TarziState.LISTENING -> Color.parseColor("#10B981")
            TarziState.THINKING -> Color.parseColor("#F59E0B")
            TarziState.SPEAKING -> Color.parseColor("#8B5CF6")
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val cx = width / 2f
            val cy = height / 2f
            val accent = accentColor()

            // Outer glow, breathing while active.
            val breathing = state != TarziState.OFFLINE
            val glowRadius = if (breathing) {
                cx * (0.75f + 0.25f * kotlin.math.sin(pulse * 2 * Math.PI).toFloat())
            } else {
                cx * 0.75f
            }

            glowPaint.shader = RadialGradient(
                cx, cy, glowRadius.coerceAtLeast(1f),
                intArrayOf(
                    Color.argb(120, Color.red(accent), Color.green(accent), Color.blue(accent)),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, glowRadius, glowPaint)

            // Solid dark core so the orb reads on any wallpaper.
            corePaint.color = Color.parseColor("#0B0E14")
            canvas.drawCircle(cx, cy, cx * 0.52f, corePaint)

            // Accent ring.
            ringPaint.color = accent
            canvas.drawCircle(cx, cy, cx * 0.52f, ringPaint)

            // Inner dot, scaled by the pulse while listening.
            val dotScale = if (state == TarziState.LISTENING) {
                0.18f + 0.10f * kotlin.math.sin(pulse * 4 * Math.PI).toFloat()
            } else {
                0.18f
            }
            corePaint.color = accent
            canvas.drawCircle(cx, cy, cx * abs(dotScale), corePaint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val params = orbParams ?: return false

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartX = event.rawX
                    dragStartY = event.rawY
                    initialX = params.x
                    initialY = params.y
                    isDragging = false
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - dragStartX
                    val dy = event.rawY - dragStartY
                    // Only treat it as a drag past a small threshold, so taps stay taps.
                    if (abs(dx) > 12 || abs(dy) > 12) {
                        isDragging = true
                        params.x = initialX + dx.toInt()
                        params.y = initialY + dy.toInt()
                        try {
                            windowManager?.updateViewLayout(this, params)
                        } catch (_: Exception) {
                        }
                    }
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    if (!isDragging) onTap?.invoke()
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            pulseAnimator.cancel()
        }
    }
}

private val ORB_SIZE_PX: Int
    get() = (72 * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
