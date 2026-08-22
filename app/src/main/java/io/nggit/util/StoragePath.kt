package io.nggit.util

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File

object StoragePath {
    private const val BASE_DIR = "NG"
    private const val STAR_DIR = "star"
    private const val MINE_DIR = "mine"

    private var basePath: String = ""

    fun init(context: Context) {
        basePath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val extDir = context.getExternalFilesDir(null)
            if (extDir != null) {
                File(extDir, BASE_DIR).absolutePath
            } else {
                File(context.filesDir, BASE_DIR).absolutePath
            }
        } else {
            File(Environment.getExternalStorageDirectory(), BASE_DIR).absolutePath
        }
    }

    fun getBasePath(): File = File(basePath)

    fun getStarPath(): File = File(basePath, STAR_DIR)

    fun getMinePath(): File = File(basePath, MINE_DIR)

    fun getRepoPath(owner: String, repo: String, isStarred: Boolean): File {
        val parentDir = if (isStarred) getStarPath() else getMinePath()
        return File(parentDir, "${owner}___${repo}")
    }

    fun getRepoFilePath(owner: String, repo: String, isStarred: Boolean, filePath: String): File {
        return File(getRepoPath(owner, repo, isStarred), filePath)
    }

    fun ensureDirectories() {
        getBasePath().mkdirs()
        getStarPath().mkdirs()
        getMinePath().mkdirs()
    }

    fun getCommitFile(owner: String, repo: String, isStarred: Boolean): File {
        return File(getRepoPath(owner, repo, isStarred), ".ng_commit.json")
    }

    fun getRepoDirName(owner: String, repo: String): String = "${owner}___${repo}"

    fun parseRepoDirName(dirName: String): Pair<String, String> {
        val parts = dirName.split("___")
        return if (parts.size == 2) Pair(parts[0], parts[1]) else Pair("", "")
    }
}
