package com.example.securityguard

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * 最小化无障碍服务实现。
 *
 * 用途：仅用于记录"当前前台是哪个 App"（通过监听窗口状态变化事件），
 * 以便悬浮窗/后台服务在需要时判断用户正在使用哪个应用。
 *
 * 明确不做的事情（重要的安全边界）：
 * - 不读取其他 App 界面上的文字/控件内容
 * - 不模拟点击、不自动操作其他 App
 * - 不做任何形式的按键记录或屏幕内容采集
 *
 * 这样的实现符合"悬浮助手类"无障碍服务的常规、合规用法，
 * 请不要在此基础上扩展成读取其他应用内容或自动点击的功能——
 * 那类行为属于间谍软件/键盘记录器的范畴，我不会协助实现。
 */
class MonitorAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var currentForegroundPackage: String? = null
            private set
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            currentForegroundPackage = event.packageName?.toString()
        }
    }

    override fun onInterrupt() {
        // 服务被中断时无需特殊处理
    }
}
