package com.kapi.notes.data.repository

import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.kapi.notes.domain.model.SyncStatus
import com.kapi.notes.domain.model.UserSession
import kotlinx.coroutines.flow.Flow

/**
 * Account + sync surface the UI talks to. [DriveSyncAuthRepository] is the only implementation:
 * "login" is Google Sign-In (see [com.kapi.notes.auth.GoogleAuthManager]) and a user's notes are
 * stored as one backup file on our own Cloudflare Worker sync server, keyed by that Google
 * account. Logging in on a second device pulls that file back down and merges it into the local
 * Room database.
 */
interface AuthRepository {
    val session: Flow<UserSession?>
    val syncStatus: Flow<SyncStatus>

    /** Call after a successful Google Sign-In (see [com.kapi.notes.auth.GoogleAuthManager.signInIntent]). */
    suspend fun completeGoogleSignIn(account: GoogleSignInAccount): Result<UserSession>

    suspend fun logout()

    /** Pulls the account's remote backup (if any) into local storage, then pushes the merged
     * result back up - the one "Đồng bộ ngay" action covers both directions. */
    suspend fun syncNow(): Result<Unit>
}
