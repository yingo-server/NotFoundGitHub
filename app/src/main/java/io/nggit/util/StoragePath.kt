/**
 * 存储路径管理工具，负责应用本地存储目录的初始化和路径计算，
 * 包括基础目录、星标仓库目录、个人仓库目录的统一管理。
 * 通过 Android 不同版本的存储策略，确定应用数据的最佳存放位置，
 * 并提供仓库路径解析和目录创建等核心功能，是本地 Git 仓库管理的基础设施。
 */
package io.nggit.util

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File

/**
 * 存储路径管理单例对象，统一管理应用本地存储的所有路径操作。
 * 基于 Android 版本自动选择合适的存储位置，确保兼容性。
 */
object StoragePath {
    private const val BASE_DIR = "NG"
    private const val STAR_DIR = "star"
    private const val MINE_DIR = "mine"

    private var basePath: String = ""

    /**
     * 初始化存储基础路径。
     * 根据 Android 版本选择外部存储或内部存储，
     * Android Q 及以上使用应用专属外部目录，较低版本使用公共外部存储。
     *
     * @param context 应用上下文，用于获取文件目录
     */
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

    /**
     * 获取应用基础存储目录。
     *
     * @return 基础目录对应的 File 对象
     */
    fun getBasePath(): File = File(basePath)

    /**
     * 获取星标仓库的存储目录。
     * 星标仓库用于存放用户收藏的远程仓库本地副本。
     *
     * @return 星标仓库目录对应的 File 对象
     */
    fun getStarPath(): File = File(basePath, STAR_DIR)

    /**
     * 获取个人仓库的存储目录。
     * 个人仓库用于存放用户自行创建或克隆的本地仓库。
     *
     * @return 个人仓库目录对应的 File 对象
     */
    fun getMinePath(): File = File(basePath, MINE_DIR)

    /**
     * 获取指定仓库的存储路径。
     * 仓库路径格式为 "owner___repo"，根据是否星标选择不同的父目录。
     *
     * @param owner 仓库所有者用户名
     * @param repo 仓库名称
     * @param isStarred 是否为星标仓库
     * @return 仓库目录对应的 File 对象
     */
    fun getRepoPath(owner: String, repo: String, isStarred: Boolean): File {
        val parentDir = if (isStarred) getStarPath() else getMinePath()
        return File(parentDir, "${owner}___${repo}")
    }

    /**
     * 获取仓库内指定文件的完整路径。
     * 在仓库路径基础上拼接相对文件路径。
     *
     * @param owner 仓库所有者用户名
     * @param repo 仓库名称
     * @param isStarred 是否为星标仓库
     * @param filePath 仓库内的相对文件路径
     * @return 文件对应的 File 对象
     */
    fun getRepoFilePath(owner: String, repo: String, isStarred: Boolean, filePath: String): File {
        return File(getRepoPath(owner, repo, isStarred), filePath)
    }

    /**
     * 确保所有必要的存储目录已创建。
     * 包括基础目录、星标仓库目录和个人仓库目录。
     * 若目录已存在则不会重复创建。
     */
    fun ensureDirectories() {
        getBasePath().mkdirs()
        getStarPath().mkdirs()
        getMinePath().mkdirs()
    }

    /**
     * 获取指定仓库的提交信息文件路径。
     * 提交信息以 JSON 格式存储在仓库根目录下的 .ng_commit.json 文件中。
     *
     * @param owner 仓库所有者用户名
     * @param repo 仓库名称
     * @param isStarred 是否为星标仓库
     * @return 提交信息文件对应的 File 对象
     */
    fun getCommitFile(owner: String, repo: String, isStarred: Boolean): File {
        return File(getRepoPath(owner, repo, isStarred), ".ng_commit.json")
    }

    /**
     * 根据仓库所有者和名称生成仓库目录名。
     * 使用三个下划线 "___" 作为分隔符连接所有者和仓库名。
     *
     * @param owner 仓库所有者用户名
     * @param repo 仓库名称
     * @return 格式化的目录名字符串，例如 "user___repo"
     */
    fun getRepoDirName(owner: String, repo: String): String = "${owner}___${repo}"

    /**
     * 解析仓库目录名，提取所有者和仓库名称。
     * 将 "owner___repo" 格式的目录名拆分为所有者和仓库名的键值对。
     * 如果目录名格式不符合预期，返回空字符串对。
     *
     * @param dirName 仓库目录名
     * @return 包含所有者和仓库名称的 Pair，格式不正确时返回 ("", "")
     */
    fun parseRepoDirName(dirName: String): Pair<String, String> {
        val parts = dirName.split("___")
        return if (parts.size == 2) Pair(parts[0], parts[1]) else Pair("", "")
    }
}
