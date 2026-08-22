package io.nggit.model

import com.google.gson.annotations.SerializedName

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
    @SerializedName("fork") val isFork: Boolean = false
) {
    fun getOwnerLogin(): String = owner.login
    fun getDisplayName(): String = fullName.ifEmpty { "${getOwnerLogin()}/$name" }
}

data class RepoOwner(
    @SerializedName("login") val login: String = "",
    @SerializedName("avatar_url") val avatarUrl: String = "",
    @SerializedName("id") val id: Long = 0
)

data class RepoBranch(
    @SerializedName("name") val name: String = "",
    @SerializedName("commit") val commit: BranchCommit? = null,
    @SerializedName("protected") val isProtected: Boolean = false
)

data class BranchCommit(
    @SerializedName("sha") val sha: String = "",
    @SerializedName("url") val url: String = ""
)

data class RepoRelease(
    @SerializedName("id") val id: Long = 0,
    @SerializedName("tag_name") val tagName: String = "",
    @SerializedName("name") val name: String? = null,
    @SerializedName("body") val body: String? = null,
    @SerializedName("draft") val isDraft: Boolean = false,
    @SerializedName("prerelease") val isPrerelease: Boolean = false,
    @SerializedName("published_at") val publishedAt: String = "",
    @SerializedName("assets") val assets: List<ReleaseAsset> = emptyList(),
    @SerializedName("author") val author: RepoOwner = RepoOwner()
)

data class ReleaseAsset(
    @SerializedName("id") val id: Long = 0,
    @SerializedName("name") val name: String = "",
    @SerializedName("size") val size: Long = 0,
    @SerializedName("browser_download_url") val downloadUrl: String = ""
)

data class RepoPages(
    @SerializedName("html_url") val htmlUrl: String = "",
    @SerializedName("status") val status: String = "",
    @SerializedName("cname") val cname: String? = null,
    @SerializedName("source") val source: PagesSource? = null,
    @SerializedName("public") val isPublic: Boolean = false,
    @SerializedName("https_enforced") val isHttpsEnforced: Boolean = false
)

data class PagesSource(
    @SerializedName("branch") val branch: String = "",
    @SerializedName("path") val path: String = "/"
)
