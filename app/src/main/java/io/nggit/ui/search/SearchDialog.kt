package io.nggit.ui.search

import android.app.Dialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.nggit.App
import io.nggit.R
import io.nggit.auth.AuthManager
import io.nggit.model.RepoInfo
import io.nggit.ui.main.MainActivity
import java.util.concurrent.Executors

class SearchDialog : DialogFragment() {

    private lateinit var searchInput: EditText
    private lateinit var tabOwn: TextView
    private lateinit var tabGlobal: TextView
    private lateinit var resultsList: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var progressBar: ProgressBar

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val api = App.instance.githubApi
    private val token get() = AuthManager.getToken() ?: ""

    private var allOwnRepos: List<RepoInfo> = emptyList()
    private var isOwnTab = true

    private var onRepoSelected: ((String, String) -> Unit)? = null

    companion object {
        fun newInstance(): SearchDialog = SearchDialog()
    }

    fun setOnRepoSelectedListener(listener: (String, String) -> Unit) {
        onRepoSelected = listener
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_search, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        searchInput = view.findViewById(R.id.search_input)
        tabOwn = view.findViewById(R.id.radio_own)
        tabGlobal = view.findViewById(R.id.radio_others)
        resultsList = view.findViewById(R.id.search_results)
        emptyText = view.findViewById(R.id.search_empty)
        progressBar = view.findViewById(R.id.file_loader)

        resultsList.layoutManager = LinearLayoutManager(requireContext())

        tabOwn.setOnClickListener {
            isOwnTab = true
            updateTabStyle()
            performSearch()
        }

        tabGlobal.setOnClickListener {
            isOwnTab = false
            updateTabStyle()
            performSearch()
        }

        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else false
        }

        updateTabStyle()
        loadOwnRepos()
    }

    private fun updateTabStyle() {
        val activeColor = requireContext().getColor(R.color.text_primary)
        val inactiveColor = requireContext().getColor(R.color.text_hint)

        tabOwn.setTextColor(if (isOwnTab) activeColor else inactiveColor)
        tabOwn.setTypeface(null, if (isOwnTab) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        tabGlobal.setTextColor(if (!isOwnTab) activeColor else inactiveColor)
        tabGlobal.setTypeface(null, if (!isOwnTab) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
    }

    private fun loadOwnRepos() {
        executor.execute {
            try {
                allOwnRepos = api.listUserRepos(token)
            } catch (e: Exception) {
                mainHandler.post {
                    showEmpty(requireContext().getString(R.string.search_load_fail, e.message))
                }
            }
        }
    }

    private fun performSearch() {
        val query = searchInput.text.toString().trim()
        if (query.isEmpty()) {
            if (isOwnTab) {
                showOwnRepos()
            } else {
                showEmpty(requireContext().getString(R.string.search_empty_hint))
            }
            return
        }
        if (isOwnTab) searchOwn(query) else searchGlobal(query)
    }

    private fun showOwnRepos() {
        if (allOwnRepos.isEmpty()) {
            showEmpty(requireContext().getString(R.string.search_empty_hint))
            return
        }
        showResults(allOwnRepos)
    }

    private fun searchOwn(query: String) {
        showLoading()
        executor.execute {
            val results = allOwnRepos.filter {
                it.name.contains(query, ignoreCase = true) ||
                    it.fullName.contains(query, ignoreCase = true) ||
                    (it.description != null && it.description.contains(query, ignoreCase = true))
            }.distinctBy { it.id }

            mainHandler.post {
                hideLoading()
                if (results.isEmpty()) {
                    showEmpty(requireContext().getString(R.string.search_no_result))
                } else {
                    showResults(results)
                }
            }
        }
    }

    private fun searchGlobal(query: String) {
        showLoading()
        executor.execute {
            try {
                val results = api.searchPublicRepos(query)
                mainHandler.post {
                    hideLoading()
                    if (results.isEmpty()) {
                        showEmpty(requireContext().getString(R.string.search_no_result))
                    } else {
                        showResults(results)
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    hideLoading()
                    showEmpty(requireContext().getString(R.string.search_fail, e.message))
                }
            }
        }
    }

    private fun showResults(repos: List<RepoInfo>) {
        val adapter = RepoAdapter(repos) { repo ->
            val mainActivity = activity as? MainActivity
            if (mainActivity != null) {
                mainActivity.enterRepo(repo.getOwnerLogin(), repo.name, repo.defaultBranch, false)
                dismiss()
            } else {
                onRepoSelected?.invoke(repo.getOwnerLogin(), repo.name)
                dismiss()
            }
        }
        resultsList.adapter = adapter
        resultsList.visibility = View.VISIBLE
        emptyText.visibility = View.GONE
    }

    private fun showLoading() {
        progressBar.visibility = View.VISIBLE
        resultsList.visibility = View.GONE
        emptyText.visibility = View.GONE
    }

    private fun hideLoading() {
        progressBar.visibility = View.GONE
    }

    private fun showEmpty(msg: String) {
        emptyText.text = msg
        emptyText.visibility = View.VISIBLE
        resultsList.visibility = View.GONE
        progressBar.visibility = View.GONE
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mainHandler.removeCallbacksAndMessages(null)
        executor.shutdownNow()
    }

    class RepoAdapter(
        private val repos: List<RepoInfo>,
        private val onClick: (RepoInfo) -> Unit
    ) : RecyclerView.Adapter<RepoAdapter.RepoViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RepoViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_repo, parent, false)
            return RepoViewHolder(view)
        }

        override fun onBindViewHolder(holder: RepoViewHolder, position: Int) {
            holder.bind(repos[position])
        }

        override fun getItemCount(): Int = repos.size

        inner class RepoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val repoName: TextView = itemView.findViewById(R.id.repo_name)
            private val repoDesc: TextView = itemView.findViewById(R.id.repo_desc)

            fun bind(repo: RepoInfo) {
                repoName.text = repo.fullName
                repoDesc.text = repo.description ?: ""
                repoDesc.visibility = if (repo.description.isNullOrEmpty()) View.GONE else View.VISIBLE
                itemView.setOnClickListener { onClick(repo) }
            }
        }
    }
}
