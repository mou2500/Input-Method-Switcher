package com.inputmethod.switcher

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.lang.reflect.Field
import java.lang.reflect.Method

class LauncherActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Android 14+ 隐藏 API 限制会拦截 setInputMethod* 反射,
        // 必须在首次反射前申请豁免,否则切换必然失败
        trySetHiddenApiExemptions()

        // 等窗口就绪后弹出"更改键盘"列表
        Handler(Looper.getMainLooper()).postDelayed({ showImePicker() }, 300)
    }

    private fun trySetHiddenApiExemptions() {
        try {
            val clazz = Class.forName("dalvik.system.VMRuntime")
            val getRuntime = clazz.getDeclaredMethod("getRuntime")
            getRuntime.isAccessible = true
            val runtime = getRuntime.invoke(null)
            // 用 Class.forName 取 String[] 类型,避免 Array<String>::class.java 写法
            val strArrayClass = Class.forName("[Ljava.lang.String;")
            val setExemptions = clazz.getDeclaredMethod(
                "setHiddenApiExemptions", strArrayClass)
            setExemptions.isAccessible = true
            setExemptions.invoke(runtime, arrayOf("L"))
        } catch (e: Throwable) {
            // 豁免失败不致命:切换失败时有兜底
        }
    }

    private fun showImePicker() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val imes = imm.enabledInputMethodList
        if (imes.isEmpty()) {
            openSettings("没有可用的输入法")
            return
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("更改键盘")
            .setAdapter(ImeAdapter(this, imes)) { _, which ->
                switchIme(imes[which].id)
            }
            .setNegativeButton("管理输入法") { _, _ -> openSettings("正在打开输入法设置") }
            .setOnCancelListener { finish() }
            .create()
        dialog.show()

        // 兜底:长时间不操作自动关闭,无后台残留
        Handler(Looper.getMainLooper()).postDelayed({
            if (dialog.isShowing) {
                dialog.dismiss()
                finish()
            }
        }, 30000)
    }

    private fun switchIme(imeId: String) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        // 策略1: 反射切换(部分 ROM 可用)
        val token = getImeToken(imm)
        if (token != null && tryReflectionSwitch(imm, token, imeId)) {
            // 反射调用结果不一定立即生效,延迟确认
            Handler(Looper.getMainLooper()).postDelayed({
                if (currentImeId(imm) != imeId) {
                    trySecureOrGuide(imeId)
                } else {
                    finish()
                }
            }, 500)
            return
        }

        // 策略2: 写 Settings.Secure(需 adb 一次性授权), 策略3: 引导授权
        trySecureOrGuide(imeId)
    }

    private fun tryReflectionSwitch(imm: InputMethodManager, token: IBinder, imeId: String): Boolean {
        try {
            try {
                val m: Method = InputMethodManager::class.java.getMethod(
                    "setInputMethodAndSubtype",
                    IBinder::class.java, String::class.java, InputMethodSubtype::class.java)
                m.invoke(imm, token, imeId, null)
            } catch (e: NoSuchMethodException) {
                val m: Method = InputMethodManager::class.java.getMethod(
                    "setInputMethod", IBinder::class.java, String::class.java)
                m.invoke(imm, token, imeId)
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    // AOSP 官方记录的切换方式: 写 Settings.Secure.DEFAULT_INPUT_METHOD。
    // 需要 WRITE_SECURE_SETTINGS 权限, 仅 adb 可授予:
    //   adb shell pm grant com.inputmethod.switcher android.permission.WRITE_SECURE_SETTINGS
    private fun trySecureOrGuide(imeId: String) {
        if (writeSecureSettings(imeId)) {
            finish()
            return
        }
        showAdbSetupDialog()
    }

    private fun writeSecureSettings(imeId: String): Boolean {
        return try {
            if (checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") ==
                PackageManager.PERMISSION_GRANTED
            ) {
                Settings.Secure.putInt(contentResolver, "selected_input_method_subtype", -1)
                Settings.Secure.putString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD, imeId)
            } else {
                false
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
            false
        }
    }

    private fun currentImeId(imm: InputMethodManager): String? {
        return try {
            InputMethodManager::class.java.getMethod("getCurrentInputMethodId")
                .invoke(imm) as? String
        } catch (e: Throwable) {
            null
        }
    }

    private fun showAdbSetupDialog() {
        val adbCmd = "adb shell pm grant com.inputmethod.switcher android.permission.WRITE_SECURE_SETTINGS"
        val msg = "这台设备的系统限制了普通应用直接切换输入法。\n\n" +
            "请在电脑上连接手机(开启 USB 调试),执行以下命令授权一次,之后即可正常切换:\n\n" +
            adbCmd
        AlertDialog.Builder(this)
            .setTitle("需要一次 adb 授权")
            .setMessage(msg)
            .setPositiveButton("复制命令") { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("adb", adbCmd))
                Toast.makeText(this, "命令已复制", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("打开输入法设置") { _, _ -> openSettings("正在打开输入法设置") }
            .setOnDismissListener { finish() }
            .show()
    }

    // 反射读取当前 IME 客户端的 token，setInputMethod* 需要它来匹配调用者
    private fun getImeToken(imm: InputMethodManager): IBinder? {
        return try {
            val f: Field = InputMethodManager::class.java.getDeclaredField("mCurClient")
            f.isAccessible = true
            val client = f.get(imm) ?: return null
            val cf: Field = client.javaClass.getDeclaredField("client")
            cf.isAccessible = true
            cf.get(client) as? IBinder
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun openSettings(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
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

private class ImeAdapter(context: Context, imes: List<InputMethodInfo>) :
    ArrayAdapter<InputMethodInfo>(context, 0, imes) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val ime = getItem(position)!!
        val pm = context.packageManager
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val icon = ImageView(context).apply {
            setImageDrawable(ime.loadIcon(pm))
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
        }
        val label = TextView(context).apply {
            text = ime.loadLabel(pm).toString()
            textSize = 16f
            setPadding(dp(16), 0, 0, 0)
        }
        row.addView(icon)
        row.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()
}
