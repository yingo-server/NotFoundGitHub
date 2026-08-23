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
import io.nggit.R
import java.io.File

class PreviewActivity : AppCompatActivity() {

    private lateinit var backBtn: ImageButton
    private lateinit var titleText: TextView
    private lateinit var imageView: ImageView
    private lateinit var videoView: VideoView
    private lateinit var audioInfo: View
    private lateinit var audioTitle: TextView
    private lateinit var loader: ProgressBar
    private val mainHandler = Handler(Looper.getMainLooper())
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
        titleText.text = fileName

        val ext = fileName.substringAfterLast('.', "").lowercase()
        when {
            IMAGE_EXTS.contains(ext) -> showImage(filePath)
            VIDEO_EXTS.contains(ext) -> showVideo(filePath)
            AUDIO_EXTS.contains(ext) -> showAudio(filePath, fileName)
            else -> { finish() }
        }
    }

    private fun showImage(path: String) {
        loader.visibility = View.GONE
        imageView.visibility = View.VISIBLE
        val file = File(path)
        if (file.exists()) {
            Glide.with(this).load(file).into(imageView)
        } else {
            val uri = Uri.parse(path)
            Glide.with(this).load(uri).into(imageView)
        }
    }

    private fun showVideo(path: String) {
        loader.visibility = View.GONE
        videoView.visibility = View.VISIBLE
        val file = File(path)
        val uri = if (file.exists()) Uri.fromFile(file) else Uri.parse(path)
        val mc = MediaController(this)
        mc.setAnchorView(videoView)
        videoView.setMediaController(mc)
        videoView.setVideoURI(uri)
        videoView.setOnPreparedListener { it.start() }
        videoView.setOnErrorListener { _, _, _ ->
            mainHandler.post {
                Toast.makeText(this, getString(R.string.open_video_fail), Toast.LENGTH_SHORT).show()
                finish()
            }
            true
        }
        videoView.start()
    }

    private fun showAudio(path: String, name: String) {
        loader.visibility = View.GONE
        audioInfo.visibility = View.VISIBLE
        audioTitle.text = name
        val file = File(path)
        val uri = if (file.exists()) Uri.fromFile(file) else Uri.parse(path)
        val mp = MediaPlayer()
        mediaPlayer = mp
        try {
            mp.setDataSource(this, uri)
            mp.prepare()
            mp.start()
            mp.setOnCompletionListener { it.release(); mediaPlayer = null }
            mp.setOnErrorListener { _, _, _ ->
                mainHandler.post {
                    Toast.makeText(this, getString(R.string.open_audio_fail), Toast.LENGTH_SHORT).show()
                }
                true
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.open_audio_fail), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { if (videoView.isPlaying) videoView.stopPlayback() } catch (_: Exception) {}
        mediaPlayer?.release()
        mediaPlayer = null
    }

    companion object {
        private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "gif", "webp", "svg", "bmp", "ico", "tiff")
        private val VIDEO_EXTS = setOf("mp4", "avi", "mkv", "mov", "wmv", "webm", "flv")
        private val AUDIO_EXTS = setOf("mp3", "wav", "ogg", "m4a", "aac", "flac", "wma")
    }
}
