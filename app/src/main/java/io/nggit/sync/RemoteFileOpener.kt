/**
 * 远程文件打开工具类，负责从GitHub仓库下载远程文件到本地缓存并调用系统应用打开。
 * 支持多种文件格式的MIME类型自动映射，提供base64解码和原始内容获取两种下载方式。
 * 内置缓存管理机制，支持自动清理过期缓存文件，避免占用过多存储空间。
 * 通过单线程执行器异步下载文件，避免阻塞主线程导致界面卡顿。
 * 适用于需要预览或打开GitHub仓库中各类文件的场景，如代码文件、文档、图片等。
 */
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

    /**
     * 打开远程文件的主入口方法，支持缓存命中直接打开和异步下载后打开两种模式。
     * 若文件已缓存则立即通过系统应用打开，否则在后台线程下载并解码后在主线程打开。
     * 下载失败或打开异常时会通过Toast提示用户具体的错误信息。
     *
     * @param context 上下文对象，用于启动Activity和显示Toast提示
     * @param fileInfo 文件信息对象，包含文件路径、SHA值、名称等元数据
     * @param owner 仓库拥有者用户名或组织名
     * @param repo 仓库名称
     * @param branch 文件所在分支名称
     */
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

    /**
     * 通过原始文件URL直接打开远程文件，适用于已知raw下载地址的场景。
     * 从GitHub API获取文件原始内容，保存到本地缓存目录后调用系统应用打开。
     * 文件名中的特殊字符会被替换为下划线以确保文件系统兼容性。
     *
     * @param context 上下文对象，用于启动Activity和显示Toast提示
     * @param url 文件的原始下载URL地址
     * @param fileName 要保存的本地文件名
     */
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

    /**
     * 从GitHub获取文件内容并解码保存到本地缓存文件，支持三种获取方式依次降级。
     * 首先尝试通过GitHub API获取blob内容并base64解码，失败后尝试通过下载URL获取原始内容，
     * 最后通过contents API获取下载链接再下载原始内容，确保尽可能成功获取文件。
     *
     * @param context 上下文对象
     * @param fileInfo 文件信息对象，包含文件路径、SHA值等元数据
     * @param owner 仓库拥有者用户名或组织名
     * @param repo 仓库名称
     * @param branch 文件所在分支名称
     * @param cacheFile 本地缓存文件对象，用于保存下载并解码后的文件内容
     * @return 文件获取并保存成功返回true，全部方式均失败返回false
     */
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

    /**
     * 根据编码方式解码文件内容字符串为字节数组，支持base64和纯文本两种编码格式。
     * base64编码会自动去除内容中的换行符以确保解码正常进行。
     * 若解码过程中发生非法参数异常则降级为UTF-8纯文本解析并记录错误日志。
     *
     * @param content 待解码的文件内容字符串
     * @param encoding 编码方式，支持"base64"和"plain"两种值
     * @return 解码后的字节数组，解码失败时返回null
     */
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

    /**
     * 通过系统Intent启动文件打开操作，将缓存文件传递给合适的系统应用处理。
     * 打开异常时会记录错误日志并通过Toast向用户展示具体的错误原因。
     *
     * @param context 上下文对象，用于启动文件打开的Activity
     * @param cacheFile 已下载到本地的缓存文件对象
     * @param fileInfo 文件信息对象，包含文件名等元数据
     */
    private fun launchFile(context: Context, cacheFile: File, fileInfo: FileInfo) {
        try {
            val intent = createViewIntent(context, cacheFile, fileInfo)
            context.startActivity(Intent.createChooser(intent, fileInfo.name))
        } catch (e: Exception) {
            Log.e(TAG, "无法打开文件: ${fileInfo.name}", e)
            Toast.makeText(context, context.getString(R.string.error_loading, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 创建用于查看文件的Intent对象，设置文件URI和对应的MIME类型。
     * 自动根据文件扩展名匹配合适的MIME类型，并授予读取权限以确保系统应用可以访问文件。
     *
     * @param context 上下文对象
     * @param file 本地缓存文件对象
     * @param fileInfo 文件信息对象，用于获取文件扩展名
     * @return 配置好数据和类型的ACTION_VIEW Intent对象
     */
    private fun createViewIntent(context: Context, file: File, fileInfo: FileInfo): Intent {
        val ext = fileInfo.getExtension()
        val mimeType = getMimeType(ext)

        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.fromFile(file), mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * 根据文件扩展名获取对应的MIME类型字符串，支持文本、图片、音视频、压缩包等常见格式。
     * 未识别的扩展名默认返回application/octet-stream二进制流类型。
     * 扩展名比较时忽略大小写以提高匹配的容错性。
     *
     * @param extension 文件扩展名（不含点号），例如"pdf"、"jpg"等
     * @return 对应的MIME类型字符串，未匹配时返回通用二进制流类型
     */
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

    /**
     * 获取远程文件缓存目录的File对象，若目录不存在则自动创建。
     * 缓存目录位于应用内部缓存目录下的remote_cache子目录中。
     *
     * @param context 上下文对象，用于获取应用缓存目录路径
     * @return 远程文件缓存目录的File对象
     */
    fun getCacheDir(context: Context): File {
        val dir = File(context.cacheDir, CACHE_DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * 清除所有远程文件缓存，删除缓存目录下的全部文件以释放存储空间。
     * 仅删除文件不删除缓存目录本身，确保后续缓存写入不受影响。
     *
     * @param context 上下文对象，用于获取应用缓存目录路径
     */
    fun clearCache(context: Context) {
        val cacheDir = File(context.cacheDir, CACHE_DIR_NAME)
        if (cacheDir.exists()) {
            cacheDir.listFiles()?.forEach { it.delete() }
        }
    }

    /**
     * 清除超过指定时长的过期缓存文件，默认保留最近七天的缓存。
     * 通过比较文件最后修改时间与当前时间的差值判断是否过期，过期文件将被删除。
     * 适用于定期清理老旧缓存以控制缓存占用空间的场景。
     *
     * @param context 上下文对象，用于获取应用缓存目录路径
     * @param maxAgeMs 缓存文件最大保留时长（毫秒），默认为七天（604800000毫秒）
     */
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
