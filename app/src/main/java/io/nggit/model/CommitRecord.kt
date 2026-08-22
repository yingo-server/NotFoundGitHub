package io.nggit.model

import com.google.gson.annotations.SerializedName

data class CommitRecord(
    @SerializedName("sha") val sha: String = "",
    @SerializedName("message") val message: String = "",
    @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis(),
    @SerializedName("fileCount") val fileCount: Int = 0,
    @SerializedName("lastModified") val lastModified: Long = System.currentTimeMillis(),
    @SerializedName("branch") val branch: String = "main"
)

data class PendingChange(
    @SerializedName("filePath") val filePath: String = "",
    @SerializedName("localPath") val localPath: String = "",
    @SerializedName("action") val action: ChangeAction = ChangeAction.UPDATE,
    @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis()
)

enum class ChangeAction {
    CREATE, UPDATE, DELETE, RENAME
}

data class SyncConflict(
    @SerializedName("repoOwner") val repoOwner: String = "",
    @SerializedName("repoName") val repoName: String = "",
    @SerializedName("localSha") val localSha: String = "",
    @SerializedName("remoteSha") val remoteSha: String = "",
    @SerializedName("localTimestamp") val localTimestamp: Long = 0L,
    @SerializedName("remoteTimestamp") val remoteTimestamp: Long = 0L
)
