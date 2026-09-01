package com.antigravity.shieldx.assistant.system

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Base64
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.antigravity.shieldx.agent.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

/**
 * Lets Tarzi actually see the screen, rather than only reading its text.
 *
 * The accessibility service already gives Tarzi the on-screen element tree, which
 * is cheaper and more precise for tapping things. This service exists for the
 * cases text cannot answer - images, video frames, charts, anything visual - by
 * capturing a real frame and handing it to the model as an image.
 *
 * MediaProjection requires the user to approve capture through a system dialog,
 * so [requestPermission] must be called from an Activity first.
 */
class ScreenVisionService : Service() {

    companion object {
        private const val TAG = "TarziVision"
        const val ACTION_START = "com.antigravity.shieldx.tarzi.VISION_START"
        const val ACTION_STOP = "com.antigravity.shieldx.tarzi.VISION_STOP"

        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        private const val NOTIFICATION_ID = 3002
        private const val CHANNEL_ID = "tarzi_vision_channel"

        /** Downscale target: enough detail for a model, small enough to send. */
        private const val MAX_DIMENSION = 1024
        private const val JPEG_QUALITY = 80

        @Volatile
        private var instance: ScreenVisionService? = null

        private val _isCapturingFlow = MutableStateFlow(false)
        val isCapturingFlow: StateFlow<Boolean> = _isCapturingFlow.asStateFlow()

        fun get(): ScreenVisionService? = instance

        /** Build the intent an Activity launches to ask the user for capture consent. */
        fun buildPermissionIntent(context: Context): Intent {
            val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                as MediaProjectionManager
            return manager.createScreenCaptureIntent()
        }

        /** Start capture with the result the consent dialog handed back. */
        fun startWithConsent(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ScreenVisionService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, ScreenVisionService::class.java).apply { action = ACTION_STOP }
            )
        }
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private var captureWidth = 0
    private var captureHeight = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            teardown()
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED

        @Suppress("DEPRECATION")
        val resultData: Intent? = intent?.getParcelableExtra(EXTRA_RESULT_DATA)

        if (resultCode != Activity.RESULT_OK || resultData == null) {
            Log.w(TAG, "[VISION_NO_CONSENT] Screen capture was not approved")
            stopSelf()
            return START_NOT_STICKY
        }

        // Android requires the foreground service to be live before the
        // projection is acquired, so this ordering matters.
        promoteToForeground()
        beginProjection(resultCode, resultData)
        return START_STICKY
    }

    private fun promoteToForeground() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Tarzi is watching your screen")
            .setContentText("Screen sharing is active")
            .setSmallIcon(R.drawable.ic_tarzi_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun beginProjection(resultCode: Int, resultData: Intent) {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = manager.getMediaProjection(resultCode, resultData)
        if (projection == null) {
            Log.e(TAG, "[VISION_FAIL] Could not obtain MediaProjection")
            stopSelf()
            return
        }

        // The user can revoke capture from the system UI at any time.
        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.i(TAG, "[VISION_REVOKED] User stopped screen sharing")
                teardown()
                stopSelf()
            }
        }, null)

        mediaProjection = projection

        val metrics = currentMetrics()
        val scale = computeScale(metrics.widthPixels, metrics.heightPixels)
        captureWidth = (metrics.widthPixels * scale).toInt()
        captureHeight = (metrics.heightPixels * scale).toInt()

        imageReader = ImageReader.newInstance(
            captureWidth,
            captureHeight,
            PixelFormat.RGBA_8888,
            2
        )

        virtualDisplay = projection.createVirtualDisplay(
            "TarziScreenVision",
            captureWidth,
            captureHeight,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )

        instance = this
        _isCapturingFlow.value = true
        Log.i(TAG, "[VISION_ACTIVE] Capturing at ${captureWidth}x$captureHeight")
    }

    @Suppress("DEPRECATION")
    private fun currentMetrics(): DisplayMetrics {
        val metrics = DisplayMetrics()
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm.defaultDisplay.getRealMetrics(metrics)
        return metrics
    }

    /** Keep the long edge at or under [MAX_DIMENSION]. */
    private fun computeScale(width: Int, height: Int): Float {
        val longest = maxOf(width, height)
        return if (longest <= MAX_DIMENSION) 1f else MAX_DIMENSION.toFloat() / longest
    }

    // ==========================================================
    // Frame capture
    // ==========================================================

    /**
     * Grab the current screen as a base64 JPEG, ready to attach to a model request.
     * Returns null when capture is not running or no frame is available yet.
     */
    suspend fun captureFrameBase64(): String? {
        val bitmap = captureBitmap() ?: return null
        return try {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
            bitmap.recycle()
            Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.w(TAG, "[VISION_ENCODE_FAIL] " + e.message)
            null
        }
    }

    /** Pull one frame off the ImageReader and convert it to a Bitmap. */
    private suspend fun captureBitmap(): Bitmap? {
        val reader = imageReader ?: return null

        // The newest available frame; may be null right after startup.
        val image: Image = reader.acquireLatestImage()
            ?: run {
                // Give the virtual display a moment to produce its first frame.
                awaitFrame(reader) ?: return null
            }

        return try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * image.width

            val bitmap = Bitmap.createBitmap(
                image.width + rowPadding / pixelStride,
                image.height,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)

            // Trim the row padding the encoder added.
            if (rowPadding == 0) {
                bitmap
            } else {
                Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height).also {
                    if (it != bitmap) bitmap.recycle()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "[VISION_CAPTURE_FAIL] " + e.message)
            null
        } finally {
            try {
                image.close()
            } catch (_: Exception) {
            }
        }
    }

    /** Wait for the next frame rather than failing on a cold reader. */
    private suspend fun awaitFrame(reader: ImageReader): Image? =
        suspendCancellableCoroutine { cont ->
            val listener = ImageReader.OnImageAvailableListener { r ->
                val img = try {
                    r.acquireLatestImage()
                } catch (_: Exception) {
                    null
                }
                r.setOnImageAvailableListener(null, null)
                if (cont.isActive) cont.resume(img)
            }
            reader.setOnImageAvailableListener(listener, null)
            cont.invokeOnCancellation {
                try {
                    reader.setOnImageAvailableListener(null, null)
                } catch (_: Exception) {
                }
            }
        }

    // ==========================================================
    // Teardown
    // ==========================================================

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Tarzi Screen Vision",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Shown while Tarzi can see your screen" }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun teardown() {
        try {
            virtualDisplay?.release()
            imageReader?.close()
            mediaProjection?.stop()
        } catch (_: Exception) {
        }
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
        instance = null
        _isCapturingFlow.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        super.onDestroy()
        teardown()
    }
}
