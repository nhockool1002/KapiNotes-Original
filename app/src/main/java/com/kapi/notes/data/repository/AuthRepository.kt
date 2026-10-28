package com.kapi.notes.data.repository

import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.kapi.notes.domain.model.SyncStatus
import com.kapi.notes.domain.model.UserSession
import kotlinx.coroutines.flow.Flow

/**
 * Account + sync surface the UI talks to. There is no custom backend: [DriveSyncAuthRepository]
 * is the only implementation, and it stores a user's notes as one backup file in *their own*
 * Google Drive (the same account/scopes already used to import documents - see
 * [com.kapi.notes.drive.DriveAuthManager]). Logging in on a second device pulls that file back
 * down and merges it into the local Room database.
 */
interface AuthRepository {
    val session: Flow<UserSession?>
    val syncStatus: Flow<SyncStatus>

    /** Call after a successful Google Sign-In (see [com.kapi.notes.drive.DriveAuthManager.signInIntent]). */
    suspend fun completeGoogleSignIn(account: GoogleSignInAccount): Result<UserSession>

    suspend fun logout()

    /** Pulls the account's Drive backup (if any) into local storage, then pushes the merged
     * result back up - the one "Đồng bộ ngay" action covers both directions. */
    suspend fun syncNow(): Result<Unit>
}
