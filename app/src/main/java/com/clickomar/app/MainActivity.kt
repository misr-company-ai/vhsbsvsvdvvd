package com.clickomar.app

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tvServiceStatus: TextView
    private lateinit var tvPointValue: TextView
    private lateinit var radioMode: RadioGroup
    private lateinit var etTargetText: EditText
    private lateinit var etInterval: EditText
    private lateinit var etDuration: EditText
    private lateinit var etStartDelay: EditText

    private var pickedX: Float? = null
    private var pickedY: Float? = null

    private val pointReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val x = intent?.getFloatExtra(OverlayService.EXTRA_X, -1f) ?: -1f
            val y = intent?.getFloatExtra(OverlayService.EXTRA_Y, -1f) ?: -1f
            if (x >= 0 && y >= 0) {
                pickedX = x
                pickedY = y
                tvPointValue.text = "النقطة المحددة: (${x.toInt()}, ${y.toInt()})"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvServiceStatus = findViewById(R.id.tvServiceStatus)
        tvPointValue = findViewById(R.id.tvPointValue)
        radioMode = findViewById(R.id.radioMode)
        etTargetText = findViewById(R.id.etTargetText)
        etInterval = findViewById(R.id.etInterval)
        etDuration = findViewById(R.id.etDuration)
        etStartDelay = findViewById(R.id.etStartDelay)

        findViewById<android.widget.Button>(R.id.btnEnableService).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<android.widget.Button>(R.id.btnOverlayPermission).setOnClickListener {
            requestOverlayPermission()
        }

        findViewById<android.widget.Button>(R.id.btnPickPoint).setOnClickListener {
            if (!hasOverlayPermission()) {
                Toast.makeText(this, "من فضلك فعّل صلاحية الظهور فوق التطبيقات أولاً", Toast.LENGTH_LONG).show()
                requestOverlayPermission()
                return@setOnClickListener
            }
            val intent = Intent(this, OverlayService::class.java)
            intent.action = OverlayService.ACTION_PICK_POINT
            ContextCompat.startForegroundService(this, intent)
            Toast.makeText(this, "اضغط في أي مكان على الشاشة لتحديد النقطة", Toast.LENGTH_SHORT).show()
        }

        findViewById<android.widget.Button>(R.id.btnStart).setOnClickListener { startClicking() }
        findViewById<android.widget.Button>(R.id.btnStop).setOnClickListener { stopClicking() }
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
        val filter = IntentFilter(OverlayService.BROADCAST_POINT_PICKED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pointReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(pointReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(pointReceiver) } catch (_: Exception) {}
    }

    private fun updateServiceStatus() {
        val enabled = isAccessibilityServiceEnabled()
        tvServiceStatus.text = if (enabled) "خدمة النقر: مفعّلة ✅" else "خدمة النقر: غير مفعّلة ❌"
        tvServiceStatus.setTextColor(
            ContextCompat.getColor(this, if (enabled) R.color.blue_dark else R.color.red_stop)
        )
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        return enabledServices.any { it.resolveInfo.serviceInfo.packageName == packageName }
    }

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun startClicking() {
        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "فعّل خدمة إمكانية الوصول أولاً", Toast.LENGTH_LONG).show()
            return
        }

        val isPointMode = radioMode.checkedRadioButtonId == R.id.radioPoint
        val interval = etInterval.text.toString().toLongOrNull() ?: 1000L
        val durationSec = etDuration.text.toString().toLongOrNull() ?: 0L
        val startDelaySec = etStartDelay.text.toString().toLongOrNull() ?: 0L
        val durationMs = durationSec * 1000L

        if (isPointMode && (pickedX == null || pickedY == null)) {
            Toast.makeText(this, "من فضلك حدد نقطة النقر أولاً", Toast.LENGTH_LONG).show()
            return
        }

        if (!isPointMode && etTargetText.text.toString().isBlank()) {
            Toast.makeText(this, "من فضلك اكتب النص المطلوب الضغط عليه", Toast.LENGTH_LONG).show()
            return
        }

        if (hasOverlayPermission()) {
            val controlIntent = Intent(this, OverlayService::class.java)
            controlIntent.action = OverlayService.ACTION_SHOW_CONTROL
            ContextCompat.startForegroundService(this, controlIntent)
        }

        etStartDelay.postDelayed({
            val service = ClickAccessibilityService.instance
            if (service == null) {
                Toast.makeText(this, "خدمة إمكانية الوصول غير متصلة، أعد تفعيلها", Toast.LENGTH_LONG).show()
                return@postDelayed
            }
            if (isPointMode) {
                service.startPointClicking(pickedX!!, pickedY!!, interval, durationMs)
            } else {
                service.startTextClicking(etTargetText.text.toString(), interval, durationMs)
            }
            Toast.makeText(this, "بدأ النقر التلقائي", Toast.LENGTH_SHORT).show()
        }, startDelaySec * 1000L)
    }

    private fun stopClicking() {
        ClickAccessibilityService.instance?.stopClicking()
        val hideIntent = Intent(this, OverlayService::class.java)
        hideIntent.action = OverlayService.ACTION_HIDE_CONTROL
        ContextCompat.startForegroundService(this, hideIntent)
        Toast.makeText(this, "تم الإيقاف", Toast.LENGTH_SHORT).show()
    }
}
