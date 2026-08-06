package com.inputmethod.switcher

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 进程内共享的日志缓冲: LauncherActivity 记事件, ImeProbeService 记界面 dump 并落盘
object LogStore {
    private val buffer = StringBuilder()
    private val format = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun append(tag: String, msg: String) {
        buffer.append(format.format(Date())).append(" [").append(tag).append("] ").append(msg).append("\n")
    }

    @Synchronized
    fun content(): String = buffer.toString()
}
