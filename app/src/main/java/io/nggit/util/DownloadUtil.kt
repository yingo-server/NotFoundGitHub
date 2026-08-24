/**
 * 文件下载工具类，封装 OkHttp 实现文件下载功能，支持进度回调和代理配置，
 * 提供直接下载和代理下载两种方式。
 * 基于应用全局的 OkHttpClient 实例，配置 60 秒连接和读取超时，
 * 支持重定向跟随，并提供实时下载进度通知能力。
 */
package io.nggit.util

import io.nggit.App
import io.nggit.service.ProxyConfig
import okhttp3.*
import java.io.*
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 文件下载单例对象，提供 HTTP 文件下载和文本内容获取功能。
 * 适用于从 Git 仓库或远程服务器下载文件、克隆仓库等场景。
 */
object DownloadUtil {

    /**
     * 下载专用的 OkHttpClient 实例，延迟初始化。
     * 在应用全局 OkHttpClient 基础上配置 60 秒超时和重定向策略。
     */
    private val client: OkHttpClient by lazy {
        App.instance.okHttpClient.newBuilder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /**
     * 直接下载文件到本地指定路径。
     * 通过 HTTP GET 请求获取文件内容，写入目标文件，支持实时进度回调。
     *
     * @param url 文件下载地址
     * @param destFile 目标保存文件
     * @param onProgress 可选的进度回调函数，参数为(已下载字节数, 总字节数)
     * @return 下载成功返回 true，失败或异常返回 false
     */
    fun download(url: String, destFile: File, onProgress: ((Long, Long) -> Unit)? = null): Boolean {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "NGGit/1.0")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return false

            val body = response.body ?: return false
            val totalBytes = body.contentLength()

            val inputStream = body.byteStream()
            val outputStream = BufferedOutputStream(FileOutputStream(destFile))

            val buffer = ByteArray(8192)
            var downloadedBytes = 0L
            var bytesRead: Int

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                downloadedBytes += bytesRead
                onProgress?.invoke(downloadedBytes, totalBytes)
            }

            outputStream.close()
            inputStream.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 通过代理服务器下载文件。
     * 如果配置了代理地址，会将原始 URL 编码后拼接到代理地址后面，
     * 实现请求转发；未配置代理时直接调用普通下载。
     *
     * @param originalUrl 原始文件下载地址
     * @param destFile 目标保存文件
     * @param onProgress 可选的进度回调函数，参数为(已下载字节数, 总字节数)
     * @return 下载成功返回 true，失败或异常返回 false
     */
    fun downloadWithProxy(originalUrl: String, destFile: File, onProgress: ((Long, Long) -> Unit)? = null): Boolean {
        val proxyUrl = ProxyConfig.getProxyUrl()
        val finalUrl = if (proxyUrl != null) {
            "${proxyUrl}${URLEncoder.encode(originalUrl, "UTF-8")}"
        } else {
            originalUrl
        }
        return download(finalUrl, destFile, onProgress)
    }

    /**
     * 下载 URL 内容并以字符串形式返回。
     * 适用于下载文本文件、配置文件或 API 响应等场景。
     * 请求失败或响应不成功时返回 null。
     *
     * @param url 下载地址
     * @return 下载的文本内容，失败时返回 null
     */
    fun downloadToString(url: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "NGGit/1.0")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) response.body?.string() else null
        } catch (e: Exception) {
            null
        }
    }
}
