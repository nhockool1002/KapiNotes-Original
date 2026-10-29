package com.kapi.notes

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.kapi.notes.ui.navigation.KapiNotesNavGraph
import com.kapi.notes.ui.theme.KapiNotesTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Triggered by the home-screen widget (see [com.kapi.notes.widget.QuickNoteWidgetProvider]):
     * skip straight to a brand-new blank note instead of landing on the Dashboard. */
    companion object {
        const val ACTION_QUICK_NOTE = "com.kapi.notes.action.QUICK_NOTE"
    }

    // A plain mutableStateOf the Activity owns (not SavedStateHandle/ViewModel-scoped) because
    // this is a one-shot "did we just get launched from the widget" signal, not persisted state -
    // Dashboard reads it once, immediately acts on it, and clears it via onQuickNoteConsumed so a
    // configuration change or recomposition can never accidentally create a second blank note.
    private var quickNoteRequested by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        quickNoteRequested = intent?.action == ACTION_QUICK_NOTE
        setContent {
            KapiNotesTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    KapiNotesNavGraph(
                        quickNoteRequested = quickNoteRequested,
                        onQuickNoteConsumed = { quickNoteRequested = false }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_QUICK_NOTE) {
            quickNoteRequested = true
        }
    }
}
