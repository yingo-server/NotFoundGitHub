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
    private val onItemLongClick: ((FileInfo) -> Unit)? = null,
    private val onSelectionChanged: ((Int) -> Unit)? = null
) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {

    private var selectedPosition = -1
    private val multiSelected = mutableSetOf<Int>()
    var multiSelectMode = false
        private set

    fun updateData(newItems: List<FileInfo>) {
        items = newItems
        selectedPosition = -1
        multiSelected.clear()
        multiSelectMode = false
        notifyDataSetChanged()
    }

    fun getSelectedFile(): FileInfo? {
        return if (selectedPosition >= 0 && selectedPosition < items.size) items[selectedPosition] else null
    }

    fun getMultiSelectedFiles(): List<FileInfo> {
        return multiSelected.mapNotNull { if (it in items.indices) items[it] else null }
    }

    fun getMultiSelectedCount(): Int = multiSelected.size

    fun clearSelection() {
        val prev = multiSelected.toSet()
        multiSelected.clear()
        multiSelectMode = false
        for (i in prev) notifyItemChanged(i)
        onSelectionChanged?.invoke(0)
    }

    fun toggleMultiSelect(position: Int) {
        if (position < 0 || position >= items.size) return
        if (!multiSelectMode) {
            multiSelectMode = true
            multiSelected.clear()
        }
        if (multiSelected.contains(position)) {
            multiSelected.remove(position)
            if (multiSelected.isEmpty()) multiSelectMode = false
        } else {
            multiSelected.add(position)
        }
        notifyItemChanged(position)
        onSelectionChanged?.invoke(multiSelected.size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val isSelected = if (multiSelectMode) multiSelected.contains(position) else position == selectedPosition
        holder.bind(items[position], isSelected, multiSelectMode)
        holder.itemView.setOnClickListener {
            if (multiSelectMode) {
                toggleMultiSelect(holder.adapterPosition)
            } else {
                val old = selectedPosition
                selectedPosition = holder.adapterPosition
                if (old >= 0) notifyItemChanged(old)
                notifyItemChanged(selectedPosition)
                onItemClick(items[holder.adapterPosition])
            }
        }
        holder.itemView.setOnLongClickListener {
            if (multiSelectMode) {
                toggleMultiSelect(holder.adapterPosition)
            } else {
                toggleMultiSelect(holder.adapterPosition)
                onItemLongClick?.invoke(items[holder.adapterPosition])
            }
            true
        }
    }

    override fun getItemCount(): Int = items.size

    inner class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.file_icon)
        private val name: TextView = itemView.findViewById(R.id.file_name)
        private val size: TextView = itemView.findViewById(R.id.file_size)
        private val checkBox: android.widget.CheckBox? = itemView.findViewById(R.id.file_check)

        fun bind(file: FileInfo, selected: Boolean, multiMode: Boolean) {
            name.text = file.name
            itemView.isSelected = selected

            checkBox?.let { cb ->
                cb.visibility = if (multiMode) View.VISIBLE else View.GONE
                cb.isChecked = selected
            }

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
