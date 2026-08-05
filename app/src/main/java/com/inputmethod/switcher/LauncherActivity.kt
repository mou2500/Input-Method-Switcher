package com.inputmethod.switcher

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.Toast

class LauncherActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val success = showInputMethodPicker()

        if (!success) {
            Toast.makeText(this, "无法打开输入法选择器", Toast.LENGTH_SHORT).show()
        }

        finish()
    }

    private fun showInputMethodPicker(): Boolean {
        return try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
            imm?.let {
                InputMethodManager::class.java.getMethod("showInputMethodPicker").invoke(it)
                true
            } ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
                imm?.showInputMethodPicker()
                true
            } catch (e2: Exception) {
                e2.printStackTrace()
                false
            }
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }
}
