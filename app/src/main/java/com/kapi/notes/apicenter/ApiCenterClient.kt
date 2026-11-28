package com.kapi.notes.apicenter

import com.kapi.notes.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private val JSON_MEDIA_TYPE = "application/json".toMediaType()
private val OCTET_STREAM_MEDIA_TYPE = "application/octet-stream".toMediaType()
private val json = Json { ignoreUnknownKeys = true }

/**
 * Talks to dev1002-api-center (KapiNotes's own account backend - separate from the Google
 * Sign-In used for Drive-era/Worker sync, see ApiAuthRepository). Every request is HMAC-signed
 * per [ApiCenterSigner] and follows the exact header/body contract the API docs specify.
 */
@Singleton
class ApiCenterClient @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val basePath = URI(BuildConfig.API_CENTER_BASE_URL).path

    /** [jsonBody] and [binaryBody] are mutually exclusive; both null means a GET with no body at
     * all - the signature's body hash and what actually goes over the wire must always be the
     * exact same bytes, since the backend verifies the signature against what it actually
     * received (a GET truly sends nothing, so its hash is the SHA-256 of an empty string, NOT of
     * a placeholder "{}" - the API docs' own curl samples show `-d '{}'` on every example
     * including GET, but that's misleading boilerplate, not what the backend actually expects). */
    private fun request(
        method: String,
        endpointPath: String,
        jsonBody: JsonObject? = null,
        binaryBody: ByteArray? = null,
        accessToken: String? = null
    ): Request {
        val url = "${BuildConfig.API_CENTER_BASE_URL}$endpointPath"
        val path = "$basePath$endpointPath"
        val bodyBytes = binaryBody ?: jsonBody?.toString()?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
        val timestamp = ApiCenterSigner.newTimestamp()
        val nonce = ApiCenterSigner.newNonce()
        val signature = ApiCenterSigner.sign(
            appSecret = BuildConfig.API_CENTER_APP_SECRET,
            method = method,
            path = path,
            sortedQueryString = "",
            timestamp = timestamp,
            nonce = nonce,
            rawBody = bodyBytes
        )

        val builder = Request.Builder()
            .url(url)
            .header("X-App-ID", BuildConfig.API_CENTER_APP_ID)
            .header("X-Timestamp", timestamp)
            .header("X-Nonce", nonce)
            .header("X-Signature", signature)
            .header("X-Platform", "android")
            .header("X-App-Version", BuildConfig.VERSION_NAME)
        if (accessToken != null) builder.header("Authorization", "Bearer $accessToken")

        return when {
            binaryBody != null -> builder.method(method, binaryBody.toRequestBody(OCTET_STREAM_MEDIA_TYPE)).build()
            jsonBody != null -> builder.header("Content-Type", "application/json")
                .method(method, bodyBytes.toRequestBody(JSON_MEDIA_TYPE)).build()
            method == "GET" -> builder.get().build()
            else -> error("Unsupported bodyless method: $method")
        }
    }

    /** Runs [call], parses the JSON envelope, and turns a failed/unsuccessful response into a
     * [Result.failure] carrying the API's own error code/message - never throws. */
    private inline fun <reified T> execute(request: Request): Result<T> = runCatching {
        client.newCall(request).execute().use { response ->
            val bodyString = response.body?.string().orEmpty()
            val envelope = runCatching { json.decodeFromString<ApiCenterEnvelope<T>>(bodyString) }.getOrNull()
            if (response.isSuccessful && envelope?.success == true && envelope.data != null) {
                envelope.data
            } else {
                throw ApiCenterException(
                    code = envelope?.code,
                    message = envelope?.message ?: "Lỗi kết nối máy chủ (HTTP ${response.code})"
                )
            }
        }
    }

    suspend fun ping(): Result<PingData> = withContext(Dispatchers.IO) {
        execute(request("GET", "/ping"))
    }

    suspend fun login(username: String, password: String): Result<LoginData> = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("username", JsonPrimitive(username))
            put("password", JsonPrimitive(password))
        }
        execute(request("POST", "/login", jsonBody = body))
    }

    suspend fun signup(username: String, password: String, confirmPassword: String): Result<SignupData> =
        withContext(Dispatchers.IO) {
            val body = buildJsonObject {
                put("username", JsonPrimitive(username))
                put("password", JsonPrimitive(password))
                put("confirm_password", JsonPrimitive(confirmPassword))
            }
            execute(request("POST", "/signup", jsonBody = body))
        }

    /** Returns null when the user has never synced before (not an error - see API docs). */
    suspend fun syncDownload(accessToken: String): Result<ByteArray?> = withContext(Dispatchers.IO) {
        runCatching {
            val req = request("GET", "/sync", accessToken = accessToken)
            client.newCall(req).execute().use { response ->
                when {
                    response.code == 204 -> null
                    response.isSuccessful -> response.body?.bytes() ?: error("Phản hồi rỗng từ máy chủ")
                    else -> {
                        val envelope = runCatching {
                            json.decodeFromString<ApiCenterEnvelope<Unit>>(response.body?.string().orEmpty())
                        }.getOrNull()
                        throw ApiCenterException(envelope?.code, envelope?.message ?: "Lỗi tải dữ liệu đồng bộ (HTTP ${response.code})")
                    }
                }
            }
        }
    }

    suspend fun syncUpload(accessToken: String, backupBytes: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        execute<SyncUploadData>(
            request("PUT", "/sync", binaryBody = backupBytes, accessToken = accessToken)
        ).map {}
    }
}
