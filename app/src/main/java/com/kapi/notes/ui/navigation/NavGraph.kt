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
                onBack = { navController.popBackStack() },
                onNoteImported = { noteId ->
                    navController.popBackStack()
                    navController.navigate(Routes.editor(noteId))
                }
            )
        }
        composable(Routes.EDITOR) {
            EditorScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }
}
