package com.kapi.notes.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kapi.notes.ui.about.AboutScreen
import com.kapi.notes.ui.dashboard.DashboardScreen
import com.kapi.notes.ui.drive.DrivePickerScreen
import com.kapi.notes.ui.editor.EditorScreen
import com.kapi.notes.ui.settings.SettingsScreen
import com.kapi.notes.ui.splash.SplashScreen

/** Plain popBackStack() happily pops the very last entry too, leaving the back stack empty with
 * nothing for the NavHost to show (a blank/black screen) - Dashboard is the effective root here
 * (Splash removes itself via popUpTo(inclusive=true) right after launch), so there should never
 * be anything below it to pop into. Guarding every pop site against that, rather than trusting
 * each caller to only ever be invoked once, protects against a caller firing twice in a race (e.g.
 * a quick double back-press queuing two pops before the first one's navigation settles). */
private fun NavHostController.safePopBackStack() {
    if (previousBackStackEntry != null) popBackStack()
}

@Composable
fun KapiNotesNavGraph(
    navController: NavHostController = rememberNavController(),
    quickNoteRequested: Boolean = false,
    onQuickNoteConsumed: () -> Unit = {}
) {
    NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onFinished = {
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onOpenNote = { noteId -> navController.navigate(Routes.editor(noteId)) },
                onOpenDrivePicker = { navController.navigate(Routes.DRIVE_PICKER) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenAbout = { navController.navigate(Routes.ABOUT) },
                quickNoteRequested = quickNoteRequested,
                onQuickNoteConsumed = onQuickNoteConsumed
            )
        }
        composable(Routes.DRIVE_PICKER) {
            DrivePickerScreen(
                onBack = { navController.safePopBackStack() },
                onNoteImported = { noteId ->
                    navController.safePopBackStack()
                    navController.navigate(Routes.editor(noteId))
                }
            )
        }
        composable(Routes.EDITOR) {
            EditorScreen(onBack = { navController.safePopBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.safePopBackStack() })
        }
        composable(Routes.ABOUT) {
            AboutScreen(onBack = { navController.safePopBackStack() })
        }
    }
}
