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
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val basePath = URI(BuildConfig.API_CENTER_BASE_URL).path

    private fun request(method: String, endpointPath: String, body: JsonObject?): Request {
        val url = "${BuildConfig.API_CENTER_BASE_URL}$endpointPath"
        val path = "$basePath$endpointPath"
        val bodyString = (body ?: JsonObject(emptyMap())).toString()
        val bodyBytes = bodyString.toByteArray(Charsets.UTF_8)
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
            .header("Content-Type", "application/json")
            .header("X-App-ID", BuildConfig.API_CENTER_APP_ID)
            .header("X-Timestamp", timestamp)
            .header("X-Nonce", nonce)
            .header("X-Signature", signature)
            .header("X-Platform", "android")
            .header("X-App-Version", BuildConfig.VERSION_NAME)

        return when (method) {
            "GET" -> builder.get().build()
            "POST" -> builder.post(bodyBytes.toRequestBody(JSON_MEDIA_TYPE)).build()
            else -> error("Unsupported method: $method")
        }
    }

    /** Runs [call], parses the envelope, and turns a failed/unsuccessful response into a
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
        execute(request("GET", "/ping", null))
    }

    suspend fun login(username: String, password: String): Result<LoginData> = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("username", JsonPrimitive(username))
            put("password", JsonPrimitive(password))
        }
        execute(request("POST", "/login", body))
    }

    suspend fun signup(username: String, password: String, confirmPassword: String): Result<SignupData> =
        withContext(Dispatchers.IO) {
            val body = buildJsonObject {
                put("username", JsonPrimitive(username))
                put("password", JsonPrimitive(password))
                put("confirm_password", JsonPrimitive(confirmPassword))
            }
            execute(request("POST", "/signup", body))
        }
}
