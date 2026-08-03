# QuickGemini

一个轻量级 Android 应用，点击图标直接唤起 Google 语音助手（Gemini 或 Google App）浮层。

## 功能特点

- 🚀 一键唤起语音助手浮层
- ⏱️ 自动等待 300ms 让侧边栏收起
- 🪶 零依赖，APK 仅约 20-40KB
- 🔒 不需要 Root 或 Shizuku
- 🌙 自动支持亮色/暗色图标
- 🎨 Android 13+ 支持 Material You 主题图标

## 使用前准备

1. 安装 **Google App** 或 **Gemini App**
2. 将其设为默认数字/语音助手

### 设置默认语音助手

#### OPPO/ColorOS 用户

尝试以下路径之一：
- **设置 → 其他设置 → 设备与隐私 → 辅助功能 → 语音助手**
- **设置 → Breeno → Breeno 语音**
- **设置 → 应用管理 → 默认应用管理 → 语音助手**

如果找不到也没关系：
- 安装 QuickGemini 后，第一次点击图标时会弹出"打开方式"选择框
- 选择 **Google** 或 **Gemini** 即可

#### 其他品牌

- **设置 → 应用 → 默认应用 → 数字助手**
- **设置 → 应用管理 → 默认应用 → 语音助手**

## 下载安装

从 GitHub Actions 构建下载：
1. 打开仓库 Actions 标签
2. 选择最新的成功构建
3. 下载 Artifacts 中的 `quickgemini-debug-apk.zip`
4. 解压得到 `QuickGemini-1.0.apk`
5. 安装到手机

## 构建方式

```bash
# Debug 构建
./gradlew assembleDebug

# Release 构建
./gradlew assembleRelease
```

APK 输出位置：`app/build/outputs/apk/`

## 版本历史

### v1.0 (2026-08-03)
- 初始版本
- 支持 ACTION_VOICE_COMMAND 唤起语音助手
- 300ms 延时等待侧边栏收起
- Adaptive Icon 支持

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

## 隐私安全

- 不请求任何权限（无网络、无存储、无麦克风...）
- 不收集任何个人信息
- 只是一个"快捷方式"，点击后立即结束自己
- 所有逻辑只有 40 多行代码，可直接查看

## GitHub Packages

本项目暂不发布到 GitHub Packages。如需发布，可以：
1. 配置签名密钥
2. 修改构建脚本生成 Release APK
3. 设置 GitHub Packages Maven 仓库发布流程

对于个人使用，直接从 Actions 下载 Artifacts 已足够方便。
