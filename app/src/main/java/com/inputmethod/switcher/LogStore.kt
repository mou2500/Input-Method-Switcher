package com.inputmethod.switcher

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 日志落盘：写入公共下载目录，固定文件名，每次启动覆盖。
 *
 * 关键设计：持有 insert 返回的 uri 直接写入，不在 log 时查询 MediaStore
 * （ColorOS 上刚写入的文件条目处于 pending 状态，查询会落空导致日志丢失）。
 * uri 失效时自动重新创建，保证日志永不丢失。
 */
object LogStore {

    private const val FILE_NAME = "ime_probe_log.txt"
    private var app: Context? = null
    private var currentUri: Uri? = null

    fun init(context: Context) {
        app = context.applicationContext
        deleteOldFiles()
        currentUri = insertNewFile()
        if (currentUri != null) {
            write(currentUri!!, "===== IME 探针日志 =====\n")
        } else {
            Log.e("LogStore", "创建日志文件失败")
        }
    }

    fun log(line: String) {
        var uri = currentUri
        if (uri == null) {
            uri = insertNewFile()
            currentUri = uri
        }
        if (uri != null) {
            val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
            write(uri, "[$time] $line\n")
        } else {
            Log.e("LogStore", "日志写入失败(无文件): $line")
        }
    }

    /** 删除所有同名旧文件，避免堆积（尽力而为，失败不影响后续写入） */
    private fun deleteOldFiles() {
        val c = app ?: return
        val resolver = c.contentResolver
        try {
            resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Downloads._ID),
                "${MediaStore.Downloads.DISPLAY_NAME} = ?",
                arrayOf(FILE_NAME), null
            )?.use { cur ->
                while (cur.moveToNext()) {
                    val id = cur.getLong(0)
                    resolver.delete(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        "${MediaStore.Downloads._ID} = ?",
                        arrayOf(id.toString())
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("LogStore", "清理旧文件失败: ${e.message}")
        }
    }

    private fun insertNewFile(): Uri? {
        val c = app ?: return null
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, FILE_NAME)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        return try {
            c.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        } catch (e: Exception) {
            Log.e("LogStore", "创建日志文件异常: ${e.message}")
            null
        }
    }

    private fun write(uri: Uri, content: String) {
        val c = app ?: return
        try {
            c.contentResolver.openOutputStream(uri, "wa")?.use { out ->
                out.write(content.toByteArray())
                out.flush()
            }
        } catch (e: Exception) {
            Log.e("LogStore", "写入失败，重置文件: ${e.message}")
            currentUri = null
        }
    }
}
