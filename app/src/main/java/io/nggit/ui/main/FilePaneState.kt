// 文件面板状态管理，封装左右双栏的导航历史栈、当前路径、排序模式和远程仓库信息，提供前进后退等路径导航操作
package io.nggit.ui.main

/**
 * 文件面板状态数据类，维护单个面板的所有状态信息，包括仓库信息、当前路径、导航历史栈、排序方式以及文件列表等。
 * 面板通过 pushPath 记录路径变更，通过 goBack / goForward 实现前进后退导航。
 */
data class FilePaneState(
    /** 面板唯一标识符，用于区分左右两个面板实例 */
    var paneId: Int = 0,
    /** 远程仓库所有者用户名 */
    var repoOwner: String = "",
    /** 远程仓库名称 */
    var repoName: String = "",
    /** 当前选中的分支名称 */
    var branch: String = "main",
    /** 当前显示的目录路径，为空时表示仓库根目录 */
    var currentPath: String = "",
    /** 标识当前面板是否正在浏览远程仓库 */
    var isRemote: Boolean = true,
    /** 标识当前仓库是否已被用户收藏 */
    var isStarred: Boolean = false,
    /** 路径导航历史栈，记录用户访问过的所有路径 */
    val history: MutableList<String> = mutableListOf(""),
    /** 当前在历史栈中的索引位置，用于前进后退定位 */
    var historyIndex: Int = 0,
    /** 当前目录下的文件信息列表 */
    var files: List<io.nggit.model.FileInfo> = emptyList(),
    /** 文件排序模式，支持按名称、大小、日期正序或倒序排列 */
    var sortMode: SortMode = SortMode.NAME_ASC,
    /** 是否显示隐藏文件的开关标志 */
    var showHidden: Boolean = false
) {
    /** 判断是否可以执行后退操作，当历史索引大于零时表示存在可后退的历史记录 */
    fun canGoBack(): Boolean = historyIndex > 0

    /** 判断是否可以执行前进操作，当历史索引未到达历史栈末尾时表示存在可前进的历史记录 */
    fun canGoForward(): Boolean = historyIndex < history.size - 1

    /**
     * 将新路径压入历史栈，若当前不在栈尾则先截断后续历史记录，然后更新当前路径和历史索引。
     *
     * @param path 新访问的目录路径
     */
    fun pushPath(path: String) {
        if (historyIndex < history.size - 1) {
            history.subList(historyIndex + 1, history.size).clear()
        }
        history.add(path)
        historyIndex = history.size - 1
        currentPath = path
    }

    /**
     * 后退到上一个历史路径，更新当前路径并返回该路径，若无法后退则返回空值。
     *
     * @return 后退后的路径字符串，不可后退时返回 null
     */
    fun goBack(): String? {
        if (!canGoBack()) return null
        historyIndex--
        currentPath = history[historyIndex]
        return currentPath
    }

    /**
     * 前进到下一个历史路径，更新当前路径并返回该路径，若无法前进则返回空值。
     *
     * @return 前进后的路径字符串，不可前进时返回 null
     */
    fun goForward(): String? {
        if (!canGoForward()) return null
        historyIndex++
        currentPath = history[historyIndex]
        return currentPath
    }

    /**
     * 获取当前路径的完整显示路径，远程仓库时拼接 owner/repo 前缀，本地仓库时以斜杠开头。
     *
     * @return 用于界面展示的完整路径字符串
     */
    fun getDisplayPath(): String {
        if (isRemote) {
            val prefix = "$repoOwner/$repoName/"
            return if (currentPath.isEmpty()) prefix else "$prefix$currentPath"
        }
        return if (currentPath.isEmpty()) "/" else "/$currentPath"
    }
}

/**
 * 文件排序模式枚举，提供六种排序方式：按名称正序、按名称倒序、按大小正序、按大小倒序、按日期倒序、按日期正序。
 */
enum class SortMode {
    NAME_ASC, NAME_DESC, SIZE_ASC, SIZE_DESC, DATE_DESC, DATE_ASC
}
