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

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
        mainHandler.removeCallbacksAndMessages(null)
        executor.shutdownNow()
    }

    companion object {
        private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "gif", "webp", "svg", "bmp")
        private val VIDEO_EXTS = setOf("mp4", "avi", "mkv", "mov", "wmv", "webm")
        private val AUDIO_EXTS = setOf("mp3", "wav", "ogg", "m4a", "aac", "flac")
    }
}
