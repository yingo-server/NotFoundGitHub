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
import io.nggit.util.EncodingDetector
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
        statusText.text = "Ln $lines | $words words"
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

    private fun toggleWordWrap() {
        editorText.inputType = if (editorText.inputType and android.text.InputType.TYPE_TEXT_FLAG_NO_WRAP != 0) {
            android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        } else {
            android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or android.text.InputType.TYPE_TEXT_FLAG_NO_WRAP
        }
    }

    private fun loadFile() {
        loader.visibility = View.VISIBLE
        editorText.visibility = View.GONE

        executor.execute {
            try {
                val file = File(filePath)
                if (file.exists()) {
                    val charset = EncodingDetector.detect(file)
                    val content = file.readText(charset)
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

    private fun saveFile(onDone: (() -> Unit)? = null) {
        val content = editorText.text.toString()
        if (content == originalContent) {
            onDone?.invoke()
            return
        }

        executor.execute {
            try {
                val file = File(filePath)
                file.writeText(content, Charsets.UTF_8)
                originalContent = content
                currentContent = content

                mainHandler.post {
                    btnSave.visibility = View.GONE
                    fileNameText.text = fileName
                    Toast.makeText(this, getString(R.string.editor_saved_local), Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    onDone?.invoke()
                }
            } catch (e: Exception) {
                mainHandler.post {
                    Toast.makeText(this, getString(R.string.editor_save_fail, e.message), Toast.LENGTH_SHORT).show()
                    onDone?.invoke()
                }
            }
        }
    }

    private fun togglePreview() {
        if (isPreviewMode) {
            previewText.visibility = View.GONE
            editorPreview.visibility = View.GONE
            editorText.visibility = View.VISIBLE
            isPreviewMode = false
        } else {
            editorText.visibility = View.GONE
            previewText.text = editorText.text
            editorPreview.visibility = View.VISIBLE
            isPreviewMode = true
        }
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
