/**
 * 数据模型定义文件，包含GitHub API返回的文件信息、提交信息、Git树结构等核心数据类。
 * 本文件定义了与GitHub仓库文件操作相关的所有数据模型，包括文件基本信息、文件链接、
 * 文件内容、提交信息、Git树结构、Git对象等。这些数据类通过Gson反序列化从GitHub
 * REST API获取的JSON数据，为应用提供类型安全的数据访问方式。支持文件类型分类、
 * 扩展名提取、目录判断等实用功能，是文件浏览和管理模块的核心数据层。
 */
package io.nggit.model

import com.google.gson.annotations.SerializedName

/**
 * 文件信息数据类，表示GitHub仓库中的一个文件或目录。
 * 包含文件的名称、路径、SHA哈希、大小、各类URL以及文件类型等核心属性。
 * 支持判断是否为目录、获取文件扩展名、根据扩展名确定文件类别等功能。
 */
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
    @SerializedName("_links") val links: FileLinks? = null,
    /** 文件最后修改时间戳，用于本地缓存更新判断 */
    var lastModified: Long = 0
) {
    /** 判断当前文件信息是否表示一个目录，根据type字段是否为"dir"来确定 */
    fun isDir(): Boolean = type == "dir"

    /**
     * 获取文件的扩展名（小写形式）。
     * 通过查找文件名中最后一个点号的位置来提取扩展名。
     * @return 文件扩展名字符串，如果文件名中没有点号则返回空字符串
     */
    fun getExtension(): String {
        val dotIndex = name.lastIndexOf('.')
        return if (dotIndex > 0) name.substring(dotIndex + 1).lowercase() else ""
    }

    /**
     * 根据文件扩展名确定文件的分类类别。
     * 支持文本、图片、音频、视频、代码、Markdown、配置、压缩包等分类。
     * 如果扩展名不属于任何已知类别，则归类为二进制文件。
     * @return 文件所属的FileCategory枚举值
     */
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

    /**
     * 伴生对象，包含文件扩展名分类的静态常量集合。
     * 定义了各类文件的扩展名列表，用于文件类型判断和分类。
     */
    companion object {
        /** 文本文件扩展名集合，包括纯文本、日志、表格数据等格式 */
        val TEXT_EXTENSIONS = setOf("txt", "log", "csv", "tsv", "rtf")
        /** 图片文件扩展名集合，包括常见的位图和矢量图格式 */
        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "svg", "ico", "bmp", "tiff")
        /** 音频文件扩展名集合，包括压缩和无损音频格式 */
        val AUDIO_EXTENSIONS = setOf("mp3", "wav", "ogg", "m4a", "aac", "flac", "wma")
        /** 视频文件扩展名集合，包括常见的流媒体和容器格式 */
        val VIDEO_EXTENSIONS = setOf("mp4", "avi", "mkv", "mov", "wmv", "webm", "flv")
        /** 编程语言和源代码文件扩展名集合，覆盖主流编程语言和构建配置文件 */
        val CODE_EXTENSIONS = setOf(
            "java", "kt", "kts", "xml", "json", "yaml", "yml", "toml",
            "js", "ts", "jsx", "tsx", "html", "css", "scss", "less",
            "py", "rb", "go", "rs", "c", "cpp", "h", "hpp",
            "sh", "bash", "zsh", "bat", "cmd", "ps1",
            "sql", "graphql", "proto",
            "gradle", "properties", "gitignore", "dockerfile",
            "makefile", "cmake", "swift", "m", "r"
        )
        /** Markdown和文档标记语言文件扩展名集合 */
        val MARKDOWN_EXTENSIONS = setOf("md", "mdx", "markdown", "rst")
        /** 配置文件扩展名集合，包括各类工具和框架的配置文件格式 */
        val CONFIG_EXTENSIONS = setOf(
            "ini", "cfg", "conf", "config", "env",
            "editorconfig", "prettierrc", "eslintrc"
        )
        /** 压缩包和归档文件扩展名集合 */
        val ARCHIVE_EXTENSIONS = setOf("zip", "tar", "gz", "bz2", "xz", "7z", "rar")
    }
}

