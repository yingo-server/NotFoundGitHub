package io.nggit.ui.file

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.nggit.R
import io.nggit.model.FileCategory
import io.nggit.model.FileInfo

class FileAdapter(
    private val context: Context,
    private var items: List<FileInfo>,
    private val onItemClick: (FileInfo) -> Unit,
    private val onItemLongClick: ((FileInfo) -> Unit)? = null
) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {

    private var selectedPosition = -1

    fun updateData(newItems: List<FileInfo>) {
        items = newItems
        selectedPosition = -1
        notifyDataSetChanged()
    }

    fun getSelectedFile(): FileInfo? {
        return if (selectedPosition >= 0 && selectedPosition < items.size) items[selectedPosition] else null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(items[position], position == selectedPosition)
        holder.itemView.setOnClickListener {
            val old = selectedPosition
            selectedPosition = holder.adapterPosition
            if (old >= 0) notifyItemChanged(old)
            notifyItemChanged(selectedPosition)
            onItemClick(items[holder.adapterPosition])
        }
        holder.itemView.setOnLongClickListener {
            onItemLongClick?.invoke(items[holder.adapterPosition])
            true
        }
    }

    override fun getItemCount(): Int = items.size

    inner class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.file_icon)
        private val name: TextView = itemView.findViewById(R.id.file_name)
        private val size: TextView = itemView.findViewById(R.id.file_size)

        fun bind(file: FileInfo, selected: Boolean) {
            name.text = file.name
            itemView.isSelected = selected

            if (file.isDir()) {
                size.text = ""
                icon.setImageResource(R.drawable.ic_folder)
                icon.setColorFilter(context.getColor(R.color.folder_color))
            } else {
                size.text = formatSize(file.size)
                setFileIcon(file)
            }
        }

        private fun setFileIcon(file: FileInfo) {
            when (file.getFileCategory()) {
                FileCategory.IMAGE -> { icon.setImageResource(R.drawable.ic_image); icon.setColorFilter(context.getColor(R.color.image_color)) }
                FileCategory.AUDIO -> { icon.setImageResource(R.drawable.ic_audio); icon.setColorFilter(context.getColor(R.color.audio_color)) }
                FileCategory.VIDEO -> { icon.setImageResource(R.drawable.ic_video); icon.setColorFilter(context.getColor(R.color.video_color)) }
                FileCategory.CODE -> { icon.setImageResource(R.drawable.ic_code); icon.setColorFilter(context.getColor(R.color.code_color)) }
                FileCategory.MARKDOWN -> { icon.setImageResource(R.drawable.ic_markdown); icon.setColorFilter(context.getColor(R.color.markdown_color)) }
                FileCategory.CONFIG -> { icon.setImageResource(R.drawable.ic_config); icon.setColorFilter(context.getColor(R.color.config_color)) }
                FileCategory.ARCHIVE -> { icon.setImageResource(R.drawable.ic_archive); icon.setColorFilter(context.getColor(R.color.archive_color)) }
                else -> { icon.setImageResource(R.drawable.ic_file); icon.setColorFilter(context.getColor(R.color.text_hint)) }
            }
        }

        private fun formatSize(bytes: Long): String {
            if (bytes == 0L) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB")
            var size = bytes.toDouble()
            var unitIndex = 0
            while (size >= 1024 && unitIndex < units.size - 1) { size /= 1024; unitIndex++ }
            return "%.1f %s".format(size, units[unitIndex])
        }
    }
}
