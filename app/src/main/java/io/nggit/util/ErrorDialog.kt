/**
 * 统一错误处理对话框工具，负责错误信息的展示、日志记录和复制功能。
 * 支持错误频率限制防止弹窗轰炸，自动清理过期日志文件。
 * 将错误详情（包括堆栈信息、设备信息）持久化到本地日志文件，
 * 并提供一键复制错误信息到剪贴板的便捷操作。
 */
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

/**
 * 错误对话框管理单例对象，统一处理应用中的错误展示和日志记录。
 * 通过频率限制机制防止短时间内重复弹出错误对话框，
 * 同时将错误信息保存到本地文件便于问题排查。
 */
object ErrorDialog {

    private const val TAG = "NGGit_Error"
    /** 上次错误弹窗显示的时间戳 */
    private var lastErrorTime = 0L
    /** 错误弹窗最小间隔时间（毫秒），防止弹窗轰炸 */
    private const val MIN_ERROR_INTERVAL = 2000L

    /**
     * 显示错误信息对话框。
     * 包含错误标题、详细消息和可选的异常信息，支持错误频率限制。
     * 会同时执行日志记录操作，将错误信息持久化到本地文件。
     *
     * @param context 上下文，用于创建对话框
     * @param title 错误标题，显示在对话框标题栏
     * @param message 错误描述消息
     * @param throwable 可选的异常对象，提供详细的错误堆栈信息
     */
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

    /**
     * 显示错误对话框的便捷重载方法。
     * 自动从异常对象中提取错误消息作为对话框内容。
     *
     * @param context 上下文，用于创建对话框
     * @param title 错误标题
     * @param throwable 异常对象，其 message 将作为对话框消息内容
     */
    fun show(context: Context, title: String, throwable: Throwable) {
        show(context, title, throwable.message ?: context.getString(R.string.error_unknown), throwable)
    }

    /**
     * 安全执行代码块，捕获异常时自动弹出错误对话框。
     * 用于包裹可能抛出异常的操作，提供统一的错误处理入口。
     *
     * @param context 上下文，用于创建错误对话框
     * @param title 发生错误时显示的标题
     * @param block 需要执行的代码块
     */
    fun wrap(context: Context, title: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            show(context, title, e)
        }
    }

    /**
     * 保存错误日志到本地文件。
     * 包含时间戳、错误标题、消息内容、堆栈信息和设备信息，
     * 便于后续问题排查和分析。
     *
     * @param title 错误标题
     * @param message 错误消息内容
     * @param throwable 可选的异常对象
     */
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

    /**
     * 清理过期的错误日志文件。
     * 保留最新的 20 个日志文件，删除更早的记录以防止磁盘空间浪费。
     *
     * @param logDir 日志文件所在目录
     */
    private fun cleanOldLogs(logDir: File) {
        try {
            val files = logDir.listFiles()?.filter { it.name.startsWith("error_") }?.sortedByDescending { it.name } ?: return
            if (files.size > 20) {
                files.drop(20).forEach { it.delete() }
            }
        } catch (_: Exception) {}
    }

    /**
     * 获取应用内部文件存储目录。
     *
     * @return 应用内部 files 目录对应的 File 对象
     */
    private fun contextFilesDir(): File {
        return App.instance.filesDir
    }

    /**
     * 将文本内容复制到系统剪贴板。
     * 复制完成后显示简短的 Toast 提示用户操作成功。
     *
     * @param context 上下文，用于获取剪贴板服务
     * @param text 需要复制到剪贴板的文本内容
     */
    private fun copyToClipboard(context: Context, text: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("error_log", text)
            clipboard.setPrimaryClip(clip)
            android.widget.Toast.makeText(context, context.getString(R.string.copied_to_clipboard), android.widget.Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
    }
}
