/**
 * 代理配置管理单例类，负责应用程序的代理服务器设置管理功能，包括代理开关控制、
 * 代理地址配置、代理URL构建等操作，通过SharedPreferences持久化存储代理配置信息。
 */
package io.nggit.service

import android.content.Context
import android.content.SharedPreferences

object ProxyConfig {
    private const val PREF_NAME = "ng_proxy"
    private const val KEY_ENABLED = "proxy_enabled"
    private const val KEY_URL = "proxy_url"
    private const val DEFAULT_PROXY = "https://web.ksx.qzz.io/"

    private var prefs: SharedPreferences? = null
    @Volatile private var enabled: Boolean = false
    @Volatile private var url: String = DEFAULT_PROXY

    /** 初始化代理配置管理器，从本地存储加载代理设置信息 */
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        enabled = prefs?.getBoolean(KEY_ENABLED, false) ?: false
        url = prefs?.getString(KEY_URL, DEFAULT_PROXY) ?: DEFAULT_PROXY
    }

    /** 获取代理功能的启用状态，返回是否开启代理 */
    fun isEnabled(): Boolean = enabled

    /** 获取完整的代理服务地址URL，未启用时返回空值 */
    fun getProxyUrl(): String? {
        return if (enabled && url.isNotEmpty()) {
            if (url.endsWith("/")) url else "$url/"
        } else null
    }

    /** 设置代理功能的启用状态并持久化保存配置 */
    fun setEnabled(value: Boolean) {
        enabled = value
        prefs?.edit()?.putBoolean(KEY_ENABLED, value)?.apply()
    }

    /** 设置代理服务器的地址URL并持久化保存配置 */
    fun setUrl(value: String) {
        url = if (value.endsWith("/")) value else "$value/"
        prefs?.edit()?.putString(KEY_URL, url)?.apply()
    }

    /** 获取代理服务器的原始地址字符串，不进行格式处理 */
    fun getRawUrl(): String = url

    /** 将原始GitHub API地址转换为经过代理服务器的完整访问地址 */
    fun buildProxiedUrl(originalUrl: String): String {
        if (!enabled || url.isEmpty()) return originalUrl
        val base = if (url.endsWith("/")) url else "$url/"
        return "$base${java.net.URLEncoder.encode(originalUrl, "UTF-8")}"
    }
}
