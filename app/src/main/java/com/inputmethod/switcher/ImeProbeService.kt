package com.inputmethod.switcher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 无障碍探针：模拟手指操作，导航 设置→系统与更新→输入法→当前输入法，
 * 触发系统"更改键盘"浮窗。每一步窗口内容全部 dump 进日志。
 *
 * 设计原则：不主动 startActivity（后台启动会被 ColorOS 拦截），
 * 设置主页由 LauncherActivity 前台打开；本服务只监听窗口、点击目标行。
 */
class ImeProbeService : AccessibilityService() {

    private enum class Stage { IDLE, WAIT_HOME, WAIT_SUB, WAIT_IME_PAGE, WAIT_PICKER, DONE }

    private var stage = Stage.IDLE
    private var scrollAttempts = 0
    private var lastWindowKey = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        LogStore.log("[SERVICE] 无障碍服务已连接")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_NAVIGATE) {
            LogStore.log("[SERVICE] 收到导航指令")
            stage = Stage.WAIT_HOME
            scrollAttempts = 0
        }
        return START_NOT_STICKY
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (stage == Stage.IDLE || stage == Stage.DONE) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val root = rootInActiveWindow ?: return
        val pkg = event.packageName?.toString() ?: "?"
        val cls = event.className?.toString() ?: "?"
        val key = "$pkg/$cls"
        if (key != lastWindowKey) {
            lastWindowKey = key
            LogStore.log("[WINDOW] 新窗口: $key")
            dumpTree(root)
        }

        when (stage) {
            Stage.WAIT_HOME -> {
                if (clickTarget(root, "系统与更新")) {
                    stage = Stage.WAIT_SUB
                } else {
                    scrollOrGiveUp("系统与更新", Stage.WAIT_SUB)
                }
            }
            Stage.WAIT_SUB -> {
                if (clickTarget(root, "输入法")) {
                    stage = Stage.WAIT_IME_PAGE
                } else {
                    LogStore.log("[MISS] 未找到「输入法」，停在当前窗口等待")
                    stage = Stage.IDLE
                }
            }
            Stage.WAIT_IME_PAGE -> {
                if (clickTarget(root, "当前输入法")) {
                    stage = Stage.WAIT_PICKER
                    LogStore.log("[CLICK] 已点击「当前输入法」，等待浮窗…")
                } else {
                    LogStore.log("[MISS] 未找到「当前输入法」，停在当前窗口等待")
                    stage = Stage.IDLE
                }
            }
            Stage.WAIT_PICKER -> {
                LogStore.log("[PICKER] 浮窗窗口已出现，其完整内容已 dump 在上方 [WINDOW] 日志中")
                stage = Stage.DONE
            }
            else -> {}
        }
    }

    /** 在窗口树中找目标文本的可点击节点并点击；返回是否命中 */
    private fun clickTarget(root: AccessibilityNodeInfo, label: String): Boolean {
        val node = findClickableByText(root, label) ?: return false
        val text = node.text?.toString() ?: label
        LogStore.log("[CLICK] 命中「$text」，执行点击")
        val ok = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        LogStore.log("[CLICK] 点击结果: $ok")
        return ok
    }

    /** 未找到目标：滚动一屏再找一次，仍无则记录 MISS 并停在该窗口 */
    private fun scrollOrGiveUp(label: String, next: Stage) {
        if (scrollAttempts < 2) {
            scrollAttempts++
            LogStore.log("[SCROLL] 未找到「$label」，滚动列表（第 ${scrollAttempts} 次）")
            scrollForward()
        } else {
            LogStore.log("[MISS] 滚动两次仍找不到「$label」，停在当前窗口等待用户操作")
            stage = Stage.IDLE
        }
    }

    private fun scrollForward() {
        val root = rootInActiveWindow ?: return
        val list = findScrollable(root)
        if (list != null) {
            list.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        } else {
            performGlobalAction(AccessibilityService.GLOBAL_ACTION_SCROLL_FORWARD)
        }
    }

    private fun findScrollable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            if (n.isScrollable && n.isVisibleToUser) return n
            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun findClickableByText(root: AccessibilityNodeInfo, label: String): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            val text = n.text?.toString() ?: ""
            val desc = n.contentDescription?.toString() ?: ""
            if (n.isVisibleToUser && (text.contains(label) || desc.contains(label))) {
                var node: AccessibilityNodeInfo? = n
                while (node != null) {
                    if (node.isClickable) return node
                    node = node.parent
                }
                return n
            }
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
