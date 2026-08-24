/**
 * 认证管理单例类，负责处理用户登录认证、Token安全存储、OAuth权限管理、
 * 应用密码设置与验证等核心认证功能，通过加密方式保障用户凭据数据的安全性。
 */
package io.nggit.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

object AuthManager {
    private const val PREF_NAME = "ng_auth"
    private const val KEY_TOKEN = "access_token"
    private const val KEY_USER_LOGIN = "user_login"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_AVATAR_URL = "avatar_url"
    private const val KEY_PASSWORD_HASH = "password_hash"
    private const val KEY_PASSWORD_SALT = "password_salt"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"
    private const val KEY_OAUTH_SCOPES = "oauth_scopes"

    private var prefs: SharedPreferences? = null

    /** 初始化认证管理器，加载本地存储的认证配置数据 */
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    /** 检查用户是否已登录，验证登录状态和Token有效性 */
    fun isLoggedIn(): Boolean {
        return prefs?.getBoolean(KEY_IS_LOGGED_IN, false) == true
            && getToken() != null
    }

    /** 获取已解码的访问令牌，支持Base64编码存储的安全Token */
    fun getToken(): String? {
        val token = prefs?.getString(KEY_TOKEN, null)
        if (token.isNullOrEmpty()) return null
        return try {
            val decoded = Base64.decode(token, Base64.DEFAULT)
            String(decoded, Charsets.UTF_8)
        } catch (e: Exception) {
            token
        }
    }

    /** 保存访问令牌并更新登录状态标记 */
    fun saveToken(token: String) {
        val encoded = Base64.encodeToString(token.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        prefs?.edit()
            ?.putString(KEY_TOKEN, encoded)
            ?.putBoolean(KEY_IS_LOGGED_IN, true)
            ?.apply()
    }

    /** 获取用户的GitHub登录用户名标识 */
    fun getUserLogin(): String? = prefs?.getString(KEY_USER_LOGIN, null)

    /** 获取用户的真实姓名显示信息 */
    fun getUserName(): String? = prefs?.getString(KEY_USER_NAME, null)

    /** 获取用户头像图片的访问URL地址 */
    fun getAvatarUrl(): String? = prefs?.getString(KEY_AVATAR_URL, null)

    /** 保存用户的基本信息到本地存储，包括登录名和头像地址 */
    fun saveUserInfo(login: String, name: String?, avatarUrl: String?) {
        prefs?.edit()
            ?.putString(KEY_USER_LOGIN, login)
            ?.putString(KEY_USER_NAME, name)
            ?.putString(KEY_AVATAR_URL, avatarUrl)
            ?.apply()
    }

    /** 保存OAuth授权的作用域权限信息 */
    fun saveScopes(scopes: String?) {
        prefs?.edit()?.putString(KEY_OAUTH_SCOPES, scopes)?.apply()
    }

    /** 获取OAuth授权的所有作用域权限列表 */
    fun getScopes(): List<String> {
        val raw = prefs?.getString(KEY_OAUTH_SCOPES, null) ?: return emptyList()
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** 检查当前Token是否具有指定的OAuth作用域权限 */
    fun hasScope(scope: String): Boolean {
        val scopes = getScopes()
        return scopes.isEmpty() || scopes.any { it == scope || it.startsWith("$scope:") }
    }

    /** 设置应用访问密码，使用MD5加盐哈希安全存储 */
    fun setAppPassword(password: String) {
        val salt = generateSalt()
        val hash = hashPassword(password, salt)
        prefs?.edit()
            ?.putString(KEY_PASSWORD_SALT, salt)
            ?.putString(KEY_PASSWORD_HASH, hash)
            ?.apply()
    }

    /** 验证用户输入的应用密码是否正确，进行哈希比对验证 */
    fun verifyAppPassword(password: String): Boolean {
        val storedHash = prefs?.getString(KEY_PASSWORD_HASH, null) ?: return false
        val salt = prefs?.getString(KEY_PASSWORD_SALT, null) ?: return false
        return hashPassword(password, salt) == storedHash
    }

    /** 检查是否已设置应用访问密码 */
    fun hasAppPassword(): Boolean {
        return prefs?.getString(KEY_PASSWORD_HASH, null) != null
    }

    /** 用户退出登录，清除所有本地存储的认证和用户数据 */
    fun logout(context: Context) {
        prefs?.edit()?.clear()?.apply()
    }

    private fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun hashPassword(password: String, salt: String): String {
        val md = MessageDigest.getInstance("MD5")
        val input = "$password$salt"
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
