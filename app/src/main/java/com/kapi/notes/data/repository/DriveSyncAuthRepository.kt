package com.kapi.notes.data.repository

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.kapi.notes.data.datastore.SessionStore
import com.kapi.notes.domain.model.SyncStatus
import com.kapi.notes.domain.model.UserSession
import com.kapi.notes.drive.DriveAuthManager
import com.kapi.notes.drive.DriveRepository
import com.kapi.notes.sync.SyncLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import javax.inject.Singleton

private const val SYNC_FILE_NAME = "kapinotes_sync.kapibackup"
private const val SYNC_MIME_TYPE = "application/zip"
private const val SYNC_TIMEOUT_MS = 45_000L

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
    private val syncLogger: SyncLogger,
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
        syncLogger.log("Đăng nhập thành công: ${account.email}")
        // Pull this account's existing notes (if any) the moment it signs in on a device.
        syncNow()
        return Result.success(session)
    }

    override suspend fun logout() {
        driveAuthManager.signOut()
        sessionStore.clear()
        _syncStatus.value = SyncStatus.IDLE
        syncLogger.log("Đã đăng xuất")
    }

    /** Every step logs to [syncLogger] - when sync fails or times out, "Chia sẻ log đồng bộ" in
     * Settings tells you exactly which step it got stuck on (listing the remote file,
     * downloading, importing, exporting, or uploading) and the underlying exception, instead of
     * just a generic "Lỗi đồng bộ". */
    override suspend fun syncNow(): Result<Unit> {
        val account = driveAuthManager.lastSignedInAccount()
        if (account == null) {
            syncLogger.log("Bỏ qua đồng bộ: chưa đăng nhập Google")
            return Result.failure(IllegalStateException("Chưa đăng nhập Google"))
        }

        syncLogger.log("Bắt đầu đồng bộ (tài khoản: ${account.email})")
        _syncStatus.value = SyncStatus.SYNCING
        val startedAt = System.currentTimeMillis()

        // A real-device network hang (flaky connection, Google services not responding) must
        // never leave the UI's sync spinner stuck forever - bound the whole operation so it
        // always resolves to SYNCED or ERROR within a fixed window, even if some underlying
        // blocking HTTP call is still unwinding in the background after we give up on it.
        val result = withTimeoutOrNull(SYNC_TIMEOUT_MS) {
            runCatching {
                syncLogger.log("Tìm file đồng bộ trên Drive ($SYNC_FILE_NAME)...")
                val remote = driveRepository.findFileByName(account, SYNC_FILE_NAME)
                    .onFailure { syncLogger.logError("tìm file trên Drive", it) }
                    .getOrThrow()

                if (remote != null) {
                    syncLogger.log("Đã có file trên Drive (id=${remote.id}) - tải về trước khi đẩy lên.")
                    val downloaded = File(context.cacheDir, "sync_pull_${UUID.randomUUID()}.kapibackup")
                    downloaded.outputStream().use { out ->
                        driveRepository.downloadFile(account, remote.id, out)
                            .onFailure { syncLogger.logError("tải file từ Drive", it) }
                            .getOrThrow()
                    }
                    syncLogger.log("Tải về xong (${downloaded.length()} bytes) - đang nhập vào dữ liệu cục bộ...")
                    backupRepository.importFromFile(downloaded)
                        .onFailure { syncLogger.logError("nhập dữ liệu tải về", it) }
                        .getOrThrow()
                    downloaded.delete()
                    syncLogger.log("Nhập dữ liệu tải về thành công.")
                } else {
                    syncLogger.log("Chưa có file đồng bộ nào trên Drive - sẽ tạo mới.")
                }

                syncLogger.log("Xuất dữ liệu cục bộ để chuẩn bị đẩy lên...")
                val toUpload = File(context.cacheDir, "sync_push_${UUID.randomUUID()}.kapibackup")
                backupRepository.exportToFile(toUpload)
                    .onFailure { syncLogger.logError("xuất dữ liệu cục bộ", it) }
                    .getOrThrow()
                val bytes = toUpload.readBytes()
                toUpload.delete()

                syncLogger.log("Đang tải lên Drive (${bytes.size} bytes)...")
                val fileId = driveRepository.upsertFile(
                    account = account,
                    fileId = remote?.id,
                    displayName = SYNC_FILE_NAME,
                    mimeType = SYNC_MIME_TYPE,
                    bytes = bytes
                ).onFailure { syncLogger.logError("tải lên Drive", it) }
                    .getOrThrow()
                syncLogger.log("Tải lên Drive thành công (id=$fileId).")

                val now = System.currentTimeMillis()
                sessionStore.updateSyncState(driveSyncFileId = fileId, lastSyncedAt = now)
            }
        } ?: run {
            syncLogger.log("Đồng bộ quá thời gian chờ (${SYNC_TIMEOUT_MS / 1000}s) - hủy và báo lỗi.")
            Result.failure(TimeoutException("Đồng bộ quá thời gian chờ, thử lại sau"))
        }

        val elapsedMs = System.currentTimeMillis() - startedAt
        result.onSuccess { syncLogger.log("Đồng bộ hoàn tất sau ${elapsedMs}ms.") }
            .onFailure { syncLogger.logError("đồng bộ (tổng thể)", it) }

        _syncStatus.value = if (result.isSuccess) SyncStatus.SYNCED else SyncStatus.ERROR
        return result
    }
}
