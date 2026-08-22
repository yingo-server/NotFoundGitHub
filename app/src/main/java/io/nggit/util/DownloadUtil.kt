package io.nggit.util

import io.nggit.App
import io.nggit.service.ProxyConfig
import okhttp3.*
import java.io.*
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object DownloadUtil {

    private val client: OkHttpClient by lazy {
        App.instance.okHttpClient.newBuilder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

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

    fun downloadWithProxy(originalUrl: String, destFile: File, onProgress: ((Long, Long) -> Unit)? = null): Boolean {
        val proxyUrl = ProxyConfig.getProxyUrl()
        val finalUrl = if (proxyUrl != null) {
            "${proxyUrl}${URLEncoder.encode(originalUrl, "UTF-8")}"
        } else {
            originalUrl
        }
        return download(finalUrl, destFile, onProgress)
    }

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
