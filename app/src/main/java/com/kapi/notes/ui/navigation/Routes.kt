package com.kapi.notes.ui.navigation

object Routes {
    const val SPLASH = "splash"
    const val DASHBOARD = "dashboard"
    const val EDITOR = "editor/{noteId}"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val LOGIN = "login"
    const val SIGNUP = "signup"

    fun editor(noteId: String) = "editor/$noteId"
}
