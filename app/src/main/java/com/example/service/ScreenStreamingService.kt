package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScreenStreamingService : Service() {

    companion object {
        private const val TAG = "ScreenStreamService"
        private const val NOTIFICATION_ID = 2026
        private const val CHANNEL_ID = "catmirror_stream_channel"

        const val ACTION_START = "com.example.catmirror.ACTION_START"
        const val ACTION_STOP = "com.example.catmirror.ACTION_STOP"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_DATA = "extra_data"

        private val _isStreaming = MutableStateFlow(false)
        val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

        var onFrameCaptured: ((width: Int, height: Int, timestamp: Long, jpegData: ByteArray) -> Unit)? = null
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private var simulationJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Default)
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        handlerThread = HandlerThread("ScreenCaptureThread").apply { start() }
        backgroundHandler = Handler(handlerThread!!.looper)

        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            wakeLock = pm?.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "catmirror:StreamWakeLock")?.apply {
                setReferenceCounted(false)
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "No se pudo crear WakeLock: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopStreaming()
                stopSelf()
            }
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                @Suppress("DEPRECATION")
                val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                } else {
                    intent.getParcelableExtra(EXTRA_DATA)
                }

                val hasValidConsent = (resultCode != 0 && resultData != null)
                startForegroundServiceNotification(hasValidConsent)

                if (hasValidConsent) {
                    startMediaProjection(resultCode, resultData!!)
                } else {
                    AppLogger.i(TAG, "Iniciando simulador de streaming (sin token MediaProjection)")
                    startSimulatedStream()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun startForegroundServiceNotification(isRealMediaProjection: Boolean) {
        val stopIntent = Intent(this, ScreenStreamingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openAppIntent = Intent(this, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_streaming_title))
            .setContentText(getString(R.string.notification_streaming_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Detener", stopPendingIntent)
            .setOngoing(true)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (isRealMediaProjection) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error llamando a startForeground: ${e.message}", e)
        }
    }

    private var displayListener: DisplayManager.DisplayListener? = null
    private var currentCaptureWidth = 0
    private var currentCaptureHeight = 0
    private var currentDensityDpi = 0
    private val captureLock = Any()

    private val orientationCheckRunnable = object : Runnable {
        override fun run() {
            if (_isStreaming.value) {
                checkOrientationAndResize()
                backgroundHandler?.postDelayed(this, 1200)
            }
        }
    }

    private fun calculateTargetDimensions(): Triple<Int, Int, Int> {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)

        val rawW = if (metrics.widthPixels > 0) metrics.widthPixels else 1080
        val rawH = if (metrics.heightPixels > 0) metrics.heightPixels else 2340
        val rawDpi = if (metrics.densityDpi > 0) metrics.densityDpi else 400

        val isLandscape = rawW > rawH
        val shortDim = if (isLandscape) rawH else rawW
        val longDim = if (isLandscape) rawW else rawH

        // Escalar lado corto a 720p máximo para latencia óptima en Wi-Fi
        val scale = (720f / shortDim).coerceAtMost(1f)
        val scaledShort = ((shortDim * scale).toInt() / 2) * 2
        val scaledLong = ((longDim * scale).toInt() / 2) * 2
        val scaledDpi = (rawDpi * scale).toInt()

        val targetWidth = if (isLandscape) scaledLong else scaledShort
        val targetHeight = if (isLandscape) scaledShort else scaledLong

        return Triple(targetWidth, targetHeight, scaledDpi)
    }

    private fun checkOrientationAndResize() {
        if (!_isStreaming.value || mediaProjection == null) return
        val (w, h, dpi) = calculateTargetDimensions()
        if (w > 0 && h > 0 && (w != currentCaptureWidth || h != currentCaptureHeight)) {
            reconfigureVirtualDisplay(w, h, dpi)
        }
    }

    private fun reconfigureVirtualDisplay(newWidth: Int, newHeight: Int, newDpi: Int) {
        val vDisplay = virtualDisplay ?: return
        if (newWidth <= 0 || newHeight <= 0) return

        synchronized(captureLock) {
            try {
                AppLogger.i(TAG, "Adaptando captura a orientación: ${newWidth}x${newHeight} ($newDpi dpi)")
                val newReader = ImageReader.newInstance(newWidth, newHeight, PixelFormat.RGBA_8888, 2)
                setupImageReaderListener(newReader, newWidth, newHeight)

                vDisplay.resize(newWidth, newHeight, newDpi)
                vDisplay.surface = newReader.surface

                val oldReader = imageReader
                imageReader = newReader
                currentCaptureWidth = newWidth
                currentCaptureHeight = newHeight
                currentDensityDpi = newDpi

                oldReader?.close()
                AppLogger.i(TAG, "VirtualDisplay actualizado a ${newWidth}x${newHeight} con éxito")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error reconfigurando VirtualDisplay: ${e.message}", e)
            }
        }
    }

    private fun setupImageReaderListener(reader: ImageReader, width: Int, height: Int) {
        reader.setOnImageAvailableListener({ r ->
            synchronized(captureLock) {
                if (r != imageReader) {
                    try { r.acquireLatestImage()?.close() } catch (_: Exception) {}
                    return@setOnImageAvailableListener
                }
                val image = try {
                    r.acquireLatestImage()
                } catch (_: Exception) {
                    null
                } ?: return@setOnImageAvailableListener

                try {
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * width

                    val bitmap = Bitmap.createBitmap(
                        width + rowPadding / pixelStride,
                        height,
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.copyPixelsFromBuffer(buffer)

                    val cleanBitmap = if (rowPadding > 0) {
                        Bitmap.createBitmap(bitmap, 0, 0, width, height)
                    } else {
                        bitmap
                    }

                    val stream = ByteArrayOutputStream()
                    cleanBitmap.compress(Bitmap.CompressFormat.JPEG, 65, stream)
                    val jpegBytes = stream.toByteArray()

                    onFrameCaptured?.invoke(width, height, System.currentTimeMillis(), jpegBytes)

                    if (cleanBitmap != bitmap) {
                        cleanBitmap.recycle()
                    }
                    bitmap.recycle()
                } catch (e: Exception) {
                    Log.w(TAG, "Error processing frame: ${e.message}")
                } finally {
                    image.close()
                }
            }
        }, backgroundHandler)
    }

    @SuppressLint("WrongConstant")
    private fun startMediaProjection(resultCode: Int, data: Intent) {
        try {
            val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
            mediaProjection = mpManager?.getMediaProjection(resultCode, data)

            if (mediaProjection == null) {
                AppLogger.e(TAG, "MediaProjection fue nulo; iniciando generador simulado")
                startSimulatedStream()
                return
            }

            // OBLIGATORIO EN ANDROID 14+ (API 34+): Debe registrarse un callback antes de createVirtualDisplay
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    AppLogger.i(TAG, "MediaProjection detenido por el sistema o usuario")
                    stopStreaming()
                }
            }, backgroundHandler)

            val (targetWidth, targetHeight, densityDpi) = calculateTargetDimensions()
            currentCaptureWidth = targetWidth
            currentCaptureHeight = targetHeight
            currentDensityDpi = densityDpi

            AppLogger.i(TAG, "Capturando pantalla real a ${targetWidth}x${targetHeight} ($densityDpi dpi)")

            imageReader = ImageReader.newInstance(targetWidth, targetHeight, PixelFormat.RGBA_8888, 2)
            setupImageReaderListener(imageReader!!, targetWidth, targetHeight)

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "CatMirrorDisplay",
                targetWidth,
                targetHeight,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                backgroundHandler
            )

            val displayManager = getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            displayListener = object : DisplayManager.DisplayListener {
                override fun onDisplayAdded(displayId: Int) {}
                override fun onDisplayRemoved(displayId: Int) {}
                override fun onDisplayChanged(displayId: Int) {
                    if (displayId == android.view.Display.DEFAULT_DISPLAY) {
                        checkOrientationAndResize()
                    }
                }
            }
            displayManager?.registerDisplayListener(displayListener, backgroundHandler)
            backgroundHandler?.postDelayed(orientationCheckRunnable, 1200)

            _isStreaming.value = true
            try {
                if (wakeLock?.isHeld == false) {
                    wakeLock?.acquire(12 * 60 * 60 * 1000L)
                    AppLogger.i(TAG, "WakeLock adquirido (evita suspensión de CPU/Red)")
                }
            } catch (_: Exception) {}
        } catch (e: Exception) {
            AppLogger.e(TAG, "Fallo al iniciar MediaProjection: ${e.message}", e)
            startSimulatedStream()
        }
    }

    private fun startSimulatedStream() {
        simulationJob?.cancel()
        _isStreaming.value = true
        try {
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(12 * 60 * 60 * 1000L)
            }
        } catch (_: Exception) {}

        val width = 720
        val height = 1280
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

        simulationJob = serviceScope.launch(Dispatchers.Default) {
            var frameIdx = 0
            while (isActive && _isStreaming.value) {
                val now = System.currentTimeMillis()
                // Render simulated phone screen
                canvas.drawColor(Color.parseColor("#0F141C"))

                // Header bar
                paint.color = Color.parseColor("#182230")
                canvas.drawRect(0f, 0f, width.toFloat(), 120f, paint)

                paint.color = Color.parseColor("#FF9E2C")
                paint.textSize = 42f
                paint.isFakeBoldText = true
                canvas.drawText("catmirror Transmisor", 40f, 75f, paint)

                paint.color = Color.parseColor("#00E676")
                paint.textSize = 28f
                paint.isFakeBoldText = false
                canvas.drawText("● EN VIVO", (width - 180).toFloat(), 75f, paint)

                // Central preview box
                paint.color = Color.parseColor("#222F42")
                canvas.drawRoundRect(40f, 160f, (width - 40).toFloat(), (height - 200).toFloat(), 32f, 32f, paint)

                // Neon animated target
                val circleY = 320f + ((frameIdx % 100) * 4f)
                paint.color = Color.parseColor("#00E5FF")
                canvas.drawCircle(width / 2f, circleY, 50f, paint)

                paint.color = Color.WHITE
                paint.textSize = 34f
                val timeStr = dateFormat.format(Date(now))
                canvas.drawText("Tiempo: $timeStr", 80f, 520f, paint)
                canvas.drawText("Cuadro: #$frameIdx", 80f, 580f, paint)

                paint.color = Color.parseColor("#94A3B8")
                paint.textSize = 26f
                canvas.drawText("Toca en el receptor para interactuar", 80f, 660f, paint)

                // Virtual bottom nav
                paint.color = Color.parseColor("#182230")
                canvas.drawRect(0f, (height - 120).toFloat(), width.toFloat(), height.toFloat(), paint)

                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 60, stream)
                val jpegBytes = stream.toByteArray()

                onFrameCaptured?.invoke(width, height, now, jpegBytes)

                frameIdx++
                delay(40) // ~25 FPS
            }
        }
    }

    private fun stopStreaming() {
        _isStreaming.value = false
        simulationJob?.cancel()
        simulationJob = null

        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                AppLogger.i(TAG, "WakeLock liberado")
            }
        } catch (_: Exception) {}

        try {
            backgroundHandler?.removeCallbacks(orientationCheckRunnable)
            displayListener?.let {
                val dm = getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
                dm?.unregisterDisplayListener(it)
                displayListener = null
            }
            virtualDisplay?.release()
            virtualDisplay = null
            synchronized(captureLock) {
                imageReader?.close()
                imageReader = null
            }
            mediaProjection?.stop()
            mediaProjection = null
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping media projection: ${e.message}")
        }
    }

    override fun onDestroy() {
        stopStreaming()
        handlerThread?.quitSafely()
        handlerThread = null
        backgroundHandler = null
        super.onDestroy()
    }
}
