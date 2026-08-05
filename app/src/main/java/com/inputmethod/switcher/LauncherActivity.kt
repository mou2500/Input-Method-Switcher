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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 立即调用会被系统服务忽略（调用方尚未处于前台，国产 ROM 上表现为点击无反应）。
        // 等窗口获得焦点后再弹；3 秒兜底以防个别 ROM 不触发焦点回调。
        Handler(Looper.getMainLooper()).postDelayed({
            if (!pickerShown) {
                pickerShown = true
                showPicker()
                finish()
            }
        }, 3000)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !pickerShown) {
            pickerShown = true
            showPicker()
            finish()
        }
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
        if (!success) {
            Toast.makeText(this, "无法弹出选择器，已打开输入法设置", Toast.LENGTH_LONG).show()
            try {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }
}
