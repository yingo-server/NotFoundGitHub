package io.nggit.ui.main

data class FilePaneState(
    var paneId: Int = 0,
    var repoOwner: String = "",
    var repoName: String = "",
    var branch: String = "main",
    var currentPath: String = "",
    var isRemote: Boolean = true,
    var isStarred: Boolean = false,
    val history: MutableList<String> = mutableListOf(""),
    var historyIndex: Int = 0,
    var files: List<io.nggit.model.FileInfo> = emptyList(),
    var sortMode: SortMode = SortMode.NAME_ASC,
    var showHidden: Boolean = false
) {
    fun canGoBack(): Boolean = historyIndex > 0
    fun canGoForward(): Boolean = historyIndex < history.size - 1

    fun pushPath(path: String) {
        if (historyIndex < history.size - 1) {
            history.subList(historyIndex + 1, history.size).clear()
        }
        history.add(path)
        historyIndex = history.size - 1
        currentPath = path
    }

    fun goBack(): String? {
        if (!canGoBack()) return null
        historyIndex--
        currentPath = history[historyIndex]
        return currentPath
    }

    fun goForward(): String? {
        if (!canGoForward()) return null
        historyIndex++
        currentPath = history[historyIndex]
        return currentPath
    }

    fun getDisplayPath(): String {
        if (isRemote) {
            val prefix = "$repoOwner/$repoName/"
            return if (currentPath.isEmpty()) prefix else "$prefix$currentPath"
        }
        return if (currentPath.isEmpty()) "/" else "/$currentPath"
    }
}

enum class SortMode {
    NAME_ASC, NAME_DESC, SIZE_ASC, SIZE_DESC
}
