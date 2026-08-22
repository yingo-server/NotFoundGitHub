package io.nggit.util

import android.app.AlertDialog
import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.PrintWriter
import io.nggit.R
import io.nggit.App
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ErrorDialog {

    private const val TAG = "NGGit_Error"
    private var lastErrorTime = 0L
    private const val MIN_ERROR_INTERVAL = 2000L

    fun show(context: Context, title: String, message: String, throwable: Throwable? = null) {
        val now = System.currentTimeMillis()
        if (now - lastErrorTime < MIN_ERROR_INTERVAL) return
        lastErrorTime = now

        val fullMessage = buildString {
            append(message)
            if (throwable != null) {
                append("\n\n")
                append("错误类型: ${throwable.javaClass.simpleName}")
                if (throwable.message != null) {
                    append("\n详情: ${throwable.message}")
                }
            }
        }

        Log.e(TAG, "$title: $fullMessage", throwable)

        saveErrorLog(title, fullMessage, throwable)

        try {
            val builder = AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(fullMessage)
                .setPositiveButton(context.getString(R.string.ok)) { dialog, _ -> dialog.dismiss() }

            if (throwable != null) {
                builder.setNeutralButton(context.getString(R.string.copy_error)) { _, _ ->
                    copyToClipboard(context, "$title\n\n$fullMessage")
                }
            }

            builder.show()
        } catch (e: Exception) {
            Log.e(TAG, "无法显示错误对话框", e)
        }
    }

    fun show(context: Context, title: String, throwable: Throwable) {
        show(context, title, throwable.message ?: context.getString(R.string.error_unknown), throwable)
    }

    fun wrap(context: Context, title: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            show(context, title, e)
        }
    }

    private fun saveErrorLog(title: String, message: String, throwable: Throwable?) {
        try {
            val logDir = File(contextFilesDir(), "logs")
            if (!logDir.exists()) logDir.mkdirs()

            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            val logFile = File(logDir, "error_$timestamp.txt")

            val stackTrace = if (throwable != null) {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                sw.toString()
            } else ""

            logFile.writeText(buildString {
                appendLine("时间: $timestamp")
                appendLine("标题: $title")
                appendLine("消息: $message")
                if (stackTrace.isNotEmpty()) {
                    appendLine("\n堆栈:")
                    appendLine(stackTrace)
                }
                appendLine("\n设备信息:")
                appendLine("  Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine("  设备: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("  存储: ${Environment.getExternalStorageState()}")
            })

            cleanOldLogs(logDir)
        } catch (e: Exception) {
            Log.e(TAG, "保存错误日志失败", e)
        }
    }

    private fun cleanOldLogs(logDir: File) {
        try {
            val files = logDir.listFiles()?.filter { it.name.startsWith("error_") }?.sortedByDescending { it.name } ?: return
            if (files.size > 20) {
                files.drop(20).forEach { it.delete() }
            }
        } catch (_: Exception) {}
    }

    private fun contextFilesDir(): File {
        return App.instance.filesDir
    }

    private fun copyToClipboard(context: Context, text: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("error_log", text)
            clipboard.setPrimaryClip(clip)
            android.widget.Toast.makeText(context, context.getString(R.string.copied_to_clipboard), android.widget.Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
    }
}
