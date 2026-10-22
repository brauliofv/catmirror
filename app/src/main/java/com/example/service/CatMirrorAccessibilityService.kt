package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
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
        Log.i(TAG, "CatMirror Accessibility Service Connected & Ready for Touch Injection")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used, gestures are driven by incoming remote socket packets
    }

    override fun onInterrupt() {
        Log.w(TAG, "CatMirror Accessibility Service Interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (serviceRef?.get() == this) {
            serviceRef = null
        }
        Log.i(TAG, "CatMirror Accessibility Service Destroyed")
    }

    fun injectTap(xRatio: Float, yRatio: Float): Boolean {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return false
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)

        val screenX = (xRatio.coerceIn(0f, 1f) * metrics.widthPixels)
        val screenY = (yRatio.coerceIn(0f, 1f) * metrics.heightPixels)

        val path = Path().apply {
            moveTo(screenX, screenY)
        }

        val stroke = GestureDescription.StrokeDescription(path, 0, 45)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Tap completed at ($screenX, $screenY)")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Tap cancelled at ($screenX, $screenY)")
            }
        }, null)
    }

    fun injectDrag(
        startXRatio: Float,
        startYRatio: Float,
        endXRatio: Float,
        endYRatio: Float,
        durationMs: Long = 100
    ): Boolean {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return false
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)

        val startX = startXRatio.coerceIn(0f, 1f) * metrics.widthPixels
        val startY = startYRatio.coerceIn(0f, 1f) * metrics.heightPixels
        val endX = endXRatio.coerceIn(0f, 1f) * metrics.widthPixels
        val endY = endYRatio.coerceIn(0f, 1f) * metrics.heightPixels

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceIn(50, 500))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return dispatchGesture(gesture, null, null)
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
