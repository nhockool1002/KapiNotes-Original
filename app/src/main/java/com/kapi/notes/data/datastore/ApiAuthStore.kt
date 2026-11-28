package com.kapi.notes.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kapi.notes.domain.model.ApiUserSession
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.apiAuthDataStore by preferencesDataStore(name = "api_auth")

/** Persists the signed-in KapiNotes account (dev1002-api-center) across app restarts - a
 * deliberately separate DataStore file from [SessionStore] (Google Sign-In), same reasoning as
 * [AppSettingsStore]: this account system is independent of Google Sign-In/Drive sync and
 * shouldn't share storage with it. */
@Singleton
class ApiAuthStore @Inject constructor(@ApplicationContext private val context: Context) {

    private object Keys {
        val USER_ID = stringPreferencesKey("user_id")
        val USERNAME = stringPreferencesKey("username")
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val EXPIRES_AT = longPreferencesKey("expires_at")
    }

    val session: Flow<ApiUserSession?> = context.apiAuthDataStore.data.map { prefs ->
        val userId = prefs[Keys.USER_ID] ?: return@map null
        val token = prefs[Keys.ACCESS_TOKEN] ?: return@map null
        ApiUserSession(
            userId = userId,
            username = prefs[Keys.USERNAME].orEmpty(),
            accessToken = token,
            expiresAt = prefs[Keys.EXPIRES_AT] ?: 0L
        )
    }

    suspend fun save(session: ApiUserSession) {
        context.apiAuthDataStore.edit { prefs ->
            prefs[Keys.USER_ID] = session.userId
            prefs[Keys.USERNAME] = session.username
            prefs[Keys.ACCESS_TOKEN] = session.accessToken
            prefs[Keys.EXPIRES_AT] = session.expiresAt
        }
    }

    suspend fun clear() {
        context.apiAuthDataStore.edit { it.clear() }
    }
}
