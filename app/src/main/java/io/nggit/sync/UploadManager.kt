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
    private val executor = Executors.newFixedThreadPool(8)
    private val api = App.instance.githubApi
    private val token = AuthManager.getToken() ?: ""

    interface UploadCallback {
        fun onUploadStarted()
        fun onUploadProgress(current: Int, total: Int, fileName: String)
        fun onUploadCompleted(successCount: Int, failCount: Int)
        fun onUploadError(error: String)
    }

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
