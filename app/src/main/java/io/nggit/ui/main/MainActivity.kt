package io.nggit.ui.main

import android.Manifest
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import io.nggit.App
import io.nggit.R
import io.nggit.auth.AuthActivity
import io.nggit.auth.AuthManager
import io.nggit.model.FileInfo
import io.nggit.model.RepoInfo
import io.nggit.sync.UploadManager
import io.nggit.ui.editor.EditorActivity
import io.nggit.ui.file.FileAdapter
import io.nggit.ui.preview.PreviewActivity
import io.nggit.ui.search.SearchDialog
import io.nggit.util.StoragePath
import java.io.File
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PERMISSION_REQUEST = 100
        private val STORAGE_PERMISSIONS = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }

    private lateinit var toolbar: MaterialToolbar
    private lateinit var leftPane: View
    private lateinit var leftPathBar: LinearLayout
    private lateinit var leftBackBtn: ImageButton
    private lateinit var leftForwardBtn: ImageButton
    private lateinit var leftPathText: TextView
    private lateinit var leftSyncBtn: ImageButton
    private lateinit var leftFileList: RecyclerView
    private lateinit var leftEmptyView: TextView
    private lateinit var leftLoader: ProgressBar
    private lateinit var leftSwipe: SwipeRefreshLayout

    private lateinit var rightPane: View
    private lateinit var rightPathBar: LinearLayout
    private lateinit var rightBackBtn: ImageButton
    private lateinit var rightForwardBtn: ImageButton
    private lateinit var rightPathText: TextView
    private lateinit var rightUpBtn: ImageButton
    private lateinit var rightFileList: RecyclerView
    private lateinit var rightEmptyView: TextView
    private lateinit var rightLoader: ProgressBar
    private lateinit var rightSwipe: SwipeRefreshLayout

    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val api get() = App.instance.githubApi
    private val token get() = AuthManager.getToken() ?: ""

    private val leftState = FilePaneState(paneId = 0, isRemote = true)
    private val rightState = FilePaneState(paneId = 1, isRemote = false)
    private var activePane = leftState

    private lateinit var leftAdapter: FileAdapter
    private lateinit var rightAdapter: FileAdapter

    private val repoInfoMap = mutableMapOf<String, RepoInfo>()
    private val repoStarredMap = mutableMapOf<String, Boolean>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        AuthManager.init(this)
        StoragePath.init(this)
        if (!AuthManager.isLoggedIn()) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
            return
        }
        requestPermissions()
        initViews()
        setupLeftPane()
        setupRightPane()
        setupBottomBar()
        loadRepos()
        loadLocalFiles()
    }

    private fun initViews() {
        DragDividerView.leftPaneId = R.id.left_pane
        DragDividerView.rightPaneId = R.id.right_pane
        toolbar = findViewById(R.id.toolbar)
        toolbar.setNavigationOnClickListener { showMoreMenu(toolbar) }
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_search -> { showSearchDialog(); true }
                R.id.action_settings -> { showSettingsDialog(); true }
                else -> false
            }
        }
        leftPane = findViewById(R.id.left_pane)
        leftPathBar = findViewById(R.id.left_path_bar)
        leftBackBtn = findViewById(R.id.left_back_btn)
        leftForwardBtn = findViewById(R.id.left_forward_btn)
        leftPathText = findViewById(R.id.left_path_text)
        leftSyncBtn = findViewById(R.id.left_sync_btn)
        leftFileList = findViewById(R.id.left_file_list)
        leftEmptyView = findViewById(R.id.left_empty_view)
        leftLoader = findViewById(R.id.left_loader)
        leftSwipe = findViewById(R.id.left_swipe)
        rightPane = findViewById(R.id.right_pane)
        rightPathBar = findViewById(R.id.right_path_bar)
        rightBackBtn = findViewById(R.id.right_back_btn)
        rightForwardBtn = findViewById(R.id.right_forward_btn)
        rightPathText = findViewById(R.id.right_path_text)
        rightUpBtn = findViewById(R.id.right_up_btn)
        rightFileList = findViewById(R.id.right_file_list)
        rightEmptyView = findViewById(R.id.right_empty_view)
        rightLoader = findViewById(R.id.right_loader)
        rightSwipe = findViewById(R.id.right_swipe)
    }

    private fun setupLeftPane() {
        leftSwipe.setColorSchemeResources(R.color.accent)
        leftSwipe.setOnRefreshListener {
            if (leftState.repoOwner.isNotEmpty()) loadRemoteFiles(leftState) else loadRepos()
        }
        leftAdapter = FileAdapter(this, emptyList(),
            onItemClick = { file -> onFileClick(leftState, file) },
            onItemLongClick = { file -> onFileLongClick(leftState, file) }
        )
        leftFileList.layoutManager = LinearLayoutManager(this)
        leftFileList.adapter = leftAdapter
        leftPane.setOnClickListener { setActivePane(leftState) }
        leftBackBtn.setOnClickListener { navigateBack(leftState) }
        leftForwardBtn.setOnClickListener { navigateForward(leftState) }
        leftSyncBtn.setOnClickListener { syncToOtherPane(leftState) }
        leftPathText.setOnClickListener { showPathEditDialog(leftState) }
        addDoubleTapRefresh(leftFileList) {
            if (leftState.repoOwner.isNotEmpty()) loadRemoteFiles(leftState) else loadRepos()
        }
    }

    private fun setupRightPane() {
        rightSwipe.setColorSchemeResources(R.color.accent)
        rightSwipe.setOnRefreshListener { loadLocalFiles() }
        rightAdapter = FileAdapter(this, emptyList(),
            onItemClick = { file -> onFileClick(rightState, file) },
            onItemLongClick = { file -> onFileLongClick(rightState, file) }
        )
        rightFileList.layoutManager = LinearLayoutManager(this)
        rightFileList.adapter = rightAdapter
        rightPane.setOnClickListener { setActivePane(rightState) }
        rightBackBtn.setOnClickListener { navigateBack(rightState) }
        rightForwardBtn.setOnClickListener { navigateForward(rightState) }
        rightUpBtn.setOnClickListener { navigateUp(rightState) }
        rightPathText.setOnClickListener { showPathEditDialog(rightState) }
        addDoubleTapRefresh(rightFileList) { loadLocalFiles() }
    }

    private fun setupBottomBar() {
        findViewById<View>(R.id.btn_back).setOnClickListener { navigateBack(activePane) }
        findViewById<View>(R.id.btn_forward).setOnClickListener { navigateForward(activePane) }
        findViewById<View>(R.id.btn_sync).setOnClickListener { syncToOtherPane(activePane) }
        findViewById<View>(R.id.btn_up).setOnClickListener { navigateUp(activePane) }
        findViewById<View>(R.id.btn_upload).setOnClickListener { showUploadDialog() }
        findViewById<View>(R.id.btn_more).setOnClickListener { v -> showMoreMenu(v) }
    }

    private fun setActivePane(pane: FilePaneState) {
        activePane = pane
        leftPane.alpha = if (pane == leftState) 1.0f else 0.85f
        rightPane.alpha = if (pane == rightState) 1.0f else 0.85f
    }

    private fun loadRepos() {
        showLoader(leftState, true)
        executor.execute {
            try {
                val userRepos = api.listUserRepos(token)
                val starredRepos = api.listStarredRepos(token)
                mainHandler.post {
                    showLoader(leftState, false)
                    leftSwipe.isRefreshing = false
                    leftState.history.clear()
                    leftState.history.add("")
                    leftState.historyIndex = 0
                    leftState.currentPath = ""
                    repoInfoMap.clear()
                    repoStarredMap.clear()
                    val allFiles = mutableListOf<FileInfo>()
                    for (repo in userRepos) {
                        repoInfoMap[repo.name] = repo
                        repoStarredMap[repo.name] = false
                        allFiles.add(FileInfo(
                            name = repo.name,
                            path = "${repo.getOwnerLogin()}/${repo.name}",
                            type = "repo",
                            size = repo.size.toLong()
                        ))
                    }
                    for (repo in starredRepos) {
                        if (!repoInfoMap.containsKey(repo.name)) {
                            repoInfoMap[repo.name] = repo
                            repoStarredMap[repo.name] = true
                            allFiles.add(FileInfo(
                                name = repo.name,
                                path = "${repo.getOwnerLogin()}/${repo.name}",
                                type = "repo",
                                size = repo.size.toLong()
                            ))
                        }
                    }
                    leftState.files = allFiles
                    leftAdapter.updateData(allFiles)
                    updatePaneViews(leftState)
                }
            } catch (e: Exception) {
                mainHandler.post {
                    showLoader(leftState, false)
                    leftSwipe.isRefreshing = false
                    showEmpty(leftState, "Failed: ${e.message}")
                }
            }
        }
    }

    fun loadRemoteFiles(pane: FilePaneState) {
        showLoader(pane, true)
        updatePaneViews(pane)
        executor.execute {
            try {
                val files = api.getContents(token, pane.repoOwner, pane.repoName, pane.currentPath, pane.branch)
                mainHandler.post {
                    showLoader(pane, false)
                    if (pane == leftState) leftSwipe.isRefreshing = false else rightSwipe.isRefreshing = false
                    if (files.isEmpty()) {
                        showEmpty(pane, getString(R.string.file_dir_empty))
                    } else {
                        pane.files = sortAndFilter(pane, files)
                        getAdapter(pane).updateData(pane.files)
                        updatePaneViews(pane)
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    showLoader(pane, false)
                    if (pane == leftState) leftSwipe.isRefreshing = false else rightSwipe.isRefreshing = false
                    showEmpty(pane, e.message ?: "Error")
                }
            }
        }
    }

    private fun sortAndFilter(pane: FilePaneState, files: List<FileInfo>): List<FileInfo> {
        var result = files
        if (!pane.showHidden) {
            result = result.filter { !it.name.startsWith(".") }
        }
        return when (pane.sortMode) {
            SortMode.NAME_ASC -> result.sortedWith(compareBy<FileInfo> { !it.isDir() }.thenBy { it.name.lowercase() })
            SortMode.NAME_DESC -> result.sortedWith(compareBy<FileInfo> { !it.isDir() }.thenByDescending { it.name.lowercase() })
            SortMode.SIZE_ASC -> result.sortedWith(compareBy<FileInfo> { !it.isDir() }.thenBy { it.size })
            SortMode.SIZE_DESC -> result.sortedWith(compareBy<FileInfo> { !it.isDir() }.thenByDescending { it.size })
            SortMode.DATE_ASC -> result.sortedWith(compareBy<FileInfo> { !it.isDir() }.thenBy { it.lastModified })
            SortMode.DATE_DESC -> result.sortedWith(compareBy<FileInfo> { !it.isDir() }.thenByDescending { it.lastModified })
        }
    }

    private fun loadLocalFiles() {
        rightSwipe.isRefreshing = false
        updatePaneViews(rightState)
        val basePath = StoragePath.getBasePath()
        val currentDir = if (rightState.currentPath.isEmpty()) basePath
        else File(basePath, rightState.currentPath)
        val items = mutableListOf<FileInfo>()
        currentDir.listFiles()?.forEach { file ->
            val type = if (file.isDirectory) "dir" else "file"
            items.add(FileInfo(
                name = file.name,
                path = file.relativeTo(basePath).path.replace("\\", "/"),
                type = type,
                size = if (file.isFile) file.length() else 0,
                lastModified = file.lastModified()
            ))
        }
        val sorted = sortAndFilter(rightState, items)
        rightState.files = sorted
        showLoader(rightState, false)
        if (sorted.isEmpty()) {
            showEmpty(rightState, getString(R.string.file_dir_empty))
        } else {
            rightEmptyView.visibility = View.GONE
            rightAdapter.updateData(sorted)
        }
        updatePaneViews(rightState)
    }

    private fun onFileClick(pane: FilePaneState, file: FileInfo) {
        setActivePane(pane)
        when {
            file.type == "repo" -> {
                val parts = file.path.split("/")
                if (parts.size == 2) {
                    val repo = repoInfoMap[parts[1]]
                    val isStarred = repoStarredMap[parts[1]] ?: false
                    if (repo != null) openRepo(pane, repo.getOwnerLogin(), parts[1], repo.defaultBranch, isStarred)
                }
            }
            file.isDir() -> {
                pane.pushPath(file.name)
                if (pane.isRemote) loadRemoteFiles(pane) else loadLocalFiles()
            }
            else -> {
                if (pane.isRemote) openRemoteFile(pane, file) else openLocalFile(file)
            }
        }
    }

    private fun onFileLongClick(pane: FilePaneState, file: FileInfo) {
        if (file.type == "repo") return
        setActivePane(pane)
        showFileContextMenu(pane, file)
    }

    fun openRepo(pane: FilePaneState, owner: String, repo: String, branch: String, isStarred: Boolean) {
        pane.repoOwner = owner
        pane.repoName = repo
        pane.branch = branch
        pane.isStarred = isStarred
        pane.isRemote = true
        pane.history.clear()
        pane.history.add("")
        pane.historyIndex = 0
        pane.currentPath = ""
        loadRemoteFiles(pane)
    }

    fun enterRepo(owner: String, repo: String, branch: String, isStarred: Boolean) {
        openRepo(leftState, owner, repo, branch, isStarred)
    }

    fun openRepoByOwner(owner: String, repo: String) {
        val repoInfo = repoInfoMap[repo]
        val isStarred = repoStarredMap[repo] ?: false
        val branch = repoInfo?.defaultBranch ?: "main"
        openRepo(leftState, owner, repo, branch, isStarred)
    }

    private fun openRemoteFile(pane: FilePaneState, file: FileInfo) {
        val ext = file.getExtension()
        if (isMediaFile(ext)) {
            val intent = Intent(this, PreviewActivity::class.java).apply {
                putExtra("file_path", file.path)
                putExtra("file_name", file.name)
                putExtra("repo_owner", pane.repoOwner)
                putExtra("repo_name", pane.repoName)
                putExtra("repo_branch", pane.branch)
                putExtra("file_sha", file.sha)
            }
            startActivity(intent)
        } else {
            val intent = Intent(this, EditorActivity::class.java).apply {
                putExtra("file_path", file.path)
                putExtra("file_name", file.name)
                putExtra("repo_owner", pane.repoOwner)
                putExtra("repo_name", pane.repoName)
                putExtra("repo_branch", pane.branch)
                putExtra("is_starred", pane.isStarred)
                putExtra("file_sha", file.sha)
            }
            startActivity(intent)
        }
    }

    private fun openLocalFile(file: FileInfo) {
        val fullPath = File(StoragePath.getBasePath(), file.path)
        if (!fullPath.exists()) {
            Toast.makeText(this, getString(R.string.editor_file_not_found), Toast.LENGTH_SHORT).show()
            return
        }
        val ext = file.getExtension()
        if (isMediaFile(ext)) {
            val intent = Intent(this, PreviewActivity::class.java).apply {
                putExtra("file_path", fullPath.absolutePath)
                putExtra("file_name", file.name)
            }
            startActivity(intent)
        } else {
            val intent = Intent(this, EditorActivity::class.java).apply {
                putExtra("file_path", fullPath.absolutePath)
                putExtra("file_name", file.name)
                putExtra("is_local", true)
            }
            startActivity(intent)
        }
    }

    private fun isMediaFile(ext: String): Boolean {
        return setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "ico", "mp4", "avi", "mkv", "mov", "wmv", "webm", "flv", "mp3", "wav", "ogg", "m4a", "aac", "flac", "wma").contains(ext.lowercase())
    }

    private fun navigateBack(pane: FilePaneState) {
        if (!pane.canGoBack()) return
        pane.goBack()
        if (pane.isRemote) {
            if (pane.repoOwner.isEmpty()) loadRepos()
            else loadRemoteFiles(pane)
        } else loadLocalFiles()
    }

    private fun navigateForward(pane: FilePaneState) {
        if (!pane.canGoForward()) return
        pane.goForward()
        if (pane.isRemote) loadRemoteFiles(pane) else loadLocalFiles()
    }

    private fun navigateUp(pane: FilePaneState) {
        if (pane.isRemote) {
            if (pane.currentPath.isEmpty() && pane.repoOwner.isNotEmpty()) {
                // At repo root -> go back to repo list
                pane.repoOwner = ""
                pane.repoName = ""
                pane.branch = "main"
                pane.history.clear()
                pane.history.add("")
                pane.historyIndex = 0
                pane.currentPath = ""
                loadRepos()
            } else if (pane.currentPath.isNotEmpty()) {
                val parts = pane.currentPath.split("/")
                val parentPath = if (parts.size <= 1) "" else parts.dropLast(1).joinToString("/")
                pane.pushPath(parentPath)
                loadRemoteFiles(pane)
            }
        } else {
            if (pane.currentPath.isEmpty()) return
            val parts = pane.currentPath.split("/")
            val parentPath = if (parts.size <= 1) "" else parts.dropLast(1).joinToString("/")
            pane.pushPath(parentPath)
            loadLocalFiles()
        }
    }

    private fun syncToOtherPane(source: FilePaneState) {
        if (source.isRemote && source.repoOwner.isNotEmpty()) {
            val remotePath = source.currentPath
            val localBase = StoragePath.getBasePath()
            val targetDir = File(localBase, remotePath)
            if (!targetDir.exists()) targetDir.mkdirs()
            rightState.history.clear()
            rightState.currentPath = remotePath
            rightState.history.add(remotePath)
            rightState.historyIndex = 0
            loadLocalFiles()
            Toast.makeText(this, "Synced: $remotePath", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Select a repo on the left first", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showUploadDialog() {
        if (leftState.repoOwner.isEmpty()) {
            Toast.makeText(this, "Select a repo first", Toast.LENGTH_SHORT).show()
            return
        }
        val repoPath = StoragePath.getRepoPath(leftState.repoOwner, leftState.repoName, leftState.isStarred)
        if (!repoPath.exists()) {
            Toast.makeText(this, "Local repo not synced yet", Toast.LENGTH_SHORT).show()
            return
        }
        val uploadManager = UploadManager(this)
        val filesToUpload = uploadManager.prepareUploadList(repoPath)
        if (filesToUpload.isEmpty()) {
            Toast.makeText(this, getString(R.string.upload_no_files), Toast.LENGTH_SHORT).show()
            return
        }
        val progressDialog = ProgressDialog(this).apply {
            setMessage(getString(R.string.uploading))
            setCancelable(false)
            show()
        }
        uploadManager.uploadFiles(
            leftState.repoOwner, leftState.repoName, leftState.branch, leftState.isStarred,
            filesToUpload,
            object : UploadManager.UploadCallback {
                override fun onUploadStarted() { progressDialog.setMessage(getString(R.string.uploading_progress)) }
                override fun onUploadProgress(current: Int, total: Int, fileName: String) {
                    progressDialog.setMessage("Uploading ($current/$total): $fileName")
                }
                override fun onUploadCompleted(successCount: Int, failCount: Int) {
                    progressDialog.dismiss()
                    Toast.makeText(this@MainActivity, "$successCount ok, $failCount failed", Toast.LENGTH_SHORT).show()
                    loadRemoteFiles(leftState)
                }
                override fun onUploadError(error: String) {
                    progressDialog.dismiss()
                    Toast.makeText(this@MainActivity, error, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun showMoreMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, "Search Repos")
        popup.menu.add(0, 2, 1, "New File")
        popup.menu.add(0, 3, 2, "New Folder")
        popup.menu.add(0, 4, 3, "Multi-select")
        popup.menu.add(0, 6, 4, "Delete Selected")
        val sortLabel = when (activePane.sortMode) {
            SortMode.NAME_ASC -> "Sort: Name A-Z"
            SortMode.NAME_DESC -> "Sort: Name Z-A"
            SortMode.SIZE_ASC -> "Sort: Size up"
            SortMode.SIZE_DESC -> "Sort: Size down"
            SortMode.DATE_ASC -> "Sort: Date old"
            SortMode.DATE_DESC -> "Sort: Date new"
        }
        popup.menu.add(0, 7, 5, sortLabel)
        popup.menu.add(0, 8, 6, if (activePane.showHidden) "Hide Hidden Files" else "Show Hidden Files")
        if (activePane.isRemote && activePane.repoOwner.isNotEmpty()) {
            val starLabel = if (activePane.isStarred) "Unstar Repo" else "Star Repo"
            popup.menu.add(0, 10, 7, starLabel)
            popup.menu.add(0, 11, 8, "Switch Branch")
            popup.menu.add(0, 12, 9, "Create Branch")
        }
        popup.menu.add(0, 9, 8, "Create Repo")
        popup.menu.add(0, 5, 9, "Refresh")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> showSearchDialog()
                2 -> showNewFileDialog()
                3 -> showNewFolderDialog()
                4 -> toggleMultiSelectMode()
                5 -> {
                    if (activePane.isRemote && activePane.repoOwner.isNotEmpty()) loadRemoteFiles(activePane)
                    else if (!activePane.isRemote) loadLocalFiles()
                    else loadRepos()
                }
                6 -> batchDeleteSelected()
                7 -> cycleSortMode()
                8 -> toggleHiddenFiles()
                9 -> showCreateRepoDialog()
                10 -> toggleStarRepo()
                11 -> showBranchSelector()
                12 -> showCreateBranchDialog()
            }
            true
        }
        popup.show()
    }

    private fun cycleSortMode() {
        activePane.sortMode = when (activePane.sortMode) {
            SortMode.NAME_ASC -> SortMode.NAME_DESC
            SortMode.NAME_DESC -> SortMode.SIZE_ASC
            SortMode.SIZE_ASC -> SortMode.SIZE_DESC
            SortMode.SIZE_DESC -> SortMode.DATE_DESC
            SortMode.DATE_DESC -> SortMode.DATE_ASC
            SortMode.DATE_ASC -> SortMode.NAME_ASC
        }
        if (activePane.isRemote && activePane.repoOwner.isNotEmpty()) loadRemoteFiles(activePane)
        else if (!activePane.isRemote) loadLocalFiles()
        Toast.makeText(this, "Sort: ${activePane.sortMode}", Toast.LENGTH_SHORT).show()
    }

    private fun toggleHiddenFiles() {
        activePane.showHidden = !activePane.showHidden
        Toast.makeText(this, if (activePane.showHidden) "Showing hidden files" else "Hiding hidden files", Toast.LENGTH_SHORT).show()
        if (activePane.isRemote && activePane.repoOwner.isNotEmpty()) loadRemoteFiles(activePane)
        else if (!activePane.isRemote) loadLocalFiles()
    }

    private fun showCreateRepoDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 20)
        }
        val nameInput = EditText(this).apply { hint = getString(R.string.create_repo_name); setPadding(0, 0, 0, 16) }
        val descInput = EditText(this).apply { hint = getString(R.string.create_repo_desc) }
        layout.addView(nameInput)
        layout.addView(descInput)
        AlertDialog.Builder(this)
            .setTitle(R.string.create_repo_title)
            .setView(layout)
            .setPositiveButton(R.string.confirm) { _, _ ->
                val name = nameInput.text.toString().trim()
                val desc = descInput.text.toString().trim()
                if (name.isNotEmpty()) {
                    executor.execute {
                        val result = api.createRepo(token, name, desc, false)
                        mainHandler.post {
                            if (result != null) {
                                Toast.makeText(this, getString(R.string.create_repo_success), Toast.LENGTH_SHORT).show()
                                loadRepos()
                            } else {
                                Toast.makeText(this, getString(R.string.create_repo_fail), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showSettingsDialog() {
        val userLogin = AuthManager.getUserLogin() ?: "Unknown"
        val sb = StringBuilder()
        sb.appendLine("NGGit v1.0.0")
        sb.appendLine("GitHub File Manager")
        sb.appendLine()
        sb.appendLine("Logged in as: $userLogin")
        sb.appendLine("Token: ${token.take(8)}...")
        sb.appendLine()
        sb.appendLine("Left pane sort: ${leftState.sortMode}")
        sb.appendLine("Right pane sort: ${rightState.sortMode}")
        sb.appendLine("Show hidden: ${activePane.showHidden}")

        AlertDialog.Builder(this)
            .setTitle("Settings")
            .setMessage(sb.toString())
            .setNeutralButton("Logout") { _, _ ->
                AlertDialog.Builder(this)
                    .setTitle("Logout")
                    .setMessage("Are you sure you want to logout?")
                    .setPositiveButton(R.string.confirm) { _, _ ->
                        AuthManager.logout(this)
                        startActivity(Intent(this, AuthActivity::class.java))
                        finish()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun toggleMultiSelectMode() {
        val adapter = getAdapter(activePane)
        if (adapter.multiSelectMode) {
            adapter.clearSelection()
            Toast.makeText(this, "Selection cleared", Toast.LENGTH_SHORT).show()
        } else {
            adapter.toggleMultiSelect(0)
            Toast.makeText(this, "Multi-select mode ON. Tap to select.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun batchDeleteSelected() {
        val adapter = getAdapter(activePane)
        val selected = adapter.getMultiSelectedFiles()
        if (selected.isEmpty()) {
            Toast.makeText(this, "No files selected", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Batch Delete")
            .setMessage("Delete ${selected.size} files?")
            .setPositiveButton(R.string.file_delete) { _, _ ->
                if (activePane.isRemote && activePane.repoOwner.isNotEmpty()) {
                    var completed = 0
                    var failed = 0
                    for (file in selected) {
                        UploadManager(this).deleteFile(activePane.repoOwner, activePane.repoName, activePane.branch, file.path, file.sha) { success ->
                            runOnUiThread {
                                completed++
                                if (!success) failed++
                                if (completed == selected.size) {
                                    adapter.clearSelection()
                                    loadRemoteFiles(activePane)
                                    Toast.makeText(this, "${completed - failed} deleted, $failed failed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                } else {
                    var deleted = 0
                    for (file in selected) {
                        val localFile = File(StoragePath.getBasePath(), file.path)
                        if (localFile.delete()) deleted++
                    }
                    adapter.clearSelection()
                    loadLocalFiles()
                    Toast.makeText(this, "$deleted files deleted", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun toggleStarRepo() {
        val pane = activePane
        if (pane.repoOwner.isEmpty()) return
        executor.execute {
            val success = if (pane.isStarred) {
                api.unstarRepo(token, pane.repoOwner, pane.repoName)
            } else {
                api.starRepo(token, pane.repoOwner, pane.repoName)
            }
            mainHandler.post {
                if (success) {
                    pane.isStarred = !pane.isStarred
                    repoStarredMap[pane.repoName] = pane.isStarred
                    Toast.makeText(this, if (pane.isStarred) "Starred" else "Unstarred", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showBranchSelector() {
        val pane = activePane
        if (pane.repoOwner.isEmpty()) return
        Toast.makeText(this, "Loading branches...", Toast.LENGTH_SHORT).show()
        executor.execute {
            try {
                val branches = api.getBranches(token, pane.repoOwner, pane.repoName)
                val names = branches.map { it.name }.toTypedArray()
                mainHandler.post {
                    if (branches.isEmpty()) {
                        Toast.makeText(this, "No branches found", Toast.LENGTH_SHORT).show()
                        return@post
                    }
                    AlertDialog.Builder(this)
                        .setTitle("Select Branch")
                        .setItems(names) { _, which ->
                            val selected = names[which]
                            if (selected != pane.branch) {
                                pane.branch = selected
                                pane.currentPath = ""
                                pane.history.clear()
                                pane.history.add("")
                                pane.historyIndex = 0
                                loadRemoteFiles(pane)
                                Toast.makeText(this, "Branch: $selected", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                }
            } catch (e: Exception) {
                mainHandler.post {
                    Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showSearchDialog() {
        val dialog = SearchDialog()
        dialog.setOnRepoSelectedListener { owner, repo -> openRepoByOwner(owner, repo) }
        dialog.show(supportFragmentManager, "search")
    }

    private fun showCreateBranchDialog() {
        val pane = activePane
        if (pane.repoOwner.isEmpty()) return
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 20)
        }
        val nameInput = EditText(this).apply {
            hint = "New branch name"
            setPadding(0, 0, 0, 16)
        }
        val fromText = TextView(this).apply {
            text = "From: ${pane.branch}"
            textSize = 12f
            setPadding(0, 0, 0, 8)
        }
        layout.addView(fromText)
        layout.addView(nameInput)
        AlertDialog.Builder(this)
            .setTitle("Create Branch")
            .setView(layout)
            .setPositiveButton("Create") { _, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isNotEmpty()) {
                    executor.execute {
                        val success = api.createBranch(token, pane.repoOwner, pane.repoName, name, pane.branch)
                        mainHandler.post {
                            if (success) {
                                Toast.makeText(this, "Branch created: $name", Toast.LENGTH_SHORT).show()
                                pane.branch = name
                                pane.currentPath = ""
                                pane.history.clear()
                                pane.history.add("")
                                pane.historyIndex = 0
                                loadRemoteFiles(pane)
                            } else {
                                Toast.makeText(this, "Failed to create branch", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showNewFileDialog() {
        val pane = activePane
        if (pane.isRemote && pane.repoOwner.isEmpty()) {
            Toast.makeText(this, "Select a repo first", Toast.LENGTH_SHORT).show()
            return
        }
        val input = EditText(this).apply { hint = getString(R.string.create_file_hint); setPadding(60, 40, 60, 20) }
        AlertDialog.Builder(this)
            .setTitle(R.string.create_new_file).setView(input)
            .setPositiveButton(R.string.create) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    if (pane.isRemote) {
                        val path = if (pane.currentPath.isEmpty()) name else "${pane.currentPath}/$name"
                        UploadManager(this).createNewFile(pane.repoOwner, pane.repoName, pane.branch, path) { success ->
                            runOnUiThread {
                                Toast.makeText(this, if (success) getString(R.string.create_file_created) else getString(R.string.create_fail), Toast.LENGTH_SHORT).show()
                                if (success) loadRemoteFiles(pane)
                            }
                        }
                    } else {
                        val basePath = StoragePath.getBasePath()
                        val dir = if (pane.currentPath.isEmpty()) basePath else File(basePath, pane.currentPath)
                        val file = File(dir, name)
                        if (file.createNewFile()) {
                            Toast.makeText(this, getString(R.string.create_file_created), Toast.LENGTH_SHORT).show()
                            loadLocalFiles()
                        } else {
                            Toast.makeText(this, getString(R.string.create_fail), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun showNewFolderDialog() {
        val pane = activePane
        if (pane.isRemote && pane.repoOwner.isEmpty()) {
            Toast.makeText(this, "Select a repo first", Toast.LENGTH_SHORT).show()
            return
        }
        val input = EditText(this).apply { hint = getString(R.string.create_folder_hint); setPadding(60, 40, 60, 20) }
        AlertDialog.Builder(this)
            .setTitle(R.string.create_new_folder).setView(input)
            .setPositiveButton(R.string.create) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    if (pane.isRemote) {
                        val path = if (pane.currentPath.isEmpty()) name else "${pane.currentPath}/$name"
                        UploadManager(this).createNewFolder(pane.repoOwner, pane.repoName, pane.branch, path) { success ->
                            runOnUiThread {
                                Toast.makeText(this, if (success) getString(R.string.create_folder_created) else getString(R.string.create_fail), Toast.LENGTH_SHORT).show()
                                if (success) loadRemoteFiles(pane)
                            }
                        }
                    } else {
                        val basePath = StoragePath.getBasePath()
                        val dir = if (pane.currentPath.isEmpty()) basePath else File(basePath, pane.currentPath)
                        val folder = File(dir, name)
                        if (folder.mkdirs()) {
                            Toast.makeText(this, getString(R.string.create_folder_created), Toast.LENGTH_SHORT).show()
                            loadLocalFiles()
                        } else {
                            Toast.makeText(this, getString(R.string.create_fail), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun showFileContextMenu(pane: FilePaneState, file: FileInfo) {
        val options = mutableListOf("Info", "Rename", "Copy Link", "Copy Path")
        if (pane.isRemote) {
            options.add("Delete")
            options.add("Download")
            options.add("Git Log")
        } else {
            options.add("Share")
            options.add("Delete")
            if (!file.isDir()) options.add("Open With")
        }
        if (!pane.isRemote) options.add("Copy to Remote")
        AlertDialog.Builder(this)
            .setTitle(file.name)
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Info" -> showFileInfo(pane, file)
                    "Rename" -> showRenameDialog(pane, file)
                    "Copy Link" -> copyFileLink(pane, file)
                    "Copy Path" -> copyFilePath(pane, file)
                    "Delete" -> confirmDelete(pane, file)
                    "Download" -> downloadRemoteFile(pane, file)
                    "Git Log" -> showGitLog(pane, file)
                    "Share" -> shareLocalFile(file)
                    "Open With" -> openLocalFileWith(file)
                    "Copy to Remote" -> copyLocalToRemote(pane, file)
                }
            }.show()
    }

    private fun showFileInfo(pane: FilePaneState, file: FileInfo) {
        val sb = StringBuilder()
        sb.appendLine("Name: ${file.name}")
        sb.appendLine("Type: ${if (file.isDir()) "Directory" else file.getExtension().uppercase().ifEmpty { "File" }}")
        sb.appendLine("Size: ${formatFileSize(file.size)}")
        sb.appendLine("Path: ${file.path}")
        if (pane.isRemote) {
            sb.appendLine("SHA: ${file.sha.take(12)}...")
            sb.appendLine("Repo: ${pane.repoOwner}/${pane.repoName}")
            sb.appendLine("Branch: ${pane.branch}")
        } else if (file.lastModified > 0) {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            sb.appendLine("Modified: ${sdf.format(java.util.Date(file.lastModified))}")
        }
        val actions = mutableListOf<String>()
        actions.add("Copy Path")
        actions.add("Copy SHA")
        AlertDialog.Builder(this)
            .setTitle("File Info")
            .setMessage(sb.toString())
            .setItems(actions.toTypedArray()) { _, which ->
                when (actions[which]) {
                    "Copy Path" -> copyFilePath(pane, file)
                    "Copy SHA" -> {
                        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("SHA", file.sha))
                        Toast.makeText(this, "SHA copied", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes == 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        var size = bytes.toDouble()
        var unitIndex = 0
        while (size >= 1024 && unitIndex < units.size - 1) { size /= 1024; unitIndex++ }
        return "%.1f %s".format(size, units[unitIndex])
    }

    private fun copyLocalToRemote(pane: FilePaneState, file: FileInfo) {
        if (leftState.repoOwner.isEmpty()) {
            Toast.makeText(this, "Select a repo first", Toast.LENGTH_SHORT).show()
            return
        }
        if (file.isDir()) {
            Toast.makeText(this, "Cannot copy directories yet", Toast.LENGTH_SHORT).show()
            return
        }
        val localFile = File(StoragePath.getBasePath(), file.path)
        if (!localFile.exists()) {
            Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show()
            return
        }
        val content = localFile.readText(Charsets.UTF_8)
        val remotePath = if (leftState.currentPath.isEmpty()) file.name else "${leftState.currentPath}/${file.name}"
        UploadManager(this).createNewFile(leftState.repoOwner, leftState.repoName, leftState.branch, remotePath) { success ->
            runOnUiThread {
                Toast.makeText(this, if (success) "Copied to repo" else "Copy failed", Toast.LENGTH_SHORT).show()
                if (success) loadRemoteFiles(leftState)
            }
        }
    }

    private fun downloadRemoteFile(pane: FilePaneState, file: FileInfo) {
        if (file.isDir()) {
            Toast.makeText(this, "Cannot download directories yet", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "Downloading ${file.name}...", Toast.LENGTH_SHORT).show()
        executor.execute {
            try {
                val blob = api.getFileContent(token, pane.repoOwner, pane.repoName, file.path, file.sha ?: "", pane.branch)
                val decoded = if (blob?.encoding == "base64") {
                    android.util.Base64.decode(blob.content, android.util.Base64.DEFAULT)
                } else {
                    blob?.content?.toByteArray() ?: ByteArray(0)
                }
                val localBase = StoragePath.getBasePath()
                val targetDir = if (pane.currentPath.isEmpty()) localBase
                else File(localBase, pane.currentPath)
                targetDir.mkdirs()
                val targetFile = File(targetDir, file.name)
                targetFile.writeBytes(decoded)
                mainHandler.post {
                    Toast.makeText(this, "Downloaded to ${targetFile.path}", Toast.LENGTH_SHORT).show()
                    loadLocalFiles()
                }
            } catch (e: Exception) {
                mainHandler.post {
                    Toast.makeText(this, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showGitLog(pane: FilePaneState, file: FileInfo) {
        if (file.isDir()) {
            Toast.makeText(this, "Git log for directories not supported", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "Loading commit history...", Toast.LENGTH_SHORT).show()
        executor.execute {
            try {
                val commits = api.getFileCommits(token, pane.repoOwner, pane.repoName, file.path, pane.branch, 15)
                mainHandler.post {
                    if (commits.isEmpty()) {
                        Toast.makeText(this, "No commits found", Toast.LENGTH_SHORT).show()
                        return@post
                    }
                    val sb = StringBuilder()
                    for (commit in commits.take(15)) {
                        val msg = commit.message.take(50).replace("\n", " ")
                        val author = commit.author.login
                        sb.appendLine("$msg")
                        sb.appendLine("  by $author  ${commit.sha.take(7)}")
                        sb.appendLine()
                    }
                    AlertDialog.Builder(this)
                        .setTitle("Git Log - ${file.name}")
                        .setMessage(sb.toString())
                        .setPositiveButton(R.string.ok, null)
                        .show()
                }
            } catch (e: Exception) {
                mainHandler.post {
                    Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun shareLocalFile(file: FileInfo) {
        val localFile = File(StoragePath.getBasePath(), file.path)
        if (!localFile.exists()) {
            Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", localFile)
        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = contentResolver.getType(uri) ?: "application/octet-stream"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(android.content.Intent.createChooser(shareIntent, "Share"))
    }

    private fun copyFilePath(pane: FilePaneState, file: FileInfo) {
        val fullPath = if (pane.isRemote && pane.repoOwner.isNotEmpty()) {
            "${pane.repoOwner}/${pane.repoName}/${file.path}"
        } else {
            File(StoragePath.getBasePath(), file.path).absolutePath
        }
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("FilePath", fullPath)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Path copied", Toast.LENGTH_SHORT).show()
    }

    private fun openLocalFileWith(file: FileInfo) {
        val localFile = File(StoragePath.getBasePath(), file.path)
        if (!localFile.exists()) {
            Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", localFile)
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, contentResolver.getType(uri))
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(android.content.Intent.createChooser(intent, "Open with"))
        } catch (e: Exception) {
            Toast.makeText(this, "No app found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRenameDialog(pane: FilePaneState, file: FileInfo) {
        val input = EditText(this).apply { setText(file.name); setPadding(60, 40, 60, 20) }
        AlertDialog.Builder(this).setTitle("Rename").setView(input)
            .setPositiveButton(R.string.confirm) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty() && newName != file.name) renameFile(pane, file, newName)
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun renameFile(pane: FilePaneState, file: FileInfo, newName: String) {
        val parts = file.path.split("/")
        val parentPath = if (parts.size > 1) parts.dropLast(1).joinToString("/") else ""
        val newPath = if (parentPath.isEmpty()) newName else "$parentPath/$newName"
        val content = if (file.isDir()) "" else {
            val localFile = File(StoragePath.getBasePath(), file.path)
            if (localFile.exists()) localFile.readText(Charsets.UTF_8) else ""
        }
        UploadManager(this).renameItem(
            pane.repoOwner, pane.repoName, pane.branch,
            file.path, newPath, file.sha, content
        ) { success ->
            runOnUiThread {
                Toast.makeText(this, if (success) "Renamed" else "Failed", Toast.LENGTH_SHORT).show()
                if (success) loadRemoteFiles(pane)
            }
        }
    }

    private fun copyFileLink(pane: FilePaneState, file: FileInfo) {
        val url = if (pane.isRemote)
            "https://github.com/${pane.repoOwner}/${pane.repoName}/blob/${pane.branch}/${file.path}"
        else file.path
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("link", url))
        Toast.makeText(this, getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
    }

    private fun confirmDelete(pane: FilePaneState, file: FileInfo) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_title)
            .setMessage(getString(R.string.file_delete_confirm, file.name))
            .setPositiveButton(R.string.file_delete) { _, _ ->
                if (pane.isRemote) {
                    UploadManager(this).deleteFile(pane.repoOwner, pane.repoName, pane.branch, file.path, file.sha) { success ->
                        runOnUiThread {
                            Toast.makeText(this, if (success) getString(R.string.delete_success) else getString(R.string.delete_fail), Toast.LENGTH_SHORT).show()
                            if (success) loadRemoteFiles(pane)
                        }
                    }
                } else {
                    val localFile = File(StoragePath.getBasePath(), file.path)
                    if (localFile.delete()) {
                        Toast.makeText(this, getString(R.string.delete_success), Toast.LENGTH_SHORT).show()
                        loadLocalFiles()
                    } else {
                        Toast.makeText(this, getString(R.string.delete_fail), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun getAdapter(pane: FilePaneState) = if (pane == leftState) leftAdapter else rightAdapter

    private fun showLoader(pane: FilePaneState, show: Boolean) {
        val loader = if (pane == leftState) leftLoader else rightLoader
        val list = if (pane == leftState) leftFileList else rightFileList
        val empty = if (pane == leftState) leftEmptyView else rightEmptyView
        if (show) {
            loader.visibility = View.VISIBLE
            list.visibility = View.GONE
            empty.visibility = View.GONE
        } else {
            loader.visibility = View.GONE
            if (pane.files.isNotEmpty()) {
                list.visibility = View.VISIBLE
                empty.visibility = View.GONE
            }
        }
    }

    private fun showEmpty(pane: FilePaneState, message: String) {
        val empty = if (pane == leftState) leftEmptyView else rightEmptyView
        val list = if (pane == leftState) leftFileList else rightFileList
        empty.text = message; empty.visibility = View.VISIBLE; list.visibility = View.GONE
    }

    private fun addDoubleTapRefresh(recyclerView: RecyclerView, onRefresh: () -> Unit) {
        val gestureDetector = android.view.GestureDetector(this,
            object : android.view.GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: android.view.MotionEvent): Boolean {
                    onRefresh()
                    return true
                }
            })
        recyclerView.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: android.view.MotionEvent): Boolean {
                gestureDetector.onTouchEvent(e)
                return false
            }
            override fun onTouchEvent(rv: RecyclerView, e: android.view.MotionEvent) {}
            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
        })
    }

    private fun updatePaneViews(pane: FilePaneState) {
        val pathText = if (pane == leftState) leftPathText else rightPathText
        val list = if (pane == leftState) leftFileList else rightFileList
        val empty = if (pane == leftState) leftEmptyView else rightEmptyView
        val pathBar = if (pane == leftState) leftPathBar else rightPathBar

        // Update toolbar title
        if (pane.isRemote && pane.repoOwner.isNotEmpty()) {
            title = "${pane.repoOwner}/${pane.repoName}"
        } else if (!pane.isRemote) {
            title = getString(R.string.app_name) + " - Local"
        } else {
            title = getString(R.string.app_name)
        }

        // Build breadcrumb path
        pathBar.removeViews(2, pathBar.childCount - 3)
        val segments = mutableListOf<String>()
        if (pane.isRemote) {
            if (pane.repoOwner.isNotEmpty()) {
                segments.add(pane.repoOwner)
                if (pane.repoName.isNotEmpty()) {
                    segments.add(pane.repoName)
                }
            }
        }
        if (pane.currentPath.isNotEmpty()) {
            segments.addAll(pane.currentPath.split("/").filter { it.isNotEmpty() })
        }

        if (segments.size > 0) {
            pathText.visibility = View.GONE
            for ((index, segment) in segments.withIndex()) {
                val tv = TextView(this).apply {
                    text = if (index < segments.size - 1) "$segment > " else segment
                    textSize = 11f
                    setTextColor(if (index < segments.size - 1) getColor(R.color.accent) else getColor(R.color.text_primary))
                    setPadding(2, 0, 2, 0)
                    isSingleLine = true
                    if (index < segments.size - 1) {
                        setOnClickListener {
                            val targetPath = segments.subList(index + 1, segments.size).joinToString("/")
                            if (pane.isRemote && index == 0) {
                                // Clicked on owner - go to repo list
                                pane.repoOwner = ""
                                pane.repoName = ""
                                pane.branch = "main"
                                pane.history.clear()
                                pane.history.add("")
                                pane.historyIndex = 0
                                pane.currentPath = ""
                                loadRepos()
                            } else if (pane.isRemote && index == 1) {
                                // Clicked on repo name - go to repo root
                                pane.currentPath = ""
                                pane.pushPath("")
                                loadRemoteFiles(pane)
                            } else {
                                pane.pushPath(targetPath)
                                if (pane.isRemote) loadRemoteFiles(pane) else loadLocalFiles()
                            }
                        }
                    }
                }
                pathBar.addView(tv, pathBar.childCount - 1)
            }
        } else {
            pathText.visibility = View.VISIBLE
            pathText.text = pane.getDisplayPath()
        }

        if (pane.files.isNotEmpty()) { list.visibility = View.VISIBLE; empty.visibility = View.GONE }
    }

    private fun showPathEditDialog(pane: FilePaneState) {
        val currentPath = if (pane.isRemote) {
            if (pane.repoOwner.isNotEmpty()) {
                val prefix = "${pane.repoOwner}/${pane.repoName}"
                if (pane.currentPath.isNotEmpty()) "$prefix/${pane.currentPath}" else prefix
            } else ""
        } else {
            val suffix = if (pane.currentPath.isNotEmpty()) "/${pane.currentPath}" else ""
            StoragePath.getBasePath().path + suffix
        }

        val input = EditText(this).apply {
            setText(currentPath)
            setSelectAllOnFocus(true)
            isSingleLine = true
            setPadding(48, 32, 48, 16)
            textSize = 14f
        }

        AlertDialog.Builder(this)
            .setTitle("Navigate to path")
            .setView(input)
            .setPositiveButton("Go") { _, _ ->
                val newPath = input.text.toString().trim()
                if (pane.isRemote) {
                    val parts = newPath.split("/")
                    if (parts.size >= 2) {
                        pane.repoOwner = parts[0]
                        pane.repoName = parts[1]
                        pane.branch = "main"
                        pane.currentPath = if (parts.size > 2) parts.subList(2, parts.size).joinToString("/") else ""
                        pane.history.clear()
                        pane.history.add(pane.currentPath)
                        pane.historyIndex = 0
                        loadRemoteFiles(pane)
                    }
                } else {
                    val basePath = StoragePath.getBasePath()
                    val target = File(newPath)
                    if (target.exists() && target.isDirectory && target.path.startsWith(basePath.path)) {
                        pane.currentPath = target.relativeTo(basePath).path.replace("\\", "/")
                        pane.history.clear()
                        pane.history.add(pane.currentPath)
                        pane.historyIndex = 0
                        loadLocalFiles()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                AlertDialog.Builder(this)
                    .setTitle(R.string.permission_title).setMessage(R.string.permission_message)
                    .setPositiveButton(R.string.permission_grant) { _, _ ->
                        val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        intent.data = android.net.Uri.parse("package:$packageName")
                        startActivity(intent)
                    }
                    .setNegativeButton(R.string.permission_exit) { _, _ -> finish() }.show()
            }
        } else {
            val needed = STORAGE_PERMISSIONS.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
            if (needed.isNotEmpty()) ActivityCompat.requestPermissions(this, needed.toTypedArray(), PERMISSION_REQUEST)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            StoragePath.ensureDirectories()
        }
    }
}