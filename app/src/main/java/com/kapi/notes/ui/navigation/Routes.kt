package com.kapi.notes.ui.navigation

object Routes {
    const val SPLASH = "splash"
    const val DASHBOARD = "dashboard"
    const val DRIVE_PICKER = "drive_picker"
    const val EDITOR = "editor/{noteId}"
    const val SETTINGS = "settings"
    const val ABOUT = "about"

    fun editor(noteId: String) = "editor/$noteId"
}
