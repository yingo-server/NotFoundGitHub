/**
 * GitHub仓库信息数据模型，包含仓库基本信息、分支、发布版本、Pages部署等相关数据结构。
 * 本文件定义了与GitHub仓库管理相关的所有数据模型，包括仓库详情、仓库所有者、
 * 分支信息、分支提交、发布版本、发布资产、Pages部署配置等。这些数据类通过
 * Gson反序列化从GitHub REST API获取的JSON数据，为仓库浏览、分支管理、
 * 版本发布和Pages部署等功能提供类型安全的数据访问支持。
 */
package io.nggit.model

import com.google.gson.annotations.SerializedName

/**
 * 仓库信息数据类，表示GitHub仓库的完整元数据。
 * 包含仓库的名称、描述、可见性、默认分支、统计数据、所有者等核心属性。
 * 是仓库列表和详情页面的主要数据模型。
 */
data class RepoInfo(
    @SerializedName("id") val id: Long = 0,
    @SerializedName("name") val name: String = "",
    @SerializedName("full_name") val fullName: String = "",
    @SerializedName("description") val description: String? = null,
    @SerializedName("private") val isPrivate: Boolean = false,
    @SerializedName("html_url") val htmlUrl: String = "",
    @SerializedName("default_branch") val defaultBranch: String = "main",
    @SerializedName("has_pages") val hasPages: Boolean = false,
    @SerializedName("homepage") val homepage: String? = null,
    @SerializedName("size") val size: Int = 0,
    @SerializedName("stargazers_count") val stargazersCount: Int = 0,
    @SerializedName("forks_count") val forksCount: Int = 0,
    @SerializedName("open_issues_count") val openIssuesCount: Int = 0,
    @SerializedName("language") val language: String? = null,
    @SerializedName("updated_at") val updatedAt: String = "",
    @SerializedName("created_at") val createdAt: String = "",
    @SerializedName("owner") val owner: RepoOwner = RepoOwner(),
    @SerializedName("archived") val isArchived: Boolean = false,
    /** 是否为Fork仓库 */
    @SerializedName("fork") val isFork: Boolean = false
) {
    /**
     * 获取仓库所有者的登录名（用户名或组织名）。
     * @return 所有者的登录名字符串
     */
    fun getOwnerLogin(): String = owner.login

    /**
     * 获取仓库的显示名称。
     * 优先使用full_name（如"owner/repo"），如果为空则拼接所有者登录名和仓库名。
     * @return 用于界面显示的仓库全名
     */
    fun getDisplayName(): String = fullName.ifEmpty { "${getOwnerLogin()}/$name" }
}

/**
 * 仓库所有者数据类，表示拥有仓库的GitHub用户或组织。
 * 包含登录名、头像URL和用户ID等基本信息。
 */
data class RepoOwner(
    @SerializedName("login") val login: String = "",
    @SerializedName("avatar_url") val avatarUrl: String = "",
    /** 用户或组织的数字ID */
    @SerializedName("id") val id: Long = 0
)

/**
 * 分支信息数据类，表示仓库中的一个Git分支。
 * 包含分支名称、分支指向的最新提交以及保护状态等信息。
 */
data class RepoBranch(
    @SerializedName("name") val name: String = "",
    @SerializedName("commit") val commit: BranchCommit? = null,
    /** 分支是否受到保护，受保护分支需要PR审核才能合并 */
    @SerializedName("protected") val isProtected: Boolean = false
)

/**
 * 分支提交数据类，表示分支指向的最新提交信息。
 * 包含提交的SHA哈希值和对应的API URL。
 */
data class BranchCommit(
    @SerializedName("sha") val sha: String = "",
    /** 提交的API URL */
    @SerializedName("url") val url: String = ""
)

/**
 * 仓库发布版本数据类，表示GitHub仓库的一个Release版本。
 * 包含版本标签、名称、描述、草稿/预发布状态、发布时间、发布资产和作者等信息。
 * 用于展示和管理仓库的版本发布历史。
 */
data class RepoRelease(
    @SerializedName("id") val id: Long = 0,
    @SerializedName("tag_name") val tagName: String = "",
    @SerializedName("name") val name: String? = null,
    @SerializedName("body") val body: String? = null,
    @SerializedName("draft") val isDraft: Boolean = false,
    @SerializedName("prerelease") val isPrerelease: Boolean = false,
    @SerializedName("published_at") val publishedAt: String = "",
    @SerializedName("assets") val assets: List<ReleaseAsset> = emptyList(),
    /** 版本发布者信息 */
    @SerializedName("author") val author: RepoOwner = RepoOwner()
)

/**
 * 发布资产数据类，表示Release版本中附带的可下载文件。
 * 包含资产ID、文件名、文件大小和下载URL等信息。
 * 用于展示和下载Release版本的附件文件。
 */
data class ReleaseAsset(
    @SerializedName("id") val id: Long = 0,
    @SerializedName("name") val name: String = "",
    @SerializedName("size") val size: Long = 0,
    /** 浏览器下载URL，用于直接下载该资产文件 */
    @SerializedName("browser_download_url") val downloadUrl: String = ""
)

/**
 * 仓库Pages部署数据类，表示GitHub Pages站点的配置和状态信息。
 * 包含站点URL、部署状态、自定义域名、源分支配置、可见性和HTTPS强制等信息。
 * 用于管理和展示仓库的GitHub Pages部署情况。
 */
data class RepoPages(
    @SerializedName("html_url") val htmlUrl: String = "",
    @SerializedName("status") val status: String = "",
    @SerializedName("cname") val cname: String? = null,
    @SerializedName("source") val source: PagesSource? = null,
    @SerializedName("public") val isPublic: Boolean = false,
    /** 是否强制使用HTTPS访问 */
    @SerializedName("https_enforced") val isHttpsEnforced: Boolean = false
)

/**
 * Pages源配置数据类，表示GitHub Pages部署的源分支和目录路径。
 * 指定从哪个分支的哪个目录构建和部署Pages站点。
 */
data class PagesSource(
    /** 部署源分支名称 */
    @SerializedName("branch") val branch: String = "",
    /** 部署源目录路径，默认为根目录"/" */
    @SerializedName("path") val path: String = "/"
)
