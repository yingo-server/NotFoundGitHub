/**
 * 文件上传管理器，负责批量文件的GitHub API上传操作，支持重试机制、进度回调、新建文件和文件夹、文件删除和重命名等远程文件操作，采用4线程并发池加速上传。
 * 该管理器封装了与GitHub仓库文件交互的核心功能，支持文件的创建、更新、删除和重命名操作。
 * 通过多线程并发池实现批量文件的并行上传，显著提升上传效率，每个上传任务支持最多3次自动重试。
 * 提供完整的进度回调机制，包括上传开始、进度更新、完成和错误处理等状态通知。
 * 同时支持文件夹的创建（通过.gitkeep文件实现）和文件的远程重命名操作。
 * 上传完成后自动更新本地提交记录，确保本地与远程仓库状态同步。
 */
package io.nggit.sync

import android.content.Context
import android.os.Handler
import android.widget.Toast
import io.nggit.R
import android.os.Looper
import io.nggit.App
import io.nggit.auth.AuthManager
import io.nggit.model.CommitRecord
import io.nggit.service.GitHubApi
import io.nggit.util.StoragePath
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class UploadManager(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val api = App.instance.githubApi
    private val token = AuthManager.getToken() ?: ""
    private val executor by lazy { Executors.newFixedThreadPool(4) }

    /**
     * 上传回调接口，定义了文件上传过程中各个阶段的回调方法。
     * 包括上传开始、进度更新、完成和错误处理等回调函数。
     */
    interface UploadCallback {
        /**
         * 上传开始时回调，通知调用方文件上传操作已经启动。
         */
        fun onUploadStarted()

        /**
         * 上传进度更新回调，提供当前已处理文件数、总文件数和当前文件名信息。
         * 参数current为当前已处理的文件数量，total为总文件数量，fileName为当前处理的文件名。
         */
        fun onUploadProgress(current: Int, total: Int, fileName: String)

        /**
         * 上传完成回调，提供成功和失败的文件数量统计。
         * 参数successCount为成功上传的文件数量，failCount为上传失败的文件数量。
         */
        fun onUploadCompleted(successCount: Int, failCount: Int)

        /**
         * 上传错误回调，提供错误信息描述。
         * 参数error为错误描述字符串，用于向用户显示错误原因。
         */
        fun onUploadError(error: String)
    }

    /**
     * 批量上传文件到指定的GitHub仓库，支持重试机制和进度回调。
     * 参数owner为仓库所有者，repo为仓库名称，branch为目标分支，isStarred为是否已收藏，
     * files为待上传的文件三元组列表（路径、内容、现有SHA），callback为上传回调接口。
     */
    fun uploadFiles(
        owner: String,
        repo: String,
        branch: String,
        isStarred: Boolean,
        files: List<Triple<String, String, String?>>,
        callback: UploadCallback
    ) {
        if (files.isEmpty()) {
            callback.onUploadError(context.getString(R.string.upload_no_files))
            return
        }

        mainHandler.post { callback.onUploadStarted() }

        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)
        val processedCount = AtomicInteger(0)
        val totalFiles = files.size

        for (file in files) {
            executor.execute {
                val (path, content, existingSha) = file
                val fileName = File(path).name

                var attempt = 0
                var uploaded = false
                while (attempt < 3 && !uploaded) {
                    attempt++
                    try {
                        val message = if (existingSha != null) "Update $fileName" else "Create $fileName"
                        val result = api.createOrUpdateFile(token, owner, repo, path, content, existingSha, message, branch)
                        if (result != null) {
                            successCount.incrementAndGet()
                            uploaded = true
                        } else {
                            if (attempt < 3) Thread.sleep(1000)
                        }
                    } catch (e: Exception) {
                        if (attempt < 3) Thread.sleep(1000)
                    }
                }

                if (!uploaded) {
                    failCount.incrementAndGet()
                }

                val current = processedCount.incrementAndGet()
                mainHandler.post {
                    callback.onUploadProgress(current, totalFiles, fileName)
                }

                if (current >= totalFiles) {
                    try {
                        val finalSha = api.getLatestCommitSha(token, owner, repo, branch)
                        if (finalSha != null) {
                            val repoPath = StoragePath.getRepoPath(owner, repo, isStarred)
                            val existing = CommitManager.loadCommit(repoPath)
                            CommitManager.saveCommit(
                                repoPath,
                                CommitRecord(
                                    sha = finalSha,
                                    timestamp = System.currentTimeMillis(),
                                    fileCount = existing?.fileCount ?: totalFiles,
                                    lastModified = System.currentTimeMillis(),
                                    branch = branch
                                )
                            )
                        }
                    } catch (e: Exception) {
                        mainHandler.post {
                            Toast.makeText(context, context.getString(R.string.upload_saving), Toast.LENGTH_SHORT).show()
                        }
                    }

                    mainHandler.post {
                        callback.onUploadCompleted(successCount.get(), failCount.get())
                    }
                }
            }
        }
    }

    /**
     * 准备上传文件列表，扫描指定仓库路径下的所有文件并构建上传任务列表。
     * 参数repoPath为本地仓库根目录，返回待上传文件的三元组列表（相对路径、文件内容、SHA值）。
     */
    fun prepareUploadList(repoPath: File): List<Triple<String, String, String?>> {
        val files = mutableListOf<Triple<String, String, String?>>()

        fun scanDir(dir: File, relativePath: String = "") {
            dir.listFiles()?.forEach { file ->
                if (file.name == ".ng_commit.json") return@forEach
                if (file.name.startsWith(".")) return@forEach

                val relPath = if (relativePath.isEmpty()) file.name else "$relativePath/${file.name}"

                if (file.isDirectory) {
                    scanDir(file, relPath)
                } else {
                    try {
                        val content = file.readText(Charsets.UTF_8)
                        files.add(Triple(relPath, content, null))
                    } catch (_: Exception) {}
                }
            }
        }

        scanDir(repoPath)
        return files
    }

    /**
     * 在远程仓库中创建新文件，通过GitHub API在指定路径创建空文件或包含初始内容的文件。
     * 参数owner为仓库所有者，repo为仓库名称，branch为目标分支，path为文件路径，
     * content为文件内容（默认为空字符串），callback为操作结果回调函数。
     */
    fun createNewFile(
        owner: String,
        repo: String,
        branch: String,
        path: String,
        content: String = "",
        callback: (Boolean) -> Unit
    ) {
        executor.execute {
            try {
                val result = api.createOrUpdateFile(token, owner, repo, path, content, null, "Create ${File(path).name}", branch)
                mainHandler.post { callback(result != null) }
            } catch (e: Exception) {
                mainHandler.post { callback(false) }
            }
        }
    }

    /**
     * 在远程仓库中创建新文件夹，通过创建.gitkeep占位文件实现文件夹的创建。
     * 参数owner为仓库所有者，repo为仓库名称，branch为目标分支，folderPath为文件夹路径，
     * callback为操作结果回调函数。
     */
    fun createNewFolder(
        owner: String,
        repo: String,
        branch: String,
        folderPath: String,
        callback: (Boolean) -> Unit
    ) {
        val gitkeepPath = "$folderPath/.gitkeep"
        createNewFile(owner, repo, branch, gitkeepPath, "", callback)
    }

    /**
     * 删除远程仓库中的指定文件，先获取文件的真实SHA值，然后执行删除操作。
     * 参数owner为仓库所有者，repo为仓库名称，branch为目标分支，path为文件路径，
     * sha为文件SHA值（备用参数），callback为操作结果回调函数。
     */
    fun deleteFile(
        owner: String,
        repo: String,
        branch: String,
        path: String,
        sha: String,
        callback: (Boolean) -> Unit
    ) {
        executor.execute {
            try {
                val realSha = api.getFileSha(token, owner, repo, path, branch)
                if (realSha == null) {
                    mainHandler.post { callback(false) }
                    return@execute
                }
                val result = api.deleteFile(token, owner, repo, path, realSha, "Delete ${File(path).name}", branch)
                mainHandler.post { callback(result) }
            } catch (e: Exception) {
                mainHandler.post { callback(false) }
            }
        }
    }

    /**
     * 重命名远程仓库中的文件或文件夹，通过创建新文件并删除旧文件实现重命名操作。
     * 参数owner为仓库所有者，repo为仓库名称，branch为目标分支，oldPath为旧路径，
     * newPath为新路径，sha为文件SHA值，content为文件内容，callback为操作结果回调函数。
     */
    fun renameItem(
        owner: String,
        repo: String,
        branch: String,
        oldPath: String,
        newPath: String,
        sha: String,
        content: String,
        callback: (Boolean) -> Unit
    ) {
        executor.execute {
            try {
                val createResult = api.createOrUpdateFile(token, owner, repo, newPath, content, null, "Rename to ${File(newPath).name}", branch)
                if (createResult != null) {
                    val realSha = api.getFileSha(token, owner, repo, oldPath, branch)
                    if (realSha == null) {
                        mainHandler.post { callback(false) }
                        return@execute
                    }
                    val deleteResult = api.deleteFile(token, owner, repo, oldPath, realSha, "Delete old ${File(oldPath).name}", branch)
                    mainHandler.post { callback(deleteResult) }
                } else {
                    mainHandler.post { callback(false) }
                }
            } catch (e: Exception) {
                mainHandler.post { callback(false) }
            }
        }
    }
}
