package com.vlplaygames.yukiandroid

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

object Logger {
    private var logFile: File? = null
    private const val MAX_LOG_SIZE = 1024 * 1024 // 1 MB

    fun init(context: Context) {
        val logDir = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(logDir, "logs")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
        logFile = File(dir, "yuki_$timestamp.log")
        info("=== Yuki Android started ===")
    }

    fun info(msg: String) = write("INFO", msg)
    fun warning(msg: String) = write("WARN", msg)
    fun error(msg: String) = write("ERROR", msg)
    fun debug(msg: String) = write("DEBUG", msg)
    fun success(msg: String) = write("SUCCESS", msg)

    private fun write(level: String, msg: String) {
        val line = "[${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}] [$level] $msg"
        android.util.Log.d("YukiLogger", line)
        logFile?.let { file ->
            try {
                if (file.length() > MAX_LOG_SIZE) {
                    // ротация: переименовываем старый лог и создаём новый
                    val rotated = File(file.parent, "yuki_old.log")
                    file.renameTo(rotated)
                    file.createNewFile()
                }
                FileWriter(file, true).use { writer ->
                    writer.write(line + "\n")
                }
            } catch (e: Exception) {
                android.util.Log.e("YukiLogger", "Failed to write log: ${e.message}")
            }
        }
    }

    fun getLogFile(): File? = logFile
}