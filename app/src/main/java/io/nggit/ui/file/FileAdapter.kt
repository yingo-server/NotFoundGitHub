package io.nggit.ui.file

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.nggit.R
import io.nggit.model.FileCategory
import io.nggit.model.FileInfo
import io.nggit.model.RepoInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileAdapter(
    private val context: Context,
    private var items: List<Any>,
    private val isStarred: Boolean = false,
    private val onItemClick: (Any) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var onItemLongClickListener: ((Any, Int) -> Unit)? = null

    companion object {
        private const val TYPE_REPO = 0
        private const val TYPE_FILE = 1
    }

    fun setOnItemLongClickListener(listener: (Any, Int) -> Unit) {
        onItemLongClickListener = listener
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is RepoInfo -> TYPE_REPO
            is FileInfo -> TYPE_FILE
            else -> TYPE_FILE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_REPO -> {
                val view = LayoutInflater.from(context).inflate(R.layout.item_repo, parent, false)
                RepoViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(context).inflate(R.layout.item_file, parent, false)
                FileViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is RepoViewHolder -> holder.bind(items[position] as RepoInfo, isStarred)
            is FileViewHolder -> holder.bind(items[position] as FileInfo)
        }
    }

    override fun getItemCount(): Int = items.size

    inner class RepoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.repo_icon)
        private val name: TextView = itemView.findViewById(R.id.repo_name)
        private val desc: TextView = itemView.findViewById(R.id.repo_desc)
        private val info: TextView = itemView.findViewById(R.id.repo_info)

        fun bind(repo: RepoInfo, isStarred: Boolean) {
            name.text = repo.name
            desc.text = repo.description ?: if (repo.getOwnerLogin() == io.nggit.auth.AuthManager.getUserLogin()) context.getString(R.string.file_repo_desc_mine) else context.getString(R.string.file_repo_desc_star, repo.getOwnerLogin())
            info.text = buildString {
                if (repo.language != null) append(repo.language)
                if (repo.isPrivate) {
                    if (isNotEmpty()) append(" | ")
                    append(context.getString(R.string.file_private))
                }
            }

            when {
                repo.isPrivate -> {
                    icon.setImageResource(R.drawable.ic_lock)
                    icon.setColorFilter(context.getColor(R.color.text_hint))
                }
                else -> {
                    icon.setImageResource(R.drawable.ic_repo)
                    icon.setColorFilter(context.getColor(R.color.text_secondary))
                }
            }

            itemView.setOnClickListener { onItemClick(repo) }

            itemView.setOnLongClickListener {
                onItemLongClickListener?.invoke(repo, adapterPosition)
                true
            }
        }
    }

    inner class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.file_icon)
        private val name: TextView = itemView.findViewById(R.id.file_name)
        private val size: TextView = itemView.findViewById(R.id.file_size)

        fun bind(file: FileInfo) {
            name.text = file.name

            if (file.isDir()) {
                size.text = ""
                icon.setImageResource(R.drawable.ic_folder)
                icon.setColorFilter(context.getColor(R.color.folder_color))
            } else {
                size.text = formatSize(file.size)
                setFileIcon(file)
            }

            itemView.setOnClickListener { onItemClick(file) }

            itemView.setOnLongClickListener {
                onItemLongClickListener?.invoke(file, adapterPosition)
                true
            }
        }

        private fun setFileIcon(file: FileInfo) {
            when (file.getFileCategory()) {
                FileCategory.IMAGE -> {
                    icon.setImageResource(R.drawable.ic_image)
                    icon.setColorFilter(context.getColor(R.color.image_color))
                }
                FileCategory.AUDIO -> {
                    icon.setImageResource(R.drawable.ic_audio)
                    icon.setColorFilter(context.getColor(R.color.audio_color))
                }
                FileCategory.VIDEO -> {
                    icon.setImageResource(R.drawable.ic_video)
                    icon.setColorFilter(context.getColor(R.color.video_color))
                }
                FileCategory.CODE -> {
                    icon.setImageResource(R.drawable.ic_code)
                    icon.setColorFilter(context.getColor(R.color.code_color))
                }
                FileCategory.MARKDOWN -> {
                    icon.setImageResource(R.drawable.ic_markdown)
                    icon.setColorFilter(context.getColor(R.color.markdown_color))
                }
                FileCategory.CONFIG -> {
                    icon.setImageResource(R.drawable.ic_config)
                    icon.setColorFilter(context.getColor(R.color.config_color))
                }
                FileCategory.ARCHIVE -> {
                    icon.setImageResource(R.drawable.ic_archive)
                    icon.setColorFilter(context.getColor(R.color.archive_color))
                }
                FileCategory.BINARY -> {
                    icon.setImageResource(R.drawable.ic_binary)
                    icon.setColorFilter(context.getColor(R.color.text_hint))
                }
                else -> {
                    icon.setImageResource(R.drawable.ic_file)
                    icon.setColorFilter(context.getColor(R.color.text_hint))
                }
            }
        }

        private fun formatSize(bytes: Long): String {
            if (bytes == 0L) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB")
            var size = bytes.toDouble()
            var unitIndex = 0
            while (size >= 1024 && unitIndex < units.size - 1) {
                size /= 1024
                unitIndex++
            }
            return "%.1f %s".format(size, units[unitIndex])
        }
    }
}
