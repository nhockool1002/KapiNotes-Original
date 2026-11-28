package com.kapi.notes.data.repository

import android.content.Context
import com.kapi.notes.apicenter.ApiCenterClient
import com.kapi.notes.data.datastore.ApiAuthStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Syncs the same .kapibackup file [BackupRepository] already produces/reads for local
 * export/import, but to the KapiNotes account's own R2 storage (dev1002-api-center) instead of a
 * file the user picks by hand. Entirely separate from Drive-era [SyncApi]/[AuthRepository] - this
 * only ever runs when a [ApiAuthRepository] session exists; callers must check that first (there
 * is no implicit fallback here), since syncing is opt-in.
 */
@Singleton
class ApiSyncRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: ApiCenterClient,
    private val authStore: ApiAuthStore,
    private val backupRepository: BackupRepository
) {
    /** Downloads the signed-in account's latest backup (if any) and merges it into local data -
     * safe to call unconditionally after every login, even when the device already has local
     * notes: [BackupRepository.importFromFile] merges rather than overwrites, the same way the
     * Drive-era sync already behaves. Returns true if there was remote data to merge. */
    suspend fun downloadAndMerge(): Result<Boolean> = withContext(Dispatchers.IO) {
        val token = authStore.session.first()?.accessToken ?: return@withContext Result.failure(
            IllegalStateException("Chưa đăng nhập tài khoản KapiNotes")
        )
        client.syncDownload(token).mapCatching { bytes ->
            if (bytes == null) {
                false
            } else {
                val tempFile = File(context.cacheDir, "api_sync_download_${UUID.randomUUID()}.kapibackup")
                tempFile.writeBytes(bytes)
                try {
                    backupRepository.importFromFile(tempFile).getOrThrow()
                } finally {
                    tempFile.delete()
                }
                true
            }
        }
    }

    /** Exports all current local data and overwrites the signed-in account's backup on R2. */
    suspend fun uploadNow(): Result<Unit> = withContext(Dispatchers.IO) {
        val token = authStore.session.first()?.accessToken ?: return@withContext Result.failure(
            IllegalStateException("Chưa đăng nhập tài khoản KapiNotes")
        )
        val tempFile = File(context.cacheDir, "api_sync_upload_${UUID.randomUUID()}.kapibackup")
        try {
            backupRepository.exportToFile(tempFile).getOrThrow()
            client.syncUpload(token, tempFile.readBytes())
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            tempFile.delete()
        }
    }
}
