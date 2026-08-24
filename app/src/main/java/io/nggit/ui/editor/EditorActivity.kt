/**
 * 文本编辑器界面，支持远程GitHub文件和本地文件的查看与编辑，提供行号跳转、字数统计、自动换行、未保存提醒等功能，远程文件通过GitHub API获取并以base64解码。
 * 本Activity负责处理文本文件的加载、编辑和保存操作，支持远程仓库文件和本地存储文件两种模式。
 * 远程文件通过GitHub API获取文件内容，支持base64解码和直接读取两种方式。
 * 本地文件通过StoragePath获取基础路径，并使用编码检测器自动识别文件编码格式。
 * 界面包含编辑文本框、预览区域、加载指示器、文件名显示、保存按钮、返回按钮、搜索按钮和换行按钮。
 * 编辑器会实时监测内容变化，当内容被修改时显示保存按钮并在文件名前添加星号标记。
 * 状态栏实时显示当前文本的行数、字数和文件大小信息。
 * 行号跳转功能允许用户输入目标行号快速定位到指定位置。
 * 自动换行功能可以切换文本的水平滚动模式，方便查看长行内容。
 * 返回按钮会检测是否有未保存的修改，如有则弹出提醒对话框让用户选择保存、放弃或取消操作。
 * 界面销毁时会清理所有回调和线程资源，避免内存泄漏和空指针异常。
 * 本文件是NGGit应用的核心组件之一，为用户提供便捷的代码和文本编辑体验。
 */
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

    /**
     * Activity创建时的初始化方法，设置布局、返回按钮回调、初始化视图组件并加载文件内容。
     * 在此处设置返回键的回调处理函数，确保用户按下返回键时能正确处理未保存的修改。
     * 调用initViews方法初始化所有界面组件，然后调用loadFile方法加载目标文件内容。
     */
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

    /**
     * 初始化所有界面组件，绑定视图引用并设置点击事件监听器。
     * 从Intent中提取文件路径、文件名、仓库所有者、仓库名称、分支等参数。
     * 设置返回按钮、保存按钮、搜索按钮和换行按钮的点击事件。
     * 添加文本变化监听器，实时监测编辑内容的变化并更新保存按钮的显示状态。
     * 当内容与原始内容不同时显示保存按钮并在文件名前添加星号标记。
     * 调用updateStatusBar方法更新状态栏的统计信息。
     */
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

    /**
     * 更新状态栏信息，统计当前文本的行数、字数和文件大小。
     * 行数通过分割换行符计算得出，字数通过分割空白字符并过滤空字符串得出。
     * 文件大小使用UTF-8编码计算字节数，小于1024字节显示字节单位，否则显示千字节单位。
     * 将统计结果格式化为"Ln 行数 | 字数 words | 大小"的格式显示在状态栏。
     */
    private fun updateStatusBar() {
        val text = editorText.text.toString()
        val lines = text.split("\n").size
        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }.size
        val bytes = text.toByteArray(Charsets.UTF_8).size
        val sizeStr = if (bytes < 1024) "${bytes}B" else "${bytes / 1024}KB"
        statusText.text = "Ln $lines | $words words | $sizeStr"
    }

    /**
     * 显示行号跳转对话框，允许用户输入目标行号快速定位到指定位置。
     * 创建数字输入类型的编辑框，提示用户可跳转的行号范围。
     * 计算目标行的字符偏移量，通过设置编辑框的光标位置实现跳转。
     * 支持跳转到指定行号，并自动限制偏移量不超过文本总长度。
     */
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

    /**
     * 切换自动换行状态，控制文本是否在水平方向滚动显示。
     * 通过切换EditText的水平滚动属性实现自动换行功能。
     * 显示Toast提示用户当前的换行状态，ON表示开启自动换行，OFF表示关闭自动换行。
     * 自动换行开启时文本会根据编辑框宽度自动换行，关闭时会显示水平滚动条。
     */
    private fun toggleWordWrap() {
        isWordWrap = !isWordWrap
        editorText.setHorizontallyScrolling(!isWordWrap)
        Toast.makeText(this, if (isWordWrap) "Word wrap ON" else "Word wrap OFF", Toast.LENGTH_SHORT).show()
    }

    /**
     * 加载文件内容，在后台线程中执行文件读取操作，完成后在主线程更新界面。
     * 显示加载指示器并隐藏编辑框，根据是否为远程文件选择不同的加载方式。
     * 远程文件通过GitHub API获取并解码，本地文件通过StoragePath读取。
     * 加载成功时设置编辑框内容并记录原始内容，加载失败时显示错误提示并关闭界面。
     * 使用线程池执行器避免阻塞主线程，通过Handler在主线程更新UI组件。
     */
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

    /**
     * 从GitHub API加载远程文件内容，支持base64解码和直接读取两种方式。
     * 首先检查认证令牌和文件SHA值是否有效，然后调用API获取文件blob数据。
     * 根据文件编码类型选择解码方式，base64编码的内容使用Base64解码器解码。
     * 其他编码格式（如utf-8、none）直接返回内容，未知编码默认使用base64解码。
     * 返回解码后的文件内容字符串，加载失败时返回null。
     */
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

    /**
     * 从本地存储加载文件内容，支持基础路径文件和绝对路径文件两种查找方式。
     * 首先尝试在基础路径下查找文件，如果不存在则尝试使用绝对路径查找。
     * 使用编码检测器自动识别文件的字符编码格式，支持中文等多字节编码。
     * 读取文件内容并返回字符串，文件不存在时返回null。
     * 该方法会优先查找基础路径下的文件，然后再查找绝对路径下的文件。
     */
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

    /**
     * 保存文件内容到远程或本地存储，支持完成回调函数。
     * 首先检查内容是否有变化，如果内容未改变则直接调用回调函数返回。
     * 在后台线程中执行保存操作，远程文件通过GitHub API更新，本地文件直接写入。
     * 保存成功时更新原始内容、隐藏保存按钮、显示成功提示并设置结果码。
     * 保存失败时显示错误提示，无论成功失败都会调用回调函数。
     * 使用线程池执行器避免阻塞主线程，确保UI响应流畅。
     */
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

    /**
     * 通过GitHub API保存远程文件，创建或更新文件并提交更改。
     * 检查用户是否已登录，未登录时抛出异常。
     * 生成包含文件名的提交信息，调用API创建或更新文件。
     * 更新本地的文件SHA值为提交后的SHA，确保后续操作使用正确的版本。
     * API调用失败时抛出异常，由上层方法处理错误情况。
     */
    private fun saveRemoteFile(content: String) {
        val token = AuthManager.getToken() ?: throw Exception("Not logged in")
        val commitMessage = "Update $fileName via NGGit"
        val result = App.instance.githubApi.createOrUpdateFile(token, repoOwner, repoName, filePath, content, fileSha, commitMessage, repoBranch)
            ?: throw Exception("API error")
        fileSha = result.commit?.sha ?: fileSha
    }

    /**
     * 保存文件到本地存储，支持基础路径文件和绝对路径文件两种保存方式。
     * 根据文件路径决定保存位置，如果路径为空则保存到基础路径下。
     * 自动创建不存在的父目录，确保存储路径有效。
     * 使用UTF-8编码写入文件内容，支持中文等多字节字符的正确保存。
     * 保存操作直接写入文件系统，无需网络连接。
     */
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

    /**
     * 处理返回按钮点击事件，检测是否有未保存的修改并弹出提醒对话框。
     * 如果内容已修改，显示对话框提供保存、放弃和取消三个选项。
     * 选择保存会先保存文件然后关闭界面，选择放弃直接关闭界面。
     * 选择取消则关闭对话框保持当前编辑状态。
     * 如果内容未修改则直接关闭界面，无需任何确认操作。
     */
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

    /**
     * Activity销毁时的清理方法，释放资源避免内存泄漏。
     * 清理Handler的所有回调和消息，防止内存泄漏和空指针异常。
     * 关闭线程池执行器，停止所有后台任务的执行。
     * 调用父类的onDestroy方法完成Activity的销毁流程。
     * 该方法确保所有资源在Activity销毁时得到正确释放。
     */
    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
        executor.shutdownNow()
    }
}
