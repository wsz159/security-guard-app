package com.example.securityguard

import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku

/**
 * 封装与 Shizuku 的交互逻辑：检测是否安装/运行、申请权限、权限状态查询。
 *
 * Shizuku 是一个开源项目，允许普通 App 在不 Root 的情况下，
 * 通过类似 ADB 的机制获得部分系统级权限（例如 PackageManager 的敏感接口）。
 * 使用前提：用户手机已安装 Shizuku App，并通过它启动了 Shizuku 服务
 * （可以通过 ADB 命令或无线调试方式启动，具体看 Shizuku 官方文档）。
 */
object ShizukuManager {

    private const val REQUEST_CODE = 1001
    private const val TAG = "ShizukuManager"

    /** Shizuku 服务是否可用（已安装且已启动） */
    fun isAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            Log.w(TAG, "Shizuku ping failed: ${e.message}")
            false
        }
    }

    /** 本应用是否已获得 Shizuku 授权 */
    fun hasPermission(): Boolean {
        if (!isAvailable()) return false
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    /** 请求 Shizuku 授权，结果通过 Shizuku.OnRequestPermissionResultListener 回调 */
    fun requestPermission() {
        if (!isAvailable()) {
            Log.w(TAG, "Shizuku 服务未运行，无法请求权限")
            return
        }
        if (Shizuku.isPreV11()) {
            Log.w(TAG, "Shizuku 版本过低，不支持权限请求 API")
            return
        }
        Shizuku.requestPermission(REQUEST_CODE)
    }

    fun addPermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener) {
        Shizuku.addRequestPermissionResultListener(listener)
    }

    fun removePermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener) {
        Shizuku.removeRequestPermissionResultListener(listener)
    }

    /**
     * 通过 Shizuku 以 shell 身份执行命令的通用封装。
     * 真实实现需要用到 Shizuku 提供的 IRemoteProcess / newProcess API，
     * 这里给出函数签名和关键步骤注释，具体 AIDL 调用请参考
     * Shizuku-API 官方 demo（RemoteProcess 示例）。
     */
    fun executeShellCommand(command: Array<String>): String {
        if (!hasPermission()) {
            return "错误：未获得 Shizuku 授权"
        }
        return try {
            // 伪代码示意，真实调用需要用到 Shizuku 的 Service/Process 封装
            // val process = Shizuku.newProcess(command, null, null)
            // process.waitFor()
            // 读取 process.inputStream 得到命令输出
            "命令已通过 Shizuku 执行（此处为示意，具体 AIDL 调用请对照 Shizuku-API 官方示例补全）"
        } catch (e: Exception) {
            "执行失败：${e.message}"
        }
    }
}
