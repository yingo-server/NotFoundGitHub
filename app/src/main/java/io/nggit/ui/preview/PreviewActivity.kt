/**
 * 多媒体预览界面，支持远程和本地图片、视频、音频文件的预览播放，远程文件通过GitHub API获取blob数据后解码为临时文件进行播放，自动根据文件扩展名选择对应的播放器。
 * 本Activity负责处理多媒体文件的预览和播放，支持远程仓库文件和本地存储文件两种模式。
 * 远程文件通过GitHub API获取blob数据，解码后保存为临时文件进行播放。
 * 本地文件直接通过文件路径进行播放，支持绝对路径和基础路径两种查找方式。
 * 界面包含返回按钮、标题文本、图片预览、视频播放器、音频信息显示和加载指示器。
 * 根据文件扩展名自动选择对应的播放器，支持图片、视频和音频三种媒体类型。
 * 图片预览使用Glide库加载显示，支持本地文件和临时文件的加载。
 * 视频播放使用VideoView组件，提供媒体控制器支持播放控制。
 * 音频播放使用MediaPlayer组件，支持播放完成回调和资源释放。
 * 所有媒体文件加载完成后会隐藏加载指示器，显示对应的播放界面。
 * 加载失败时会显示错误提示并关闭界面，确保用户体验流畅。
 * 界面销毁时会释放MediaPlayer资源，清理Handler回调和关闭线程池。
 * 本文件是NGGit应用的多媒体预览组件，为用户提供便捷的媒体文件查看体验。
 */
package io.nggit.ui.preview

import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.MediaController
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import io.nggit.App
import io.nggit.R
import io.nggit.auth.AuthManager
import io.nggit.util.StoragePath
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

class PreviewActivity : AppCompatActivity() {

    private lateinit var backBtn: ImageButton
    private lateinit var titleText: TextView
    private lateinit var imageView: ImageView
    private lateinit var videoView: VideoView
    private lateinit var audioInfo: View
    private lateinit var audioTitle: TextView
    private lateinit var loader: ProgressBar
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private var mediaPlayer: MediaPlayer? = null

    /**
     * Activity创建时的初始化方法，设置布局和初始化视图组件。
     * 从Intent中提取文件路径、文件名、仓库所有者、分支、SHA值等参数。
     * 根据文件扩展名判断媒体类型，调用对应的加载方法。
     * 设置返回按钮的点击事件监听器。
     * 如果文件扩展名不支持则直接关闭界面。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preview)

        backBtn = findViewById(R.id.preview_back)
        titleText = findViewById(R.id.preview_title)
        imageView = findViewById(R.id.preview_image)
        videoView = findViewById(R.id.preview_video)
        audioInfo = findViewById(R.id.preview_audio_info)
        audioTitle = findViewById(R.id.preview_audio_title)
        loader = findViewById(R.id.preview_loader)

        backBtn.setOnClickListener { finish() }

        val filePath = intent.getStringExtra("file_path") ?: ""
        val fileName = intent.getStringExtra("file_name") ?: ""
        val repoOwner = intent.getStringExtra("repo_owner") ?: ""
        val repoBranch = intent.getStringExtra("repo_branch") ?: "main"
        val fileSha = intent.getStringExtra("file_sha") ?: ""
        val repoName = intent.getStringExtra("repo_name") ?: ""
        titleText.text = fileName

        val dotIndex = fileName.lastIndexOf(".")
        val ext = if (dotIndex > 0) fileName.substring(dotIndex + 1).lowercase() else ""
        when {
            IMAGE_EXTS.contains(ext) -> loadAndShowImage(filePath, repoOwner, repoName, repoBranch, fileSha)
            VIDEO_EXTS.contains(ext) -> loadAndShowVideo(filePath, repoOwner, repoName, repoBranch, fileSha)
            AUDIO_EXTS.contains(ext) -> loadAndShowAudio(filePath, repoOwner, repoName, repoBranch, fileSha, fileName)
            else -> { finish() }
        }
    }

    /**
     * 从GitHub API下载远程文件并保存为临时文件，供媒体播放器使用。
     * 检查认证令牌和文件SHA值是否有效，然后调用API获取文件blob数据。
     * 根据文件编码类型解码内容，支持base64解码和直接解码两种方式。
     * 根据文件扩展名创建临时文件，保存解码后的二进制数据。
     * 返回临时文件对象，下载失败时返回null。
     */
    private fun downloadToTempFile(filePath: String, repoOwner: String, repoName: String, repoBranch: String, fileSha: String): File? {
        val token = AuthManager.getToken() ?: return null
        if (fileSha.isEmpty()) return null
        val blob = App.instance.githubApi.getFileContent(token, repoOwner, repoName, filePath, fileSha, repoBranch) ?: return null
        val decoded = when (blob.encoding) {
            "base64" -> android.util.Base64.decode(blob.content, android.util.Base64.DEFAULT)
            else -> android.util.Base64.decode(blob.content, android.util.Base64.DEFAULT)
        }
        val ext = filePath.substringAfterLast('.', "")
        val tempFile = File(cacheDir, "preview_${System.currentTimeMillis()}.$ext")
        FileOutputStream(tempFile).use { it.write(decoded) }
        return tempFile
    }

