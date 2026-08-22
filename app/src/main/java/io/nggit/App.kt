package io.nggit

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import io.nggit.service.GitHubApi
import io.nggit.service.ProxyConfig
import io.nggit.util.ErrorDialog
import io.nggit.util.StoragePath
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import io.nggit.R

class App : Application() {

    companion object {
        lateinit var instance: App
            private set

        const val CLIENT_ID = "Ov23liHvV3pKNTQ1G3Dt"
        const val REDIRECT_URI = "gk://login"
        const val OAUTH_SCOPES = "repo,repo:status,repo_deployment,public_repo,workflow,user,notifications"
        const val API_BASE = "https://api.github.com"
        const val NOTIFICATION_CHANNEL_ID = "ng_sync"
    }

    lateinit var okHttpClient: OkHttpClient
        private set
    lateinit var githubApi: GitHubApi
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        setupCrashHandler()

        try {
            StoragePath.init(this)
        } catch (e: Exception) {
            ErrorDialog.show(this, getString(R.string.storage_init_fail), e)
        }

        try {
            ProxyConfig.init(this)
        } catch (e: Exception) {
            ErrorDialog.show(this, getString(R.string.proxy_init_fail), e)
        }

        initOkHttp()
        initApi()
        createNotificationChannel()
    }

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                saveCrashLog(throwable)
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun saveCrashLog(throwable: Throwable) {
        try {
            val logDir = File(filesDir, "logs")
            if (!logDir.exists()) logDir.mkdirs()

            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            val logFile = File(logDir, "crash_$timestamp.txt")

            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))

            logFile.writeText(buildString {
                appendLine("崩溃时间: $timestamp")
                appendLine("异常类型: ${throwable.javaClass.name}")
                appendLine("错误信息: ${throwable.message}")
                appendLine("\n堆栈:")
                appendLine(sw.toString())
                appendLine("\n设备信息:")
                appendLine("  Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine("  设备: ${Build.MANUFACTURER} ${Build.MODEL}")
            })
        } catch (_: Exception) {}
    }

    private fun initOkHttp() {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

        if (BuildConfig.DEBUG) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            builder.addInterceptor(logging)
        }

        builder.addInterceptor { chain ->
            val original = chain.request()
            val token = io.nggit.auth.AuthManager.getToken()
            val request = if (token != null) {
                original.newBuilder()
                    .header("Authorization", "token $token")
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "NGGit/1.0")
                    .build()
            } else {
                original.newBuilder()
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "NGGit/1.0")
                    .build()
            }

            val proxyUrl = ProxyConfig.getProxyUrl()
            val finalRequest = if (proxyUrl != null && original.url.toString().contains("github")) {
                val proxied = request.newBuilder()
                    .url("${proxyUrl}${java.net.URLEncoder.encode(original.url.toString(), "UTF-8")}")
                    .build()
                proxied
            } else {
                request
            }

            chain.proceed(finalRequest)
        }

        okHttpClient = builder.build()
    }

    private fun initApi() {
        githubApi = GitHubApi(okHttpClient)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.channel_sync),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_sync_desc)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
