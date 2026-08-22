package io.nggit.sync

import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import io.nggit.App
import io.nggit.R
import io.nggit.auth.AuthManager
import io.nggit.model.CommitRecord
import io.nggit.model.SyncConflict
import io.nggit.service.GitHubApi
import io.nggit.util.DownloadUtil
import io.nggit.util.StoragePath
import io.nggit.util.ZipUtil
import java.io.File
import java.util.concurrent.Executors

class SyncManager(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val api = App.instance.githubApi
    private val token = AuthManager.getToken() ?: ""

    interface SyncCallback {
        fun onSyncStarted()
        fun onProgress(current: Int, total: Int, message: String)
        fun onSyncCompleted(fileCount: Int)
        fun onSyncError(error: String)
        fun onConflictDetected(conflict: SyncConflict)
    }

    fun checkAndSync(
        owner: String,
        repo: String,
        branch: String,
        isStarred: Boolean,
        callback: SyncCallback
    ) {
        val repoPath = StoragePath.getRepoPath(owner, repo, isStarred)
        val localCommit = CommitManager.loadCommit(repoPath)

        executor.execute {
            try {
                val remoteSha = api.getLatestCommitSha(token, owner, repo, branch)

                if (remoteSha != null && localCommit != null && localCommit.sha == remoteSha) {
                    mainHandler.post {
                        Toast.makeText(context, context.getString(R.string.sync_already_latest), Toast.LENGTH_SHORT).show()
                        callback.onSyncCompleted(localCommit.fileCount)
                    }
                    return@execute
                }

                if (localCommit != null && remoteSha != null && localCommit.sha != remoteSha) {
                    mainHandler.post {
                        showConflictDialog(
                            owner, repo, branch, isStarred,
                            localCommit, remoteSha, callback
                        )
                    }
                    return@execute
                }

                performFullSync(owner, repo, branch, isStarred, remoteSha, callback)
            } catch (e: Exception) {
                mainHandler.post {
                    callback.onSyncError(context.getString(R.string.sync_check_fail, e.message))
                }
            }
        }
    }

    private fun performFullSync(
        owner: String,
        repo: String,
        branch: String,
        isStarred: Boolean,
        remoteSha: String?,
        callback: SyncCallback
    ) {
        mainHandler.post { callback.onSyncStarted() }

        val repoPath = StoragePath.getRepoPath(owner, repo, isStarred)
        val repoDirName = StoragePath.getRepoDirName(owner, repo)
        val zipFileName = "${repoDirName}.zip"

        try {
            val zipFile = File(context.cacheDir, zipFileName)

            mainHandler.post { callback.onProgress(0, 100, context.getString(R.string.sync_downloading)) }

            val zipUrl = "https://codeload.github.com/$owner/$repo/zip/refs/heads/$branch"
            val success = DownloadUtil.downloadWithProxy(zipUrl, zipFile) { downloaded, total ->
                if (total > 0) {
                    val progress = (downloaded * 100 / total).toInt()
                    mainHandler.post { callback.onProgress(progress, 100, context.getString(R.string.sync_downloading_progress, progress)) }
                }
            }

            if (!success) {
                mainHandler.post { callback.onSyncError(context.getString(R.string.sync_download_fail)) }
                return
            }

            mainHandler.post { callback.onProgress(100, 100, context.getString(R.string.sync_extracting)) }

            if (repoPath.exists()) {
                repoPath.listFiles()?.forEach { it.deleteRecursively() }
            }
            repoPath.mkdirs()

            val fileCount = ZipUtil.unzip(zipFile, repoPath) { current, total, name ->
                mainHandler.post {
                    val progress = (current * 100 / total)
                    callback.onProgress(progress, 100, context.getString(R.string.sync_extract_progress, name))
                }
            }

            zipFile.delete()

            val finalSha = remoteSha ?: api.getLatestCommitSha(token, owner, repo, branch) ?: "unknown"
            val commitRecord = CommitRecord(
                sha = finalSha,
                message = "sync",
                timestamp = System.currentTimeMillis(),
                fileCount = fileCount,
                lastModified = System.currentTimeMillis(),
                branch = branch
            )
            CommitManager.saveCommit(repoPath, commitRecord)

            mainHandler.post {
                callback.onSyncCompleted(fileCount)
                Toast.makeText(context, context.getString(R.string.sync_complete, fileCount), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            mainHandler.post { callback.onSyncError(context.getString(R.string.sync_fail, e.message)) }
        }
    }

    private fun showConflictDialog(
        owner: String,
        repo: String,
        branch: String,
        isStarred: Boolean,
        localCommit: CommitRecord,
        remoteSha: String,
        callback: SyncCallback
    ) {
        val conflict = SyncConflict(
            repoOwner = owner,
            repoName = repo,
            localSha = localCommit.sha,
            remoteSha = remoteSha,
            localTimestamp = localCommit.lastModified,
            remoteTimestamp = System.currentTimeMillis()
        )

        val localShort = localCommit.sha.take(7)
        val remoteShort = remoteSha.take(7)

        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.sync_conflict))
            .setMessage(
                context.getString(R.string.sync_message, repo, localShort, remoteShort)
            )
            .setPositiveButton(context.getString(R.string.sync_override_remote)) { _, _ ->
                performFullSync(owner, repo, branch, isStarred, remoteSha, callback)
            }
            .setNeutralButton(context.getString(R.string.sync_override_local)) { _, _ ->
                callback.onConflictDetected(conflict)
            }
            .setNegativeButton(context.getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    fun syncToCloud(
        owner: String,
        repo: String,
        branch: String,
        isStarred: Boolean,
        filesToUpload: List<Triple<String, String, String?>>,
        callback: SyncCallback
    ) {
        executor.execute {
            mainHandler.post { callback.onSyncStarted() }
            var successCount = 0
            var failCount = 0

            for ((index, file) in filesToUpload.withIndex()) {
                val (path, content, existingSha) = file
                mainHandler.post {
                    callback.onProgress(index + 1, filesToUpload.size, "上传: ${File(path).name}")
                }

                try {
                    val message = "Update ${File(path).name}"
                    val result = api.createOrUpdateFile(token, owner, repo, path, content, existingSha, message, branch)
                    if (result != null) {
                        successCount++
                    } else {
                        failCount++
                    }
                } catch (e: Exception) {
                    failCount++
                }

                try {
                    Thread.sleep(200)
                } catch (_: InterruptedException) {
                    break
                }
            }

            try {
                val finalSha = api.getLatestCommitSha(token, owner, repo, branch)
                if (finalSha != null) {
                    val repoPath = StoragePath.getRepoPath(owner, repo, isStarred)
                    val existingCommit = CommitManager.loadCommit(repoPath)
                    CommitManager.saveCommit(
                        repoPath,
                        CommitRecord(
                            sha = finalSha,
                            timestamp = System.currentTimeMillis(),
                            fileCount = existingCommit?.fileCount ?: filesToUpload.size,
                            lastModified = System.currentTimeMillis(),
                            branch = branch
                        )
                    )
                }
            } catch (e: Exception) {
                mainHandler.post {
                    Toast.makeText(context, context.getString(R.string.sync_save_commit_fail, e.message), Toast.LENGTH_SHORT).show()
                }
            }

            mainHandler.post {
                callback.onSyncCompleted(successCount)
                if (failCount > 0) {
                    Toast.makeText(context, context.getString(R.string.upload_partial, successCount, failCount), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, context.getString(R.string.upload_all_success, successCount), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun getModifiedFiles(repoPath: File): List<Triple<String, String, String?>> {
        val modified = mutableListOf<Triple<String, String, String?>>()

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
                        modified.add(Triple(relPath, content, null))
                    } catch (_: Exception) {}
                }
            }
        }

        scanDir(repoPath)
        return modified
    }
}
