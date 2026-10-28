package com.kapi.notes.data.repository

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.kapi.notes.data.datastore.SessionStore
import com.kapi.notes.domain.model.SyncStatus
import com.kapi.notes.domain.model.UserSession
import com.kapi.notes.drive.DriveAuthManager
import com.kapi.notes.drive.DriveRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val SYNC_FILE_NAME = "kapinotes_sync.kapibackup"
private const val SYNC_MIME_TYPE = "application/zip"

/**
 * "Login" is just Google Sign-In (the same account/scopes already used to browse Drive in
 * [com.kapi.notes.ui.drive.DrivePickerScreen]) and "sync" is one backup file - produced by the
 * exact same [BackupRepository] zip format used for the on-device export/import in Settings -
 * stored in that account's own Drive. Signing in on a second device downloads that file and
 * merges it in; there is no other backend.
 */
@Singleton
class DriveSyncAuthRepository @Inject constructor(
    private val sessionStore: SessionStore,
    private val driveAuthManager: DriveAuthManager,
    private val driveRepository: DriveRepository,
    private val backupRepository: BackupRepository,
    @ApplicationContext private val context: Context
) : AuthRepository {

    override val session: Flow<UserSession?> = sessionStore.session

    private val _syncStatus = MutableStateFlow(SyncStatus.IDLE)
    override val syncStatus: Flow<SyncStatus> = _syncStatus.asStateFlow()

    override suspend fun completeGoogleSignIn(account: GoogleSignInAccount): Result<UserSession> {
        val session = UserSession(
            userId = account.id ?: account.email.orEmpty(),
            email = account.email.orEmpty(),
            displayName = account.displayName ?: account.email.orEmpty(),
            avatarUrl = account.photoUrl?.toString()
        )
        sessionStore.save(session)
        // Pull this account's existing notes (if any) the moment it signs in on a device.
        syncNow()
        return Result.success(session)
    }

    override suspend fun logout() {
        driveAuthManager.signOut()
        sessionStore.clear()
        _syncStatus.value = SyncStatus.IDLE
    }

    override suspend fun syncNow(): Result<Unit> {
        val account = driveAuthManager.lastSignedInAccount()
            ?: return Result.failure(IllegalStateException("Chưa đăng nhập Google"))

        _syncStatus.value = SyncStatus.SYNCING
        val result = runCatching {
            val remote = driveRepository.findFileByName(account, SYNC_FILE_NAME).getOrThrow()

            if (remote != null) {
                val downloaded = File(context.cacheDir, "sync_pull_${UUID.randomUUID()}.kapibackup")
                downloaded.outputStream().use { out ->
                    driveRepository.downloadFile(account, remote.id, out).getOrThrow()
                }
                backupRepository.importFromFile(downloaded).getOrThrow()
                downloaded.delete()
            }

            val toUpload = File(context.cacheDir, "sync_push_${UUID.randomUUID()}.kapibackup")
            backupRepository.exportToFile(toUpload).getOrThrow()
            val bytes = toUpload.readBytes()
            toUpload.delete()

            val fileId = driveRepository.upsertFile(
                account = account,
                fileId = remote?.id,
                displayName = SYNC_FILE_NAME,
                mimeType = SYNC_MIME_TYPE,
                bytes = bytes
            ).getOrThrow()

            val now = System.currentTimeMillis()
            sessionStore.updateSyncState(driveSyncFileId = fileId, lastSyncedAt = now)
        }

        _syncStatus.value = if (result.isSuccess) SyncStatus.SYNCED else SyncStatus.ERROR
        return result
    }
}
