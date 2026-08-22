package io.nggit.sync

import android.util.Log
import io.nggit.model.CommitRecord
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File

object CommitManager {

    private const val TAG = "CommitManager"
    private const val FILE_NAME = ".ng_commit.json"
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    fun getCommitFile(repoPath: File): File {
        return File(repoPath, FILE_NAME)
    }

    fun saveCommit(repoPath: File, record: CommitRecord) {
        val file = getCommitFile(repoPath)
        try {
            repoPath.mkdirs()
            file.writeText(gson.toJson(record))
        } catch (e: Exception) {
            Log.e(TAG, "保存提交记录失败: ${repoPath.absolutePath}", e)
        }
    }

    fun loadCommit(repoPath: File): CommitRecord? {
        val file = getCommitFile(repoPath)
        if (!file.exists()) return null
        return try {
            val text = file.readText()
            if (text.isBlank()) null
            else gson.fromJson(text, CommitRecord::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "读取提交记录失败: ${repoPath.absolutePath}", e)
            null
        }
    }

    fun deleteCommitFile(repoPath: File) {
        val file = getCommitFile(repoPath)
        if (file.exists()) {
            try {
                file.delete()
            } catch (e: Exception) {
                Log.e(TAG, "删除提交记录失败: ${repoPath.absolutePath}", e)
            }
        }
    }

    fun hasCommit(repoPath: File): Boolean {
        return getCommitFile(repoPath).exists()
    }

    fun updateSha(repoPath: File, sha: String) {
        val existing = loadCommit(repoPath)
        if (existing != null) {
            val updated = existing.copy(
                sha = sha,
                timestamp = System.currentTimeMillis()
            )
            saveCommit(repoPath, updated)
        } else {
            saveCommit(repoPath, CommitRecord(
                sha = sha,
                timestamp = System.currentTimeMillis(),
                fileCount = 0,
                lastModified = System.currentTimeMillis(),
                branch = "main"
            ))
        }
    }

    fun updateFileCount(repoPath: File, fileCount: Int) {
        val existing = loadCommit(repoPath)
        if (existing != null) {
            val updated = existing.copy(
                fileCount = fileCount,
                lastModified = System.currentTimeMillis()
            )
            saveCommit(repoPath, updated)
        }
    }

    fun getSha(repoPath: File): String? {
        return loadCommit(repoPath)?.sha
    }

    fun getBranch(repoPath: File): String? {
        return loadCommit(repoPath)?.branch
    }

    fun isOutdated(repoPath: File, remoteSha: String): Boolean {
        val localSha = getSha(repoPath) ?: return true
        return localSha != remoteSha
    }
}