    /**
     * 加载并显示远程图片文件，支持远程仓库文件的预览显示。
     * 如果仓库所有者为空则调用本地图片显示方法，否则在后台线程下载临时文件。
     * 使用Glide库加载临时文件中的图片到ImageView组件。
     * 下载失败或加载失败时显示错误提示并关闭界面。
     * 通过Handler在主线程更新UI，确保界面响应流畅。
     */
    private fun loadAndShowImage(filePath: String, repoOwner: String, repoName: String, repoBranch: String, fileSha: String) {
        if (repoOwner.isEmpty()) {
            showImageLocal(filePath)
            return
        }
        executor.execute {
            try {
                val tempFile = downloadToTempFile(filePath, repoOwner, repoName, repoBranch, fileSha)
                mainHandler.post {
                    loader.visibility = View.GONE
                    imageView.visibility = View.VISIBLE
                    if (tempFile != null && tempFile.exists()) {
                        Glide.with(this).load(tempFile).into(imageView)
                    } else {
                        Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    loader.visibility = View.GONE
                    Toast.makeText(this, "Load failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    /**
     * 显示本地图片文件，支持绝对路径和基础路径两种查找方式。
     * 首先尝试使用绝对路径查找文件，如果不存在则尝试在基础路径下查找。
     * 使用Glide库加载找到的文件到ImageView组件显示。
     * 如果文件不存在则显示错误提示并关闭界面。
     * 隐藏加载指示器并显示图片预览界面。
     */
    private fun showImageLocal(path: String) {
        loader.visibility = View.GONE
        imageView.visibility = View.VISIBLE
        val file = File(path)
        if (file.exists()) {
            Glide.with(this).load(file).into(imageView)
        } else {
            val basePath = StoragePath.getBasePath()
            val localFile = File(basePath, path)
            if (localFile.exists()) {
                Glide.with(this).load(localFile).into(imageView)
            } else {
                Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    /**
     * 加载并显示远程视频文件，支持远程仓库视频的播放预览。
     * 如果仓库所有者为空则调用本地视频显示方法，否则在后台线程下载临时文件。
     * 下载完成后创建MediaController媒体控制器，设置视频URI并开始播放。
     * 下载失败或加载失败时显示错误提示并关闭界面。
     * 通过Handler在主线程更新UI，确保界面响应流畅。
     */
    private fun loadAndShowVideo(filePath: String, repoOwner: String, repoName: String, repoBranch: String, fileSha: String) {
        if (repoOwner.isEmpty()) {
            showVideoLocal(filePath)
            return
        }
        executor.execute {
            try {
                val tempFile = downloadToTempFile(filePath, repoOwner, repoName, repoBranch, fileSha)
                mainHandler.post {
                    loader.visibility = View.GONE
                    videoView.visibility = View.VISIBLE
                    if (tempFile != null && tempFile.exists()) {
                        val mc = MediaController(this)
                        mc.setAnchorView(videoView)
                        videoView.setMediaController(mc)
                        videoView.setVideoURI(Uri.fromFile(tempFile))
                        videoView.start()
                    } else {
                        Toast.makeText(this, "Failed to load video", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    loader.visibility = View.GONE
                    Toast.makeText(this, "Load failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    /**
     * 显示本地视频文件，支持绝对路径和基础路径两种查找方式。
     * 首先尝试使用绝对路径查找文件，如果不存在则尝试在基础路径下查找。
     * 创建MediaController媒体控制器，设置视频URI并开始播放。
     * 如果文件不存在则显示错误提示并关闭界面。
     * 隐藏加载指示器并显示视频播放界面。
     */
    private fun showVideoLocal(path: String) {
        loader.visibility = View.GONE
        videoView.visibility = View.VISIBLE
        val file = File(path)
        val uri = if (file.exists()) Uri.fromFile(file) else {
            val basePath = StoragePath.getBasePath()
            val localFile = File(basePath, path)
            if (localFile.exists()) Uri.fromFile(localFile) else null
        }
        if (uri == null) {
            Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val mc = MediaController(this)
        mc.setAnchorView(videoView)
        videoView.setMediaController(mc)
        videoView.setVideoURI(uri)
        videoView.start()
    }

    /**
     * 加载并显示远程音频文件，支持远程仓库音频的播放预览。
     * 如果仓库所有者为空则调用本地音频显示方法，否则在后台线程下载临时文件。
     * 下载完成后创建MediaPlayer组件，设置数据源并开始播放。
     * 设置播放完成回调，播放结束时显示完成提示。
     * 下载失败或加载失败时显示错误提示并关闭界面。
     * 通过Handler在主线程更新UI，确保界面响应流畅。
     */
    private fun loadAndShowAudio(filePath: String, repoOwner: String, repoName: String, repoBranch: String, fileSha: String, fileName: String) {
        if (repoOwner.isEmpty()) {
            showAudioLocal(filePath, fileName)
            return
        }
        executor.execute {
            try {
                val tempFile = downloadToTempFile(filePath, repoOwner, repoName, repoBranch, fileSha)
                mainHandler.post {
                    loader.visibility = View.GONE
                    audioInfo.visibility = View.VISIBLE
                    audioTitle.text = fileName
                    if (tempFile != null && tempFile.exists()) {
                        mediaPlayer = MediaPlayer().apply {
                            setDataSource(this@PreviewActivity, Uri.fromFile(tempFile))
                            prepare()
                            start()
                            setOnCompletionListener {
                                Toast.makeText(this@PreviewActivity, "Playback complete", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        Toast.makeText(this, "Failed to load audio", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    loader.visibility = View.GONE
                    Toast.makeText(this, "Load failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    /**
     * 显示本地音频文件，支持绝对路径和基础路径两种查找方式。
     * 首先尝试使用绝对路径查找文件，如果不存在则尝试在基础路径下查找。
     * 创建MediaPlayer组件，设置数据源并开始播放音频。
     * 设置播放完成回调，播放结束时显示完成提示。
     * 如果文件不存在则显示错误提示并关闭界面。
     * 隐藏加载指示器并显示音频信息界面。
     */
    private fun showAudioLocal(path: String, fileName: String) {
        loader.visibility = View.GONE
        audioInfo.visibility = View.VISIBLE
        audioTitle.text = fileName
        val file = File(path)
        val uri = if (file.exists()) Uri.fromFile(file) else {
            val basePath = StoragePath.getBasePath()
            val localFile = File(basePath, path)
            if (localFile.exists()) Uri.fromFile(localFile) else null
        }
        if (uri == null) {
            Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        mediaPlayer = MediaPlayer().apply {
            setDataSource(this@PreviewActivity, uri)
            prepare()
            start()
            setOnCompletionListener {
                Toast.makeText(this@PreviewActivity, "Playback complete", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Activity销毁时的清理方法，释放MediaPlayer资源和清理线程池。
     * 释放MediaPlayer播放器资源，避免内存泄漏。
     * 清理Handler的所有回调和消息，防止内存泄漏和空指针异常。
     * 关闭线程池执行器，停止所有后台任务的执行。
     * 调用父类的onDestroy方法完成Activity的销毁流程。
     */
    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
        mainHandler.removeCallbacksAndMessages(null)
        executor.shutdownNow()
    }

    /**
     * 伴生对象，包含支持的媒体文件扩展名常量集合。
     * IMAGE_EXTS定义支持的图片文件扩展名，包括jpg、jpeg、png、gif等格式。
     * VIDEO_EXTS定义支持的视频文件扩展名，包括mp4、avi、mkv、mov等格式。
     * AUDIO_EXTS定义支持的音频文件扩展名，包括mp3、wav、ogg、m4a等格式。
     * 这些常量用于根据文件扩展名判断媒体类型并选择对应的播放器。
     */
    companion object {
        private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "gif", "webp", "svg", "bmp")
        private val VIDEO_EXTS = setOf("mp4", "avi", "mkv", "mov", "wmv", "webm")
        private val AUDIO_EXTS = setOf("mp3", "wav", "ogg", "m4a", "aac", "flac")
    }
}
