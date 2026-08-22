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
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import io.nggit.App
import io.nggit.R
import io.nggit.auth.AuthManager
import io.nggit.model.FileInfo
import io.nggit.model.RepoInfo
import io.nggit.service.GitHubApi
import io.nggit.service.ProxyConfig
import io.nggit.sync.CommitManager
import io.nggit.sync.SyncManager
import io.nggit.sync.UploadManager
import io.nggit.ui.deploy.DeployFragment
import io.nggit.ui.editor.EditorActivity
import io.nggit.ui.file.FileAdapter
import io.nggit.ui.file.FileListFragment
import io.nggit.ui.profile.ProfileFragment
import io.nggit.ui.search.SearchDialog
import io.nggit.util.StoragePath
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
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

    private lateinit var toolbar: android.widget.Toolbar
    private lateinit var breadcrumbBar: LinearLayout
    private lateinit var breadcrumbText: TextView
    private lateinit var searchIcon: ImageButton
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var fabBtn: FloatingActionButton
    private lateinit var fragmentContainer: FrameLayout
    private lateinit var swipeRefresh: SwipeRefreshLayout

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val api = App.instance.githubApi
    private val token get() = AuthManager.getToken() ?: ""

    var currentPath: String = ""
    var currentRepoOwner: String = ""
    var currentRepoName: String = ""
    var currentBranch: String = "main"
    var isStarredRepo: Boolean = false

    private var pendingChanges: MutableMap<String, String> = mutableMapOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        AuthManager.init(this)
        ProxyConfig.init(this)
        StoragePath.init(this)

        if (!AuthManager.isLoggedIn()) {
            startActivity(Intent(this, io.nggit.auth.AuthActivity::class.java))
            finish()
            return
        }

        requestPermissions()
        initViews()
        setupBottomNav()
        showFragment("repos")
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        breadcrumbBar = findViewById(R.id.breadcrumb_bar)
        breadcrumbText = findViewById(R.id.breadcrumb_text)
        searchIcon = findViewById(R.id.search_icon)
        bottomNav = findViewById(R.id.bottom_nav)
        fabBtn = findViewById(R.id.fab_btn)
        fragmentContainer = findViewById(R.id.fragment_container)
        swipeRefresh = findViewById(R.id.swipe_refresh)

        toolbar.setNavigationOnClickListener {
            onBackPressedCompat()
        }

        searchIcon.setOnClickListener {
            showSearchDialog()
        }

        breadcrumbBar.setOnClickListener {
            showSearchDialog()
        }

        swipeRefresh.setOnRefreshListener {
            refreshCurrentView()
        }

        swipeRefresh.setColorSchemeResources(
            R.color.primary,
            R.color.primary_dark
        )

        fabBtn.setOnClickListener {
            onFabClicked()
        }
    }

    private fun setupBottomNav() {
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_repos -> {
                    showFragment("repos")
                    updateToolbar(getString(R.string.repo), false)
                    fabBtn.visibility = View.VISIBLE
                    true
                }
                R.id.nav_starred -> {
                    showFragment("starred")
                    updateToolbar(getString(R.string.starred), false)
                    fabBtn.visibility = View.GONE
                    true
                }
                R.id.nav_deploy -> {
                    showFragment("deploy")
                    updateToolbar(getString(R.string.pages), false)
                    fabBtn.visibility = View.GONE
                    true
                }
                R.id.nav_profile -> {
                    showFragment("profile")
                    updateToolbar(getString(R.string.profile), false)
                    fabBtn.visibility = View.GONE
                    true
                }
                else -> false
            }
        }
    }

    private fun showFragment(type: String) {
        val fragment = when (type) {
            "repos" -> FileListFragment.newInstance("repos")
            "starred" -> FileListFragment.newInstance("starred")
            "deploy" -> DeployFragment()
            "profile" -> ProfileFragment()
            else -> return
        }

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    fun enterRepo(owner: String, repo: String, branch: String, isStarred: Boolean) {
        currentRepoOwner = owner
        currentRepoName = repo
        currentBranch = branch
        isStarredRepo = isStarred
        currentPath = ""

        updateToolbar(repo, true)
        breadcrumbBar.visibility = View.VISIBLE
        updateBreadcrumb()

        val syncManager = SyncManager(this)
        if (isFinishing) return
        val progressDialog = ProgressDialog(this).apply {
            setMessage(getString(R.string.syncing_toast))
            setCancelable(false)
            show()
        }

        syncManager.checkAndSync(
            owner, repo, branch, isStarred,
            object : SyncManager.SyncCallback {
                override fun onSyncStarted() {
                    if (!isFinishing) progressDialog.setMessage(getString(R.string.syncing_toast))
                }

                override fun onProgress(current: Int, total: Int, message: String) {
                    if (!isFinishing) progressDialog.setMessage(message)
                }

                override fun onSyncCompleted(fileCount: Int) {
                    if (!isFinishing) progressDialog.dismiss()
                    showFileList()
                }

                override fun onSyncError(error: String) {
                    if (!isFinishing) progressDialog.dismiss()
                    Toast.makeText(this@MainActivity, error, Toast.LENGTH_SHORT).show()
                    showFileList()
                }

                override fun onConflictDetected(conflict: io.nggit.model.SyncConflict) {
                    if (!isFinishing) progressDialog.dismiss()
                    Toast.makeText(this@MainActivity, getString(R.string.conflict_local_selected), Toast.LENGTH_SHORT).show()
                    showFileList()
                }
            }
        )
    }

    private fun showFileList() {
        val fragment = FileListFragment.newInstance("files")
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    fun enterFolder(folderPath: String) {
        currentPath = if (currentPath.isEmpty()) folderPath else "$currentPath/$folderPath"
        updateBreadcrumb()
        refreshFileList()
    }

    fun navigateUp(): Boolean {
        if (currentPath.isEmpty()) return false

        val parts = currentPath.split("/")
        parts.dropLast(1)
        currentPath = if (parts.size <= 1) "" else parts.dropLast(1).joinToString("/")
        updateBreadcrumb()
        refreshFileList()
        return true
    }

    fun updateBreadcrumb() {
        val parts = if (currentPath.isEmpty()) emptyList() else currentPath.split("/")
        breadcrumbText.text = buildString {
            append("/")
            parts.forEachIndexed { index, part ->
                append(part)
                if (index < parts.size - 1) append(" > ")
            }
        }
    }

    fun openFile(fileInfo: FileInfo) {
        val category = fileInfo.getFileCategory()
        when (category) {
            io.nggit.model.FileCategory.IMAGE -> {
                openImageViewer(fileInfo)
            }
            io.nggit.model.FileCategory.AUDIO -> {
                openAudioPlayer(fileInfo)
            }
            io.nggit.model.FileCategory.VIDEO -> {
                openVideoPlayer(fileInfo)
            }
            else -> {
                openEditor(fileInfo)
            }
        }
    }

    private fun openEditor(fileInfo: FileInfo) {
        val localPath = StoragePath.getRepoFilePath(
            currentRepoOwner, currentRepoName, isStarredRepo, fileInfo.path
        ).absolutePath

        val intent = Intent(this, EditorActivity::class.java).apply {
            putExtra("file_path", localPath)
            putExtra("file_name", fileInfo.name)
            putExtra("repo_owner", currentRepoOwner)
            putExtra("repo_name", currentRepoName)
            putExtra("repo_branch", currentBranch)
            putExtra("is_starred", isStarredRepo)
            putExtra("file_sha", fileInfo.sha)
        }
        startActivity(intent)
    }

    private fun openImageViewer(fileInfo: FileInfo) {
        val localPath = StoragePath.getRepoFilePath(
            currentRepoOwner, currentRepoName, isStarredRepo, fileInfo.path
        ).absolutePath
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(android.net.Uri.fromFile(File(localPath)), "image/*")
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.open_image_fail), Toast.LENGTH_SHORT).show()
        }
    }

    private fun openAudioPlayer(fileInfo: FileInfo) {
        val localPath = StoragePath.getRepoFilePath(
            currentRepoOwner, currentRepoName, isStarredRepo, fileInfo.path
        ).absolutePath
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(android.net.Uri.fromFile(File(localPath)), "audio/*")
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.open_audio_fail), Toast.LENGTH_SHORT).show()
        }
    }

    private fun openVideoPlayer(fileInfo: FileInfo) {
        val localPath = StoragePath.getRepoFilePath(
            currentRepoOwner, currentRepoName, isStarredRepo, fileInfo.path
        ).absolutePath
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(android.net.Uri.fromFile(File(localPath)), "video/*")
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.open_video_fail), Toast.LENGTH_SHORT).show()
        }
    }

    fun markFileModified(filePath: String) {
        pendingChanges[filePath] = System.currentTimeMillis().toString()
        fabBtn.visibility = View.VISIBLE
        fabBtn.setImageResource(R.drawable.ic_save)
    }

    private fun onFabClicked() {
        if (currentRepoOwner.isEmpty()) {
            showCreateRepoDialog()
        } else if (pendingChanges.isNotEmpty()) {
            showUploadDialog()
        } else {
            showNewFileDialog()
        }
    }

    private fun showCreateRepoDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_repo, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.repo_name_input)
        val descInput = dialogView.findViewById<EditText>(R.id.repo_desc_input)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.create_repo_title))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.create)) { _, _ ->
                val name = nameInput.text.toString().trim()
                val desc = descInput.text.toString().trim()
                if (name.isNotEmpty()) {
                    createRepo(name, desc)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun createRepo(name: String, description: String) {
        if (isFinishing) return
        val progressDialog = ProgressDialog(this).apply {
            setMessage(getString(R.string.uploading_progress))
            setCancelable(false)
            show()
        }

        executor.execute {
            try {
                val result = api.createRepo(token, name, description, false)
                mainHandler.post {
                    if (!isFinishing) {
                        progressDialog.dismiss()
                        if (result != null) {
                            Toast.makeText(this, getString(R.string.create_repo_success), Toast.LENGTH_SHORT).show()
                            refreshCurrentView()
                        } else {
                            Toast.makeText(this, getString(R.string.create_fail), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    if (!isFinishing) {
                        progressDialog.dismiss()
                        Toast.makeText(this, "创建失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun showNewFileDialog() {
        val options = arrayOf(getString(R.string.create_new_file), getString(R.string.create_new_folder))
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.create_new))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showNewFileNameDialog()
                    1 -> showNewFolderNameDialog()
                }
            }
            .show()
    }

    private fun showNewFileNameDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.create_file_hint)
            setPadding(60, 40, 60, 20)
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.create_new_file))
            .setView(input)
            .setPositiveButton(getString(R.string.create)) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val path = if (currentPath.isEmpty()) name else "$currentPath/$name"
                    createNewFile(path)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showNewFolderNameDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.create_folder_hint)
            setPadding(60, 40, 60, 20)
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.create_new_folder))
            .setView(input)
            .setPositiveButton(getString(R.string.create)) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val path = if (currentPath.isEmpty()) name else "$currentPath/$name"
                    createNewFolder(path)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun createNewFile(path: String) {
        val uploadManager = UploadManager(this)
        uploadManager.createNewFile(currentRepoOwner, currentRepoName, currentBranch, path) { success ->
            if (success) {
                Toast.makeText(this, getString(R.string.create_file_created), Toast.LENGTH_SHORT).show()
                refreshFileList()
            } else {
                Toast.makeText(this, getString(R.string.create_fail), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun createNewFolder(path: String) {
        val uploadManager = UploadManager(this)
        uploadManager.createNewFolder(currentRepoOwner, currentRepoName, currentBranch, path) { success ->
            if (success) {
                Toast.makeText(this, getString(R.string.create_folder_created), Toast.LENGTH_SHORT).show()
                refreshFileList()
            } else {
                Toast.makeText(this, getString(R.string.create_fail), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showUploadDialog() {
        val repoPath = StoragePath.getRepoPath(currentRepoOwner, currentRepoName, isStarredRepo)
        val uploadManager = UploadManager(this)
        val filesToUpload = uploadManager.prepareUploadList(repoPath)

        if (filesToUpload.isEmpty()) {
            Toast.makeText(this, getString(R.string.upload_no_files), Toast.LENGTH_SHORT).show()
            return
        }

        val fileNames = filesToUpload.map { File(it.first).name }.toTypedArray()
        val checkedItems = BooleanArray(fileNames.size) { true }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.upload_file_title, fileNames.size))
            .setMultiChoiceItems(fileNames, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton(getString(R.string.upload_btn)) { _, _ ->
                val selectedFiles = filesToUpload.filterIndexed { index, _ -> checkedItems[index] }
                if (selectedFiles.isNotEmpty()) {
                    executeUpload(selectedFiles)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun executeUpload(files: List<Triple<String, String, String?>>) {
        if (isFinishing) return
        val progressDialog = ProgressDialog(this).apply {
            setMessage(getString(R.string.uploading))
            setCancelable(false)
            show()
        }

        val uploadManager = UploadManager(this)
        uploadManager.uploadFiles(
            currentRepoOwner, currentRepoName, currentBranch, isStarredRepo, files,
            object : UploadManager.UploadCallback {
                override fun onUploadStarted() {
                    if (!isFinishing) progressDialog.setMessage(getString(R.string.uploading_progress))
                }

                override fun onUploadProgress(current: Int, total: Int, fileName: String) {
                    if (!isFinishing) progressDialog.setMessage("上传中 ($current/$total): $fileName")
                }

                override fun onUploadCompleted(successCount: Int, failCount: Int) {
                    if (!isFinishing) progressDialog.dismiss()
                    pendingChanges.clear()
                    fabBtn.setImageResource(R.drawable.ic_add)
                    Toast.makeText(this@MainActivity, getString(R.string.upload_complete_toast, successCount), Toast.LENGTH_SHORT).show()
                    refreshFileList()
                }

                override fun onUploadError(error: String) {
                    if (!isFinishing) progressDialog.dismiss()
                    Toast.makeText(this@MainActivity, error, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    fun showSearchDialog() {
        val dialog = SearchDialog.newInstance()
        dialog.show(supportFragmentManager, "search")
    }

    private fun refreshCurrentView() {
        swipeRefresh.isRefreshing = false
        val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        if (fragment is FileListFragment) {
            fragment.refresh()
        }
    }

    private fun refreshFileList() {
        val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        if (fragment is FileListFragment) {
            fragment.refresh()
        }
    }

    private fun updateToolbar(title: String, showBack: Boolean) {
        toolbar.title = title
        toolbar.setNavigationIcon(if (showBack) R.drawable.ic_back else 0)
    }

    private fun onBackPressedCompat() {
        if (currentRepoOwner.isNotEmpty()) {
            if (!navigateUp()) {
                currentRepoOwner = ""
                currentRepoName = ""
                currentPath = ""
                breadcrumbBar.visibility = View.GONE
                supportFragmentManager.popBackStack()
                bottomNav.selectedItemId = R.id.nav_repos
            }
        } else {
            if (supportFragmentManager.backStackEntryCount > 0) {
                supportFragmentManager.popBackStack()
            } else {
                finish()
            }
        }
    }

    override fun onBackPressed() {
        onBackPressedCompat()
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
        executor.shutdownNow()
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.permission_title))
                    .setMessage(getString(R.string.permission_message))
                    .setPositiveButton(getString(R.string.permission_grant)) { _, _ ->
                        val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        intent.data = android.net.Uri.parse("package:$packageName")
                        startActivity(intent)
                    }
                    .setNegativeButton(getString(R.string.permission_exit)) { _, _ -> finish() }
                    .show()
            }
        } else {
            val needed = STORAGE_PERMISSIONS.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (needed.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, needed.toTypedArray(), PERMISSION_REQUEST)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                StoragePath.ensureDirectories()
            } else {
                Toast.makeText(this, getString(R.string.permission_required), Toast.LENGTH_SHORT).show()
            }
        }
    }
}
