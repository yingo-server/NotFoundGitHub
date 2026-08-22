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
import android.widget.RadioButton
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.nggit.App
import io.nggit.R
import io.nggit.auth.AuthManager
import io.nggit.model.RepoInfo
import io.nggit.ui.file.FileAdapter
import io.nggit.ui.main.MainActivity
import java.util.concurrent.Executors

class SearchDialog : DialogFragment() {

    private lateinit var searchInput: EditText
    private lateinit var radioOwn: RadioButton
    private lateinit var radioOthers: RadioButton
    private lateinit var resultsList: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var progressBar: ProgressBar

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val api = App.instance.githubApi
    private val token get() = AuthManager.getToken() ?: ""

    private var allOwnRepos: List<RepoInfo> = emptyList()
    private var allStarredRepos: List<RepoInfo> = emptyList()

    companion object {
        fun newInstance(): SearchDialog = SearchDialog()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_search, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        searchInput = view.findViewById(R.id.search_input)
        radioOwn = view.findViewById(R.id.radio_own)
        radioOthers = view.findViewById(R.id.radio_others)
        resultsList = view.findViewById(R.id.search_results)
        emptyText = view.findViewById(R.id.search_empty)
        progressBar = view.findViewById(R.id.file_loader)

        resultsList.layoutManager = LinearLayoutManager(requireContext())

        radioOwn.setOnCheckedChangeListener { _, _ -> performSearch() }
        radioOthers.setOnCheckedChangeListener { _, _ -> performSearch() }

        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else false
        }

        loadOwnRepos()
    }

    private fun loadOwnRepos() {
        executor.execute {
            try {
                allOwnRepos = api.listUserRepos(token)
                allStarredRepos = api.listStarredRepos(token)
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
            showEmpty(requireContext().getString(R.string.search_empty_hint))
            return
        }
        if (radioOwn.isChecked) searchOwn(query) else searchGlobal(query)
    }

    private fun searchOwn(query: String) {
        showLoading()
        executor.execute {
            val results = (allOwnRepos + allStarredRepos).filter {
                it.name.contains(query, ignoreCase = true) ||
                it.fullName.contains(query, ignoreCase = true)
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
        val adapter = FileAdapter(requireContext(), repos, false) { repo ->
            val r = repo as RepoInfo
            val mainActivity = activity as? MainActivity
            mainActivity?.enterRepo(r.getOwnerLogin(), r.name, r.defaultBranch, false)
            dismiss()
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
}
