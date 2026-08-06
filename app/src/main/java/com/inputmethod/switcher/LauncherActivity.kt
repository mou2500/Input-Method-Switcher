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
 * 入口：点击图标后
 * 1) 无障碍服务未开启 → 引导页，跳无障碍设置开启
 * 2) 已开启 → 发导航指令给探针，前台打开设置主页，本页立即退出
 *
 * 关键：设置主页由本 Activity（前台）打开，探针服务只负责监听与点击，
 * 避免 ColorOS 后台启动拦截。
 */
class LauncherActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogStore.init(this)
        LogStore.log("[APP] 图标已点击，无障碍服务: ${if (isAccessibilityOn()) "已开启" else "未开启"}")

        if (isAccessibilityOn()) {
            LogStore.log("[APP] 发送导航指令，打开设置主页")
            try {
                startService(Intent(this, ImeProbeService::class.java)
                    .setAction(ImeProbeService.ACTION_NAVIGATE))
            } catch (e: Exception) {
                LogStore.log("[APP] 发送导航指令失败: $e")
            }
            val settings = Intent(Settings.ACTION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(settings)
            Toast.makeText(this, "开始自动导航", Toast.LENGTH_SHORT).show()
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
        val desc = TextView(this).apply {
            text = "本应用用无障碍服务替你自动点击「当前输入法」行。\n\n" +
                    "开启步骤：\n" +
                    "1. 点击下方按钮，进入无障碍设置\n" +
                    "2. 找到「输入法切换器（界面探针）」并开启\n" +
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
                LogStore.log("[APP] 跳转无障碍设置页")
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
