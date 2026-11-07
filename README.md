# 安全管家 (Security Guard) - 项目骨架

一个基于 Shizuku 的安卓安全管理工具原型，功能包括：

- 摇一摇手势开启/关闭"防护扫描"（App 在前台时）
- **后台悬浮窗防护**：开启后即使 App 切到后台，摇一摇依然能触发扫描；检测到风险应用会弹出悬浮窗，可直接点击"卸载"或"冻结"
- 已安装应用列表展示，支持卸载 / 冻结
- 借助 Shizuku 获取系统权限，支持静默卸载第三方应用、冻结（禁用）应用
- 一键清理所有第三方应用的后台进程
- 基础的权限风险提示（哪些 App 申请了敏感权限，如通讯录、定位等）
- 无障碍服务（仅用于识别当前前台应用，**不读取其他应用内容、不自动点击**）

## 重要说明

本项目**不包含真正的病毒特征码检测引擎**。真实的病毒查杀需要专业安全厂商持续维护的病毒库，个人项目无法从零构建。
这里实现的是更现实可落地的功能：**权限审计 + 应用管理 + 系统清理**，并包装为"安全防护"的交互体验。

请不要将本应用宣传为"能查杀病毒"，这样的宣传涉嫌虚假宣传。

无障碍服务的实现严格限定为"只识别前台应用包名"，配置文件里明确设置了 `canRetrieveWindowContent="false"`，
不会读取其他应用界面上的文字/控件内容，也不会模拟点击。请不要在此基础上扩展成内容读取或自动操作——
那属于间谍软件/键盘记录器的范畴。

## 技术栈

- Kotlin
- Android Jetpack (ViewModel, RecyclerView)
- Shizuku API (https://github.com/RikkaApps/Shizuku-API)
- SensorManager（加速度传感器，用于摇一摇检测）
- Foreground Service + WindowManager（悬浮窗/悬浮球）
- AccessibilityService（仅做前台应用识别）

## 前置条件

1. 用户手机已安装并运行 **Shizuku** App
2. 本 App 通过 Shizuku 提供的 AIDL 接口请求权限，无需 Root
3. 需要手动在系统设置里授予"悬浮窗权限"和"无障碍服务"权限（Android 系统限制，无法由 App 自动授予，这是系统的安全设计）

## 目录结构

```
app/
  src/main/java/com/example/securityguard/
    MainActivity.kt                  # 主界面，前台摇一摇、各项权限入口
    ShakeDetector.kt                 # 封装加速度传感器，识别摇一摇手势
    ShizukuManager.kt                # Shizuku 权限请求与绑定封装
    AppListActivity.kt               # 已安装应用列表 + 卸载管理
    AppInfo.kt                       # 应用信息数据类
    AppListAdapter.kt                # RecyclerView 适配器
    RiskScanner.kt                   # 风险权限扫描规则（非病毒库）
    PermissionHelper.kt              # 悬浮窗/无障碍权限检测与跳转
    OverlayService.kt                # 后台前台服务：悬浮球 + 后台摇一摇 + 风险弹窗
    MonitorAccessibilityService.kt   # 最小化无障碍服务，仅识别前台应用
    BackgroundCleaner.kt             # 一键清理后台进程
  src/main/res/layout/
    activity_main.xml
    activity_app_list.xml
    item_app.xml
  src/main/res/xml/
    accessibility_service_config.xml
  src/main/AndroidManifest.xml
build.gradle
app/build.gradle
gradle.properties
```

## 如何运行

1. 用 Android Studio 打开本项目文件夹（或直接用 GitHub Actions 自动编译，见 `.github/workflows/build.yml`）
2. 等待 Gradle 同步完成（需要联网下载依赖）
3. 真机或模拟器运行（需要先在设备上安装并启动 Shizuku）
4. 首次打开 App 会请求 Shizuku 授权，在 Shizuku App 里确认授权
5. 在主界面依次点击"授予悬浮窗权限"、"开启无障碍服务"，跳转到系统设置里手动打开
6. 点击"启动后台防护服务"，屏幕上会出现一个绿色小悬浮球，App 切到后台后摇一摇手机依然能触发扫描

## 已知限制 / 注意事项

- **后台存活问题**：部分手机厂商的定制系统（MIUI、ColorOS、EMUI 等）对后台进程管控非常激进，即使用了前台服务，也可能被系统杀掉。如果发现悬浮窗总是消失，请到系统设置里把本 App 加入"后台运行白名单 / 自启动管理 / 电池优化豁免名单"
- **静默卸载/冻结**依赖 Shizuku 授权，未授权时卸载会退化为走系统标准确认框（无法绕过，这是 Android 的安全设计）
- **冻结后恢复**：被"冻结"（禁用）的应用，用户可以随时去系统设置 → 应用管理里手动重新启用
- `ShizukuManager.executeShellCommand()` 中的 Shizuku shell 调用部分是示意实现，真实的 AIDL 远程进程调用细节请对照 [Shizuku-API 官方 demo](https://github.com/RikkaApps/Shizuku-API) 补全

## 后续可扩展方向

- 悬浮窗支持多个风险应用的列表式展示，而不是逐个弹出
- 加入"权限滥用检测规则库"（比如某 App 不该申请读取短信权限却申请了）
- 垃圾文件扫描与清理
- 定时自动扫描（结合 WorkManager）
- 开机自启动悬浮窗防护（BroadcastReceiver 监听 BOOT_COMPLETED）
