package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.hardware.display.DisplayManager
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.example.util.AppLogger
import java.lang.ref.WeakReference

class CatMirrorAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "CatMirrorA11y"
        private var serviceRef: WeakReference<CatMirrorAccessibilityService>? = null

        fun getInstance(): CatMirrorAccessibilityService? = serviceRef?.get()

        fun isRunning(): Boolean = getInstance() != null

        fun isAccessibilityEnabled(context: Context): Boolean {
            val serviceName = "${context.packageName}/${CatMirrorAccessibilityService::class.java.canonicalName}"
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabledServices.contains(serviceName)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceRef = WeakReference(this)
        AppLogger.i(TAG, "Servicio de Accesibilidad catmirror conectado y listo para inyección táctil")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used, gestures are driven by incoming remote socket packets
    }

    override fun onInterrupt() {
        AppLogger.w(TAG, "Servicio de Accesibilidad catmirror interrumpido")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (serviceRef?.get() == this) {
            serviceRef = null
        }
        AppLogger.i(TAG, "Servicio de Accesibilidad catmirror detenido")
    }

    private fun getScreenDimensions(): Pair<Int, Int> {
        return try {
            val displayManager = getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            val display = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            display?.getRealMetrics(metrics)

            val width = if (metrics.widthPixels > 0) metrics.widthPixels else resources.displayMetrics.widthPixels
            val height = if (metrics.heightPixels > 0) metrics.heightPixels else resources.displayMetrics.heightPixels
            Pair(width, height)
        } catch (e: Exception) {
            Pair(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
        }
    }

    fun injectTap(xRatio: Float, yRatio: Float): Boolean {
        val (screenWidth, screenHeight) = getScreenDimensions()
        val screenX = (xRatio.coerceIn(0f, 1f) * screenWidth)
        val screenY = (yRatio.coerceIn(0f, 1f) * screenHeight)

        // En Android y Samsung One UI, un trazo requiere longitud mínima > 0 para generar eventos UP/DOWN
        val path = Path().apply {
            moveTo(screenX, screenY)
            lineTo(screenX + 1f, screenY + 1f)
        }

        // Duración de 80ms garantiza registro de toque en todas las versiones de Android
        val stroke = GestureDescription.StrokeDescription(path, 0, 80)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        AppLogger.i(TAG, "Inyectando tap en (${screenX.toInt()}, ${screenY.toInt()}) [pantalla ${screenWidth}x${screenHeight}]")

        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                AppLogger.i(TAG, "Tap ejecutado con éxito en (${screenX.toInt()}, ${screenY.toInt()})")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                AppLogger.w(TAG, "Tap CANCELADO por el sistema en (${screenX.toInt()}, ${screenY.toInt()})")
            }
        }, null)
    }

    fun injectDrag(
        startXRatio: Float,
        startYRatio: Float,
        endXRatio: Float,
        endYRatio: Float,
        durationMs: Long = 150
    ): Boolean {
        val (screenWidth, screenHeight) = getScreenDimensions()
        val startX = startXRatio.coerceIn(0f, 1f) * screenWidth
        val startY = startYRatio.coerceIn(0f, 1f) * screenHeight
        val endX = endXRatio.coerceIn(0f, 1f) * screenWidth
        val endY = endYRatio.coerceIn(0f, 1f) * screenHeight

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceIn(80, 600))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        AppLogger.i(TAG, "Inyectando deslizamiento (${startX.toInt()},${startY.toInt()}) -> (${endX.toInt()},${endY.toInt()}) en ${stroke.duration}ms")

        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                AppLogger.i(TAG, "Deslizamiento ejecutado con éxito")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                AppLogger.w(TAG, "Deslizamiento cancelado por el sistema")
            }
        }, null)
    }

    fun executeGlobal(actionCode: Byte): Boolean {
        return when (actionCode) {
            com.example.network.Protocol.GLOBAL_BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
            com.example.network.Protocol.GLOBAL_HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
            com.example.network.Protocol.GLOBAL_RECENTS -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            else -> false
        }
    }
}
