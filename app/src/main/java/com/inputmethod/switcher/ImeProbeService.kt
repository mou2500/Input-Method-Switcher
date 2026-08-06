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

// 无障碍探针: 打开输入法设置页, dump 页面节点树(每行的 view id / 文本 / 可点击性),
// 自动把日志写入手机"下载"目录 (ime_probe_log.txt), 无需 adb。
class ImeProbeService : AccessibilityService() {

    private var dumpCount = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        LogStore.append("Probe", "=== 无障碍服务已连接, 包名: ${packageName} ===")
        saveLog("服务连接")
        try {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            LogStore.append("Probe", "已请求打开输入法设置页")
        } catch (e: Exception) {
            LogStore.append("Probe", "打开设置页失败: $e")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val pkg = event.packageName?.toString() ?: "?"
                val cls = event.className?.toString() ?: "?"
                LogStore.append("Probe", "窗口切换: pkg=$pkg cls=$cls")
                dumpTree("窗口($pkg/$cls)")
                saveLog("窗口切换")
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val pkg = event.packageName?.toString() ?: ""
                val low = pkg.lowercase()
                // 只分析输入法/设置相关页面, 避免刷屏
                if (low.contains("settings") || low.contains("inputmethod") ||
                    low.contains("oplus") || low.contains("coloros")
                ) {
                    val cls = event.className?.toString() ?: "?"
                    val node = event.source
                    if (node != null && node.childCount > 0) {
                        LogStore.append("Probe", "内容变化: pkg=$pkg cls=$cls")
                        dumpTree("内容变化($pkg)")
                        node.recycle()
                    }
                }
            }
        }
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

    private fun saveLog(reason: String) {
        try {
            val filename = "ime_probe_log.txt"
            val data = LogStore.content().toByteArray()
            val uri: android.net.Uri?
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(data)
                    }
                }
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "ImeProbe")
                if (dir.exists() || dir.mkdirs()) {
                    File(dir, filename).writeBytes(data)
                }
                uri = null
            }
            LogStore.append("Probe", "日志已写入 下载/$filename (原因: $reason)")
        } catch (e: Exception) {
            LogStore.append("Probe", "日志写入异常: $e")
        }
    }

    override fun onInterrupt() {
        LogStore.append("Probe", "服务被中断")
    }
}
