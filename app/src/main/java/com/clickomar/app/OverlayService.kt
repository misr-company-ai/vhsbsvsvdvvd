package com.clickomar.app

import android.app.*
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    companion object {
        const val ACTION_PICK_POINT = "com.clickomar.app.PICK_POINT"
        const val ACTION_SHOW_CONTROL = "com.clickomar.app.SHOW_CONTROL"
        const val ACTION_HIDE_CONTROL = "com.clickomar.app.HIDE_CONTROL"
        const val BROADCAST_POINT_PICKED = "com.clickomar.app.POINT_PICKED"
        const val EXTRA_X = "extra_x"
        const val EXTRA_Y = "extra_y"
        private const val CHANNEL_ID = "click_omar_overlay_channel"
        private const val NOTIF_ID = 1001
    }

    private lateinit var windowManager: WindowManager
    private var pickerView: View? = null
    private var controlView: View? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        when (intent?.action) {
            ACTION_PICK_POINT -> showPicker()
            ACTION_SHOW_CONTROL -> showControl()
            ACTION_HIDE_CONTROL -> removeControl()
        }
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Click Omar", NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Click Omar")
            .setContentText("الخدمة تعمل في الخلفية")
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .build()
    }

    private fun showPicker() {
        removePicker()
        val inflater = LayoutInflater.from(this)
        pickerView = inflater.inflate(R.layout.overlay_point_picker, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        pickerView?.findViewById<View>(R.id.overlayBackground)?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val intent = Intent(BROADCAST_POINT_PICKED)
                intent.putExtra(EXTRA_X, event.rawX)
                intent.putExtra(EXTRA_Y, event.rawY)
                sendBroadcast(intent)
                removePicker()
                stopSelfIfIdle()
            }
            true
        }

        windowManager.addView(pickerView, params)
    }

    private fun showControl() {
        removeControl()
        val inflater = LayoutInflater.from(this)
        controlView = inflater.inflate(R.layout.overlay_floating_control, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = android.view.Gravity.TOP or android.view.Gravity.START
        params.x = 20
        params.y = 100

        controlView?.findViewById<View>(R.id.btnFloatingStop)?.setOnClickListener {
            ClickAccessibilityService.instance?.stopClicking()
            removeControl()
        }

        controlView?.setOnTouchListener(object : View.OnTouchListener {
            var initialX = 0
            var initialY = 0
            var touchX = 0f
            var touchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        touchX = event.rawX
                        touchY = event.rawY
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - touchX).toInt()
                        params.y = initialY + (event.rawY - touchY).toInt()
                        windowManager.updateViewLayout(controlView, params)
                    }
                }
                return false
            }
        })

        windowManager.addView(controlView, params)
    }

    private fun overlayType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun removePicker() {
        pickerView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        pickerView = null
    }

    private fun removeControl() {
        controlView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        controlView = null
    }

    private fun stopSelfIfIdle() {
        if (controlView == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        removePicker()
        removeControl()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
