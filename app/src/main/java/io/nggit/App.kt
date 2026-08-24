/**
 * 应用全局入口类，继承自 Application，作为整个应用的生命周期管理者和核心基础设施提供者。
 * 本类负责在应用启动时初始化所有必要的全局组件，包括 OkHttp 网络客户端、GitHub API 实例、
 * 本地存储路径、网络代理配置、全局崩溃日志记录机制以及 Android 通知渠道的创建。
 * 同时定义了 OAuth 认证所需的客户端标识、重定向地址、权限范围以及 GitHub API 基础地址等常量，
 * 为应用内所有网络请求和身份验证流程提供统一的配置来源。
 * 通过 companion object 暴露单例实例，确保应用全局范围内可以便捷地访问这些共享资源。
 * 崩溃处理机制能够在应用发生未捕获异常时，自动将详细的崩溃信息（包括时间戳、异常类型、
 * 错误消息、完整堆栈以及设备信息）写入本地日志文件，便于后续调试和问题排查。
 */
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

/**
 * 应用主类，负责全局初始化和提供应用级别的单例访问点。
 * 在 Android 应用生命周期中，该类最先被创建，用于设置全局状态和初始化各子系统。
 */
class App : Application() {

    /**
     * 伴生对象，存放应用级别的常量和单例引用。
     * 通过 companion object 可以在不实例化 App 类的情况下访问这些成员，
     * 便于在应用各处获取全局配置和实例。
     */
    companion object {
        /**
         * 应用全局单例实例，在 onCreate 中赋值，供整个应用生命周期内使用。
         * 使用 lateinit 延迟初始化，确保在 Application 创建后才可访问。
         */
        lateinit var instance: App
            private set

        /** GitHub OAuth 应用的客户端 ID，用于身份验证流程中标识本应用 */
        const val CLIENT_ID = "Ov23liHvV3pKNTQ1G3Dt"

        /** OAuth 认证完成后的重定向地址，采用自定义 URI scheme 以便应用内拦截回调 */
        const val REDIRECT_URI = "gk://login"

        /** OAuth 授权范围，定义本应用请求的 GitHub 资源访问权限，涵盖代码仓库、状态、部署、工作流、用户信息和通知等 */
        const val OAUTH_SCOPES = "repo,repo:status,repo_deployment,public_repo,workflow,user,notifications"

        /** GitHub REST API 的基础 URL 地址，所有 API 请求均以此为前缀 */
        const val API_BASE = "https://api.github.com"

        /** 通知渠道 ID，用于标识应用内同步相关的通知类别，在 Android 8.0 及以上版本中创建通知时使用 */
        const val NOTIFICATION_CHANNEL_ID = "ng_sync"
    }

    /** OkHttp 网络客户端实例，配置了超时、认证拦截器和代理等，供所有网络请求使用 */
    lateinit var okHttpClient: OkHttpClient
        private set

    /** GitHub API 封装实例，基于 OkHttp 客户端构建，提供对 GitHub REST API 的便捷调用接口 */
    lateinit var githubApi: GitHubApi
        private set

    /**
     * 应用生命周期回调方法，在应用进程首次创建时由系统调用。
     * 依次完成以下初始化工作：设置全局单例引用、注册崩溃处理器、
     * 初始化本地存储路径、加载代理配置、创建网络客户端、
     * 实例化 GitHub API 对象以及创建通知渠道。
     * 任何初始化步骤失败都不会导致应用崩溃，而是通过错误对话框提示用户。
     */
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

    /**
     * 设置全局未捕获异常处理器，用于在应用发生崩溃时捕获异常并记录详细的崩溃日志。
     * 该方法会保存系统默认的异常处理器，以便在自定义处理完成后将异常继续传递给系统默认处理器，
     * 确保应用能够按照系统预期的方式终止进程并可能触发崩溃报告。
     */
    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                saveCrashLog(throwable)
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * 将崩溃信息保存到本地日志文件中，包含崩溃时间、异常类型、错误消息、完整堆栈追踪
     * 以及设备的 Android 版本和型号信息。日志文件存储在应用私有的 logs 目录下，
     * 文件名以时间戳命名以便于按时间顺序查看和管理多个崩溃日志。
     * 该方法内部进行异常捕获，确保日志写入失败不会影响崩溃处理流程。
     *
     * @param throwable 导致崩溃的异常对象
     */
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

    /**
     * 初始化并配置 OkHttp 网络客户端实例。
     * 设置连接、读取和写入超时时间均为 30 秒，启用连接失败自动重试。
     * 在调试模式下添加日志拦截器以输出完整的请求和响应信息。
     * 同时注册自定义拦截器，负责为每个请求注入 GitHub OAuth 认证令牌、
     * 设置必要的请求头（Accept 和 User-Agent），以及在配置了代理时
     * 将 GitHub 相关请求通过代理服务器转发。
     * 最终将构建好的 OkHttpClient 实例赋值给全局成员变量供应用内所有网络请求使用。
     */
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

    /**
     * 初始化 GitHub API 封装实例，使用已配置好的 OkHttp 客户端构建。
     * 该实例将作为应用与 GitHub REST API 交互的统一入口，
     * 封装了仓库管理、用户信息、通知等各类 API 调用方法。
     */
    private fun initApi() {
        githubApi = GitHubApi(okHttpClient)
    }

    /**
     * 创建 Android 通知渠道，仅在 Android 8.0（API 26）及以上版本执行。
     * 该渠道用于应用同步相关的通知推送，设置为低重要性级别以避免对用户产生过多打扰。
     * 通知渠道一旦创建，在应用卸载前会持续存在，后续更新应用时不会重复创建。
     */
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
