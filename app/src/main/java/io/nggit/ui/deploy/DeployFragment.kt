package io.nggit.ui.deploy

import android.app.AlertDialog
import android.app.ProgressDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.nggit.App
import io.nggit.R
import io.nggit.auth.AuthManager
import io.nggit.model.RepoInfo
import java.util.concurrent.Executors

class DeployFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var loader: ProgressBar
    private lateinit var emptyText: TextView

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val api = App.instance.githubApi
    private val token get() = AuthManager.getToken() ?: ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_deploy, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = view.findViewById(R.id.deploy_list)
        loader = view.findViewById(R.id.deploy_loader)
        emptyText = view.findViewById(R.id.deploy_empty)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        loadRepos()
    }

    private fun loadRepos() {
        loader.visibility = View.VISIBLE
        executor.execute {
            try {
                val repos = api.listUserRepos(token).sortedByDescending { it.hasPages }
                mainHandler.post {
                    loader.visibility = View.GONE
                    if (repos.isEmpty()) {
                        emptyText.visibility = View.VISIBLE
                    } else {
                        showDeployList(repos)
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    loader.visibility = View.GONE
                    emptyText.text = requireContext().getString(R.string.error_loading)
                    emptyText.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun showDeployList(repos: List<RepoInfo>) {
        val adapter = DeployAdapter(repos) { repo ->
            openDeployPage(repo)
        }
        recyclerView.adapter = adapter
        recyclerView.visibility = View.VISIBLE
        emptyText.visibility = View.GONE
    }

    private fun openDeployPage(repo: RepoInfo) {
        if (!isAdded) return
        val progressDialog = ProgressDialog(requireContext()).apply {
            setMessage(requireContext().getString(R.string.pages_config_loading))
            setCancelable(false)
            show()
        }

        executor.execute {
            try {
                val pagesInfo = api.getPages(token, repo.getOwnerLogin(), repo.name)
                mainHandler.post {
                    if (isAdded) {
                        progressDialog.dismiss()
                        showDeployDialog(repo, pagesInfo != null, pagesInfo?.cname)
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    if (isAdded) {
                        progressDialog.dismiss()
                        Toast.makeText(requireContext(), requireContext().getString(R.string.pages_config_fail), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun showDeployDialog(repo: RepoInfo, hasPages: Boolean, cname: String?) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("${repo.name} - " + requireContext().getString(R.string.pages_deploy))

        if (hasPages) {
            val isMainSite = repo.name.lowercase() == "${repo.getOwnerLogin().lowercase()}.github.io"
            val defaultUrl = "https://${repo.getOwnerLogin()}.github.io${if (isMainSite) "" else "/${repo.name}"}"

            val message = buildString {
                appendLine(requireContext().getString(R.string.pages_enabled))
                appendLine()
                appendLine(requireContext().getString(R.string.pages_default_url, defaultUrl))
                if (cname != null) appendLine(requireContext().getString(R.string.pages_custom_url, cname))
            }
            builder.setMessage(message)

            builder.setPositiveButton(requireContext().getString(R.string.pages_copy_link)) { _, _ ->
                val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("pages_url", defaultUrl)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), requireContext().getString(R.string.pages_link_copied), Toast.LENGTH_SHORT).show()
            }

            builder.setNegativeButton(requireContext().getString(R.string.cancel), null)

            if (cname == null) {
                builder.setNeutralButton(requireContext().getString(R.string.pages_set_domain)) { _, _ ->
                    showDomainInputDialog(repo)
                }
            }
        } else {
            builder.setMessage(requireContext().getString(R.string.pages_disabled))
            builder.setPositiveButton(requireContext().getString(R.string.pages_enable_btn)) { _, _ ->
                enablePages(repo)
            }
            builder.setNegativeButton(requireContext().getString(R.string.cancel), null)
        }

        builder.show()
    }

    private fun showDomainInputDialog(repo: RepoInfo) {
        val input = EditText(requireContext()).apply {
            hint = requireContext().getString(R.string.pages_domain_hint)
            setPadding(60, 40, 60, 20)
        }

        AlertDialog.Builder(requireContext())
            .setTitle(requireContext().getString(R.string.pages_set_domain))
            .setView(input)
            .setPositiveButton(requireContext().getString(R.string.pages_bind)) { _, _ ->
                val domain = input.text.toString().trim()
                if (domain.isNotEmpty()) {
                    setCustomDomain(repo, domain)
                }
            }
            .setNegativeButton(requireContext().getString(R.string.cancel), null)
            .show()
    }

    private fun enablePages(repo: RepoInfo) {
        if (!isAdded) return
        val progressDialog = ProgressDialog(requireContext()).apply {
            setMessage(requireContext().getString(R.string.pages_deploying))
            setCancelable(false)
            show()
        }

        executor.execute {
            try {
                val success = api.enablePages(token, repo.getOwnerLogin(), repo.name, repo.defaultBranch)
                mainHandler.post {
                    if (isAdded) {
                        progressDialog.dismiss()
                        Toast.makeText(requireContext(), if (success) requireContext().getString(R.string.pages_deployed) else requireContext().getString(R.string.pages_deploy_fail), Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    if (isAdded) {
                        progressDialog.dismiss()
                        Toast.makeText(requireContext(), requireContext().getString(R.string.pages_deploy_fail) + ": ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun setCustomDomain(repo: RepoInfo, domain: String) {
        if (!isAdded) return
        val progressDialog = ProgressDialog(requireContext()).apply {
            setMessage(requireContext().getString(R.string.pages_binding))
            setCancelable(false)
            show()
        }

        executor.execute {
            try {
                val success = api.setCustomDomain(token, repo.getOwnerLogin(), repo.name, domain)
                mainHandler.post {
                    if (isAdded) {
                        progressDialog.dismiss()
                        Toast.makeText(requireContext(), if (success) requireContext().getString(R.string.pages_bound) else requireContext().getString(R.string.pages_bind_fail), Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    if (isAdded) {
                        progressDialog.dismiss()
                        Toast.makeText(requireContext(), requireContext().getString(R.string.pages_bind_fail) + ": ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    inner class DeployAdapter(
        private val repos: List<RepoInfo>,
        private val onClick: (RepoInfo) -> Unit
    ) : RecyclerView.Adapter<DeployAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.deploy_repo_name)
            val status: TextView = view.findViewById(R.id.deploy_status)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_deploy, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val repo = repos[position]
            holder.name.text = repo.name
            if (repo.hasPages) {
                holder.status.text = requireContext().getString(R.string.pages_enabled)
                holder.status.setTextColor(holder.itemView.context.getColor(R.color.success))
            } else {
                holder.status.text = requireContext().getString(R.string.pages_disabled)
                holder.status.setTextColor(holder.itemView.context.getColor(R.color.text_hint))
            }
            holder.itemView.setOnClickListener { onClick(repo) }
        }

        override fun getItemCount() = repos.size
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mainHandler.removeCallbacksAndMessages(null)
        executor.shutdownNow()
    }
}
