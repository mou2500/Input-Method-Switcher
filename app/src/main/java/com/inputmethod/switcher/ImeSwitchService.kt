package com.inputmethod.switcher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

/**
 * 无障碍导航服务：替用户自动点击「当前输入法」行，调出系统"更改键盘"浮窗。
 *
 * 流程：点图标 → LauncherActivity 前台打开设置主页 → 本服务收到指令后自动导航
 * 「系统与更新」→「输入法」→「当前输入法」。找不到目标自动滚动重试，
 * 滚动超限或浮窗未出现时 Toast 提示。浮窗弹出后服务进入待命，不主动做事。
 *
 * 关键点：设置主页必须由前台 Activity 打开（ColorOS 拦截后台 startActivity），
 * 本服务只负责查找与点击。
 */
class ImeSwitchService : AccessibilityService() {

    private enum class Stage { IDLE, WAIT_HOME, WAIT_SUB, WAIT_IME_PAGE, DONE }

    private var stage = Stage.IDLE
    private var scrollCount = 0
    private var lastWindowKey = ""
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        const val ACTION_NAVIGATE = "com.inputmethod.switcher.NAVIGATE"
    }

    /** 定时重试：滚动/页面切换后主动再找，不依赖系统事件（ColorOS 不触发 content changed） */
    private val retryScroll = object : Runnable {
        override fun run() {
            if (stage == Stage.IDLE || stage == Stage.DONE) return
            rootInActiveWindow?.let { advance(it) }
        }
    }

    /** 点击「当前输入法」后确认浮窗是否真的出现 */
    private val confirmPicker = object : Runnable {
        override fun run() {
            val title = pickerWindowTitle()
            if (title != null) {
                toast("已打开键盘切换：$title")
            } else {
                toast("未检测到切换浮窗，请手动点击「当前输入法」")
            }
            stage = Stage.DONE
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_NAVIGATE) {
            stage = Stage.WAIT_HOME
            scrollCount = 0
            lastWindowKey = ""
            handler.removeCallbacks(retryScroll)
            handler.removeCallbacks(confirmPicker)
            handler.postDelayed(retryScroll, 1000)
        }
        return START_NOT_STICKY
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (stage == Stage.IDLE || stage == Stage.DONE) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: ""
            val cls = event.className?.toString() ?: ""
            val key = "$pkg/$cls"
            if (key != lastWindowKey) {
                lastWindowKey = key
                if (pkg.contains("settings", ignoreCase = true)) scrollCount = 0
            }
            rootInActiveWindow?.let { advance(it) }
        }
    }

    private fun advance(root: AccessibilityNodeInfo) {
        when (stage) {
            Stage.WAIT_HOME -> tryClick(root, "系统与更新", Stage.WAIT_SUB, maxScrolls = 12)
            Stage.WAIT_SUB -> tryClick(root, "输入法", Stage.WAIT_IME_PAGE, maxScrolls = 8)
            Stage.WAIT_IME_PAGE -> tryClick(root, "当前输入法", Stage.DONE, maxScrolls = 8)
            else -> {}
        }
    }

    /** 找到目标 → 点击并进入下一阶段；找不到 → 滚动一屏，定时重试 */
    private fun tryClick(root: AccessibilityNodeInfo, label: String, next: Stage, maxScrolls: Int) {
        if (clickTarget(root, label)) {
            scrollCount = 0
            stage = next
            handler.removeCallbacks(retryScroll)
            handler.postDelayed(retryScroll, 800)
            if (next == Stage.DONE) {
                handler.postDelayed(confirmPicker, 1500)
            }
            return
        }
        if (scrollCount >= maxScrolls) {
            toast("未找到「$label」，已停止自动导航")
            stage = Stage.IDLE
            handler.removeCallbacks(retryScroll)
            return
        }
        scrollCount++
        scrollForward()
        handler.removeCallbacks(retryScroll)
        handler.postDelayed(retryScroll, 1200)
    }

    /** 命中目标文本后点击其可点击祖先，返回是否成功 */
    private fun clickTarget(root: AccessibilityNodeInfo, label: String): Boolean {
        val node = findNodeByText(root, label) ?: return false
        val text = node.text?.toString() ?: label
        val ok = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        if (ok) toast("已点击「$text」")
        return ok
    }

    private fun findNodeByText(root: AccessibilityNodeInfo, label: String): AccessibilityNodeInfo? {
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
            if (text == label || desc == label || text.contains(label) || desc.contains(label)) {
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
        findScrollable(root)?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
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

    /** 在全部窗口中查找"更改键盘"浮窗（系统悬浮窗，不在 rootInActiveWindow 里） */
    private fun pickerWindowTitle(): String? {
        return try {
            for (w in windows) {
                val t = w.title?.toString()?.trim()
                if (!t.isNullOrEmpty() &&
                    (t.contains("键盘") || t.contains("输入法") || t.contains("选择"))
                ) return t
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun toast(msg: String) {
        try {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
