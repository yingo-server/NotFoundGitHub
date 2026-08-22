package io.nggit.model

import com.google.gson.annotations.SerializedName

data class FileInfo(
    @SerializedName("name") val name: String = "",
    @SerializedName("path") val path: String = "",
    @SerializedName("sha") val sha: String = "",
    @SerializedName("size") val size: Long = 0,
    @SerializedName("url") val url: String = "",
    @SerializedName("html_url") val htmlUrl: String = "",
    @SerializedName("git_url") val gitUrl: String = "",
    @SerializedName("download_url") val downloadUrl: String? = null,
    @SerializedName("type") val type: String = "file",
    @SerializedName("_links") val links: FileLinks? = null
) {
    fun isDir(): Boolean = type == "dir"

    fun getExtension(): String {
        val dotIndex = name.lastIndexOf('.')
        return if (dotIndex > 0) name.substring(dotIndex + 1).lowercase() else ""
    }

    fun getFileCategory(): FileCategory {
        val ext = getExtension()
        return when {
            TEXT_EXTENSIONS.contains(ext) -> FileCategory.TEXT
            IMAGE_EXTENSIONS.contains(ext) -> FileCategory.IMAGE
            AUDIO_EXTENSIONS.contains(ext) -> FileCategory.AUDIO
            VIDEO_EXTENSIONS.contains(ext) -> FileCategory.VIDEO
            CODE_EXTENSIONS.contains(ext) -> FileCategory.CODE
            MARKDOWN_EXTENSIONS.contains(ext) -> FileCategory.MARKDOWN
            CONFIG_EXTENSIONS.contains(ext) -> FileCategory.CONFIG
            ARCHIVE_EXTENSIONS.contains(ext) -> FileCategory.ARCHIVE
            else -> FileCategory.BINARY
        }
    }

    companion object {
        val TEXT_EXTENSIONS = setOf("txt", "log", "csv", "tsv", "rtf")
        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "svg", "ico", "bmp", "tiff")
        val AUDIO_EXTENSIONS = setOf("mp3", "wav", "ogg", "m4a", "aac", "flac", "wma")
        val VIDEO_EXTENSIONS = setOf("mp4", "avi", "mkv", "mov", "wmv", "webm", "flv")
        val CODE_EXTENSIONS = setOf(
            "java", "kt", "kts", "xml", "json", "yaml", "yml", "toml",
            "js", "ts", "jsx", "tsx", "html", "css", "scss", "less",
            "py", "rb", "go", "rs", "c", "cpp", "h", "hpp",
            "sh", "bash", "zsh", "bat", "cmd", "ps1",
            "sql", "graphql", "proto",
            "gradle", "properties", "gitignore", "dockerfile",
            "makefile", "cmake", "swift", "m", "r"
        )
        val MARKDOWN_EXTENSIONS = setOf("md", "mdx", "markdown", "rst")
        val CONFIG_EXTENSIONS = setOf(
            "ini", "cfg", "conf", "config", "env",
            "editorconfig", "prettierrc", "eslintrc"
        )
        val ARCHIVE_EXTENSIONS = setOf("zip", "tar", "gz", "bz2", "xz", "7z", "rar")
    }
}

enum class FileCategory {
    TEXT, IMAGE, AUDIO, VIDEO, CODE, MARKDOWN, CONFIG, ARCHIVE, BINARY
}

data class FileLinks(
    @SerializedName("self") val self: String = "",
    @SerializedName("git") val git: String = "",
    @SerializedName("html") val html: String = ""
)

data class FileContent(
    @SerializedName("name") val name: String = "",
    @SerializedName("path") val path: String = "",
    @SerializedName("sha") val sha: String = "",
    @SerializedName("size") val size: Long = 0,
    @SerializedName("content") val content: String = "",
    @SerializedName("encoding") val encoding: String = "",
    @SerializedName("download_url") val downloadUrl: String? = null,
    @SerializedName("type") val type: String = "file"
)

data class FileCommit(
    @SerializedName("content") val content: FileContent? = null,
    @SerializedName("commit") val commit: CommitInfo? = null
)

data class CommitInfo(
    @SerializedName("sha") val sha: String = "",
    @SerializedName("message") val message: String = "",
    @SerializedName("author") val author: RepoOwner = RepoOwner()
)

data class GitTreeItem(
    @SerializedName("path") val path: String = "",
    @SerializedName("mode") val mode: String = "",
    @SerializedName("type") val type: String = "",
    @SerializedName("size") val size: Long = 0,
    @SerializedName("sha") val sha: String = ""
)

data class GitTree(
    @SerializedName("sha") val sha: String = "",
    @SerializedName("tree") val tree: List<GitTreeItem> = emptyList(),
    @SerializedName("truncated") val truncated: Boolean = false
)

data class GitBlob(
    @SerializedName("content") val content: String = "",
    @SerializedName("encoding") val encoding: String = "",
    @SerializedName("sha") val sha: String = "",
    @SerializedName("size") val size: Long = 0
)
