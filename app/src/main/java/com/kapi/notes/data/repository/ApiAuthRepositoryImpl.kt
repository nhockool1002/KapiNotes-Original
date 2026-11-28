package com.kapi.notes.data.repository

import com.kapi.notes.apicenter.ApiCenterClient
import com.kapi.notes.data.datastore.ApiAuthStore
import com.kapi.notes.domain.model.ApiUserSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiAuthRepositoryImpl @Inject constructor(
    private val client: ApiCenterClient,
    private val store: ApiAuthStore
) : ApiAuthRepository {

    override val session: Flow<ApiUserSession?> = store.session

    private val _connectivity = MutableStateFlow(BackendConnectivity.UNKNOWN)
    override val connectivity = _connectivity.asStateFlow()

    override suspend fun checkConnectivity(): Result<Unit> {
        _connectivity.value = BackendConnectivity.CHECKING
        val result = client.ping()
        _connectivity.value = if (result.isSuccess) BackendConnectivity.ONLINE else BackendConnectivity.OFFLINE
        return result.map {}
    }

    override suspend fun login(username: String, password: String): Result<ApiUserSession> =
        client.login(username, password).map { data ->
            val session = ApiUserSession(
                userId = data.user_id,
                username = data.username,
                accessToken = data.access_token,
                expiresAt = System.currentTimeMillis() + data.expires_in * 1000
            )
            store.save(session)
            session
        }

    override suspend fun signup(username: String, password: String, confirmPassword: String): Result<Unit> =
        client.signup(username, password, confirmPassword).map {}

    override suspend fun logout() {
        store.clear()
    }
}
