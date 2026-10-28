package com.kapi.notes.domain.model

/**
 * The signed-in Google account. There is no separate KapiNotes account or backend-issued token:
 * [userId] is the Google account id and notes are synced straight to/from a backup file in this
 * same account's Google Drive (see [com.kapi.notes.data.repository.DriveSyncAuthRepository]).
 */
data class UserSession(
    val userId: String,
    val email: String,
    val displayName: String,
    val avatarUrl: String? = null,
    /** Drive file id of this account's note backup, once a sync has created/found one. */
    val driveSyncFileId: String? = null,
    val lastSyncedAt: Long? = null
)

enum class SyncStatus {
    IDLE,
    SYNCING,
    SYNCED,
    ERROR
}
