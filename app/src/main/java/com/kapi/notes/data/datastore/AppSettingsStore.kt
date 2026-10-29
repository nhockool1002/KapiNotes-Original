package com.kapi.notes.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appSettingsDataStore by preferencesDataStore(name = "app_settings")

/** App-wide toggles that aren't tied to any one note or account - deliberately a separate
 * DataStore file from [SessionStore] (which is Drive-account state) so this stays simple and
 * untouched by anything sync/login-related. */
@Singleton
class AppSettingsStore @Inject constructor(@ApplicationContext private val context: Context) {

    private object Keys {
        val PALM_REJECTION_ENABLED = booleanPreferencesKey("palm_rejection_enabled")
    }

    /** When on, any finger/palm touch landing while a stylus pointer is actively down is ignored
     * for drawing AND for two-finger pan/zoom - see DrawingCanvas's pointer loop. Defaults to on:
     * the app's primary target (Xiaomi Pad Pro + real stylus) benefits from it immediately, and a
     * finger-only user is never affected since the suppression only ever activates relative to an
     * actually-pressed stylus pointer. */
    val palmRejectionEnabled: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs ->
        prefs[Keys.PALM_REJECTION_ENABLED] ?: true
    }

    suspend fun setPalmRejectionEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { prefs -> prefs[Keys.PALM_REJECTION_ENABLED] = enabled }
    }
}
