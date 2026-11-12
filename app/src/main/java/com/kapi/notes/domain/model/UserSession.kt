package com.kapi.notes.domain.model

/**
 * The signed-in Google account. There is no separate KapiNotes account or backend-issued token:
 * [userId] is the Google account id and notes are synced to/from a backup file keyed by this
 * same account on our own sync server (see [com.kapi.notes.data.repository.DriveSyncAuthRepository]).
 */
data class UserSession(
    val userId: String,
    val email: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val lastSyncedAt: Long? = null
)

enum class SyncStatus {
    IDLE,
    SYNCING,
    SYNCED,
    ERROR
}
