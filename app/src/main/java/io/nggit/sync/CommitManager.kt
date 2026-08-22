package io.nggit.sync

import io.nggit.model.CommitRecord
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File

object CommitManager {
    private const val FILE_NAME = ".ng_commit.json"
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    fun getCommitFile(repoPath: File): File {
        return File(repoPath, FILE_NAME)
    }

    fun saveCommit(repoPath: File, record: CommitRecord) {
        val file = getCommitFile(repoPath)
        try {
            file.writeText(gson.toJson(record))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadCommit(repoPath: File): CommitRecord? {
        val file = getCommitFile(repoPath)
        if (!file.exists()) return null
        return try {
            gson.fromJson(file.readText(), CommitRecord::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun deleteCommitFile(repoPath: File) {
        val file = getCommitFile(repoPath)
        if (file.exists()) file.delete()
    }

    fun hasCommit(repoPath: File): Boolean {
        return getCommitFile(repoPath).exists()
    }
}
