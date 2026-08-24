/**
 * 提交记录与同步冲突数据模型，用于追踪本地与远程仓库的同步状态和变更历史。
 * 本文件定义了本地提交记录、待处理变更、变更操作类型和同步冲突等数据模型。
 * 这些数据类支持离线提交管理、变更跟踪、冲突检测和解决等功能，是实现
 * 本地仓库与GitHub远程仓库同步机制的核心数据层。
 */
package io.nggit.model

import com.google.gson.annotations.SerializedName

/**
 * 提交记录数据类，表示本地仓库的一次提交操作记录。
 * 包含提交的SHA哈希、提交消息、时间戳、变更文件数量、最后修改时间和所属分支等信息。
 * 用于追踪本地提交历史和同步状态。
 */
data class CommitRecord(
    @SerializedName("sha") val sha: String = "",
    @SerializedName("message") val message: String = "",
    @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis(),
    @SerializedName("fileCount") val fileCount: Int = 0,
    @SerializedName("lastModified") val lastModified: Long = System.currentTimeMillis(),
    /** 提交所属的Git分支名称，默认为"main" */
    @SerializedName("branch") val branch: String = "main"
)

/**
 * 待处理变更数据类，表示本地仓库中尚未提交到远程的文件变更。
 * 包含文件路径、本地路径、变更操作类型和时间戳等信息。
 * 用于在同步前展示和管理待提交的变更内容。
 */
data class PendingChange(
    @SerializedName("filePath") val filePath: String = "",
    @SerializedName("localPath") val localPath: String = "",
    @SerializedName("action") val action: ChangeAction = ChangeAction.UPDATE,
    /** 变更发生的时间戳，用于排序和冲突判断 */
    @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis()
)

/**
 * 变更操作类型枚举，定义了文件变更支持的所有操作类型。
 * 包括创建、更新、删除和重命名四种基本操作。
 * 用于在提交和同步过程中标识每个文件变更的操作类型。
 */
enum class ChangeAction {
    CREATE, UPDATE, DELETE, RENAME
}

/**
 * 同步冲突数据类，表示本地仓库与远程仓库之间的同步冲突信息。
 * 包含仓库所有者、仓库名、本地SHA、远程SHA以及各自的时间戳。
 * 用于检测和展示冲突详情，辅助用户解决同步冲突。
 */
data class SyncConflict(
    /** 仓库所有者用户名 */
    @SerializedName("repoOwner") val repoOwner: String = "",
    /** 仓库名称 */
    @SerializedName("repoName") val repoName: String = "",
    /** 本地仓库当前的提交SHA哈希 */
    @SerializedName("localSha") val localSha: String = "",
    /** 远程仓库当前的提交SHA哈希 */
    @SerializedName("remoteSha") val remoteSha: String = "",
    /** 本地最后提交的时间戳 */
    @SerializedName("localTimestamp") val localTimestamp: Long = 0L,
    /** 远程最后提交的时间戳 */
    @SerializedName("remoteTimestamp") val remoteTimestamp: Long = 0L
)
