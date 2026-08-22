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

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        enabled = prefs?.getBoolean(KEY_ENABLED, false) ?: false
        url = prefs?.getString(KEY_URL, DEFAULT_PROXY) ?: DEFAULT_PROXY
    }

    fun isEnabled(): Boolean = enabled

    fun getProxyUrl(): String? {
        return if (enabled && url.isNotEmpty()) {
            if (url.endsWith("/")) url else "$url/"
        } else null
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        prefs?.edit()?.putBoolean(KEY_ENABLED, value)?.apply()
    }

    fun setUrl(value: String) {
        url = if (value.endsWith("/")) value else "$value/"
        prefs?.edit()?.putString(KEY_URL, url)?.apply()
    }

    fun getRawUrl(): String = url

    fun buildProxiedUrl(originalUrl: String): String {
        if (!enabled || url.isEmpty()) return originalUrl
        val base = if (url.endsWith("/")) url else "$url/"
        return "$base${java.net.URLEncoder.encode(originalUrl, "UTF-8")}"
    }
}
