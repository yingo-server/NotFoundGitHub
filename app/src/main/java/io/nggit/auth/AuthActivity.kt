package io.nggit.auth

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import io.nggit.App
import io.nggit.R
import io.nggit.ui.main.MainActivity
import io.nggit.util.ErrorDialog
import io.nggit.util.StoragePath
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

class AuthActivity : AppCompatActivity() {

    private lateinit var tokenInput: EditText
    private lateinit var loginBtn: Button
    private lateinit var oauthBtn: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var passwordInput: EditText
    private lateinit var passwordSection: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)

        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = android.graphics.Color.TRANSPARENT

        AuthManager.init(this)

        initViews()

        if (AuthManager.isLoggedIn()) {
            showPasswordOrMain()
            return
        }

        handleOAuthCallback(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleOAuthCallback(intent)
    }

    private fun initViews() {
        tokenInput = findViewById(R.id.token_input)
        loginBtn = findViewById(R.id.login_btn)
        oauthBtn = findViewById(R.id.oauth_btn)
        progressBar = findViewById(R.id.progress_bar)
        statusText = findViewById(R.id.status_text)
        passwordInput = findViewById(R.id.password_input)
        passwordSection = findViewById(R.id.password_section)

        loginBtn.setOnClickListener { loginWithToken() }
        oauthBtn.setOnClickListener { startOAuth() }
        findViewById<Button>(R.id.password_confirm_btn).setOnClickListener { confirmPassword() }
    }

    private fun handleOAuthCallback(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "gk" && data.host == "login") {
            val uri = data
            val code = uri.getQueryParameter("code")
            if (code != null) {
                showLoading(true, getString(R.string.auth_verifying_oauth))
                exchangeToken(code)
            }
        }
    }

    private fun startOAuth() {
        val clientId = App.CLIENT_ID
        val redirectUri = URLEncoder.encode(App.REDIRECT_URI, "UTF-8")
        val scopes = URLEncoder.encode(App.OAUTH_SCOPES, "UTF-8")
        val url = "https://github.com/login/oauth/authorize?client_id=$clientId&redirect_uri=$redirectUri&scope=$scopes"

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }

    private fun exchangeToken(code: String) {
        val body = FormBody.Builder()
            .add("client_id", App.CLIENT_ID)
            .add("code", code)
            .add("redirect_uri", App.REDIRECT_URI)
            .build()

        val request = Request.Builder()
            .url("https://github.com/login/oauth/access_token")
            .post(body)
            .header("Accept", "application/json")
            .build()

        App.instance.okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    showLoading(false)
                    Toast.makeText(this@AuthActivity, getString(R.string.auth_network_error, e.message), Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val bodyStr = response.body?.string() ?: throw Exception(getString(R.string.auth_empty_response))
                    val json = JSONObject(bodyStr)
                    val accessToken = json.optString("access_token", null)
                        ?: throw Exception(json.optString("error_description", getString(R.string.auth_fail)))

                    AuthManager.saveToken(accessToken)

                    val scopesHeader = response.header("X-OAuth-Scopes")
                    AuthManager.saveScopes(scopesHeader)

                    fetchUserInfo()
                } catch (e: Exception) {
                    runOnUiThread {
                        showLoading(false)
                        Toast.makeText(this@AuthActivity, getString(R.string.auth_fail_detail, e.message), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun loginWithToken() {
        val token = tokenInput.text.toString().trim()
        if (token.isEmpty()) {
            Toast.makeText(this, getString(R.string.auth_input_token), Toast.LENGTH_SHORT).show()
            return
        }

        showLoading(true, getString(R.string.auth_verifying))

        val request = Request.Builder()
            .url("https://api.github.com/user")
            .header("Authorization", "token $token")
            .header("Accept", "application/vnd.github.v3+json")
            .build()

        App.instance.okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    showLoading(false)
                    Toast.makeText(this@AuthActivity, getString(R.string.auth_network_error, e.message), Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string()
                    if (bodyStr == null) {
                        runOnUiThread {
                            showLoading(false)
                            ErrorDialog.show(this@AuthActivity, getString(R.string.auth_login_fail), getString(R.string.auth_server_empty))
                        }
                        return
                    }
                    val json = JSONObject(bodyStr)
                    val login = json.getString("login")
                    val name = json.optString("name", login)
                    val avatarUrl = json.optString("avatar_url", "")

                    AuthManager.saveToken(token)
                    AuthManager.saveUserInfo(login, name, avatarUrl)

                    val scopesHeader = response.header("X-OAuth-Scopes")
                    AuthManager.saveScopes(scopesHeader)

                    runOnUiThread {
                        showLoading(false)
                        showPasswordSetup()
                    }
                } else {
                    runOnUiThread {
                        showLoading(false)
                        ErrorDialog.show(this@AuthActivity, getString(R.string.auth_token_invalid), getString(R.string.auth_server_error, response.code))
                    }
                }
            }
        })
    }

    private fun fetchUserInfo() {
        val token = AuthManager.getToken() ?: return

        val request = Request.Builder()
            .url("https://api.github.com/user")
            .header("Authorization", "token $token")
            .header("Accept", "application/vnd.github.v3+json")
            .build()

        App.instance.okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    showLoading(false)
                    showPasswordSetup()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string()
                    if (bodyStr != null) {
                        val json = JSONObject(bodyStr)
                        val login = json.getString("login")
                        val name = json.optString("name", login)
                        val avatarUrl = json.optString("avatar_url", "")
                        AuthManager.saveUserInfo(login, name, avatarUrl)
                    }
                }
                runOnUiThread {
                    showLoading(false)
                    showPasswordSetup()
                }
            }
        })
    }

    private fun showPasswordSetup() {
        if (AuthManager.hasAppPassword()) {
            passwordSection.visibility = View.VISIBLE
            tokenInput.visibility = View.GONE
            loginBtn.visibility = View.GONE
            oauthBtn.visibility = View.GONE
            passwordInput.hint = getString(R.string.auth_input_password)
        } else {
            showPasswordSetupDialog()
        }
    }

    private fun showPasswordSetupDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.auth_set_password)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(60, 40, 60, 20)
        }

        val inputConfirm = EditText(this).apply {
            hint = getString(R.string.auth_confirm_password)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(60, 20, 60, 40)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(input)
            addView(inputConfirm)
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.auth_set_password_title))
            .setMessage(getString(R.string.auth_set_password_desc))
            .setView(container)
            .setCancelable(false)
            .setPositiveButton(getString(R.string.auth_confirm)) { _, _ ->
                val pw = input.text.toString()
                val pwConfirm = inputConfirm.text.toString()
                if (pw.length < 4) {
                    Toast.makeText(this, getString(R.string.auth_password_short), Toast.LENGTH_SHORT).show()
                    showPasswordSetupDialog()
                    return@setPositiveButton
                }
                if (pw != pwConfirm) {
                    Toast.makeText(this, getString(R.string.auth_password_mismatch), Toast.LENGTH_SHORT).show()
                    showPasswordSetupDialog()
                    return@setPositiveButton
                }
                AuthManager.setAppPassword(pw)
                goToMain()
            }
            .setNegativeButton(getString(R.string.auth_skip)) { _, _ ->
                goToMain()
            }
            .show()
    }

    private fun showPasswordOrMain() {
        if (AuthManager.hasAppPassword()) {
            passwordSection.visibility = View.VISIBLE
            tokenInput.visibility = View.GONE
            loginBtn.visibility = View.GONE
            oauthBtn.visibility = View.GONE
            passwordInput.hint = getString(R.string.auth_input_app_password)
        } else {
            goToMain()
        }
    }

    private fun confirmPassword() {
        val password = passwordInput.text.toString()
        if (password.isEmpty()) {
            Toast.makeText(this, getString(R.string.auth_please_input_password), Toast.LENGTH_SHORT).show()
            return
        }
        if (AuthManager.verifyAppPassword(password)) {
            goToMain()
        } else {
            Toast.makeText(this, getString(R.string.auth_password_wrong), Toast.LENGTH_SHORT).show()
        }
    }

    private fun goToMain() {
        try {
            StoragePath.ensureDirectories()
        } catch (e: Exception) {
            ErrorDialog.show(this, getString(R.string.auth_storage_init_fail), e)
        }
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun showLoading(show: Boolean, message: String = getString(R.string.loading)) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        statusText.visibility = if (show) View.VISIBLE else View.GONE
        statusText.text = message
        loginBtn.isEnabled = !show
        oauthBtn.isEnabled = !show
    }
}
