package io.nggit.auth

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

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

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun isLoggedIn(): Boolean {
        return prefs?.getBoolean(KEY_IS_LOGGED_IN, false) == true
            && getToken() != null
    }

    fun getToken(): String? {
        val token = prefs?.getString(KEY_TOKEN, null)
        if (token.isNullOrEmpty()) return null
        return try {
            val encrypted = Base64.getDecoder().decode(token)
            String(encrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            token
        }
    }

    fun saveToken(token: String) {
        val encoded = Base64.getEncoder().encodeToString(token.toByteArray(Charsets.UTF_8))
        prefs?.edit()
            ?.putString(KEY_TOKEN, encoded)
            ?.putBoolean(KEY_IS_LOGGED_IN, true)
            ?.apply()
    }

    fun getUserLogin(): String? = prefs?.getString(KEY_USER_LOGIN, null)

    fun getUserName(): String? = prefs?.getString(KEY_USER_NAME, null)

    fun getAvatarUrl(): String? = prefs?.getString(KEY_AVATAR_URL, null)

    fun saveUserInfo(login: String, name: String?, avatarUrl: String?) {
        prefs?.edit()
            ?.putString(KEY_USER_LOGIN, login)
            ?.putString(KEY_USER_NAME, name)
            ?.putString(KEY_AVATAR_URL, avatarUrl)
            ?.apply()
    }

    fun saveScopes(scopes: String?) {
        prefs?.edit()?.putString(KEY_OAUTH_SCOPES, scopes)?.apply()
    }

    fun getScopes(): List<String> {
        val raw = prefs?.getString(KEY_OAUTH_SCOPES, null) ?: return emptyList()
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun hasScope(scope: String): Boolean {
        val scopes = getScopes()
        return scopes.isEmpty() || scopes.any { it == scope || it.startsWith("$scope:") }
    }

    fun setAppPassword(password: String) {
        val salt = generateSalt()
        val hash = hashPassword(password, salt)
        prefs?.edit()
            ?.putString(KEY_PASSWORD_SALT, salt)
            ?.putString(KEY_PASSWORD_HASH, hash)
            ?.apply()
    }

    fun verifyAppPassword(password: String): Boolean {
        val storedHash = prefs?.getString(KEY_PASSWORD_HASH, null) ?: return false
        val salt = prefs?.getString(KEY_PASSWORD_SALT, null) ?: return false
        return hashPassword(password, salt) == storedHash
    }

    fun hasAppPassword(): Boolean {
        return prefs?.getString(KEY_PASSWORD_HASH, null) != null
    }

    fun logout(context: Context) {
        prefs?.edit()?.clear()?.apply()
    }

    private fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }

    private fun hashPassword(password: String, salt: String): String {
        val md = MessageDigest.getInstance("MD5")
        val input = "$password$salt"
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
