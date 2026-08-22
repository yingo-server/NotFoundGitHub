package io.nggit.ui.profile

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import io.nggit.R
import io.nggit.auth.AuthActivity
import io.nggit.auth.AuthManager
import io.nggit.service.ProxyConfig

class ProfileFragment : Fragment() {

    private lateinit var avatarImg: ImageView
    private lateinit var nameText: TextView
    private lateinit var loginText: TextView
    private lateinit var logoutBtn: Button
    private lateinit var proxyToggle: Switch
    private lateinit var proxyUrlInput: EditText
    private lateinit var proxySaveBtn: Button
    private lateinit var proxyCard: LinearLayout
    private lateinit var proxyArrow: ImageView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        avatarImg = view.findViewById(R.id.profile_avatar)
        nameText = view.findViewById(R.id.profile_name)
        loginText = view.findViewById(R.id.profile_login)
        logoutBtn = view.findViewById(R.id.logout_btn)
        proxyToggle = view.findViewById(R.id.proxy_toggle)
        proxyUrlInput = view.findViewById(R.id.proxy_url_input)
        proxySaveBtn = view.findViewById(R.id.proxy_save_btn)
        proxyCard = view.findViewById(R.id.proxy_card)
        proxyArrow = view.findViewById(R.id.proxy_arrow)

        loadUserInfo()
        setupProxy()

        logoutBtn.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("退出登录")
                .setMessage("确定要退出当前账号吗？")
                .setPositiveButton(requireContext().getString(R.string.permission_exit)) { _, _ ->
                    AuthManager.logout(requireContext())
                    startActivity(Intent(requireContext(), AuthActivity::class.java))
                    requireActivity().finish()
                }
                .setNegativeButton(requireContext().getString(R.string.cancel), null)
                .show()
        }

        view.findViewById<View>(R.id.proxy_header).setOnClickListener {
            if (proxyCard.visibility == View.VISIBLE) {
                proxyCard.visibility = View.GONE
                proxyArrow.rotation = 0f
            } else {
                proxyCard.visibility = View.VISIBLE
                proxyArrow.rotation = 180f
            }
        }
    }

    private fun loadUserInfo() {
        val login = AuthManager.getUserLogin() ?: "未登录"
        val name = AuthManager.getUserName() ?: login
        val avatarUrl = AuthManager.getAvatarUrl()

        nameText.text = name
        loginText.text = login

        if (avatarUrl != null) {
            com.github.bumptech.glide.Glide.with(this)
                .load(avatarUrl)
                .placeholder(R.drawable.ic_user)
                .circleCrop()
                .into(avatarImg)
        }
    }

    private fun setupProxy() {
        proxyToggle.isChecked = ProxyConfig.isEnabled()
        proxyUrlInput.setText(ProxyConfig.getRawUrl())

        proxyToggle.setOnCheckedChangeListener { _, isChecked ->
            ProxyConfig.setEnabled(isChecked)
            Toast.makeText(requireContext(), if (isChecked) requireContext().getString(R.string.proxy_enabled) else requireContext().getString(R.string.proxy_disabled), Toast.LENGTH_SHORT).show()
        }

        proxySaveBtn.setOnClickListener {
            val url = proxyUrlInput.text.toString().trim()
            if (url.isNotEmpty()) {
                ProxyConfig.setUrl(url)
                Toast.makeText(requireContext(), requireContext().getString(R.string.proxy_saved), Toast.LENGTH_SHORT).show()
            }
        }
    }
}
