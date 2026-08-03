# QuickGemini

一个轻量级 Android 应用，点击图标直接唤起 Gemini 浮层。

## 功能特点

- 🚀 一键唤起 Gemini 浮层
- ⏱️ 自动等待 300ms 让侧边栏收起
- 🪶 零依赖，APK 仅约 20-40KB
- 🔒 不需要 Root 或 Shizuku
- 🌙 自动支持亮色/暗色图标
- 🎨 Android 13+ 支持 Material You 主题图标

## 使用前准备

1. 安装 Gemini App
2. 将 Gemini 设为默认数字助手：设置 → 应用 → 默认应用 → 数字助手

## 构建方式

```bash
# Debug 构建
./gradlew assembleDebug

# Release 构建
./gradlew assembleRelease
```

APK 输出位置：`app/build/outputs/apk/`

## 项目结构

```
QuickGemini/
├── app/
│   └── src/main/
│       ├── java/com/quickgemini/app/LauncherActivity.kt  # 核心逻辑
│       ├── AndroidManifest.xml                             # 清单文件
│       └── res/                                           # 资源文件
└── gradle 配置文件
```

## 技术细节

- **minSdk**: 26 (Android 8.0)
- **targetSdk**: 35
- **零外部依赖**：仅使用 Android 框架 API
- **唤起方式**：`Intent.ACTION_VOICE_COMMAND`（官方稳定 API）

## 调整延时

如需调整等待侧边栏收起的时长，修改 `LauncherActivity.kt` 中的 `LAUNCH_DELAY_MS` 常量。
