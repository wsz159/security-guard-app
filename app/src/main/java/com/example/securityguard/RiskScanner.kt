package com.example.securityguard

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

/**
 * 风险权限规则库（示意）：根据应用申请的敏感权限数量/组合，
 * 粗略判断一个 App 是否"看起来可疑"。
 *
 * 再次强调：这不是真正的病毒特征码检测，只是权限审计。
 * 一个申请了很多敏感权限的正规 App（比如相机/地图类）完全可能是正常软件，
 * 这里的"风险"只是提示用户关注，不代表真的有恶意行为，请勿在产品文案里
 * 宣传为"病毒查杀"。
 */
object RiskScanner {

    private val RISKY_PERMISSIONS = mapOf(
        "android.permission.READ_CONTACTS" to "读取通讯录",
        "android.permission.READ_SMS" to "读取短信",
        "android.permission.SEND_SMS" to "发送短信",
        "android.permission.ACCESS_FINE_LOCATION" to "精确定位",
        "android.permission.RECORD_AUDIO" to "录音",
        "android.permission.CAMERA" to "摄像头",
        "android.permission.READ_CALL_LOG" to "读取通话记录",
        "android.permission.SYSTEM_ALERT_WINDOW" to "悬浮窗",
        "android.permission.BIND_ACCESSIBILITY_SERVICE" to "无障碍服务"
    )

    /** 风险判定阈值：命中几个敏感权限就算"高风险" */
    private const val RISK_THRESHOLD = 3

    data class ScanResult(
        val packageName: String,
        val appName: String,
        val riskyPermissionNames: List<String>,
        val isSystemApp: Boolean
    )

    /**
     * 扫描所有已安装的第三方应用，返回命中风险阈值的应用列表。
     * 只读操作，不需要任何特殊权限，普通 PackageManager 即可完成。
     */
    fun scanForRiskyApps(context: Context): List<ScanResult> {
        val pm = context.packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val results = mutableListOf<ScanResult>()

        for (appInfo in packages) {
            // 跳过系统应用，避免对系统组件产生大量误报
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (isSystem) continue

            // 跳过自己
            if (appInfo.packageName == context.packageName) continue

            val riskyNames = try {
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

            if (riskyNames.size >= RISK_THRESHOLD) {
                results.add(
                    ScanResult(
                        packageName = appInfo.packageName,
                        appName = pm.getApplicationLabel(appInfo).toString(),
                        riskyPermissionNames = riskyNames,
                        isSystemApp = false
                    )
                )
            }
        }
        return results
    }
}
