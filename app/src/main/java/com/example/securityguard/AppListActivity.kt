package com.example.securityguard

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * 风险权限提示规则示意：这里列出几个常见的"高敏感"权限，
 * 实际项目可以维护一份更完整的规则库（比如结合权限的用途与 App 类型联合判断）。
 */
private val RISKY_PERMISSIONS = mapOf(
    "android.permission.READ_CONTACTS" to "读取通讯录",
    "android.permission.READ_SMS" to "读取短信",
    "android.permission.ACCESS_FINE_LOCATION" to "精确定位",
    "android.permission.RECORD_AUDIO" to "录音",
    "android.permission.CAMERA" to "摄像头",
    "android.permission.READ_CALL_LOG" to "读取通话记录"
)

class AppListActivity : AppCompatActivity() {

    private lateinit var adapter: AppListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_list)

        val recyclerView = findViewById<RecyclerView>(R.id.recycler_apps)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val apps = loadInstalledApps()
        adapter = AppListAdapter(apps.toMutableList()) { app ->
            confirmUninstall(app)
        }
        recyclerView.adapter = adapter
    }

    /** 枚举已安装应用，并标注风险权限 */
    private fun loadInstalledApps(): List<AppInfo> {
        val pm = packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        return packages.map { appInfo ->
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

            val granted = try {
                val packageInfo = pm.getPackageInfo(
                    appInfo.packageName,
                    PackageManager.GET_PERMISSIONS
                )
                val requested = packageInfo.requestedPermissions ?: emptyArray()
                requested.filter { RISKY_PERMISSIONS.containsKey(it) }
                    .mapNotNull { RISKY_PERMISSIONS[it] }
            } catch (e: Exception) {
                emptyList()
            }

            AppInfo(
                packageName = appInfo.packageName,
                appName = pm.getApplicationLabel(appInfo).toString(),
                icon = pm.getApplicationIcon(appInfo),
                isSystemApp = isSystem,
                riskyPermissions = granted
            )
        }.sortedBy { it.appName.lowercase() }
    }

    private fun confirmUninstall(app: AppInfo) {
        val message = if (app.isSystemApp) {
            "「${app.appName}」是系统应用。\n\n卸载系统应用存在较高风险，可能导致系统功能异常甚至无法开机，请谨慎操作。\n\n确定要继续吗？"
        } else {
            "确定要卸载「${app.appName}」吗？"
        }

        AlertDialog.Builder(this)
            .setTitle("卸载确认")
            .setMessage(message)
            .setPositiveButton("卸载") { _, _ -> performUninstall(app) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun performUninstall(app: AppInfo) {
        if (!app.isSystemApp) {
            // 普通应用：走标准系统卸载流程（会弹出系统确认框，用户需二次确认）
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:${app.packageName}")
            }
            startActivity(intent)
        } else {
            // 系统应用：标准 API 无法直接卸载，需要借助 Shizuku 获得的更高权限
            if (!ShizukuManager.hasPermission()) {
                Toast.makeText(
                    this,
                    "卸载系统应用需要 Shizuku 授权，请先在主界面完成授权",
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            // 实际卸载系统应用需要调用 PackageManager 隐藏 API（通过 Shizuku 的
            // "影子" PackageManager 服务或 `pm uninstall --user 0 <package>` shell 命令实现）。
            // 这里给出 shell 命令方式的调用示意：
            val result = ShizukuManager.executeShellCommand(
                arrayOf("pm", "uninstall", "--user", "0", app.packageName)
            )
            Toast.makeText(this, result, Toast.LENGTH_LONG).show()

            // 命令执行成功后，从列表中移除（实际项目中应解析命令输出确认是否成功）
            adapter.removeApp(app.packageName)
        }
    }
}
