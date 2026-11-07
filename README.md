# 安全管家 (Security Guard) - 项目骨架

一个基于 Shizuku 的安卓安全管理工具原型，功能包括：

- 摇一摇手势开启/关闭"防护扫描"
- 已安装应用列表展示
- 借助 Shizuku 获取系统权限，支持卸载第三方应用（部分系统应用在设备允许的情况下也可卸载）
- 基础的权限风险提示（哪些 App 申请了敏感权限，如通讯录、定位等）

## 重要说明

本项目**不包含真正的病毒特征码检测引擎**。真实的病毒查杀需要专业安全厂商持续维护的病毒库，个人项目无法从零构建。
这里实现的是更现实可落地的功能：**权限审计 + 应用管理 + 系统清理**，并包装为"安全防护"的交互体验。

请不要将本应用宣传为"能查杀病毒"，这样的宣传涉嫌虚假宣传。

## 技术栈

- Kotlin
- Android Jetpack (ViewModel, RecyclerView)
- Shizuku API (https://github.com/RikkaApps/Shizuku-API)
- SensorManager（加速度传感器，用于摇一摇检测）

## 前置条件

1. 用户手机已安装并运行 **Shizuku** App（你截图里已经有了）
2. 本 App 通过 Shizuku 提供的 AIDL 接口请求权限，无需 Root

## 目录结构

```
app/
  src/main/java/com/example/securityguard/
    MainActivity.kt          # 主界面，摇一摇检测、防护开关
    ShakeDetector.kt         # 封装加速度传感器，识别摇一摇手势
    ShizukuManager.kt        # Shizuku 权限请求与绑定封装
    AppListActivity.kt       # 已安装应用列表 + 卸载管理
    AppInfo.kt                # 应用信息数据类
    AppListAdapter.kt        # RecyclerView 适配器
  src/main/res/layout/
    activity_main.xml
    activity_app_list.xml
    item_app.xml
  src/main/AndroidManifest.xml
build.gradle
app/build.gradle
```

## 如何运行

1. 用 Android Studio 打开本项目文件夹
2. 等待 Gradle 同步完成（需要联网下载依赖）
3. 真机或模拟器运行（需要先在设备上安装并启动 Shizuku）
4. 首次打开 App 会请求 Shizuku 授权，在 Shizuku App 里确认授权

## 后续可扩展方向

- 加入"权限滥用检测规则库"（比如某 App 不该申请读取短信权限却申请了）
- 垃圾文件扫描与清理
- 悬浮窗实时监控（需要额外权限）
- 定时自动扫描
