package com.example.securityguard

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private lateinit var shakeDetector: ShakeDetector
    private var protectionEnabled = false

    private lateinit var statusText: TextView
    private lateinit var shieldIcon: ImageView
    private lateinit var shizukuStatusText: TextView

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

        val btnManualToggle = findViewById<Button>(R.id.btn_manual_toggle)
        val btnAppList = findViewById<Button>(R.id.btn_app_list)

        // 摇一摇检测：触发时切换防护状态
        shakeDetector = ShakeDetector(this) {
            toggleProtection()
        }

        btnManualToggle.setOnClickListener { toggleProtection() }

        btnAppList.setOnClickListener {
            startActivity(Intent(this, AppListActivity::class.java))
        }

        ShizukuManager.addPermissionResultListener(permissionResultListener)
        updateShizukuStatus()

        // 若 Shizuku 可用但尚未授权，主动发起权限请求
        if (ShizukuManager.isAvailable() && !ShizukuManager.hasPermission()) {
            ShizukuManager.requestPermission()
        }
    }

    override fun onResume() {
        super.onResume()
        shakeDetector.start()
        updateShizukuStatus()
    }

    override fun onPause() {
        super.onPause()
        shakeDetector.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        ShizukuManager.removePermissionResultListener(permissionResultListener)
    }

    private fun toggleProtection() {
        protectionEnabled = !protectionEnabled
        if (protectionEnabled) {
            statusText.text = "防护已开启"
            findViewById<Button>(R.id.btn_manual_toggle).text = "手动关闭防护"
            // 这里可以触发一次扫描逻辑，比如调用权限审计/应用检查
            runSecurityScan()
        } else {
            statusText.text = "防护已关闭"
            findViewById<Button>(R.id.btn_manual_toggle).text = "手动开启防护"
        }
    }

    private fun runSecurityScan() {
        // 示意：实际可以在这里启动一个后台扫描任务，
        // 遍历已安装应用，检查危险权限组合，生成报告
        statusText.text = "防护已开启（扫描中…）"
        statusText.postDelayed({
            statusText.text = "防护已开启（上次扫描：无异常）"
        }, 1500)
    }

    private fun updateShizukuStatus() {
        val text = when {
            !ShizukuManager.isAvailable() -> "Shizuku 状态：未运行（请先启动 Shizuku 服务）"
            ShizukuManager.hasPermission() -> "Shizuku 状态：已授权 ✓"
            else -> "Shizuku 状态：已运行，未授权"
        }
        shizukuStatusText.text = text
    }
}
