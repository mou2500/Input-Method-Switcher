package com.inputmethod.switcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast

class LauncherActivity : Activity() {

    private var pickerShown = false
    private var pausedOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // showInputMethodPicker() 只是向系统服务发异步请求，弹窗由系统进程稍后完成。
        // 若 Activity 立即 finish，窗口先销毁，系统服务会判定调用方已不在前台而忽略请求
        // —— 表现为点击无反应。因此弹出后必须让 Activity 存活足够时间。
        Handler(Looper.getMainLooper()).postDelayed({
            if (!pickerShown) {
                pickerShown = true
                showPicker()
            }
        }, 300)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !pickerShown) {
            pickerShown = true
            showPicker()
        }
    }

    override fun onPause() {
        super.onPause()
        pausedOnce = true
    }

    private fun showPicker() {
        val success = try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            InputMethodManager::class.java.getMethod("showInputMethodPicker").invoke(imm)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }

        if (success) {
            // 浮窗弹出会抢走本窗口焦点（触发 onPause）。
            // 1.2 秒后从未 pause 过，说明浮窗没弹出来，走兜底。
            Handler(Looper.getMainLooper()).postDelayed({
                if (!pausedOnce) openSettingsFallback()
            }, 1200)
        } else {
            openSettingsFallback()
        }

        // 保持 Activity 存活给用户选择时间；选完自动关闭，无后台残留
        Handler(Looper.getMainLooper()).postDelayed({ finish() }, 15000)
    }

    private fun openSettingsFallback() {
        Toast.makeText(this, "无法弹出选择器，已打开输入法设置", Toast.LENGTH_LONG).show()
        try {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }
}
