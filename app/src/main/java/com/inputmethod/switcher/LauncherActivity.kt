package com.inputmethod.switcher

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * 入口：点击图标
 * 1) 无障碍服务未开启 → 引导页，跳无障碍设置开启
 * 2) 已开启 → 发导航指令给服务 + 前台打开设置主页 + 本页立即退出
 *
 * 关键：设置主页由本 Activity（前台）打开，服务只负责查找与点击，
 * 避免 ColorOS 后台启动拦截。
 */
class LauncherActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (isAccessibilityOn()) {
            startService(Intent(this, ImeSwitchService::class.java)
                .setAction(ImeSwitchService.ACTION_NAVIGATE))
            startActivity(Intent(Settings.ACTION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
            Toast.makeText(this, "开始自动打开键盘切换…", Toast.LENGTH_SHORT).show()
            finish()
        } else {
            showGuide()
        }
    }

    private fun isAccessibilityOn(): Boolean {
        val expected = ComponentName(this, ImeSwitchService::class.java)
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any {
            ComponentName.unflattenFromString(it) == expected
        }
    }

    private fun showGuide() {
        val desc = TextView(this).apply {
            text = "点一下图标，自动打开系统「更改键盘」浮窗。\n\n" +
                    "首次使用需开启无障碍（仅用于自动点击「当前输入法」，不读取其他信息）：\n\n" +
                    "1. 点击下方按钮，进入无障碍设置\n" +
                    "2. 找到「输入法切换器」并开启\n" +
                    "3. 返回桌面，再次点击本应用图标"
            textSize = 15f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setLineSpacing(4f, 1.2f)
            setPadding(24, 24, 24, 24)
        }
        val btn = Button(this).apply {
            text = "去开启无障碍"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }
        layout.addView(desc, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = 32
        layout.addView(btn, lp)
        setContentView(layout)
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }
}
