package com.clickomar.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ClickAccessibilityService : AccessibilityService() {

    companion object {
        var instance: ClickAccessibilityService? = null
        const val MODE_POINT = 0
        const val MODE_TEXT = 1
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false

    private var mode = MODE_POINT
    private var targetX = 0f
    private var targetY = 0f
    private var targetText = ""
    private var intervalMs = 1000L
    private var totalDurationMs = 0L
    private var startTimeMs = 0L

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return

            if (totalDurationMs > 0 &&
                System.currentTimeMillis() - startTimeMs >= totalDurationMs
            ) {
                stopClicking()
                return
            }

            when (mode) {
                MODE_POINT -> performTap(targetX, targetY)
                MODE_TEXT -> performTextClick(targetText)
            }

            handler.postDelayed(this, intervalMs)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        stopClicking()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    }

    override fun onInterrupt() {
        stopClicking()
    }

    fun startPointClicking(x: Float, y: Float, intervalMillis: Long, durationMillis: Long) {
        mode = MODE_POINT
        targetX = x
        targetY = y
        startCommon(intervalMillis, durationMillis)
    }

    fun startTextClicking(text: String, intervalMillis: Long, durationMillis: Long) {
        mode = MODE_TEXT
        targetText = text
        startCommon(intervalMillis, durationMillis)
    }

    private fun startCommon(intervalMillis: Long, durationMillis: Long) {
        intervalMs = intervalMillis.coerceAtLeast(50L)
        totalDurationMs = durationMillis
        startTimeMs = System.currentTimeMillis()
        isRunning = true
        handler.removeCallbacks(tickRunnable)
        handler.post(tickRunnable)
    }

    fun stopClicking() {
        isRunning = false
        handler.removeCallbacks(tickRunnable)
    }

    fun isServiceRunning(): Boolean = isRunning

    private fun performTap(x: Float, y: Float) {
        val path = Path()
        path.moveTo(x, y)
        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, 50))
        dispatchGesture(gestureBuilder.build(), null, null)
    }

    private fun performTextClick(text: String) {
        if (text.isBlank()) return
        val root = rootInActiveWindow ?: return
        val node = findNodeByText(root, text)
        node?.let {
            val clicked = it.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (!clicked) {
                val bounds = android.graphics.Rect()
                it.getBoundsInScreen(bounds)
                performTap(bounds.exactCenterX(), bounds.exactCenterY())
            }
            it.recycle()
        }
        root.recycle()
    }

    private fun findNodeByText(
        node: AccessibilityNodeInfo,
        text: String
    ): AccessibilityNodeInfo? {
        val nodeText = node.text?.toString() ?: ""
        val nodeDesc = node.contentDescription?.toString() ?: ""
        if (nodeText.contains(text, ignoreCase = true) ||
            nodeDesc.contains(text, ignoreCase = true)
        ) {
            return AccessibilityNodeInfo.obtain(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeByText(child, text)
            if (result != null) {
                child.recycle()
                return result
            }
            child.recycle()
        }
        return null
    }
}
