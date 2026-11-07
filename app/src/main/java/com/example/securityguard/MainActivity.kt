package com.example.securityguard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private lateinit var shakeDetector: ShakeDetector
    private var protectionEnabled = false

    private lateinit var statusText: TextView
    private lateinit var shieldIcon: ImageView
    private lateinit var shizukuStatusText: TextView
    private lateinit var overlayStatusText: TextView
    private lateinit var accessibilityStatusText: TextView
    private lateinit var btnToggleOverlayService: Button

    private var overlayServiceRunning = false

    private val notificationPermissionRequestCode = 2001

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            runOnUiThread { updateShizukuStatus() }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.status_text)
        shieldIcon = findViewById(R.id.shield_icon)
        shizukuStatusText = findViewById(R.id.shizuku_status)
        overlayStatusText = findViewById(R.id.overlay_status)
        accessibilityStatusText = findViewById(R.id.accessibility_status)
        btnToggleOverlayService = findViewById(R.id.btn_toggle_overlay_service)

        val btnManualToggle = findViewById<Button>(R.id.btn_manual_toggle)
        val btnAppList = findViewById<Button>(R.id.btn_app_list)
        val btnRequestOverlay = findViewById<Button>(R.id.btn_request_overlay)
        val btnRequestAccessibility = findViewById<Button>(R.id.btn_request_accessibility)
        val btnClearBackground = findViewById<Button>(R.id.btn_clear_background)

        // 前台摇一摇检测（App 在前台时使用；后台防护交给 OverlayService）
        shakeDetector = ShakeDetector(this) {
            toggleProtection()
        }

        btnManualToggle.setOnClickListener { toggleProtection() }

        btnAppList.setOnClickListener {
            startActivity(Intent(this, AppListActivity::class.java))
        }

        btnRequestOverlay.setOnClickListener {
            if (PermissionHelper.hasOverlayPermission(this)) {
                Toast.makeText(this, "已经有悬浮窗权限了", Toast.LENGTH_SHORT).show()
            } else {
                PermissionHelper.requestOverlayPermission(this)
            }
        }

        btnRequestAccessibility.setOnClickListener {
            if (PermissionHelper.isAccessibilityServiceEnabled(this)) {
                Toast.makeText(this, "无障碍服务已开启", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "请在列表中找到「安全管家」并开启", Toast.LENGTH_LONG).show()
                PermissionHelper.requestAccessibilityPermission(this)
            }
        }

        btnToggleOverlayService.setOnClickListener {
            toggleOverlayService()
        }

        btnClearBackground.setOnClickListener {
            val count = BackgroundCleaner.clearAll(this)
            Toast.makeText(this, "已清理 $count 个后台应用进程", Toast.LENGTH_SHORT).show()
        }

        requestNotificationPermissionIfNeeded()

        ShizukuManager.addPermissionResultListener(permissionResultListener)
        updateShizukuStatus()

        if (ShizukuManager.isAvailable() && !ShizukuManager.hasPermission()) {
            ShizukuManager.requestPermission()
        }
    }

    override fun onResume() {
        super.onResume()
        shakeDetector.start()
        updateShizukuStatus()
        updateOverlayStatus()
        updateAccessibilityStatus()
    }

    override fun onPause() {
        super.onPause()
        shakeDetector.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        ShizukuManager.removePermissionResultListener(permissionResultListener)
    }

    // ---------------- 前台摇一摇开关（仅 App 在前台时有效的简单演示） ----------------

    private fun toggleProtection() {
        protectionEnabled = !protectionEnabled
        if (protectionEnabled) {
            statusText.text = "防护已开启"
            findViewById<Button>(R.id.btn_manual_toggle).text = "手动关闭防护"
            runSecurityScan()
        } else {
            statusText.text = "防护已关闭"
            findViewById<Button>(R.id.btn_manual_toggle).text = "手动开启防护"
        }
    }

    private fun runSecurityScan() {
        statusText.text = "防护已开启（扫描中…）"
        statusText.postDelayed({
            val risky = RiskScanner.scanForRiskyApps(this)
            statusText.text = if (risky.isEmpty()) {
                "防护已开启（上次扫描：无异常）"
            } else {
                "防护已开启（发现 ${risky.size} 个风险应用，详情见应用管理）"
            }
        }, 800)
    }

    // ---------------- 后台悬浮窗防护服务开关 ----------------

    private fun toggleOverlayService() {
        if (!PermissionHelper.hasOverlayPermission(this)) {
            Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
            PermissionHelper.requestOverlayPermission(this)
            return
        }

        if (!overlayServiceRunning) {
            OverlayService.start(this)
            overlayServiceRunning = true
            btnToggleOverlayService.text = "停止后台防护服务"
            Toast.makeText(this, "后台防护已启动，退到后台依然生效", Toast.LENGTH_SHORT).show()
        } else {
            OverlayService.stop(this)
            overlayServiceRunning = false
            btnToggleOverlayService.text = "启动后台防护服务"
            Toast.makeText(this, "后台防护已停止", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------------- 状态刷新 ----------------

    private fun updateShizukuStatus() {
        val text = when {
            !ShizukuManager.isAvailable() -> "Shizuku 状态：未运行（请先启动 Shizuku 服务）"
            ShizukuManager.hasPermission() -> "Shizuku 状态：已授权 ✓"
            else -> "Shizuku 状态：已运行，未授权"
        }
        shizukuStatusText.text = text
    }

    private fun updateOverlayStatus() {
        overlayStatusText.text = if (PermissionHelper.hasOverlayPermission(this)) {
            "悬浮窗权限：已授予 ✓"
        } else {
            "悬浮窗权限：未授予"
        }
    }

    private fun updateAccessibilityStatus() {
        accessibilityStatusText.text = if (PermissionHelper.isAccessibilityServiceEnabled(this)) {
            "无障碍服务：已开启 ✓"
        } else {
            "无障碍服务：未开启"
        }
    }

    // ---------------- Android 13+ 通知权限 ----------------

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    notificationPermissionRequestCode
                )
            }
        }
    }
}
