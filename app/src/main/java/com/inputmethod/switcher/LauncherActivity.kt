package com.inputmethod.switcher

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

// 点图标 → 检查无障碍服务是否开启：
//   未开启 → 显示引导界面, 一键跳到无障碍设置页开启
//   已开启 → 提示"正在自动打开键盘切换…"并退出, 由 ImeProbeService 自动导航点击
class LauncherActivity : Activity() {

    private lateinit var tv: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogStore.append("App", "LauncherActivity 启动, 无障碍服务状态: " + if (isAccessibilityOn()) "已开启" else "未开启")

        if (isAccessibilityOn()) {
            // 服务已开启: 发导航指令让探针立即自动点击, 提示后延迟退出
            LogStore.append("App", "无障碍服务已开启, 发送导航指令")
            try {
                startService(Intent(this, ImeProbeService::class.java)
                    .setAction(ImeProbeService.ACTION_NAVIGATE))
            } catch (e: Exception) {
                LogStore.append("App", "发送导航指令失败: $e")
            }
            setContentView(makeTextView("正在自动打开键盘切换…"))
            Toast.makeText(this, "正在自动打开键盘切换…", Toast.LENGTH_SHORT).show()
            Handler(Looper.getMainLooper()).postDelayed({ finish() }, 1500)
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

    private fun makeTextView(text: String): TextView {
        val v = TextView(this)
        v.text = text
        v.textSize = 18f
        v.gravity = Gravity.CENTER
        return v
    }

    private fun showGuide() {
        tv = makeTextView(
            "本应用需要一个辅助功能开关来替你点击「当前输入法」。\n\n请点击下方按钮开启，然后在列表中找到「输入法切换器(界面探针)」并打开它。"
        )
        tv.textSize = 15f
        tv.setTextColor(Color.BLACK)

        val btn = Button(this)

        btn.text = "去开启辅助功能"
        btn.setOnClickListener {
            LogStore.append("App", "点击按钮, 跳转无障碍设置页")
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        val btn2 = Button(this)
        btn2.text = "已开启，开始"
        btn2.setOnClickListener {
            if (isAccessibilityOn()) {
                LogStore.append("App", "确认服务已开启, 开始自动导航")
                finish()
            } else {
                LogStore.append("App", "服务仍未开启")
                tv.setText("还没检测到服务开启，请在上一步的列表中找到「输入法切换器(界面探针)」并打开。")
            }
        }

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(48, 96, 48, 48)
        layout.addView(tv)
        val lp1 = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp1.topMargin = 48
        layout.addView(btn, lp1)
        val lp2 = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp2.topMargin = 24
        layout.addView(btn2, lp2)
        setContentView(layout)
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }
}
