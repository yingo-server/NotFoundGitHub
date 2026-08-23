package io.nggit.ui.editor

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import io.nggit.R
import io.nggit.App
import io.nggit.auth.AuthManager
import io.nggit.util.EncodingDetector
import io.nggit.util.StoragePath
import java.io.File
import java.util.concurrent.Executors

class EditorActivity : AppCompatActivity() {

    private lateinit var editorText: EditText
    private lateinit var previewText: TextView
    private lateinit var editorPreview: ScrollView
    private lateinit var editorContainer: FrameLayout
    private lateinit var loader: ProgressBar
    private lateinit var fileNameText: TextView
    private lateinit var btnSave: Button
    private lateinit var btnBack: ImageButton
    private lateinit var btnSearch: ImageButton
    private lateinit var btnWrap: ImageButton
    private lateinit var statusText: TextView

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var filePath: String = ""
    private var fileName: String = ""
    private var originalContent: String = ""
    private var currentContent: String = ""
    private var isPreviewMode: Boolean = false
    private var isRemote: Boolean = false

    private var repoOwner: String = ""
    private var repoName: String = ""
    private var repoBranch: String = "main"
    private var isStarred: Boolean = false
    private var fileSha: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editor)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBack()
            }
        })

        initViews()
        loadFile()
    }

    private fun initViews() {
        editorText = findViewById(R.id.editor_text)
        previewText = findViewById(R.id.preview_text)
        editorPreview = findViewById(R.id.editor_preview)
        editorContainer = findViewById(R.id.editor_container)
        loader = findViewById(R.id.editor_loader)
        fileNameText = findViewById(R.id.editor_file_name)
        btnSave = findViewById(R.id.btn_save)
        btnBack = findViewById(R.id.btn_back)
        btnSearch = findViewById(R.id.btn_search)
        btnWrap = findViewById(R.id.btn_wrap)
        statusText = findViewById(R.id.editor_status)

        filePath = intent.getStringExtra("file_path") ?: ""
        fileName = intent.getStringExtra("file_name") ?: ""
        repoOwner = intent.getStringExtra("repo_owner") ?: ""
        repoName = intent.getStringExtra("repo_name") ?: ""
        repoBranch = intent.getStringExtra("repo_branch") ?: "main"
        isStarred = intent.getBooleanExtra("is_starred", false)
        fileSha = intent.getStringExtra("file_sha") ?: ""
        isRemote = repoOwner.isNotEmpty()

        fileNameText.text = fileName

        btnBack.setOnClickListener { finish() }
        btnSave.setOnClickListener { saveFile() }
        btnSearch.setOnClickListener { showGoToLineDialog() }
        btnWrap.setOnClickListener { toggleWordWrap() }

        editorText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                currentContent = s?.toString() ?: ""
                if (currentContent != originalContent) {
                    btnSave.visibility = View.VISIBLE
                    fileNameText.text = "*$fileName"
                } else {
                    btnSave.visibility = View.GONE
                    fileNameText.text = fileName
                }
                updateStatusBar()
            }
        })
    }

    private fun updateStatusBar() {
        val text = editorText.text.toString()
        val lines = text.split("\n").size
        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }.size
        val bytes = text.toByteArray(Charsets.UTF_8).size
        val sizeStr = if (bytes < 1024) "${bytes}B" else "${bytes / 1024}KB"
        statusText.text = "Ln $lines | $words words | $sizeStr"
    }

    private fun showGoToLineDialog() {
        val text = editorText.text.toString()
        val totalLines = text.split("\n").size
        val input = EditText(this).apply {
            hint = "Line 1-$totalLines"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setPadding(60, 40, 60, 20)
        }
        AlertDialog.Builder(this)
            .setTitle("Go to line")
            .setView(input)
            .setPositiveButton("Go") { _, _ ->
                val line = input.text.toString().toIntOrNull()
                if (line != null && line in 1..totalLines) {
                    val lines = text.split("\n")
                    var offset = 0
                    for (i in 0 until line - 1) {
                        offset += lines[i].length + 1
                    }
                    editorText.setSelection(offset.coerceAtMost(editorText.text.length))
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private var isWordWrap = false

    private fun toggleWordWrap() {
        isWordWrap = !isWordWrap
        editorText.setHorizontallyScrolling(!isWordWrap)
        Toast.makeText(this, if (isWordWrap) "Word wrap ON" else "Word wrap OFF", Toast.LENGTH_SHORT).show()
    }

    private fun loadFile() {
        loader.visibility = View.VISIBLE
        editorText.visibility = View.GONE

        executor.execute {
            try {
                val content = if (isRemote) {
                    loadRemoteFile()
                } else {
                    loadLocalFile()
                }
                if (content != null) {
                    mainHandler.post {
                        originalContent = content
                        currentContent = content
                        editorText.setText(content)
                        loader.visibility = View.GONE
                        editorText.visibility = View.VISIBLE
                        editorText.setSelection(0)
                    }
                } else {
                    mainHandler.post {
                        loader.visibility = View.GONE
                        editorText.visibility = View.VISIBLE
                        Toast.makeText(this, getString(R.string.editor_file_not_found), Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    loader.visibility = View.GONE
                    editorText.visibility = View.VISIBLE
                    Toast.makeText(this, getString(R.string.editor_read_fail, e.message), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadRemoteFile(): String? {
        val token = AuthManager.getToken() ?: return null
        if (fileSha.isEmpty()) return null
        val blob = App.instance.githubApi.getFileContent(token, repoOwner, repoName, filePath, fileSha, repoBranch) ?: return null
        return when (blob.encoding) {
            "base64" -> {
                val decoded = android.util.Base64.decode(blob.content, android.util.Base64.DEFAULT)
                String(decoded, Charsets.UTF_8)
            }
            "utf-8", "none" -> blob.content
            else -> {
                val decoded = android.util.Base64.decode(blob.content, android.util.Base64.DEFAULT)
                String(decoded, Charsets.UTF_8)
            }
        }
    }

    private fun loadLocalFile(): String? {
        val basePath = StoragePath.getBasePath()
        val file = if (filePath.isEmpty()) {
            File(basePath, fileName)
        } else {
            File(basePath, filePath)
        }
        if (!file.exists()) {
            val file2 = File(filePath)
            if (file2.exists()) {
                val charset = EncodingDetector.detect(file2)
                return file2.readText(charset)
            }
            return null
        }
        val charset = EncodingDetector.detect(file)
        return file.readText(charset)
    }

    private fun saveFile(onDone: (() -> Unit)? = null) {
        val content = editorText.text.toString()
        if (content == originalContent) {
            onDone?.invoke()
            return
        }

        executor.execute {
            try {
                if (isRemote) {
                    saveRemoteFile(content)
                } else {
                    saveLocalFile(content)
                }
                originalContent = content
                currentContent = content
                mainHandler.post {
                    btnSave.visibility = View.GONE
                    fileNameText.text = fileName
                    Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    onDone?.invoke()
                }
            } catch (e: Exception) {
                mainHandler.post {
                    Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    onDone?.invoke()
                }
            }
        }
    }

    private fun saveRemoteFile(content: String) {
        val token = AuthManager.getToken() ?: throw Exception("Not logged in")
        val commitMessage = "Update $fileName via NGGit"
        val result = App.instance.githubApi.createOrUpdateFile(token, repoOwner, repoName, filePath, content, fileSha, commitMessage, repoBranch)
            ?: throw Exception("API error")
        fileSha = result.commit?.sha ?: fileSha
    }

    private fun saveLocalFile(content: String) {
        val basePath = StoragePath.getBasePath()
        val file = if (filePath.isEmpty()) {
            File(basePath, fileName)
        } else {
            File(basePath, filePath)
        }
        if (!file.parentFile?.exists()!!) {
            file.parentFile?.mkdirs()
        }
        file.writeText(content, Charsets.UTF_8)
    }

    private fun handleBack() {
        if (currentContent != originalContent) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.editor_unsaved))
                .setMessage(getString(R.string.editor_unsaved_msg))
                .setPositiveButton(getString(R.string.editor_save)) { _, _ ->
                    saveFile { finish() }
                }
                .setNegativeButton(getString(R.string.cancel)) { _, _ ->
                    finish()
                }
                .setNeutralButton(getString(R.string.cancel), null)
                .show()
        } else {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
        executor.shutdownNow()
    }
}
