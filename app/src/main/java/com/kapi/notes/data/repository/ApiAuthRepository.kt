package com.kapi.notes.data.repository

import com.kapi.notes.domain.model.ApiUserSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class BackendConnectivity { UNKNOWN, CHECKING, ONLINE, OFFLINE }

/** The KapiNotes account system (dev1002-api-center) - entirely separate from [AuthRepository]
 * (Google Sign-In, used only for Drive-era/Worker sync). Signing in here isn't required to use
 * the app; it exists for a future account-based sync to R2. */
interface ApiAuthRepository {
    val session: Flow<ApiUserSession?>
    val connectivity: StateFlow<BackendConnectivity>

    /** Pings the backend; also updates [connectivity]. Safe to call from Splash on every
     * launch - never throws, and a failure here never blocks using the app. */
    suspend fun checkConnectivity(): Result<Unit>

    suspend fun login(username: String, password: String): Result<ApiUserSession>
    suspend fun signup(username: String, password: String, confirmPassword: String): Result<Unit>
    suspend fun logout()
}
