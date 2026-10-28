package com.kapi.notes.drive

import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.services.drive.model.File as DriveApiFile
import com.kapi.notes.domain.model.DriveFile
import com.kapi.notes.domain.model.DriveFileKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val FOLDER_MIME = "application/vnd.google-apps.folder"
private const val FIELDS = "files(id,name,mimeType,modifiedTime,size,iconLink),nextPageToken"

@Singleton
class DriveRepository @Inject constructor(
    private val authManager: DriveAuthManager
) {
    /**
     * Lists files inside [folderId] (or the Drive root when null) that are either folders,
     * PDFs or images - everything else can't be opened as a note background.
     */
    suspend fun listFiles(account: GoogleSignInAccount, folderId: String? = null): Result<List<DriveFile>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val drive = authManager.buildDriveService(account)
                val parent = folderId ?: "root"
                val query = "'$parent' in parents and trashed = false and " +
                    "(mimeType = '$FOLDER_MIME' or mimeType = 'application/pdf' or mimeType contains 'image/')"

                val result = drive.files().list()
                    .setQ(query)
                    .setFields(FIELDS)
                    .setOrderBy("folder,modifiedTime desc")
                    .execute()

                result.files.orEmpty().map { it.toDomain() }
            }
        }

    suspend fun downloadFile(account: GoogleSignInAccount, fileId: String, out: OutputStream): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val drive = authManager.buildDriveService(account)
                drive.files().get(fileId).executeMediaAndDownloadTo(out)
            }
        }

    suspend fun uploadFile(
        account: GoogleSignInAccount,
        displayName: String,
        mimeType: String,
        bytes: ByteArray
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val drive = authManager.buildDriveService(account)
            val metadata = DriveApiFile().setName(displayName)
            val content = com.google.api.client.http.ByteArrayContent(mimeType, bytes)
            val uploaded = drive.files().create(metadata, content).setFields("id").execute()
            uploaded.id
        }
    }

    /**
     * Finds a file by exact name among the files this app is allowed to see under the
     * `drive.file` scope (anything it created itself, anywhere in the user's Drive - the scope
     * does not require a specific folder). Used to locate the one sync backup file per account.
     */
    suspend fun findFileByName(account: GoogleSignInAccount, name: String): Result<DriveFile?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val drive = authManager.buildDriveService(account)
                val result = drive.files().list()
                    .setQ("name = '$name' and trashed = false")
                    .setFields(FIELDS)
                    .setOrderBy("modifiedTime desc")
                    .setPageSize(1)
                    .execute()
                result.files.orEmpty().firstOrNull()?.toDomain()
            }
        }

    /** Creates [displayName] the first time (fileId == null), or overwrites its content after. */
    suspend fun upsertFile(
        account: GoogleSignInAccount,
        fileId: String?,
        displayName: String,
        mimeType: String,
        bytes: ByteArray
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val drive = authManager.buildDriveService(account)
            val content = com.google.api.client.http.ByteArrayContent(mimeType, bytes)
            if (fileId == null) {
                val metadata = DriveApiFile().setName(displayName)
                drive.files().create(metadata, content).setFields("id").execute().id
            } else {
                drive.files().update(fileId, null, content).setFields("id").execute().id
            }
        }
    }

    /**
     * Grants "anyone with the link can view" on [fileId] (a file this app uploaded, so always
     * within the `drive.file` scope's reach) and returns its view link - used for "Chia sẻ dạng
     * link xem-only" so the user can send a read-only copy to a friend/teacher without sharing
     * their whole Drive.
     */
    suspend fun shareFilePublicly(account: GoogleSignInAccount, fileId: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val drive = authManager.buildDriveService(account)
                val permission = com.google.api.services.drive.model.Permission()
                    .setType("anyone")
                    .setRole("reader")
                drive.permissions().create(fileId, permission).execute()
                val file = drive.files().get(fileId).setFields("webViewLink").execute()
                file.webViewLink ?: "https://drive.google.com/file/d/$fileId/view"
            }
        }

    private fun DriveApiFile.toDomain(): DriveFile = DriveFile(
        id = id,
        name = name ?: "Không tên",
        mimeType = mimeType ?: "",
        kind = when {
            mimeType == FOLDER_MIME -> DriveFileKind.FOLDER
            mimeType == "application/pdf" -> DriveFileKind.PDF
            mimeType?.startsWith("image/") == true -> DriveFileKind.IMAGE
            else -> DriveFileKind.OTHER
        },
        modifiedTime = modifiedTime?.value ?: 0L,
        sizeBytes = size.toLong(),
        iconLink = iconLink
    )
}
