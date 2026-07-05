package com.cragnet.wysawyg

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object WysawygLogger {

    private const val TAG = "WYSAWYG"
    private const val MAX_LINES = 5000
    private var logFile: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.UK)

    fun init(context: Context) {
        logFile = File(context.filesDir, "wysawyg.log")
        logFile?.let {
            if (!it.exists()) it.createNewFile()
            i("Logger initialized: ${it.absolutePath}")
        }
    }

    fun d(msg: String) {
        Log.d(TAG, msg)
        append("D", msg)
    }

    fun i(msg: String) {
        Log.i(TAG, msg)
        append("I", msg)
    }

    fun w(msg: String) {
        Log.w(TAG, msg)
        append("W", msg)
    }

    fun e(msg: String, throwable: Throwable? = null) {
        Log.e(TAG, msg, throwable)
        val detail = throwable?.let { "\n${it.stackTraceToString()}" } ?: ""
        append("E", "$msg$detail")
    }

    private fun append(level: String, msg: String) {
        try {
            logFile?.let { file ->
                val timestamp = dateFormat.format(Date())
                file.appendText("$timestamp [$level] $msg\n")
                trimIfNeeded(file)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write log", e)
        }
    }

    private fun trimIfNeeded(file: File) {
        try {
            val lines = file.readLines()
            if (lines.size > MAX_LINES) {
                val trimmed = lines.takeLast(MAX_LINES)
                file.writeText(trimmed.joinToString("\n") + "\n")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trim log", e)
        }
    }

    fun getLogFile(context: Context): File {
        return logFile ?: File(context.filesDir, "wysawyg.log").also { logFile = it }
    }

    fun getLogText(context: Context): String {
        return try {
            getLogFile(context).readText()
        } catch (e: Exception) {
            "Unable to read log: ${e.message}"
        }
    }

    fun clear(context: Context) {
        try {
            getLogFile(context).writeText("")
            i("Log cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear log", e)
        }
    }
}
