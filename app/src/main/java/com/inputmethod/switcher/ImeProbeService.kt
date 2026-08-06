package com.inputmethod.switcher

import android.accessibilityservice.AccessibilityService
import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File

// 无障碍探针 + 自动导航:
// 1) 打开设置主页, 自动点击 系统与更新 -> 输入法 -> 当前输入法, 触发系统"更改键盘"浮窗
// 2) dump 每一步的界面树, 日志覆盖写入手机"下载"目录 (ime_probe_log.txt)
// ColorOS 拒绝第三方 app 调 showInputMethodPicker(), 但无障碍模拟点击等同用户操作, 系统不拦截
class ImeProbeService : AccessibilityService() {

    private var dumpCount = 0

    // 0=设置主页找"系统与更新"  1=系统与更新页找"输入法"
    // 2=输入法页找"当前输入法"  3=完成(不再自动点击)  10=无悬浮窗权限, 等用户手动打开设置
    private var navState = 0
    private var lastWindowPkg = ""
    private var lastWindowCls = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        LogStore.append("Probe", "=== 无障碍服务已连接 ===")
        saveLog("服务连接")
        navigateNow()
    }

    // 点击应用图标 → LauncherActivity 发来指令 → 立即重新导航
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_NAVIGATE) {
            LogStore.append("Probe", "=== 收到导航指令(点击图标触发) ===")
            navigateNow()
        }
        return START_NOT_STICKY
    }

    private fun navigateNow() {
        if (Settings.canDrawOverlays(this)) {
            navState = 0
            try {
                val intent = Intent(Settings.ACTION_SETTINGS)
                    .setPackage("com.android.settings")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                LogStore.append("Probe", "已请求打开设置主页 (悬浮窗权限正常)")
            } catch (e: Exception) {
                LogStore.append("Probe", "打开设置主页失败: $e")
                navState = 10
            }
        } else {
            // 无悬浮窗权限: 先试直接打开(部分 ROM 无障碍服务可豁免后台启动限制), 失败再引导授权
            navState = 10
            try {
                val intent = Intent(Settings.ACTION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                LogStore.append("Probe", "无悬浮窗权限, 尝试直接打开设置主页")
            } catch (e: Exception) {
                LogStore.append("Probe", "无悬浮窗权限, 后台启动设置被拦截: $e")
                try {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:$packageName")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    LogStore.append("Probe", "已打开悬浮窗授权页, 授权后请重新点击应用图标")
                } catch (e2: Exception) {
                    LogStore.append("Probe", "打开授权页失败: $e2")
                }
                LogStore.append("Probe", "降级模式: 请手动打开 设置→系统与更新→输入法, 探针会帮你点击 当前输入法")
            }
        }
        saveLog("导航触发")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val pkg = event.packageName?.toString() ?: "?"
                val cls = event.className?.toString() ?: "?"
                if (pkg != lastWindowPkg || cls != lastWindowCls) {
                    lastWindowPkg = pkg
                    lastWindowCls = cls
                    LogStore.append("Probe", "窗口切换: pkg=$pkg cls=$cls")
                    dumpTree("窗口($pkg/$cls)")
                    if (pkg.contains("settings", true) && (navState < 3 || navState == 10)) {
                        autoNavigate()
                    }
                    saveLog("窗口切换")
                }
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val pkg = event.packageName?.toString() ?: ""
                val low = pkg.lowercase()
                if (low.contains("settings") || low.contains("oplus") || low.contains("coloros")) {
                    val node = event.source
                    if (node != null && node.childCount > 0) {
                        dumpTree("内容变化($pkg)")
                        node.recycle()
                    }
                }
            }
        }
    }

    private fun autoNavigate() {
        when (navState) {
            0 -> {
                // 设置主页: 优先"系统与更新", 其次直接的"输入法"入口
                val clicked = findAndClick(listOf("系统与更新"), listOf())
                if (clicked) {
                    navState = 1
                    LogStore.append("Probe", "导航: 已点击 系统与更新, 等待页面")
                } else if (findAndClick(listOf(), listOf("输入法", "语言和输入法"))) {
                    navState = 2
                    LogStore.append("Probe", "导航: 已点击 输入法 入口, 等待页面")
                } else {
                    LogStore.append("Probe", "导航: 设置主页未找到 系统与更新/输入法, 请手动导航(仍会记录)")
                    navState = 3
                }
            }
            1 -> {
                // 系统与更新页: 找"输入法"行
                if (findAndClick(listOf(), listOf("输入法", "语言和输入法"))) {
                    navState = 2
                    LogStore.append("Probe", "导航: 已点击 输入法, 等待页面")
                } else {
                    LogStore.append("Probe", "导航: 系统与更新页未找到 输入法 行")
                    navState = 3
                }
            }
            2, 10 -> {
                // 输入法页: 找"当前输入法"行 (手动模式 10 下, 任何 settings 页面都尝试,
                // 只有真正含"当前输入法"行的页面才会命中, 不会误点)
                if (findAndClick(listOf("当前输入法", "当前键盘", "默认键盘", "默认输入法"), listOf())) {
                    navState = 3
                    LogStore.append("Probe", "导航: 已点击 当前输入法, 浮窗应弹出!")
                } else if (navState == 2) {
                    LogStore.append("Probe", "导航: 输入法页未找到 当前输入法 行")
                    navState = 3
                } else {
                    LogStore.append("Probe", "导航: 当前页面没有 当前输入法 行, 继续等待(手动模式)")
                }
            }
            else -> {
            }
        }
    }

    private fun findAndClick(exact: List<String>, contains: List<String>): Boolean {
        val root = rootInActiveWindow ?: return false
        return findClickableNode(root, exact, contains)
    }

    private fun findClickableNode(
        node: AccessibilityNodeInfo,
        exact: List<String>,
        contains: List<String>
    ): Boolean {
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        val avoid = text.contains("管理") || desc.contains("管理")
        var matched = false
        for (p in exact) {
            if (text == p || desc == p) {
                matched = true
                break
            }
        }
        if (!matched) {
            for (p in contains) {
                if ((text.contains(p) || desc.contains(p)) && !avoid) {
                    matched = true
                    break
                }
            }
        }
        if (matched) {
            var n: AccessibilityNodeInfo? = node
            while (n != null) {
                if (n.isClickable) {
                    LogStore.append("Probe", "导航: 命中 '$text' (${n.className}), 执行点击")
                    val ok = n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    LogStore.append("Probe", "导航: 点击结果=$ok")
                    return ok
                }
                n = n.parent
            }
        }
        val count = node.childCount
        for (i in 0 until count) {
            val child = node.getChild(i) ?: continue
            if (findClickableNode(child, exact, contains)) return true
        }
        return false
    }

    private fun dumpTree(reason: String) {
        dumpCount++
        val root = rootInActiveWindow ?: return
        LogStore.append("Probe", "---- dump #$dumpCount 原因: $reason ----")
        dumpNode(root, 0)
        LogStore.append("Probe", "---- dump 结束 ----")
    }

    private fun dumpNode(node: AccessibilityNodeInfo, depth: Int) {
        if (depth > 14) return
        val sb = StringBuilder()
        for (i in 0 until depth) sb.append("  ")
        sb.append(node.className?.toString() ?: "?")
        val viewId = node.viewIdResourceName
        if (viewId != null) sb.append(" | id=").append(viewId)
        val text = node.text?.toString()
        if (!text.isNullOrEmpty()) sb.append(" | text=").append(text.replace("\n", "\\n"))
        val desc = node.contentDescription?.toString()
        if (!desc.isNullOrEmpty()) sb.append(" | desc=").append(desc)
        if (node.isClickable) sb.append(" | CLICKABLE")
        if (node.isSelected) sb.append(" | SELECTED")
        LogStore.append("Probe", sb.toString())

        val count = node.childCount
        if (count > 0) {
            for (i in 0 until count) {
                val child = node.getChild(i) ?: continue
                dumpNode(child, depth + 1)
            }
        }
    }

    // 覆盖写入固定文件名, 避免同名文件堆积
    private fun saveLog(reason: String) {
        try {
            val filename = "ime_probe_log.txt"
            val data = LogStore.content().toByteArray()
            if (Build.VERSION.SDK_INT >= 29) {
                val resolver = contentResolver
                resolver.delete(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    "${MediaStore.MediaColumns.DISPLAY_NAME}=?",
                    arrayOf(filename))
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        out.write(data)
                    }
                }
            } else {
                val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return
                File(dir, filename).writeBytes(data)
            }
            LogStore.append("Probe", "日志已写入 下载/$filename (原因: $reason)")
        } catch (e: Exception) {
            LogStore.append("Probe", "日志写入异常: $e")
        }
    }

    override fun onInterrupt() {
        LogStore.append("Probe", "服务被中断")
    }

    companion object {
        const val ACTION_NAVIGATE = "com.inputmethod.switcher.NAVIGATE"
    }
}
