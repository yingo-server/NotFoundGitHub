// 文件列表适配器，负责将文件信息绑定到列表项视图，支持单选和多选模式切换，提供文件图标分类显示和大小格式化功能
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

/**
 * 文件列表 RecyclerView 适配器，将文件数据绑定到列表项视图。
 * 支持单选点击和多选批量操作两种模式，提供点击、长按回调以及选中数量变更通知。
 */
class FileAdapter(
    /** 上下文对象，用于获取资源和系统服务 */
    private val context: Context,
    /** 当前列表展示的文件信息数据集 */
    private var items: List<FileInfo>,
    /** 单个文件被点击时的回调函数 */
    private val onItemClick: (FileInfo) -> Unit,
    /** 文件被长按时的回调函数，可为空 */
    private val onItemLongClick: ((FileInfo) -> Unit)? = null,
    /** 选中文件数量发生变化时的回调函数，可为空 */
    private val onSelectionChanged: ((Int) -> Unit)? = null
) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {

    /** 单选模式下当前选中的位置索引，-1 表示无选中项 */
    private var selectedPosition = -1

    /** 多选模式下已选中项的位置索引集合 */
    private val multiSelected = mutableSetOf<Int>()

    /** 当前是否处于多选模式，外部只读 */
    var multiSelectMode = false
        private set

    /**
     * 更新适配器数据源并重置所有选中状态，刷新整个列表。
     *
     * @param newItems 新的文件信息列表
     */
    fun updateData(newItems: List<FileInfo>) {
        items = newItems
        selectedPosition = -1
        multiSelected.clear()
        multiSelectMode = false
        notifyDataSetChanged()
    }

    /**
     * 获取单选模式下当前被选中的文件信息。
     *
     * @return 选中的 FileInfo 对象，无选中时返回 null
     */
    fun getSelectedFile(): FileInfo? {
        return if (selectedPosition >= 0 && selectedPosition < items.size) items[selectedPosition] else null
    }

    /**
     * 获取多选模式下所有被选中的文件信息列表。
     *
     * @return 已选中文件的 FileInfo 列表
     */
    fun getMultiSelectedFiles(): List<FileInfo> {
        return multiSelected.mapNotNull { if (it in items.indices) items[it] else null }
    }

    /**
     * 获取多选模式下已选中文件的数量。
     *
     * @return 已选中的文件数量
     */
    fun getMultiSelectedCount(): Int = multiSelected.size

    /**
     * 清除所有选中状态，退出多选模式，并刷新受影响的列表项。
     */
    fun clearSelection() {
        val prev = multiSelected.toSet()
        multiSelected.clear()
        multiSelectMode = false
        for (i in prev) notifyItemChanged(i)
        onSelectionChanged?.invoke(0)
    }

    /**
     * 切换指定位置的多选选中状态，若未处于多选模式则自动进入多选模式。
     *
     * @param position 需要切换选中状态的列表项位置索引
     */
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

    /**
     * 创建列表项视图持有者，加载 item_file 布局文件。
     *
     * @param parent 父视图容器
     * @param viewType 视图类型
     * @return 新创建的 FileViewHolder 实例
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    /**
     * 绑定数据到指定位置的视图持有者，设置选中状态并注册点击和长按事件监听器。
     *
     * @param holder 目标视图持有者
     * @param position 列表中的数据位置索引
     */
    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val isSelected = if (multiSelectMode) multiSelected.contains(position) else position == selectedPosition
        holder.bind(items[position], isSelected, multiSelectMode)
        holder.itemView.setOnClickListener {
            if (multiSelectMode) {
                toggleMultiSelect(holder.bindingAdapterPosition)
            } else {
                val old = selectedPosition
                selectedPosition = holder.bindingAdapterPosition
                if (old >= 0) notifyItemChanged(old)
                notifyItemChanged(selectedPosition)
                onItemClick(items[holder.bindingAdapterPosition])
            }
        }
        holder.itemView.setOnLongClickListener {
            onItemLongClick?.invoke(items[holder.adapterPosition])
            true
        }
    }

    /**
     * 返回数据集中文件的总数量。
     *
     * @return 文件列表的大小
     */
    override fun getItemCount(): Int = items.size

    /**
     * 文件列表项视图持有者，持有图标、文件名、文件大小和多选复选框的视图引用。
     * 负责将 FileInfo 数据绑定到对应的视图控件上。
     */
    inner class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        /** 文件类型图标视图 */
        private val icon: ImageView = itemView.findViewById(R.id.file_icon)

        /** 文件名文本视图 */
        private val name: TextView = itemView.findViewById(R.id.file_name)

        /** 文件大小文本视图 */
        private val size: TextView = itemView.findViewById(R.id.file_size)

        /** 多选模式下的复选框视图，可能为 null */
        private val checkBox: android.widget.CheckBox? = itemView.findViewById(R.id.file_check)

        /**
         * 将文件信息绑定到视图控件，根据文件类型设置图标和大小显示，多选模式时显示复选框。
         *
         * @param file 要展示的文件信息对象
         * @param selected 当前项是否被选中
         * @param multiMode 是否处于多选模式
         */
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

        /**
         * 根据文件类别设置对应的图标资源和着色颜色，支持图片、音频、视频、代码、Markdown、配置和压缩包等类型。
         *
         * @param file 要设置图标的文件信息对象
         */
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

        /**
         * 将字节大小格式化为可读的文件大小字符串，自动选择合适的单位（B、KB、MB、GB）。
         *
         * @param bytes 文件的字节大小
         * @return 格式化后的大小字符串，例如 "1.5 MB"
         */
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
