package com.kapi.notes.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kapi.notes.domain.model.UserSession
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sessionDataStore by preferencesDataStore(name = "session")

/**
 * Persists the signed-in Google account (+ which Drive file holds its notes backup) across app
 * restarts. See [com.kapi.notes.data.repository.DriveSyncAuthRepository] - there is no backend,
 * so this is the entire "session" the app needs to remember.
 */
@Singleton
class SessionStore @Inject constructor(@ApplicationContext private val context: Context) {

    private object Keys {
        val USER_ID = stringPreferencesKey("user_id")
        val EMAIL = stringPreferencesKey("email")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val AVATAR_URL = stringPreferencesKey("avatar_url")
        val DRIVE_SYNC_FILE_ID = stringPreferencesKey("drive_sync_file_id")
        val LAST_SYNCED_AT = longPreferencesKey("last_synced_at")
    }

    val session: Flow<UserSession?> = context.sessionDataStore.data.map { prefs ->
        val userId = prefs[Keys.USER_ID] ?: return@map null
        UserSession(
            userId = userId,
            email = prefs[Keys.EMAIL].orEmpty(),
            displayName = prefs[Keys.DISPLAY_NAME].orEmpty(),
            avatarUrl = prefs[Keys.AVATAR_URL],
            driveSyncFileId = prefs[Keys.DRIVE_SYNC_FILE_ID],
            lastSyncedAt = prefs[Keys.LAST_SYNCED_AT]
        )
    }

    suspend fun save(session: UserSession) {
        context.sessionDataStore.edit { prefs ->
            prefs[Keys.USER_ID] = session.userId
            prefs[Keys.EMAIL] = session.email
            prefs[Keys.DISPLAY_NAME] = session.displayName
            session.avatarUrl?.let { prefs[Keys.AVATAR_URL] = it }
            session.driveSyncFileId?.let { prefs[Keys.DRIVE_SYNC_FILE_ID] = it }
            session.lastSyncedAt?.let { prefs[Keys.LAST_SYNCED_AT] = it }
        }
    }

    suspend fun updateSyncState(driveSyncFileId: String?, lastSyncedAt: Long) {
        context.sessionDataStore.edit { prefs ->
            driveSyncFileId?.let { prefs[Keys.DRIVE_SYNC_FILE_ID] = it }
            prefs[Keys.LAST_SYNCED_AT] = lastSyncedAt
        }
    }

    suspend fun clear() {
        context.sessionDataStore.edit { it.clear() }
    }
}