/**
 * 文件分类枚举，定义了支持的所有文件类型类别。
 * 用于根据扩展名对文件进行分类，方便在界面上展示不同的图标或处理逻辑。
 */
enum class FileCategory {
    TEXT, IMAGE, AUDIO, VIDEO, CODE, MARKDOWN, CONFIG, ARCHIVE, BINARY
}

/**
 * 文件链接数据类，包含GitHub API返回的文件相关URL链接。
 * 提供自引用链接、Git链接和HTML页面链接三种访问方式。
 */
data class FileLinks(
    @SerializedName("self") val self: String = "",
    @SerializedName("git") val git: String = "",
    /** HTML页面链接，用于在浏览器中查看文件 */
    @SerializedName("html") val html: String = ""
)

/**
 * 文件内容数据类，表示从GitHub API获取的文件详细内容信息。
 * 包含文件的Base64编码内容、编码方式、下载地址等属性。
 * 通常用于查看单个文件的完整内容和元数据。
 */
data class FileContent(
    @SerializedName("name") val name: String = "",
    @SerializedName("path") val path: String = "",
    @SerializedName("sha") val sha: String = "",
    @SerializedName("size") val size: Long = 0,
    @SerializedName("content") val content: String = "",
    @SerializedName("encoding") val encoding: String = "",
    @SerializedName("download_url") val downloadUrl: String? = null,
    /** 文件类型，如"file"或"dir" */
    @SerializedName("type") val type: String = "file"
)

/**
 * 文件提交数据类，表示文件内容与其关联的提交信息的组合。
 * 在创建或更新文件时，GitHub API会同时返回文件内容和对应的提交记录。
 */
data class FileCommit(
    @SerializedName("content") val content: FileContent? = null,
    /** 关联的提交信息，包含提交SHA、消息和作者等 */
    @SerializedName("commit") val commit: CommitInfo? = null
)

/**
 * 提交信息数据类，表示一次Git提交的基本信息。
 * 包含提交的SHA哈希值、提交消息和作者信息。
 */
data class CommitInfo(
    @SerializedName("sha") val sha: String = "",
    @SerializedName("message") val message: String = "",
    /** 提交作者信息 */
    @SerializedName("author") val author: RepoOwner = RepoOwner()
)

/**
 * Git树条目数据类，表示Git树对象中的一个文件或目录条目。
 * 包含路径、文件模式、类型、大小和SHA哈希等Git对象元数据。
 * 用于遍历仓库的目录结构和文件树。
 */
data class GitTreeItem(
    @SerializedName("path") val path: String = "",
    @SerializedName("mode") val mode: String = "",
    @SerializedName("type") val type: String = "",
    @SerializedName("size") val size: Long = 0,
    /** 对象SHA哈希值，用于唯一标识Git对象 */
    @SerializedName("sha") val sha: String = ""
)

/**
 * Git树数据类，表示一个完整的Git树对象，包含多个文件和目录条目。
 * 用于获取仓库在某个提交时的完整目录结构快照。
 * 如果结果被截断，truncated字段将为true，表示需要分页获取剩余数据。
 */
data class GitTree(
    @SerializedName("sha") val sha: String = "",
    @SerializedName("tree") val tree: List<GitTreeItem> = emptyList(),
    /** 是否数据被截断，为true时表示需要分页获取完整数据 */
    @SerializedName("truncated") val truncated: Boolean = false
)

/**
 * Git二进制大对象数据类，表示Git仓库中的一个blob对象。
 * 包含文件内容的编码表示、编码方式、SHA哈希和大小信息。
 * blob对象是Git中存储文件内容的基本单元。
 */
data class GitBlob(
    @SerializedName("content") val content: String = "",
    @SerializedName("encoding") val encoding: String = "",
    @SerializedName("sha") val sha: String = "",
    @SerializedName("size") val size: Long = 0
)
