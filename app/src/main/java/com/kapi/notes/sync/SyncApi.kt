package com.kapi.notes.sync

import com.kapi.notes.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private val OCTET_STREAM = "application/octet-stream".toMediaType()
private val PDF = "application/pdf".toMediaType()

@Serializable
private data class ShareUploadResponse(val id: String)

/**
 * Talks to our own Cloudflare Worker (see sync-worker/), which is the only thing holding R2
 * credentials - this app only ever sends the Google ID token it already has from signing in.
 * The Worker verifies that token and reads/writes the one backup file for that Google account
 * (keyed by the account's stable "sub" claim), so there is no bucket key or per-user secret for
 * the app itself to hold or leak.
 */
@Singleton
class SyncApi @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val syncUrl = "${BuildConfig.SYNC_BASE_URL}/sync"
    private val shareUrl = "${BuildConfig.SYNC_BASE_URL}/share"

    /** Downloads the signed-in account's remote backup into [destination]. Returns false (not an
     * error) when the account has never synced before - there is simply nothing there yet. */
    suspend fun download(idToken: String, destination: File): Result<Boolean> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(syncUrl)
                    .header("Authorization", "Bearer $idToken")
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    when {
                        response.code == 404 -> false
                        response.isSuccessful -> {
                            val body = response.body ?: error("Empty response body")
                            destination.outputStream().use { out -> body.byteStream().copyTo(out) }
                            true
                        }
                        else -> error("Download failed: HTTP ${response.code}")
                    }
                }
            }
        }

    suspend fun upload(idToken: String, bytes: ByteArray): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(syncUrl)
                    .header("Authorization", "Bearer $idToken")
                    .put(bytes.toRequestBody(OCTET_STREAM))
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("Upload failed: HTTP ${response.code}")
                }
            }
        }

    /** Uploads [bytes] (a PDF export) to a new, permanent, publicly-readable R2 object and
     * returns its "anyone with the link can view" URL - backs the Editor's "Chia sẻ dạng link
     * xem-only" feature. Unlike [upload]/[download] (one object per account, overwritten every
     * sync), each call here creates a brand new object, so sharing one note never clobbers
     * another previously-shared one. */
    suspend fun uploadShare(idToken: String, pdfBytes: ByteArray): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(shareUrl)
                    .header("Authorization", "Bearer $idToken")
                    .post(pdfBytes.toRequestBody(PDF))
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("Share upload failed: HTTP ${response.code}")
                    val body = response.body?.string() ?: error("Empty response body")
                    val id = Json.decodeFromString<ShareUploadResponse>(body).id
                    "${BuildConfig.SYNC_BASE_URL}/shared/$id"
                }
            }
        }
}
