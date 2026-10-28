package com.kapi.notes.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kapi.notes.ui.dashboard.DashboardScreen
import com.kapi.notes.ui.drive.DrivePickerScreen
import com.kapi.notes.ui.editor.EditorScreen
import com.kapi.notes.ui.settings.SettingsScreen

@Composable
fun KapiNotesNavGraph(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.DASHBOARD) {
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onOpenNote = { noteId -> navController.navigate(Routes.editor(noteId)) },
                onOpenDrivePicker = { navController.navigate(Routes.DRIVE_PICKER) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
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
    }
}
