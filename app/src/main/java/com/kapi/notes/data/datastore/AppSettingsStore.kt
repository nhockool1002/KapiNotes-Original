package com.kapi.notes.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
        val LAST_TOOL = stringPreferencesKey("last_tool")
        val LAST_COLOR_ARGB = longPreferencesKey("last_color_argb")
        val LAST_STROKE_WIDTH_DP = floatPreferencesKey("last_stroke_width_dp")
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

    /** The last tool/color/stroke-width the user had selected in the Editor (stored as
     * [com.kapi.notes.ui.editor.EditorTool]'s name) - read once when a note is opened so the
     * toolbar comes back exactly as the user left it instead of always resetting to the pen/black
     * defaults. Null means "nothing saved yet" (fresh install), so the caller keeps its own
     * default in that case. */
    suspend fun lastToolName(): String? = context.appSettingsDataStore.data.first()[Keys.LAST_TOOL]
    suspend fun lastColorArgb(): Long? = context.appSettingsDataStore.data.first()[Keys.LAST_COLOR_ARGB]
    suspend fun lastStrokeWidthDp(): Float? = context.appSettingsDataStore.data.first()[Keys.LAST_STROKE_WIDTH_DP]

    suspend fun setLastTool(toolName: String) {
        context.appSettingsDataStore.edit { prefs -> prefs[Keys.LAST_TOOL] = toolName }
    }

    suspend fun setLastColorArgb(colorArgb: Long) {
        context.appSettingsDataStore.edit { prefs -> prefs[Keys.LAST_COLOR_ARGB] = colorArgb }
    }

    suspend fun setLastStrokeWidthDp(widthDp: Float) {
        context.appSettingsDataStore.edit { prefs -> prefs[Keys.LAST_STROKE_WIDTH_DP] = widthDp }
    }
}
