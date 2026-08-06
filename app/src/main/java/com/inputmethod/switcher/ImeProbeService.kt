package com.inputmethod.switcher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 无障碍探针：模拟手指操作，导航 设置→系统与更新→输入法→当前输入法，
 * 触发系统"更改键盘"浮窗。每一步窗口内容全部 dump 进日志。
 *
 * 设计原则：不主动 startActivity（后台启动会被 ColorOS 拦截），
 * 设置主页由 LauncherActivity 前台打开；本服务只监听窗口、滚动、点击目标行。
 */
class ImeProbeService : AccessibilityService() {

    private enum class Stage { IDLE, WAIT_HOME, WAIT_SUB, WAIT_IME_PAGE, WAIT_PICKER, DONE }

    private var stage = Stage.IDLE
    private var scrollCount = 0
    private var lastWindowKey = ""
    private val handler = Handler(Looper.getMainLooper())

    /** 滚动后定时重试，不依赖系统事件（ColorOS 自动滚动不触发 content changed） */
    private val retryScroll = object : Runnable {
        override fun run() {
            rootInActiveWindow?.let { advance(it) }
        }
    }

    /** 浮窗渲染完成后枚举全部窗口 */
    private val dumpWindowsLater = Runnable { dumpAllWindows() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        LogStore.log("[SERVICE] 无障碍服务已连接")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_NAVIGATE) {
            LogStore.log("[SERVICE] 收到导航指令")
            stage = Stage.WAIT_HOME
            scrollCount = 0
            lastWindowKey = ""
            handler.removeCallbacks(retryScroll)
            handler.post(retryScroll)
        }
        return START_NOT_STICKY
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (stage == Stage.IDLE || stage == Stage.DONE) return
        val root = rootInActiveWindow ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val pkg = event.packageName?.toString() ?: "?"
                val cls = event.className?.toString() ?: "?"
                val key = "$pkg/$cls"
                if (key != lastWindowKey) {
                    lastWindowKey = key
                    LogStore.log("[WINDOW] 新窗口: $key")
                    dumpTree(root)
                    // 切到新页面后重新开始滚动计数
                    if (pkg.contains("settings", ignoreCase = true)) {
                        scrollCount = 0
                    }
                }
                advance(root)
            }
            // 内容变化事件：目标行可能已滚入/加载出来
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (event.packageName?.toString()?.contains("settings", ignoreCase = true) == true) {
                    advance(root)
                }
            }
        }
    }

    private fun advance(root: AccessibilityNodeInfo) {
        when (stage) {
            Stage.WAIT_HOME -> tryClick(root, "系统与更新", Stage.WAIT_SUB, maxScrolls = 12)
            Stage.WAIT_SUB -> tryClick(root, "输入法", Stage.WAIT_IME_PAGE, maxScrolls = 8)
            Stage.WAIT_IME_PAGE -> tryClick(root, "当前输入法", Stage.WAIT_PICKER, maxScrolls = 8)
            Stage.WAIT_PICKER -> {
                handler.removeCallbacks(retryScroll)
                LogStore.log("[PICKER] 浮窗出现，1.5 秒后枚举全部窗口")
                handler.removeCallbacks(dumpWindowsLater)
                handler.postDelayed(dumpWindowsLater, 1500)
                stage = Stage.DONE
            }
            else -> {}
        }
    }

    /** 找目标 → 点击；找不到 → 滚动一屏并定时重试；滚动超限 → 停在该窗口 */
    private fun tryClick(root: AccessibilityNodeInfo, label: String, next: Stage, maxScrolls: Int) {
        if (clickTarget(root, label)) {
            scrollCount = 0
            stage = next
            handler.removeCallbacks(retryScroll)
            handler.postDelayed(retryScroll, 800)
            return
        }
        if (scrollCount >= maxScrolls) {
            LogStore.log("[MISS] 滚动 $maxScrolls 次仍找不到「$label」，停在当前窗口等待")
            stage = Stage.IDLE
            handler.removeCallbacks(retryScroll)
            return
        }
        scrollCount++
        scrollForward()
        LogStore.log("[SCROLL] 第 $scrollCount/$maxScrolls 次滚动，继续找「$label」")
        // 定时重试：滚动完成后主动再找，不依赖系统事件
        handler.removeCallbacks(retryScroll)
        handler.postDelayed(retryScroll, 1200)
    }

    /** 精确匹配优先，其次包含匹配；返回并点击可点击节点 */
    private fun clickTarget(root: AccessibilityNodeInfo, label: String): Boolean {
        val exact = findNodeByText(root, label, exact = true)
        val node = exact ?: findNodeByText(root, label, exact = false) ?: return false
        val text = node.text?.toString() ?: label
        LogStore.log("[CLICK] 命中「$text」（${node.className}），执行点击")
        val ok = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        LogStore.log("[CLICK] 点击结果: $ok")
        return ok
    }

    private fun findNodeByText(root: AccessibilityNodeInfo, label: String, exact: Boolean): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            if (!n.isVisibleToUser) {
                enqueueChildren(queue, n)
                continue
            }
            val text = n.text?.toString() ?: ""
            val desc = n.contentDescription?.toString() ?: ""
            val hit = if (exact) (text == label || desc == label) else (text.contains(label) || desc.contains(label))
            if (hit) {
                var node: AccessibilityNodeInfo? = n
                while (node != null) {
                    if (node.isClickable) return node
                    node = node.parent
                }
                return n
            }
            enqueueChildren(queue, n)
        }
        return null
    }

    private fun enqueueChildren(queue: ArrayDeque<AccessibilityNodeInfo>, n: AccessibilityNodeInfo) {
        for (i in 0 until n.childCount) {
            n.getChild(i)?.let { queue.add(it) }
        }
    }

    private fun scrollForward() {
        val root = rootInActiveWindow ?: return
        val list = findScrollable(root)
        if (list != null) {
            LogStore.log("[SCROLL] 滚动容器: ${list.className} id=${list.viewIdResourceName}")
            val ok = list.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            LogStore.log("[SCROLL] 滚动执行结果: $ok")
        } else {
            LogStore.log("[SCROLL] 窗口内无滚动容器，无法滚动")
        }
    }

    /** 枚举全部窗口并 dump 文本节点——用于捕获系统"更改键盘"浮窗的组件信息 */
    private fun dumpAllWindows() {
        val sb = StringBuilder()
        var windowIndex = 0
        try {
            for (w in windows) {
                windowIndex++
                val root = w.root ?: continue
                val title = w.title?.toString() ?: ""
                sb.append("窗口#$windowIndex: pkg=${root.packageName} type=${w.type} title=$title\n")
                val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
                queue.add(Pair(root, 0))
                var count = 0
                while (queue.isNotEmpty()) {
                    val (n, depth) = queue.removeFirst()
                    if (depth > 14) continue
                    val text = n.text?.toString() ?: ""
                    val desc = n.contentDescription?.toString() ?: ""
                    val vid = n.viewIdResourceName ?: ""
                    if (text.isNotBlank() || desc.isNotBlank()) {
                        val indent = "  ".repeat(depth)
                        sb.append("$indent${n.className}")
                        if (vid.isNotEmpty()) sb.append(" id=$vid")
                        if (text.isNotBlank()) sb.append(" text=$text")
                        if (desc.isNotBlank()) sb.append(" desc=$desc")
                        if (n.isClickable) sb.append(" [可点击]")
                        sb.append("\n")
                        count++
                    }
                    for (i in 0 until n.childCount) {
                        n.getChild(i)?.let { queue.add(Pair(it, depth + 1)) }
                    }
                }
                sb.append("  (节点数 $count)\n")
            }
        } catch (e: Exception) {
            sb.append("枚举窗口异常: $e\n")
        }
        LogStore.log("[WINDOWS] 共 $windowIndex 个窗口:\n${sb.trimEnd()}")
    }

    private fun findScrollable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            if (n.isScrollable) return n
            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    /** 完整 dump 窗口树（文本/描述/id/可点击），逐行进日志 */
    private fun dumpTree(root: AccessibilityNodeInfo) {
        val sb = StringBuilder()
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.add(Pair(root, 0))
        var count = 0
        while (queue.isNotEmpty()) {
            val (n, depth) = queue.removeFirst()
            if (depth > 16) continue
            val text = n.text?.toString() ?: ""
            val desc = n.contentDescription?.toString() ?: ""
            val vid = n.viewIdResourceName ?: ""
            if (n.isVisibleToUser && (text.isNotBlank() || desc.isNotBlank())) {
                val indent = "  ".repeat(depth)
                sb.append("$indent${n.className}")
                if (vid.isNotEmpty()) sb.append(" id=$vid")
                if (text.isNotBlank()) sb.append(" text=$text")
                if (desc.isNotBlank()) sb.append(" desc=$desc")
                if (n.isClickable) sb.append(" [可点击]")
                sb.append("\n")
                count++
            }
            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { queue.add(Pair(it, depth + 1)) }
            }
        }
        LogStore.log("[DUMP] 文本节点数=$count\n${sb.trimEnd()}")
    }

    override fun onInterrupt() {}

    companion object {
        const val ACTION_NAVIGATE = "com.inputmethod.switcher.NAVIGATE"
    }
}
