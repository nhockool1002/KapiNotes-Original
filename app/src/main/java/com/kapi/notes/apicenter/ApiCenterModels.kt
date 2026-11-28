package com.kapi.notes.apicenter

import kotlinx.serialization.Serializable

/** Every dev1002-api-center response (success or failure) comes wrapped in this same envelope -
 * [data] is only present on success, [code]/[message] carry the failure reason otherwise (see
 * the per-endpoint error tables in the API docs, e.g. `KAPINOTES_AUTH_003`). */
@Serializable
data class ApiCenterEnvelope<T>(
    val code: String? = null,
    val data: T? = null,
    val message: String? = null,
    val request_id: String? = null,
    val success: Boolean = false,
    val timestamp: Long? = null
)

@Serializable
data class PingData(
    val app_code: String? = null,
    val server_time: String? = null
)

@Serializable
data class LoginData(
    val access_token: String,
    val expires_in: Long,
    val token_type: String = "Bearer",
    val user_id: String,
    val username: String
)

@Serializable
data class SignupData(
    val user_id: String,
    val username: String
)

@Serializable
data class SyncUploadData(
    val updated_at: String? = null
)

/** Carries the API's own [code]/[message] (already a user-facing Vietnamese sentence per the
 * docs, e.g. "Username hoặc mật khẩu không đúng.") so the UI never has to invent its own copy
 * for a server-side validation/business error - only a transport-level failure (timeout, no
 * body, malformed JSON) falls back to [message] being null. */
class ApiCenterException(val code: String?, message: String?) : Exception(message)
