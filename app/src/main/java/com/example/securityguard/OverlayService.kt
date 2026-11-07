package com.example.securityguard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat

/**
 * 后台悬浮窗防护服务。
 *
 * 以前台服务（Foreground Service）形式运行，保证：
 * 1. App 切到后台甚至被系统回收内存时，摇一摇检测依然在跑
 * 2. 屏幕上有一个可拖动的小悬浮球，提示防护正在运行
 * 3. 摇一摇触发时执行风险扫描，如果发现风险应用，弹出悬浮窗提示，
 *    可以直接点击"卸载"或"冻结"处理，无需切回 App
 *
 * 根据 Android 14 (API 34) 的要求，前台服务必须声明一个 foregroundServiceType；
 * 这里的用途（持续后台安全监控）不属于官方预置的标准类型
 * （如 location、camera、mediaPlayback 等），因此在 Manifest 中使用
 * `specialUse` 类型，并附带说明用途的 property。
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var alertView: View? = null
    private lateinit var shakeDetector: ShakeDetector

    companion object {
        const val CHANNEL_ID = "security_guard_overlay_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.example.securityguard.action.STOP_OVERLAY"

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForegroundCompat()
        showBubble()

        // 摇一摇检测：即使 App 在后台，只要这个 Service 存活，传感器监听就一直有效
        shakeDetector = ShakeDetector(this) {
            onShakeDetectedInBackground()
        }
        shakeDetector.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        shakeDetector.stop()
        removeBubble()
        removeAlert()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---------------- 前台服务通知 ----------------

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "安全防护运行通知",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示安全管家后台防护正在运行"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundCompat() {
        val stopIntent = Intent(this, OverlayService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("安全管家防护中")
            .setContentText("摇一摇手机可触发风险扫描")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "关闭防护", stopPendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    // ---------------- 悬浮球 ----------------

    private fun showBubble() {
        if (!PermissionHelper.hasOverlayPermission(this)) return
        if (bubbleView != null) return

        val bubble = TextView(this).apply {
            text = "盾"
            textSize = 18f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2E7D32"))
            setPadding(24, 24, 24, 24)
        }

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }

        // 简单的拖动逻辑
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        bubble.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(view, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // 单击悬浮球：手动触发一次扫描
                    val moved = kotlin.math.abs(event.rawX - initialTouchX) > 10 ||
                        kotlin.math.abs(event.rawY - initialTouchY) > 10
                    if (!moved) {
                        onShakeDetectedInBackground()
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(bubble, params)
        bubbleView = bubble
    }

    private fun removeBubble() {
        bubbleView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // 视图可能已被移除，忽略
            }
        }
        bubbleView = null
    }

    // ---------------- 风险提示弹窗（悬浮窗形式） ----------------

    private fun onShakeDetectedInBackground() {
        val riskyApps = RiskScanner.scanForRiskyApps(this)
        if (riskyApps.isEmpty()) {
            return
        }
        showAlertOverlay(riskyApps)
    }

    private fun showAlertOverlay(riskyApps: List<RiskScanner.ScanResult>) {
        if (!PermissionHelper.hasOverlayPermission(this)) return
        removeAlert() // 避免重复弹出

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(32, 32, 32, 32)
        }

        val title = TextView(this).apply {
            text = "检测到 ${riskyApps.size} 个风险应用"
            textSize = 16f
            setTextColor(Color.parseColor("#D32F2F"))
        }
        container.addView(title)

        // 为避免悬浮窗过大，只展示第一个风险应用，附带"下一个"的隐式处理：
        // 处理完一个后自动弹出下一个（简单队列）
        val app = riskyApps.first()

        val desc = TextView(this).apply {
            text = "${app.appName}\n申请了: ${app.riskyPermissionNames.joinToString("、")}"
            textSize = 13f
            setTextColor(Color.DKGRAY)
            setPadding(0, 16, 0, 16)
        }
        container.addView(desc)

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val btnUninstall = Button(this).apply {
            text = "卸载"
            setOnClickListener {
                uninstallApp(app.packageName)
                removeAlert()
                showAlertOverlay(riskyApps.drop(1))
            }
        }
        val btnFreeze = Button(this).apply {
            text = "冻结"
            setOnClickListener {
                freezeApp(app.packageName)
                removeAlert()
                showAlertOverlay(riskyApps.drop(1))
            }
        }
        val btnIgnore = Button(this).apply {
            text = "忽略"
            setOnClickListener {
                removeAlert()
                showAlertOverlay(riskyApps.drop(1))
            }
        }

        buttonRow.addView(btnUninstall)
        buttonRow.addView(btnFreeze)
        buttonRow.addView(btnIgnore)
        container.addView(buttonRow)

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            dimAmount = 0.4f
        }

        windowManager.addView(container, params)
        alertView = container
    }

    private fun removeAlert() {
        alertView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // 忽略
            }
        }
        alertView = null
    }

    // ---------------- 卸载 / 冻结 / 清理后台 ----------------

    /**
     * 卸载应用。
     * 普通应用：通过 Shizuku shell 命令静默卸载（不会跳出系统确认框，
     * 因为是以 shell 身份执行，而非走 PackageInstaller UI 流程）。
     * 如果 Shizuku 未授权，退化为标准卸载流程（会有系统确认框，
     * 且从后台 Service 发起需要 FLAG_ACTIVITY_NEW_TASK）。
     */
    private fun uninstallApp(packageName: String) {
        if (ShizukuManager.hasPermission()) {
            ShizukuManager.executeShellCommand(arrayOf("pm", "uninstall", "--user", "0", packageName))
        } else {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }
    }

    /**
     * "冻结"应用：禁用该应用（用户仍可在系统设置里重新启用）。
     * 必须通过 Shizuku 获得的 shell 权限执行，普通 App 没有这个能力。
     */
    private fun freezeApp(packageName: String) {
        if (!ShizukuManager.hasPermission()) return
        ShizukuManager.executeShellCommand(
            arrayOf("pm", "disable-user", "--user", "0", packageName)
        )
    }

}
