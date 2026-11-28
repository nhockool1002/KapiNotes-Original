package com.kapi.notes.domain.model

/** A signed-in KapiNotes account (dev1002-api-center) - entirely separate from [UserSession]
 * (Google Sign-In, used only for Drive-era/Worker sync). Having one doesn't require the other:
 * this is for a future account-based sync (R2), see ApiAuthRepository. */
data class ApiUserSession(
    val userId: String,
    val username: String,
    val accessToken: String,
    /** Epoch millis; past this point [accessToken] should be treated as expired. */
    val expiresAt: Long
)
