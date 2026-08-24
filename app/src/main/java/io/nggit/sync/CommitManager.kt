/**
 * 提交记录管理器，通过JSON文件持久化存储本地仓库与远程的同步提交状态，支持提交记录的读写、SHA更新、文件数统计和过期检测。
 * 该管理器采用单例模式设计，为所有仓库提供统一的提交记录管理功能。
 * 通过JSON格式的配置文件实现数据的持久化存储，确保应用重启后仍能保持同步状态。
 * 支持提交记录的完整生命周期管理，包括创建、读取、更新和删除操作。
 * 提供过期检测机制，用于判断本地提交记录是否与远程仓库状态一致。
 * 所有操作都包含异常处理，确保在文件操作失败时不会导致应用崩溃。
 */
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

    /**
     * 获取指定仓库路径下的提交记录文件对象，返回.git_commit.json文件的File实例。
     * 参数repoPath为本地仓库根目录路径，返回对应的提交记录文件对象。
     */
    fun getCommitFile(repoPath: File): File {
        return File(repoPath, FILE_NAME)
    }

    /**
     * 保存提交记录到指定仓库路径的JSON文件中，将CommitRecord对象序列化为JSON格式并写入文件。
     * 参数repoPath为本地仓库根目录路径，record为要保存的提交记录对象。
     * 如果目录不存在会自动创建，保存失败时会记录错误日志。
     */
    fun saveCommit(repoPath: File, record: CommitRecord) {
        val file = getCommitFile(repoPath)
        try {
            repoPath.mkdirs()
            file.writeText(gson.toJson(record))
        } catch (e: Exception) {
            Log.e(TAG, "保存提交记录失败: ${repoPath.absolutePath}", e)
        }
    }

    /**
     * 从指定仓库路径加载提交记录，读取JSON文件并反序列化为CommitRecord对象。
     * 参数repoPath为本地仓库根目录路径，返回加载的提交记录对象，如果文件不存在或读取失败则返回null。
     */
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

    /**
     * 删除指定仓库路径下的提交记录文件，如果文件存在则执行删除操作。
     * 参数repoPath为本地仓库根目录路径，删除失败时会记录错误日志但不会抛出异常。
     */
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

    /**
     * 检查指定仓库路径是否存在提交记录文件，用于判断该仓库是否已有同步记录。
     * 参数repoPath为本地仓库根目录路径，返回布尔值表示是否存在提交记录。
     */
    fun hasCommit(repoPath: File): Boolean {
        return getCommitFile(repoPath).exists()
    }

    /**
     * 更新指定仓库的提交SHA值，如果已存在提交记录则更新SHA和时间戳，否则创建新的提交记录。
     * 参数repoPath为本地仓库根目录路径，sha为新的提交SHA值。
     */
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

    /**
     * 更新指定仓库的文件数量统计，修改提交记录中的文件计数和最后修改时间。
     * 参数repoPath为本地仓库根目录路径，fileCount为新的文件数量值。
     */
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

    /**
     * 获取指定仓库的提交SHA值，从提交记录中读取并返回SHA字符串。
     * 参数repoPath为本地仓库根目录路径，返回SHA值字符串，如果记录不存在则返回null。
     */
    fun getSha(repoPath: File): String? {
        return loadCommit(repoPath)?.sha
    }

    /**
     * 获取指定仓库的分支名称，从提交记录中读取并返回分支名字符串。
     * 参数repoPath为本地仓库根目录路径，返回分支名称字符串，如果记录不存在则返回null。
     */
    fun getBranch(repoPath: File): String? {
        return loadCommit(repoPath)?.branch
    }

    /**
     * 检测本地提交记录是否已过期，通过比较本地SHA与远程SHA判断同步状态。
     * 参数repoPath为本地仓库根目录路径，remoteSha为远程仓库的最新SHA值。
     * 返回布尔值表示本地记录是否已过期，如果本地记录不存在或SHA不匹配则返回true。
     */
    fun isOutdated(repoPath: File, remoteSha: String): Boolean {
        val localSha = getSha(repoPath) ?: return true
        return localSha != remoteSha
    }
}
