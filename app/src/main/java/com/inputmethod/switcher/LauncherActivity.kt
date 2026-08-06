package com.inputmethod.switcher

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

// 点图标 → 检查无障碍服务是否开启：
//   未开启 → 显示引导界面, 一键跳到无障碍设置页开启
//   已开启 → 提示"正在自动打开键盘切换…"并退出, 由 ImeProbeService 自动导航点击
class LauncherActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogStore.append("App", "LauncherActivity 启动, 无障碍服务状态: " + if (isAccessibilityOn()) "已开启" else "未开启")

        if (isAccessibilityOn()) {
            // 服务已开启: 服务会自动导航, 这里只提示后退出
            setContentView(TextView(this).apply {
                text = "正在自动打开键盘切换…"
                setTextSize(18f)
                gravity = Gravity.CENTER
            })
            finish()
        } else {
            showGuide()
        }
    }

    private fun isAccessibilityOn(): Boolean {
        val expected = ComponentName(this, ImeProbeService::class.java)
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any {
            ComponentName.unflattenFromString(it) == expected
        }
    }

    private fun showGuide() {
        val tv = TextView(this).apply {
            text = "本应用需要一个辅助功能开关来替你点击「当前输入法」。\n\n请点击下方按钮开启，然后在列表中找到「输入法切换器(界面探针)」并打开它。"
            setTextSize(15f)
            setTextColor(Color.BLACK)
            lineSpacingExtra = 6f
        }
        val btn = Button(this).apply {
            text = "去开启辅助功能"
            setOnClickListener {
                LogStore.append("App", "点击按钮, 跳转无障碍设置页")
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        val btn2 = Button(this).apply {
            text = "已开启，开始"
            setOnClickListener {
                if (isAccessibilityOn()) {
                    LogStore.append("App", "确认服务已开启, 开始自动导航")
                    finish()
                } else {
                    LogStore.append("App", "服务仍未开启")
                    tv.text = "还没检测到服务开启，请在上一步的列表中找到「输入法切换器(界面探针)」并打开。"
                }
            }
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
            addView(tv)
            addView(btn, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 48 })
            addView(btn2, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 24 })
        }
        setContentView(layout)
    }
}
