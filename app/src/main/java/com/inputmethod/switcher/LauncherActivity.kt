package com.inputmethod.switcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast

// 系统"更改键盘"浮窗的触发条件是: 调用方必须持有窗口焦点
// (AOSP InputMethodManagerService.canShowInputMethodPickerLocked)。
// 因此这里用一个可见 Activity 等到获得焦点后再调 showInputMethodPicker()。
class LauncherActivity : Activity() {

    private var pickerInvoked = false
    private var pickerLostFocus = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogStore.append("App", "Activity 启动")
        setContentView(TextView(this).apply {
            text = "正在打开键盘切换…"
            setTextSize(18f)
            gravity = Gravity.CENTER
        })
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        LogStore.append("App", "焦点变化: hasFocus=$hasFocus pickerInvoked=$pickerInvoked pickerLostFocus=$pickerLostFocus")
        if (hasFocus) {
            if (pickerLostFocus) {
                // 用户已选完(或关闭)浮窗, 自动退出
                LogStore.append("App", "浮窗关闭, 自动退出")
                finish()
            } else if (!pickerInvoked) {
                pickerInvoked = true
                invokePicker()
            }
        } else if (pickerInvoked) {
            // 焦点被"更改键盘"浮窗抢走 → 说明浮窗已弹出
            pickerLostFocus = true
            LogStore.append("App", "失焦: 浮窗可能已弹出")
            // 兜底: 浮窗异常未关闭时 8 秒后自动退出
            Handler(Looper.getMainLooper()).postDelayed({
                if (!hasWindowFocus()) finish()
            }, 8000)
        }
    }

    private fun invokePicker() {
        LogStore.append("App", "调用 showInputMethodPicker()")
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        } catch (e: Exception) {
            LogStore.append("App", "showInputMethodPicker 异常: $e")
            e.printStackTrace()
            openSettingsFallback()
            return
        }
        // 兜底: 3 秒后仍持有焦点 → 浮窗未弹出(被系统拒绝) → 打开设置页
        Handler(Looper.getMainLooper()).postDelayed({
            LogStore.append("App", "3秒检查: pickerLostFocus=$pickerLostFocus hasWindowFocus=${hasWindowFocus()}")
            if (!pickerLostFocus && hasWindowFocus()) {
                LogStore.append("App", "浮窗未弹出, 走设置页兜底")
                openSettingsFallback()
            }
        }, 3000)
    }

    private fun openSettingsFallback() {
        LogStore.append("App", "兜底: 打开输入法设置页")
        Toast.makeText(this, "无法弹出键盘切换浮窗，已打开输入法设置", Toast.LENGTH_LONG).show()
        try {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        } catch (e: Exception) {
            e.printStackTrace()
        }
        finish()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }
}
