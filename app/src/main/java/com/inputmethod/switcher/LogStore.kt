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
 * 用户通过 文件管理 → 下载 → ime_probe_log.txt 即可取到。
 */
object LogStore {

    private const val FILE_NAME = "ime_probe_log.txt"
    private var app: Context? = null

    fun init(context: Context) {
        app = context.applicationContext
        deleteOldFiles()
        val uri = insertNewFile()
        if (uri != null) {
            write(uri, "===== IME 探针日志 =====\n")
            log("日志文件已创建: $FILE_NAME")
        } else {
            Log.e("LogStore", "创建日志文件失败")
        }
    }

    fun log(line: String) {
        val c = app ?: return
        val resolver = c.contentResolver
        val id = findFileId(resolver) ?: run {
            Log.e("LogStore", "日志文件不存在，跳过: $line")
            return
        }
        val uri = Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id.toString())
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        write(uri, "[$time] $line\n")
    }

    private fun deleteOldFiles() {
        val c = app ?: return
        val resolver = c.contentResolver
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
    }

    private fun insertNewFile(): Uri? {
        val c = app ?: return null
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, FILE_NAME)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        return c.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
    }

    private fun findFileId(resolver: android.content.ContentResolver): String? {
        var id: String? = null
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.DISPLAY_NAME} = ?",
            arrayOf(FILE_NAME), null
        )?.use { cur ->
            if (cur.moveToFirst()) id = cur.getLong(0).toString()
        }
        return id
    }

    private fun write(uri: Uri, content: String) {
        val c = app ?: return
        try {
            c.contentResolver.openOutputStream(uri, "wa")?.use { out ->
                out.write(content.toByteArray())
                out.flush()
            }
        } catch (e: Exception) {
            Log.e("LogStore", "写入失败: ${e.message}")
        }
    }
}
