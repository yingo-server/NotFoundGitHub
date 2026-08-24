/**
 * 仓库搜索对话框，支持搜索用户自己的仓库和GitHub公共仓库，提供标签页切换、异步搜索和结果列表展示，点击搜索结果可直接进入对应仓库。
 * 该对话框实现了完整的仓库搜索功能，包括本地仓库的快速过滤和远程GitHub仓库的在线搜索。
 * 通过标签页设计，用户可以在个人仓库和公共仓库之间快速切换，提升搜索效率。
 * 搜索结果以列表形式展示，支持点击交互，方便用户快速进入目标仓库进行操作。
 * 采用异步处理机制，避免在搜索过程中阻塞主线程，保证界面流畅响应。
 * 同时支持空状态提示和加载状态指示，提供良好的用户体验。
 */
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
        /**
         * 创建搜索对话框的新实例，用于初始化对话框对象并返回给调用方。
         */
        fun newInstance(): SearchDialog = SearchDialog()
    }

    /**
     * 设置仓库选择监听器，当用户从搜索结果中选择仓库时触发回调。
     * 参数listener为回调函数，接收仓库所有者和仓库名称作为参数。
     */
    fun setOnRepoSelectedListener(listener: (String, String) -> Unit) {
        onRepoSelected = listener
    }

    /**
     * 创建对话框的视图层级，通过布局填充器加载搜索对话框的布局文件。
     * 返回对话框的根视图对象，用于后续的视图初始化和交互处理。
     */
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_search, container, false)
    }

    /**
     * 在视图创建完成后执行初始化操作，设置搜索输入框、标签页、结果列表等组件的事件监听器。
     * 初始化布局管理器，设置标签页点击事件和搜索按钮事件，同时加载用户个人仓库列表。
     */
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

    /**
     * 更新标签页的视觉样式，根据当前选中的标签页设置对应的文本颜色和字体粗细。
     * 活动标签页使用主文本颜色和粗体字，非活动标签页使用提示颜色和普通字体。
     */
    private fun updateTabStyle() {
        val activeColor = requireContext().getColor(R.color.text_primary)
        val inactiveColor = requireContext().getColor(R.color.text_hint)

        tabOwn.setTextColor(if (isOwnTab) activeColor else inactiveColor)
        tabOwn.setTypeface(null, if (isOwnTab) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        tabGlobal.setTextColor(if (!isOwnTab) activeColor else inactiveColor)
        tabGlobal.setTypeface(null, if (!isOwnTab) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
    }

    /**
     * 异步加载用户的个人仓库列表，通过GitHub API获取当前登录用户的所有仓库数据。
     * 在后台线程执行网络请求，成功后将结果存储到allOwnRepos变量中，失败则显示错误信息。
     */
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

    /**
     * 执行搜索操作，根据当前选中的标签页和搜索关键词执行相应的搜索逻辑。
     * 如果搜索框为空，则显示个人仓库列表或空状态提示；否则执行本地搜索或全局搜索。
     */
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

    /**
     * 显示用户个人仓库列表，如果仓库列表为空则显示空状态提示，否则将仓库列表显示在结果列表中。
     */
    private fun showOwnRepos() {
        if (allOwnRepos.isEmpty()) {
            showEmpty(requireContext().getString(R.string.search_empty_hint))
            return
        }
        showResults(allOwnRepos)
    }

    /**
     * 在用户个人仓库中执行本地搜索，根据关键词过滤仓库名称、全称和描述信息。
     * 在后台线程执行过滤操作，完成后在主线程显示搜索结果或空状态提示。
     */
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

    /**
     * 在GitHub公共仓库中执行远程搜索，通过API接口搜索匹配关键词的公共仓库。
     * 在后台线程执行网络请求，成功后显示搜索结果，失败则显示错误提示信息。
     */
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

    /**
     * 显示搜索结果列表，创建适配器并设置到RecyclerView中，同时隐藏空状态提示和加载指示器。
     * 点击列表项时触发回调，将仓库信息传递给监听器或直接进入仓库。
     */
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

    /**
     * 显示加载状态指示器，隐藏结果列表和空状态提示，提示用户正在进行搜索操作。
     */
    private fun showLoading() {
        progressBar.visibility = View.VISIBLE
        resultsList.visibility = View.GONE
        emptyText.visibility = View.GONE
    }

    /**
     * 隐藏加载状态指示器，用于搜索完成后或搜索失败时取消加载状态显示。
     */
    private fun hideLoading() {
        progressBar.visibility = View.GONE
    }

    /**
     * 显示空状态提示信息，隐藏结果列表和加载指示器，向用户展示相应的提示文本。
     * 参数msg为要显示的提示信息文本。
     */
    private fun showEmpty(msg: String) {
        emptyText.text = msg
        emptyText.visibility = View.VISIBLE
        resultsList.visibility = View.GONE
        progressBar.visibility = View.GONE
    }

    /**
     * 在对话框开始显示时设置窗口布局参数，将宽度设置为匹配父容器，高度设置为包裹内容。
     */
    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    /**
     * 在视图销毁时清理资源，移除主线程处理器中的所有回调消息，并关闭后台执行器服务。
     */
    override fun onDestroyView() {
        super.onDestroyView()
        mainHandler.removeCallbacksAndMessages(null)
        executor.shutdownNow()
    }

    /**
     * 仓库列表适配器，负责将仓库数据绑定到列表项视图中，处理列表项的创建和绑定操作。
     * 参数repos为仓库信息列表，onClick为仓库项点击回调函数。
     */
    class RepoAdapter(
        private val repos: List<RepoInfo>,
        private val onClick: (RepoInfo) -> Unit
    ) : RecyclerView.Adapter<RepoAdapter.RepoViewHolder>() {

        /**
         * 创建新的视图持有者对象，通过布局填充器加载仓库列表项的布局文件。
         * 参数parent为父视图容器，viewType为视图类型标识，返回创建的视图持有者实例。
         */
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RepoViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_repo, parent, false)
            return RepoViewHolder(view)
        }

        /**
         * 绑定仓库数据到视图持有者，将指定位置的仓库信息设置到对应的视图组件中。
         * 参数holder为视图持有者对象，position为当前项在列表中的位置索引。
         */
        override fun onBindViewHolder(holder: RepoViewHolder, position: Int) {
            holder.bind(repos[position])
        }

        /**
         * 获取仓库列表的总数量，用于RecyclerView计算列表长度和滚动范围。
         * 返回仓库信息列表的大小。
         */
        override fun getItemCount(): Int = repos.size

        /**
         * 仓库列表项视图持有者，负责管理单个仓库列表项的视图组件和数据绑定。
         */
        inner class RepoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val repoName: TextView = itemView.findViewById(R.id.repo_name)
            private val repoDesc: TextView = itemView.findViewById(R.id.repo_desc)

            /**
             * 绑定仓库数据到视图组件，设置仓库名称和描述信息，并处理描述为空的显示逻辑。
             * 参数repo为要绑定的仓库信息对象。
             */
            fun bind(repo: RepoInfo) {
                repoName.text = repo.fullName
                repoDesc.text = repo.description ?: ""
                repoDesc.visibility = if (repo.description.isNullOrEmpty()) View.GONE else View.VISIBLE
                itemView.setOnClickListener { onClick(repo) }
            }
        }
    }
}
