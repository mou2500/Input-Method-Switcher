# 输入法切换器 (Input Method Switcher)

一个轻量级 Android 应用，点击图标直接弹出系统输入法选择器，选完自动关闭，无后台残留。

## 功能特点

- 🚀 点开即弹出系统输入法切换浮窗
- ⏱️ 选完输入法后 App 自动结束，不占用后台
- 🪶 零依赖，APK 仅约 20-40KB
- 🔒 不需要 Root、Shizuku 或任何权限
- 🌙 自动支持亮色/暗色图标
- 🎨 Android 13+ 支持 Material You 主题图标

## 适用场景

微信输入法、搜狗输入法等没有内置"切换输入法"入口时，点一下桌面图标即可弹出系统输入法选择器，无需退出当前界面去系统设置里切换。

## 下载安装

从 GitHub Actions 构建下载：
1. 打开仓库 Actions 标签
2. 选择最新的成功构建
3. 下载 Artifacts 中的 `app-debug.zip`
4. 解压得到 `InputMethodSwitcher-1.0.apk`
5. 安装到手机，点击图标即可使用

## 构建方式

```bash
# Debug 构建
gradle assembleDebug
```

APK 输出位置：`app/build/outputs/apk/`

## 项目结构

```
InputMethodSwitcher/
├── app/
│   └── src/main/
│       ├── java/com/inputmethod/switcher/LauncherActivity.kt  # 核心逻辑
│       ├── AndroidManifest.xml                                # 清单文件
│       └── res/                                              # 资源文件
└── gradle 配置文件
```

## 技术细节

- **minSdk**: 26 (Android 8.0)
- **targetSdk**: 35
- **零外部依赖**：仅使用 Android 框架 API
- **唤起方式**：`InputMethodManager.showInputMethodPicker()`（@hide API，通过反射调用）
- **无界面**：`Theme.Translucent.NoTitleBar.Fullscreen` 透明主题，Activity 打开即 `finish()`

## 隐私安全

- 不请求任何权限（无网络、无存储...）
- 不收集任何个人信息
- 只是一个"快捷方式"，点击后立即结束自己
- 所有逻辑只有 40 多行代码，可直接查看
