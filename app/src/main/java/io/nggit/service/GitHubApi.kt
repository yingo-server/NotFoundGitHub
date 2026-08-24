/**
 * GitHub API 客户端封装类，提供与GitHub REST API交互的各种功能方法，
 * 包括用户认证、仓库管理、文件操作、分支管理、发布管理等全面的GitHub平台操作接口支持。
 * 本类通过OkHttp进行网络请求，并使用Gson进行JSON数据的序列化与反序列化处理。
 */
package io.nggit.service

import io.nggit.model.*
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import android.util.Log

class GitHubApi(private val client: OkHttpClient) {

    companion object {
        private const val TAG = "GitHubApi"
    }

    private val gson: Gson = GsonBuilder()
        .setDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
        .create()

    class ApiException(val code: Int, message: String) : Exception(message)

    private fun get(url: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) response.body?.string() else null
        } catch (e: IOException) {
            Log.e(TAG, "GET 请求失败: $url", e)
            null
        }
    }

    private fun getWithAuth(url: String, token: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "token $token")
                .get()
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) response.body?.string() else null
        } catch (e: IOException) {
            Log.e(TAG, "GET(认证) 请求失败: $url", e)
            null
        }
    }

    private fun post(url: String, json: String, token: String = ""): Response {
        val body = json.toRequestBody("application/json".toMediaType())
        val builder = Request.Builder()
            .url(url)
            .post(body)
        if (token.isNotEmpty()) builder.header("Authorization", "token $token")
        return try {
            client.newCall(builder.build()).execute()
        } catch (e: IOException) {
            Log.e(TAG, "POST 请求失败: $url", e)
            throw ApiException(-1, "网络请求失败: ${e.message}")
        }
    }

    private fun put(url: String, json: String, token: String = ""): Response {
        val body = json.toRequestBody("application/json".toMediaType())
        val builder = Request.Builder()
            .url(url)
            .put(body)
        if (token.isNotEmpty()) builder.header("Authorization", "token $token")
        return try {
            client.newCall(builder.build()).execute()
        } catch (e: IOException) {
            Log.e(TAG, "PUT 请求失败: $url", e)
            throw ApiException(-1, "网络请求失败: ${e.message}")
        }
    }

    private fun delete(url: String, token: String = ""): Response {
        val builder = Request.Builder()
            .url(url)
            .delete()
        if (token.isNotEmpty()) builder.header("Authorization", "token $token")
        return try {
            client.newCall(builder.build()).execute()
        } catch (e: IOException) {
            Log.e(TAG, "DELETE 请求失败: $url", e)
            throw ApiException(-1, "网络请求失败: ${e.message}")
        }
    }

    /** 通过访问令牌获取当前认证用户的详细信息，返回用户基本信息对象 */
    fun getUser(token: String): RepoOwner? {
        val json = getWithAuth("https://api.github.com/user", token) ?: return null
        return gson.fromJson(json, RepoOwner::class.java)
    }

    /** 获取指定用户拥有的所有仓库列表，支持分页自动加载获取完整数据 */
    fun listUserRepos(token: String): List<RepoInfo> {
        val results = mutableListOf<RepoInfo>()
        var page = 1
        while (true) {
            val json = getWithAuth(
                "https://api.github.com/user/repos?sort=updated&per_page=100&page=$page&affiliation=owner",
                token
            ) ?: break
            val repos = gson.fromJson(json, Array<RepoInfo>::class.java)?.toList() ?: emptyList()
            if (repos.isEmpty()) break
            results.addAll(repos)
            page++
            if (repos.size < 100) break
        }
        return results
    }

    /** 获取当前用户已星标的所有仓库列表，按星标时间倒序排列返回 */
    fun listStarredRepos(token: String): List<RepoInfo> {
        val results = mutableListOf<RepoInfo>()
        var page = 1
        while (true) {
            val json = getWithAuth(
                "https://api.github.com/user/starred?per_page=100&page=$page",
                token
            ) ?: break
            val repos = gson.fromJson(json, Array<RepoInfo>::class.java)?.toList() ?: emptyList()
            if (repos.isEmpty()) break
            results.addAll(repos)
            page++
            if (repos.size < 100) break
        }
        return results
    }

    /** 根据关键词搜索GitHub平台上的公开仓库，按星标数排序返回搜索结果 */
    fun searchPublicRepos(query: String): List<RepoInfo> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val json = get("https://api.github.com/search/repositories?q=$encoded&sort=stars&per_page=50")
            ?: return emptyList()
        val searchResult = gson.fromJson(json, SearchResult::class.java)
        return searchResult?.items ?: emptyList()
    }

    /** 获取指定仓库路径下的文件和目录列表信息，支持指定分支和路径参数 */
    fun getContents(token: String, owner: String, repo: String, path: String = "", branch: String = "main"): List<FileInfo> {
        val url = "https://api.github.com/repos/$owner/$repo/contents/$path?ref=$branch&t=${System.currentTimeMillis()}"
        val json = getWithAuth(url, token) ?: return emptyList()

        return try {
            val type = object : com.google.gson.reflect.TypeToken<List<FileInfo>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            try {
                val single = gson.fromJson(json, FileInfo::class.java)
                if (single != null) listOf(single) else emptyList()
            } catch (e2: Exception) {
                emptyList()
            }
        }
    }

    /** 获取指定文件的SHA哈希值，用于后续文件更新或删除操作的身份标识 */
    fun getFileSha(token: String, owner: String, repo: String, path: String, branch: String = "main"): String? {
        val url = "https://api.github.com/repos/$owner/$repo/contents/$path?ref=$branch"
        val json = getWithAuth(url, token) ?: return null
        return try {
            val file = gson.fromJson(json, FileInfo::class.java)
            file?.sha
        } catch (e: Exception) {
            null
        }
    }

    /** 根据文件SHA获取文件的二进制内容数据，返回Git Blob对象 */
    fun getFileContent(token: String, owner: String, repo: String, path: String, sha: String, branch: String = "main"): GitBlob? {
        val url = "https://api.github.com/repos/$owner/$repo/git/blobs/$sha"
        val json = getWithAuth(url, token) ?: return null
        return gson.fromJson(json, GitBlob::class.java)
    }

    /** 获取指定URL的原始文件内容，用于下载和显示文件数据 */
    fun getRawFile(url: String): String? {
        return get(url)
    }

    /** 获取指定分支的最新提交SHA值，用于创建新分支或进行提交操作 */
    fun getLatestCommitSha(token: String, owner: String, repo: String, branch: String = "main"): String? {
        val url = "https://api.github.com/repos/$owner/$repo/commits?sha=$branch&per_page=1"
        val json = getWithAuth(url, token) ?: return null
        return try {
            val commits = gson.fromJson(json, Array<CommitInfo>::class.java)
            commits?.firstOrNull()?.sha
        } catch (e: Exception) {
            null
        }
    }

    /** 获取指定文件的提交历史记录列表，支持分页和数量限制 */
    fun getFileCommits(token: String, owner: String, repo: String, path: String, branch: String = "main", perPage: Int = 10): List<CommitInfo> {
        val url = "https://api.github.com/repos/$owner/$repo/commits?sha=$branch&path=$path&per_page=$perPage"
        val json = getWithAuth(url, token) ?: return emptyList()
        return try {
            gson.fromJson(json, Array<CommitInfo>::class.java)?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 获取Git仓库的文件树结构信息，支持递归获取所有子目录文件 */
    fun getGitTree(token: String, owner: String, repo: String, sha: String, recursive: Boolean = true): GitTree? {
        val url = "https://api.github.com/repos/$owner/$repo/git/trees/$sha?recursive=${if (recursive) "1" else "0"}"
        val json = getWithAuth(url, token) ?: return null
        return gson.fromJson(json, GitTree::class.java)
    }

    /** 创建或更新仓库中的指定文件，支持新建文件和修改已有文件操作 */
    fun createOrUpdateFile(
        token: String, owner: String, repo: String, path: String,
        content: String, sha: String?, message: String, branch: String = "main"
    ): FileCommit? {
        val base64 = android.util.Base64.encodeToString(content.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
        val jsonMap = mutableMapOf<String, Any>(
            "message" to message,
            "content" to base64,
            "branch" to branch
        )
        if (sha != null) jsonMap["sha"] = sha

        val url = "https://api.github.com/repos/$owner/$repo/contents/$path"
        val response = put(url, gson.toJson(jsonMap), token)
        val body = response.body?.string() ?: return null
        return gson.fromJson(body, FileCommit::class.java)
    }

    /** 删除仓库中的指定文件，需要提供文件SHA和删除提交信息 */
    fun deleteFile(
        token: String, owner: String, repo: String, path: String,
        sha: String, message: String, branch: String = "main"
    ): Boolean {
        val json = gson.toJson(mapOf("message" to message, "sha" to sha, "branch" to branch))
        val url = "https://api.github.com/repos/$owner/$repo/contents/$path"
        val body = json.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .delete(body)
            .header("Authorization", "token $token")
            .build()
        val response = client.newCall(request).execute()
        return response.isSuccessful
    }

    /** 获取指定仓库的所有分支列表，支持分页获取完整数据 */
    fun getBranches(token: String, owner: String, repo: String): List<RepoBranch> {
        val results = mutableListOf<RepoBranch>()
        var page = 1
        while (true) {
            val json = getWithAuth(
                "https://api.github.com/repos/$owner/$repo/branches?per_page=100&page=$page",
                token
            ) ?: break
            val branches = gson.fromJson(json, Array<RepoBranch>::class.java)?.toList() ?: emptyList()
            if (branches.isEmpty()) break
            results.addAll(branches)
            page++
            if (branches.size < 100) break
        }
        return results
    }

    /** 基于指定源分支创建新的Git分支，返回创建操作是否成功 */
    fun createBranch(token: String, owner: String, repo: String, newBranch: String, fromBranch: String): Boolean {
        val sha = getLatestCommitSha(token, owner, repo, fromBranch) ?: return false
        val json = gson.toJson(mapOf("ref" to "refs/heads/$newBranch", "sha" to sha))
        val url = "https://api.github.com/repos/$owner/$repo/git/refs"
        val response = post(url, json, token)
        return response.isSuccessful
    }

    /** 删除指定仓库的指定分支，返回删除操作是否成功 */
    fun deleteBranch(token: String, owner: String, repo: String, branch: String): Boolean {
        val url = "https://api.github.com/repos/$owner/$repo/git/refs/heads/$branch"
        val response = delete(url, token)
        return response.isSuccessful
    }

    /** 重命名指定仓库的分支名称，返回重命名操作是否成功 */
    fun renameBranch(token: String, owner: String, repo: String, oldName: String, newName: String): Boolean {
        val json = gson.toJson(mapOf("new_name" to newName))
        val url = "https://api.github.com/repos/$owner/$repo/branches/$oldName/rename"
        val response = post(url, json, token)
        return response.isSuccessful
    }

    /** 获取指定仓库的所有发布版本列表，按发布时间倒序排列 */
    fun getReleases(token: String, owner: String, repo: String): List<RepoRelease> {
        val json = getWithAuth(
            "https://api.github.com/repos/$owner/$repo/releases?per_page=50",
            token
        ) ?: return emptyList()
        return gson.fromJson(json, Array<RepoRelease>::class.java)?.toList() ?: emptyList()
    }

    /** 创建新的仓库发布版本，指定标签名、标题和描述信息 */
    fun createRelease(token: String, owner: String, repo: String, tag: String, title: String, body: String): RepoRelease? {
        val json = gson.toJson(mapOf("tag_name" to tag, "name" to title, "body" to body))
        val url = "https://api.github.com/repos/$owner/$repo/releases"
        val response = post(url, json, token)
        val bodyStr = response.body?.string() ?: return null
        return gson.fromJson(bodyStr, RepoRelease::class.java)
    }

    /** 获取仓库的GitHub Pages配置信息，包括部署状态和访问地址 */
    fun getPages(token: String, owner: String, repo: String): RepoPages? {
        val json = getWithAuth(
            "https://api.github.com/repos/$owner/$repo/pages",
            token
        ) ?: return null
        return gson.fromJson(json, RepoPages::class.java)
    }

    /** 启用仓库的GitHub Pages功能，指定部署的源分支 */
    fun enablePages(token: String, owner: String, repo: String, branch: String): Boolean {
        val json = gson.toJson(mapOf("source" to mapOf("branch" to branch, "path" to "/")))
        val url = "https://api.github.com/repos/$owner/$repo/pages"
        val response = post(url, json, token)
        return response.isSuccessful
    }

    /** 禁用仓库的GitHub Pages功能，删除Pages配置 */
    fun deletePages(token: String, owner: String, repo: String): Boolean {
        val url = "https://api.github.com/repos/$owner/$repo/pages"
        val response = delete(url, token)
        return response.isSuccessful
    }

    /** 设置仓库GitHub Pages的自定义域名，配置CNAME记录 */
    fun setCustomDomain(token: String, owner: String, repo: String, cname: String): Boolean {
        return try {
            val json = gson.toJson(mapOf("cname" to cname))
            val url = "https://api.github.com/repos/$owner/$repo/pages"
            val request = Request.Builder()
                .url(url)
                .method("PUT", json.toRequestBody("application/json".toMediaType()))
                .header("Authorization", "token $token")
                .header("Accept", "application/vnd.github.v3+json")
                .build()
            client.newCall(request).execute().isSuccessful
        } catch (e: IOException) {
            Log.e(TAG, "设置自定义域名失败", e)
            false
        }
    }

    /** 将指定仓库添加到当前用户的星标收藏列表 */
    fun starRepo(token: String, owner: String, repo: String): Boolean {
        return try {
            val url = "https://api.github.com/user/starred/$owner/$repo"
            val request = Request.Builder()
                .url(url)
                .put("".toRequestBody(null))
                .header("Authorization", "token $token")
                .build()
            client.newCall(request).execute().isSuccessful
        } catch (e: IOException) {
            Log.e(TAG, "星标仓库失败", e)
            false
        }
    }

    /** 将指定仓库从当前用户的星标收藏列表中移除 */
    fun unstarRepo(token: String, owner: String, repo: String): Boolean {
        return try {
            val url = "https://api.github.com/user/starred/$owner/$repo"
            val request = Request.Builder()
                .url(url)
                .delete()
                .header("Authorization", "token $token")
                .build()
            client.newCall(request).execute().isSuccessful
        } catch (e: IOException) {
            Log.e(TAG, "取消星标失败", e)
            false
        }
    }

    /** 永久删除指定仓库，此操作不可逆需谨慎使用 */
    fun deleteRepo(token: String, owner: String, repo: String): Boolean {
        return try {
            val url = "https://api.github.com/repos/$owner/$repo"
            val request = Request.Builder()
                .url(url)
                .delete()
                .header("Authorization", "token $token")
                .build()
            client.newCall(request).execute().isSuccessful
        } catch (e: IOException) {
            Log.e(TAG, "删除仓库失败", e)
            false
        }
    }

    /** 创建新的GitHub仓库，指定仓库名称、描述和公开私有属性 */
    fun createRepo(token: String, name: String, description: String, isPrivate: Boolean): RepoInfo? {
        val json = gson.toJson(mapOf("name" to name, "description" to description, "private" to isPrivate, "auto_init" to true))
        val url = "https://api.github.com/user/repos"
        val response = post(url, json, token)
        val body = response.body?.string() ?: return null
        return gson.fromJson(body, RepoInfo::class.java)
    }

    /** 复制指定仓库到当前用户账户下，支持自定义新仓库名称 */
    fun forkRepo(token: String, owner: String, repo: String, newName: String?): RepoInfo? {
        val json = if (newName != null) gson.toJson(mapOf("name" to newName)) else "{}"
        val url = "https://api.github.com/repos/$owner/$repo/forks"
        val response = post(url, json, token)
        val body = response.body?.string() ?: return null
        return gson.fromJson(body, RepoInfo::class.java)
    }

    /** 手动触发指定GitHub Actions工作流的执行，返回触发结果状态 */
    fun dispatchWorkflow(token: String, owner: String, repo: String, workflowPath: String, branch: String = "main"): Boolean {
        return try {
            val fileName = workflowPath.split("/").last()
            val json = gson.toJson(mapOf("ref" to branch))
            val url = "https://api.github.com/repos/$owner/$repo/actions/workflows/$fileName/dispatches"
            val request = Request.Builder()
                .url(url)
                .method("POST", json.toRequestBody("application/json".toMediaType()))
                .header("Authorization", "token $token")
                .header("Accept", "application/vnd.github.v3+json")
                .build()
            client.newCall(request).execute().code == 204
        } catch (e: IOException) {
            Log.e(TAG, "触发工作流失败", e)
            false
        }
    }

    /** 重命名指定仓库的名称，更新仓库的显示名称 */
    fun renameRepo(token: String, owner: String, oldName: String, newName: String): Boolean {
        return try {
            val json = gson.toJson(mapOf("name" to newName))
            val url = "https://api.github.com/repos/$owner/$oldName"
            val request = Request.Builder()
                .url(url)
                .method("PATCH", json.toRequestBody("application/json".toMediaType()))
                .header("Authorization", "token $token")
                .build()
            client.newCall(request).execute().isSuccessful
        } catch (e: IOException) {
            Log.e(TAG, "重命名仓库失败", e)
            false
        }
    }

    /** 更新仓库的基本设置信息，包括名称、描述和公开私有属性 */
    fun updateRepoSettings(token: String, owner: String, repo: String, name: String, description: String?, isPrivate: Boolean): Boolean {
        return try {
            val jsonMap = mutableMapOf<String, Any>(
                "name" to name,
                "private" to isPrivate
            )
            if (description != null) jsonMap["description"] = description
            val json = gson.toJson(jsonMap)
            val url = "https://api.github.com/repos/$owner/$repo"
            val request = Request.Builder()
                .url(url)
                .method("PATCH", json.toRequestBody("application/json".toMediaType()))
                .header("Authorization", "token $token")
                .build()
            client.newCall(request).execute().isSuccessful
        } catch (e: IOException) {
            Log.e(TAG, "更新仓库设置失败", e)
            false
        }
    }

    class SearchResult {
        var total_count: Int = 0
        var incomplete_results: Boolean = false
        var items: List<RepoInfo> = emptyList()
    }
}
