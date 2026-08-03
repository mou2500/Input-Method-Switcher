package com.quickgemini.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/**
 * 一个完全透明、无界面的启动 Activity。
 *
 * 点击桌面图标后：
 *  1. 本 Activity 启动（用户不可见）；
 *  2. 等待 [LAUNCH_DELAY_MS] 毫秒，让侧边栏/Edge Panel 有时间收起；
 *  3. 通过 [Intent.ACTION_VOICE_COMMAND] 唤起 Gemini 浮层；
 *  4. 立即 finish() 自身，无过渡动画。
 *
 * 不使用 setContentView / 不使用 AndroidX，保证 APK 极小、启动极快。
 */
class LauncherActivity : Activity() {

    private val mainHandler = Handler(Looper.getMainLooper())

    private val launchRunnable = Runnable {
        launchVoiceCommand()
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 故意不调用 setContentView —— 整个 Activity 是透明的，
        // 不需要任何 View 层级。

        // 300ms 后唤起 Gemini。postDelayed 绑定主线程 Looper，
        // 即使 Activity 在 300ms 内被销毁，也会在 onDestroy 中移除回调。
        mainHandler.postDelayed(launchRunnable, LAUNCH_DELAY_MS)
    }

    override fun onDestroy() {
        // 防止 Activity 在延时期间被销毁后 Runnable 仍被执行
        // （例如用户立即按 Home 或系统回收）。
        mainHandler.removeCallbacks(launchRunnable)
        super.onDestroy()
    }

    private fun launchVoiceCommand() {
        val intent = Intent(Intent.ACTION_VOICE_COMMAND)
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // 设备上没有任何应用注册 VOICE_COMMAND（几乎不会发生，
            // 因为系统 Google App 通常会注册）。给用户一个提示。
            Toast.makeText(
                this,
                R.string.error_no_voice_handler,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun finish() {
        super.finish()
        // 消除 Activity 退出动画，避免透明窗口消失时出现任何闪烁或黑屏。
        overridePendingTransition(0, 0)
    }

    private companion object {
        /** 等待侧边栏收起的时长（毫秒）。 */
        const val LAUNCH_DELAY_MS = 300L
    }
}
