package io.nggit.ui.file

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import io.nggit.App
import io.nggit.R
import io.nggit.auth.AuthManager
import io.nggit.model.FileInfo
import io.nggit.model.RepoInfo
import io.nggit.sync.SyncManager
import io.nggit.ui.main.MainActivity
import io.nggit.util.StoragePath
import java.io.File
import java.util.concurrent.Executors

class FileListFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var loader: ProgressBar
    private lateinit var swipeRefresh: SwipeRefreshLayout

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val api = App.instance.githubApi
    private val token get() = AuthManager.getToken() ?: ""

    private var adapter: FileAdapter? = null
    private var fileListType: String = "repos"

    companion object {
        fun newInstance(type: String): FileListFragment {
            val fragment = FileListFragment()
            val args = Bundle()
            args.putString("type", type)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_file_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fileListType = arguments?.getString("type") ?: "repos"

        recyclerView = view.findViewById(R.id.file_list_container) as? RecyclerView
            ?: RecyclerView(requireContext()).also {
                (view.findViewById<ViewGroup>(R.id.file_list_container))?.addView(it)
            }
        emptyView = view.findViewById(R.id.empty_view)
        loader = view.findViewById(R.id.file_loader)
        swipeRefresh = view.findViewById(R.id.file_swipe_refresh)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        swipeRefresh.setOnRefreshListener { refresh() }
        swipeRefresh.setColorSchemeResources(R.color.primary)

        when (fileListType) {
            "repos" -> loadRepos()
            "starred" -> loadStarredRepos()
            "files" -> loadFiles()
        }
    }

    fun refresh() {
        when (fileListType) {
            "repos" -> loadRepos()
            "starred" -> loadStarredRepos()
            "files" -> loadFiles()
        }
    }

    private fun loadRepos() {
        showLoading()
        executor.execute {
            try {
                val repos = api.listUserRepos(token)
                mainHandler.post {
                    hideLoading()
                    if (repos.isEmpty()) {
                        showEmpty(requireContext().getString(R.string.file_list_empty))
                    } else {
                        showRepoList(repos, false)
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    hideLoading()
                    showEmpty(requireContext().getString(R.string.file_list_load_fail, e.message))
                }
            }
        }
    }

    private fun loadStarredRepos() {
        showLoading()
        executor.execute {
            try {
                val repos = api.listStarredRepos(token)
                mainHandler.post {
                    hideLoading()
                    if (repos.isEmpty()) {
                        showEmpty(requireContext().getString(R.string.file_list_empty_starred))
                    } else {
                        showRepoList(repos, true)
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    hideLoading()
                    showEmpty(requireContext().getString(R.string.file_list_load_fail, e.message))
                }
            }
        }
    }

    private fun loadFiles() {
        val mainActivity = activity as? MainActivity ?: return
        val owner = mainActivity.currentRepoOwner
        val repo = mainActivity.currentRepoName
        val branch = mainActivity.currentBranch
        val path = mainActivity.currentPath

        showLoading()

        val repoPath = StoragePath.getRepoPath(owner, repo, mainActivity.isStarredRepo)
        val localDir = File(repoPath, path)

        if (localDir.exists() && localDir.isDirectory) {
            val items = listLocalFiles(localDir)
            mainHandler.post {
                hideLoading()
                if (items.isEmpty()) {
                    showEmpty(requireContext().getString(R.string.file_dir_empty))
                } else {
                    showFileList(items)
                }
            }
        } else {
            executor.execute {
                try {
                    val files = api.getContents(token, owner, repo, path, branch)
                    mainHandler.post {
                        hideLoading()
                        if (files.isEmpty()) {
                            showEmpty(requireContext().getString(R.string.file_dir_empty))
                        } else {
                            showFileList(files)
                        }
                    }
                } catch (e: Exception) {
                    mainHandler.post {
                        hideLoading()
                        showEmpty(requireContext().getString(R.string.file_list_load_fail, ""))
                    }
                }
            }
        }
    }

    private fun listLocalFiles(dir: File): List<FileInfo> {
        val items = mutableListOf<FileInfo>()
        dir.listFiles()?.forEach { file ->
            if (file.name == ".ng_commit.json") return@forEach
            if (file.name.startsWith(".")) return@forEach

            val type = if (file.isDirectory) "dir" else "file"
            val sha = io.nggit.util.HashUtil.md5(file.absolutePath)

            items.add(FileInfo(
                name = file.name,
                path = file.relativeTo(File(StoragePath.getBasePath())).path,
                sha = sha,
                size = file.length(),
                type = type,
                downloadUrl = file.absolutePath
            ))
        }
        return items.sortedWith(compareBy<FileInfo> { !it.isDir() }.thenBy { it.name.lowercase() })
    }

    private fun showRepoList(repos: List<RepoInfo>, isStarred: Boolean) {
        adapter = FileAdapter(requireContext(), repos, isStarred) { repo ->
            (activity as? MainActivity)?.enterRepo(
                repo.getOwnerLogin(), repo.name, repo.defaultBranch, isStarred
            )
        }
        recyclerView.adapter = adapter
        recyclerView.visibility = View.VISIBLE
        emptyView.visibility = View.GONE
    }

    private fun showFileList(files: List<FileInfo>) {
        val mainActivity = activity as? MainActivity
        adapter = FileAdapter(requireContext(), files) { fileInfo ->
            if (fileInfo.isDir()) {
                mainActivity?.enterFolder(fileInfo.name)
            } else {
                mainActivity?.openFile(fileInfo)
            }
        }
        adapter?.setOnItemLongClickListener { fileInfo, position ->
            showFileContextMenu(fileInfo)
        }
        recyclerView.adapter = adapter
        recyclerView.visibility = View.VISIBLE
        emptyView.visibility = View.GONE
    }

    private fun showFileContextMenu(fileInfo: FileInfo) {
        val mainActivity = activity as? MainActivity ?: return
        val isOwnRepo = mainActivity.currentRepoOwner == AuthManager.getUserLogin()

        val renameStr = requireContext().getString(R.string.file_rename)
        val bookmarkStr = requireContext().getString(R.string.file_bookmark)
        val copyLinkStr = requireContext().getString(R.string.file_copy_link)
        val downloadStr = requireContext().getString(R.string.file_download)
        val deleteStr = requireContext().getString(R.string.file_delete)

        val options = mutableListOf<String>()

        options.add(renameStr)
        options.add(bookmarkStr)

        if (fileInfo.isDir().not()) {
            options.add(copyLinkStr)
            options.add(downloadStr)
        }

        if (isOwnRepo) {
            options.add(deleteStr)
        }

        AlertDialog.Builder(requireContext())
            .setTitle(fileInfo.name)
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    renameStr -> renameItem(fileInfo)
                    bookmarkStr -> addToBookmarks(fileInfo)
                    copyLinkStr -> copyLink(fileInfo)
                    downloadStr -> downloadFile(fileInfo)
                    deleteStr -> deleteItem(fileInfo)
                }
            }
            .show()
    }

    private fun renameItem(fileInfo: FileInfo) {
        val input = android.widget.EditText(requireContext()).apply {
            setText(fileInfo.name)
            setPadding(60, 40, 60, 20)
        }

        AlertDialog.Builder(requireContext())
            .setTitle(requireContext().getString(R.string.file_rename))
            .setView(input)
            .setPositiveButton(requireContext().getString(R.string.confirm)) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty() && newName != fileInfo.name) {
                    performRename(fileInfo, newName)
                }
            }
            .setNegativeButton(requireContext().getString(R.string.cancel), null)
            .show()
    }

    private fun performRename(fileInfo: FileInfo, newName: String) {
        val mainActivity = activity as? MainActivity ?: return
        val uploadManager = io.nggit.sync.UploadManager(requireContext())

        val oldPath = fileInfo.path
        val pathParts = oldPath.split("/")
        val parentPath = if (pathParts.size > 1) pathParts.dropLast(1).joinToString("/") else ""
        val newPath = if (parentPath.isEmpty()) newName else "$parentPath/$newName"

        val repoPath = StoragePath.getRepoPath(
            mainActivity.currentRepoOwner, mainActivity.currentRepoName, mainActivity.isStarredRepo
        )
        val localFile = File(repoPath, fileInfo.path)

        try {
            if (localFile.exists()) {
                val content = if (fileInfo.isDir()) "" else localFile.readText(Charsets.UTF_8)
                uploadManager.renameItem(
                    mainActivity.currentRepoOwner, mainActivity.currentRepoName, mainActivity.currentBranch,
                    oldPath, newPath, fileInfo.sha, content
                ) { success ->
                    Toast.makeText(requireContext(), if (success) requireContext().getString(R.string.file_rename_success) else requireContext().getString(R.string.file_rename_fail), Toast.LENGTH_SHORT).show()
                    if (success) refresh()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), requireContext().getString(R.string.file_rename_fail), Toast.LENGTH_SHORT).show()
        }
    }

    private fun addToBookmarks(fileInfo: FileInfo) {
        Toast.makeText(requireContext(), requireContext().getString(R.string.file_bookmark_added), Toast.LENGTH_SHORT).show()
    }

    private fun copyLink(fileInfo: FileInfo) {
        val mainActivity = activity as? MainActivity ?: return
        val owner = mainActivity.currentRepoOwner
        val repo = mainActivity.currentRepoName
        val branch = mainActivity.currentBranch
        val type = if (fileInfo.isDir()) "tree" else "blob"
        val url = "https://github.com/$owner/$repo/$type/$branch/${fileInfo.path}"

        val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("repo_link", url)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), requireContext().getString(R.string.pages_link_copied), Toast.LENGTH_SHORT).show()
    }

    private fun downloadFile(fileInfo: FileInfo) {
        Toast.makeText(requireContext(), requireContext().getString(R.string.file_start_download), Toast.LENGTH_SHORT).show()
    }

    private fun deleteItem(fileInfo: FileInfo) {
        val mainActivity = activity as? MainActivity ?: return

        AlertDialog.Builder(requireContext())
            .setTitle(requireContext().getString(R.string.delete_title))
            .setMessage(requireContext().getString(R.string.file_delete_confirm, fileInfo.name))
            .setPositiveButton(requireContext().getString(R.string.file_delete)) { _, _ ->
                try {
                    val uploadManager = io.nggit.sync.UploadManager(requireContext())
                    uploadManager.deleteFile(
                        mainActivity.currentRepoOwner, mainActivity.currentRepoName,
                        mainActivity.currentBranch, fileInfo.path, fileInfo.sha
                    ) { success ->
                        Toast.makeText(requireContext(), if (success) requireContext().getString(R.string.delete_success) else requireContext().getString(R.string.delete_fail), Toast.LENGTH_SHORT).show()
                        if (success) refresh()
                    }
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), requireContext().getString(R.string.delete_fail), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(requireContext().getString(R.string.cancel), null)
            .show()
    }

    private fun showLoading() {
        loader.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        emptyView.visibility = View.GONE
        swipeRefresh.isRefreshing = false
    }

    private fun hideLoading() {
        loader.visibility = View.GONE
        swipeRefresh.isRefreshing = false
    }

    private fun showEmpty(message: String) {
        emptyView.text = message
        emptyView.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mainHandler.removeCallbacksAndMessages(null)
        executor.shutdownNow()
    }
}
