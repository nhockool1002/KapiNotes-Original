package com.kapi.notes.data.repository

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.kapi.notes.data.datastore.SessionStore
import com.kapi.notes.domain.model.SyncStatus
import com.kapi.notes.domain.model.UserSession
import com.kapi.notes.auth.GoogleAuthManager
import com.kapi.notes.sync.SyncApi
import com.kapi.notes.sync.SyncLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import javax.inject.Singleton

// Backups carry every imported PDF/photo page as a full-size PNG, so a heavily-used note set can
// run to 100+ MB - 45s was timing out a normal download+import+export+upload round trip for any
// account with a non-trivial amount of content (not just a slow connection), as seen in sync
// logs where the upload alone hadn't finished by the 45s mark. 4 minutes gives a large backup on
// a modest connection enough room while still guaranteeing the UI's spinner resolves eventually.
private const val SYNC_TIMEOUT_MS = 240_000L

/**
 * "Login" is Google Sign-In (see [GoogleAuthManager]) and "sync" is one backup file - produced by
 * the exact same [BackupRepository] zip format used for the on-device export/import in Settings -
 * sent to our own Cloudflare Worker via [SyncApi], keyed by this Google account's stable id. The
 * Worker, not this app, is the only thing holding storage credentials. Signing in on a second
 * device downloads that file back down and merges it in.
 */
@Singleton
class DriveSyncAuthRepository @Inject constructor(
    private val sessionStore: SessionStore,
    private val googleAuthManager: GoogleAuthManager,
    private val syncApi: SyncApi,
    private val backupRepository: BackupRepository,
    private val syncLogger: SyncLogger,
    @ApplicationContext private val context: Context
) : AuthRepository {

    override val session: Flow<UserSession?> = sessionStore.session

    private val _syncStatus = MutableStateFlow(SyncStatus.IDLE)
    override val syncStatus: Flow<SyncStatus> = _syncStatus.asStateFlow()

    // Several places can call syncNow() close together (app-start auto-sync, the dashboard's own
    // auto-sync, and the user tapping "Đồng bộ ngay") - without this, two overlapping runs each
    // download/import/export/upload the same local data at once, doubling the work and the
    // upload size mid-flight (exactly what the sync logs showed: a second "Bắt đầu đồng bộ"
    // firing while the first was still mid-upload, both then racing the timeout). A later caller
    // now just waits for the in-flight sync's result instead of starting a second one.
    private val syncMutex = Mutex()

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
        googleAuthManager.signOut()
        sessionStore.clear()
        _syncStatus.value = SyncStatus.IDLE
        syncLogger.log("Đã đăng xuất")
    }

    /** Every step logs to [syncLogger] - when sync fails or times out, "Chia sẻ log đồng bộ" in
     * Settings tells you exactly which step it got stuck on (listing the remote file,
     * downloading, importing, exporting, or uploading) and the underlying exception, instead of
     * just a generic "Lỗi đồng bộ". */
    override suspend fun syncNow(): Result<Unit> {
        val account = googleAuthManager.lastSignedInAccount()
        if (account == null) {
            syncLogger.log("Bỏ qua đồng bộ: chưa đăng nhập Google")
            return Result.failure(IllegalStateException("Chưa đăng nhập Google"))
        }

        if (!syncMutex.tryLock()) {
            syncLogger.log("Bỏ qua: đã có một lượt đồng bộ khác đang chạy")
            return Result.success(Unit)
        }
        try {
            return syncLocked(account)
        } finally {
            syncMutex.unlock()
        }
    }

    private suspend fun syncLocked(account: GoogleSignInAccount): Result<Unit> {
        syncLogger.log("Bắt đầu đồng bộ (tài khoản: ${account.email})")
        _syncStatus.value = SyncStatus.SYNCING
        val startedAt = System.currentTimeMillis()

        // A real-device network hang (flaky connection, Google services not responding) must
        // never leave the UI's sync spinner stuck forever - bound the whole operation so it
        // always resolves to SYNCED or ERROR within a fixed window, even if some underlying
        // blocking HTTP call is still unwinding in the background after we give up on it.
        val result = withTimeoutOrNull(SYNC_TIMEOUT_MS) {
            runCatching {
                val idToken = account.idToken
                    ?: error("Thiếu Google ID token - hãy đăng xuất rồi đăng nhập lại")

                syncLogger.log("Kiểm tra bản sao lưu từ server...")
                val downloaded = File(context.cacheDir, "sync_pull_${UUID.randomUUID()}.kapibackup")
                val hasRemote = syncApi.download(idToken, downloaded)
                    .onFailure { syncLogger.logError("tải bản sao lưu từ server", it) }
                    .getOrThrow()

                if (hasRemote) {
                    syncLogger.log("Đã có bản sao lưu trên server (${downloaded.length()} bytes) - đang nhập vào dữ liệu cục bộ...")
                    backupRepository.importFromFile(downloaded)
                        .onFailure { syncLogger.logError("nhập dữ liệu tải về", it) }
                        .getOrThrow()
                    downloaded.delete()
                    syncLogger.log("Nhập dữ liệu tải về thành công.")
                } else {
                    syncLogger.log("Chưa có bản sao lưu nào trên server - sẽ tạo mới.")
                }

                syncLogger.log("Xuất dữ liệu cục bộ để chuẩn bị đẩy lên...")
                val toUpload = File(context.cacheDir, "sync_push_${UUID.randomUUID()}.kapibackup")
                backupRepository.exportToFile(toUpload)
                    .onFailure { syncLogger.logError("xuất dữ liệu cục bộ", it) }
                    .getOrThrow()
                val bytes = toUpload.readBytes()
                toUpload.delete()

                syncLogger.log("Đang tải lên server (${bytes.size} bytes)...")
                syncApi.upload(idToken, bytes)
                    .onFailure { syncLogger.logError("tải lên server", it) }
                    .getOrThrow()
                syncLogger.log("Tải lên server thành công.")

                sessionStore.updateLastSyncedAt(System.currentTimeMillis())
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
