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
import io.nggit.App
import io.nggit.R
import io.nggit.auth.AuthActivity
import io.nggit.auth.AuthManager
import io.nggit.model.FileInfo
import io.nggit.model.RepoInfo
import io.nggit.sync.UploadManager
import io.nggit.ui.editor.EditorActivity
import io.nggit.ui.file.FileAdapter
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

    private lateinit var toolbar: View
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

    private lateinit var btnBack: ImageButton
    private lateinit var btnForward: ImageButton
    private lateinit var btnSync: ImageButton
    private lateinit var btnUp: ImageButton
    private lateinit var btnUpload: ImageButton
    private lateinit var btnMore: ImageButton

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
        toolbar = findViewById(R.id.toolbar)
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
        btnBack = findViewById(R.id.btn_back)
        btnForward = findViewById(R.id.btn_forward)
        btnSync = findViewById(R.id.btn_sync)
        btnUp = findViewById(R.id.btn_up)
        btnUpload = findViewById(R.id.btn_upload)
        btnMore = findViewById(R.id.btn_more)
    }

    private fun setupLeftPane() {
        leftSwipe.setColorSchemeResources(R.color.colorPrimary)
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
    }

    private fun setupRightPane() {
        rightSwipe.setColorSchemeResources(R.color.colorPrimary)
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
    }

    private fun setupBottomBar() {
        btnBack.setOnClickListener { navigateBack(activePane) }
        btnForward.setOnClickListener { navigateForward(activePane) }
        btnSync.setOnClickListener { syncToOtherPane(activePane) }
        btnUp.setOnClickListener { navigateUp(activePane) }
        btnUpload.setOnClickListener { showUploadDialog() }
        btnMore.setOnClickListener { v -> showMoreMenu(v) }
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
                        val sorted = files.sortedWith(
                            compareBy<FileInfo> { !it.isDir() }.thenBy { it.name.lowercase() }
                        )
                        pane.files = sorted
                        getAdapter(pane).updateData(sorted)
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

    private fun loadLocalFiles() {
        rightSwipe.isRefreshing = false
        updatePaneViews(rightState)
        val basePath = StoragePath.getBasePath()
        val currentDir = if (rightState.currentPath.isEmpty()) basePath
        else File(basePath, rightState.currentPath)
        val items = mutableListOf<FileInfo>()
        currentDir.listFiles()?.forEach { file ->
            if (file.name.startsWith(".")) return@forEach
            val type = if (file.isDirectory) "dir" else "file"
            items.add(FileInfo(
                name = file.name,
                path = file.relativeTo(basePath).path.replace("\\", "/"),
                type = type,
                size = if (file.isFile) file.length() else 0
            ))
        }
        val sorted = items.sortedWith(
            compareBy<FileInfo> { !it.isDir() }.thenBy { it.name.lowercase() }
        )
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
        val intent = Intent(this, EditorActivity::class.java).apply {
            putExtra("file_path", file.downloadUrl ?: "")
            putExtra("file_name", file.name)
            putExtra("repo_owner", pane.repoOwner)
            putExtra("repo_name", pane.repoName)
            putExtra("repo_branch", pane.branch)
            putExtra("is_starred", pane.isStarred)
            putExtra("file_sha", file.sha)
        }
        startActivity(intent)
    }

    private fun openLocalFile(file: FileInfo) {
        val fullPath = File(StoragePath.getBasePath(), file.path)
        if (!fullPath.exists()) {
            Toast.makeText(this, getString(R.string.editor_file_not_found), Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, EditorActivity::class.java).apply {
            putExtra("file_path", fullPath.absolutePath)
            putExtra("file_name", file.name)
            putExtra("is_local", true)
        }
        startActivity(intent)
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
        popup.menu.add(0, 4, 3, "Refresh")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> showSearchDialog()
                2 -> showNewFileDialog()
                3 -> showNewFolderDialog()
                4 -> {
                    if (activePane.isRemote && activePane.repoOwner.isNotEmpty()) loadRemoteFiles(activePane)
                    else if (!activePane.isRemote) loadLocalFiles()
                    else loadRepos()
                }
            }
            true
        }
        popup.show()
    }

    private fun showSearchDialog() {
        val dialog = SearchDialog()
        dialog.setOnRepoSelectedListener { owner, repo -> openRepoByOwner(owner, repo) }
        dialog.show(supportFragmentManager, "search")
    }

    private fun showNewFileDialog() {
        val pane = activePane
        if (!pane.isRemote || pane.repoOwner.isEmpty()) {
            Toast.makeText(this, "Select a repo first", Toast.LENGTH_SHORT).show()
            return
        }
        val input = EditText(this).apply { hint = getString(R.string.create_file_hint); setPadding(60, 40, 60, 20) }
        AlertDialog.Builder(this)
            .setTitle(R.string.create_new_file).setView(input)
            .setPositiveButton(R.string.create) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val path = if (pane.currentPath.isEmpty()) name else "${pane.currentPath}/$name"
                    UploadManager(this).createNewFile(pane.repoOwner, pane.repoName, pane.branch, path) { success ->
                        runOnUiThread {
                            Toast.makeText(this, if (success) getString(R.string.create_file_created) else getString(R.string.create_fail), Toast.LENGTH_SHORT).show()
                            if (success) loadRemoteFiles(pane)
                        }
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun showNewFolderDialog() {
        val pane = activePane
        if (!pane.isRemote || pane.repoOwner.isEmpty()) {
            Toast.makeText(this, "Select a repo first", Toast.LENGTH_SHORT).show()
            return
        }
        val input = EditText(this).apply { hint = getString(R.string.create_folder_hint); setPadding(60, 40, 60, 20) }
        AlertDialog.Builder(this)
            .setTitle(R.string.create_new_folder).setView(input)
            .setPositiveButton(R.string.create) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val path = if (pane.currentPath.isEmpty()) name else "${pane.currentPath}/$name"
                    UploadManager(this).createNewFolder(pane.repoOwner, pane.repoName, pane.branch, path) { success ->
                        runOnUiThread {
                            Toast.makeText(this, if (success) getString(R.string.create_folder_created) else getString(R.string.create_fail), Toast.LENGTH_SHORT).show()
                            if (success) loadRemoteFiles(pane)
                        }
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun showFileContextMenu(pane: FilePaneState, file: FileInfo) {
        val options = mutableListOf("Rename", "Copy Link")
        if (pane.isRemote) options.add("Delete")
        AlertDialog.Builder(this)
            .setTitle(file.name)
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Rename" -> showRenameDialog(pane, file)
                    "Copy Link" -> copyFileLink(pane, file)
                    "Delete" -> confirmDelete(pane, file)
                }
            }.show()
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
                UploadManager(this).deleteFile(pane.repoOwner, pane.repoName, pane.branch, file.path, file.sha) { success ->
                    runOnUiThread {
                        Toast.makeText(this, if (success) getString(R.string.delete_success) else getString(R.string.delete_fail), Toast.LENGTH_SHORT).show()
                        if (success) loadRemoteFiles(pane)
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

    private fun updatePaneViews(pane: FilePaneState) {
        val pathText = if (pane == leftState) leftPathText else rightPathText
        val list = if (pane == leftState) leftFileList else rightFileList
        val empty = if (pane == leftState) leftEmptyView else rightEmptyView
        pathText.text = pane.getDisplayPath()
        if (pane.files.isNotEmpty()) { list.visibility = View.VISIBLE; empty.visibility = View.GONE }
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