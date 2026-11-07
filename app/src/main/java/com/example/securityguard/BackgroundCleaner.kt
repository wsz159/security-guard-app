package com.example.securityguard

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

/**
 * "一键清理后台"：结束所有第三方应用的后台进程。
 * 使用标准权限 KILL_BACKGROUND_PROCESSES（普通权限，安装时自动授予），
 * 不需要 Shizuku。
 *
 * 注意：这只是结束进程，不是卸载，应用依然安装在手机上，
 * 用户下次点开图标会重新启动。对于有前台服务/推送常驻的应用，
 * 系统可能很快又把它拉起来，这是系统机制决定的，不是 bug。
 */
object BackgroundCleaner {

    fun clearAll(context: Context): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val pm = context.packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        var count = 0
        for (appInfo in packages) {
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (isSystem) continue
            if (appInfo.packageName == context.packageName) continue
            try {
                am.killBackgroundProcesses(appInfo.packageName)
                count++
            } catch (e: Exception) {
                // 部分受保护的包可能无法结束，忽略即可
            }
        }
        return count
    }
}
