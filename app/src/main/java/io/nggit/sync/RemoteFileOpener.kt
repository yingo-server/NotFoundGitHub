package io.nggit.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.widget.Toast
import io.nggit.App
import io.nggit.R
import io.nggit.auth.AuthManager
import io.nggit.model.FileInfo
import io.nggit.util.StoragePath
import java.io.File
import java.util.concurrent.Executors

object RemoteFileOpener {

    private const val TAG = "RemoteFileOpener"
    private const val CACHE_DIR_NAME = "remote_cache"
    private val executor = Executors.newSingleThreadExecutor()

    fun openFile(context: Context, fileInfo: FileInfo, owner: String, repo: String, branch: String) {
        if (fileInfo.isDir()) {
            Toast.makeText(context, context.getString(R.string.error_unknown), Toast.LENGTH_SHORT).show()
            return
        }

        val cacheDir = File(context.cacheDir, CACHE_DIR_NAME)
        if (!cacheDir.exists()) cacheDir.mkdirs()

        val cacheFile = File(cacheDir, "${fileInfo.sha}_${fileInfo.name}")

        if (cacheFile.exists() && cacheFile.length() > 0) {
            launchFile(context, cacheFile, fileInfo)
            return
        }

        Toast.makeText(context, context.getString(R.string.loading), Toast.LENGTH_SHORT).show()

        executor.execute {
            try {
                val success = fetchAndDecode(context, fileInfo, owner, repo, branch, cacheFile)
                if (success) {
                    (context as? android.app.Activity)?.runOnUiThread {
                        launchFile(context, cacheFile, fileInfo)
                    } ?: run {
                        val intent = createViewIntent(context, cacheFile, fileInfo)
                        context.startActivity(Intent.createChooser(intent, fileInfo.name))
                    }
                } else {
                    (context as? android.app.Activity)?.runOnUiThread {
                        Toast.makeText(context, context.getString(R.string.error_loading), Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "打开远程文件失败: ${fileInfo.path}", e)
                (context as? android.app.Activity)?.runOnUiThread {
                    Toast.makeText(context, context.getString(R.string.error_loading, e.message), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun openByUrl(context: Context, url: String, fileName: String) {
        executor.execute {
            try {
                val api = App.instance.githubApi
                val content = api.getRawFile(url)
                if (content == null) {
                    (context as? android.app.Activity)?.runOnUiThread {
                        Toast.makeText(context, context.getString(R.string.error_loading), Toast.LENGTH_SHORT).show()
                    }
                    return@execute
                }

                val cacheDir = File(context.cacheDir, CACHE_DIR_NAME)
                if (!cacheDir.exists()) cacheDir.mkdirs()
                val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val cacheFile = File(cacheDir, safeName)
                cacheFile.writeText(content)

                (context as? android.app.Activity)?.runOnUiThread {
                    val ext = safeName.substringAfterLast('.', "").lowercase()
                    val mimeType = getMimeType(ext)
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.fromFile(cacheFile), mimeType)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    try {
                        context.startActivity(Intent.createChooser(intent, safeName))
                    } catch (e: Exception) {
                        Toast.makeText(context, context.getString(R.string.error_loading), Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "通过URL打开文件失败: $url", e)
                (context as? android.app.Activity)?.runOnUiThread {
                    Toast.makeText(context, context.getString(R.string.error_loading, e.message), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun fetchAndDecode(
        context: Context,
        fileInfo: FileInfo,
        owner: String,
        repo: String,
        branch: String,
        cacheFile: File
    ): Boolean {
        val api = App.instance.githubApi
        val token = AuthManager.getToken() ?: return false

        val blob = api.getFileContent(token, owner, repo, fileInfo.path, fileInfo.sha, branch)
        if (blob != null) {
            return try {
                val decoded = decodeContent(blob.content, blob.encoding)
                if (decoded != null) {
                    cacheFile.writeBytes(decoded)
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Base64解码失败: ${fileInfo.path}", e)
                false
            }
        }

        val downloadUrl = fileInfo.downloadUrl
        if (downloadUrl != null) {
            val rawContent = api.getRawFile(downloadUrl)
            if (rawContent != null) {
                cacheFile.writeText(rawContent)
                return true
            }
        }

        val contents = api.getContents(token, owner, repo, fileInfo.path, branch)
        val file = contents.firstOrNull { it.path == fileInfo.path }
        if (file != null && file.downloadUrl != null) {
            val rawContent = api.getRawFile(file.downloadUrl)
            if (rawContent != null) {
                cacheFile.writeText(rawContent)
                return true
            }
        }

        return false
    }

    private fun decodeContent(content: String, encoding: String): ByteArray? {
        return try {
            when (encoding) {
                "base64" -> Base64.decode(content.replace("\n", "").replace("\r", ""), Base64.DEFAULT)
                "plain" -> content.toByteArray(Charsets.UTF_8)
                else -> Base64.decode(content.replace("\n", "").replace("\r", ""), Base64.DEFAULT)
            }
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "解码失败: encoding=$encoding", e)
            content.toByteArray(Charsets.UTF_8)
        }
    }

    private fun launchFile(context: Context, cacheFile: File, fileInfo: FileInfo) {
        try {
            val intent = createViewIntent(context, cacheFile, fileInfo)
            context.startActivity(Intent.createChooser(intent, fileInfo.name))
        } catch (e: Exception) {
            Log.e(TAG, "无法打开文件: ${fileInfo.name}", e)
            Toast.makeText(context, context.getString(R.string.error_loading, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    private fun createViewIntent(context: Context, file: File, fileInfo: FileInfo): Intent {
        val ext = fileInfo.getExtension()
        val mimeType = getMimeType(ext)

        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.fromFile(file), mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun getMimeType(extension: String): String {
        return when (extension.lowercase()) {
            "txt", "log", "csv", "tsv", "rtf" -> "text/plain"
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "js", "mjs" -> "application/javascript"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "yaml", "yml" -> "text/yaml"
            "md", "mdx", "markdown", "rst" -> "text/markdown"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            "bmp" -> "image/bmp"
            "ico" -> "image/x-icon"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "m4a", "aac" -> "audio/mp4"
            "flac" -> "audio/flac"
            "mp4" -> "video/mp4"
            "avi" -> "video/x-msvideo"
            "mkv" -> "video/x-matroska"
            "mov" -> "video/quicktime"
            "wmv" -> "video/x-ms-wmv"
            "webm" -> "video/webm"
            "zip" -> "application/zip"
            "tar" -> "application/x-tar"
            "gz" -> "application/gzip"
            "bz2" -> "application/x-bzip2"
            "xz" -> "application/x-xz"
            "7z" -> "application/x-7z-compressed"
            "rar" -> "application/vnd.rar"
            "pdf" -> "application/pdf"
            "doc", "docx" -> "application/msword"
            "xls", "xlsx" -> "application/vnd.ms-excel"
            "ppt", "pptx" -> "application/vnd.ms-powerpoint"
            "apk" -> "application/vnd.android.package-archive"
            "java", "kt", "kts", "py", "rb", "go", "rs", "c", "cpp", "h", "hpp" -> "text/x-source"
            "sh", "bash", "zsh" -> "application/x-shellscript"
            else -> "application/octet-stream"
        }
    }

    fun getCacheDir(context: Context): File {
        val dir = File(context.cacheDir, CACHE_DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun clearCache(context: Context) {
        val cacheDir = File(context.cacheDir, CACHE_DIR_NAME)
        if (cacheDir.exists()) {
            cacheDir.listFiles()?.forEach { it.delete() }
        }
    }

    fun clearOldCache(context: Context, maxAgeMs: Long = 7 * 24 * 60 * 60 * 1000L) {
        val cacheDir = File(context.cacheDir, CACHE_DIR_NAME)
        if (!cacheDir.exists()) return

        val now = System.currentTimeMillis()
        cacheDir.listFiles()?.forEach { file ->
            if (now - file.lastModified() > maxAgeMs) {
                file.delete()
            }
        }
    }
}
