package com.kapi.notes.auth

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.kapi.notes.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles Google Sign-In for the whole app. This is identity only - no Google Drive (or any
 * other Google API) scope is requested, just the account's email and an ID token (`requestIdToken`,
 * audience = [BuildConfig.GOOGLE_SERVER_CLIENT_ID]). That token is what the app sends to our own
 * Cloudflare Worker (see [com.kapi.notes.data.repository.DriveSyncAuthRepository] and
 * [com.kapi.notes.sync.SyncApi]) for both note sync and the "share view-only link" feature -
 * the Worker verifies the token itself and is the only thing holding storage credentials, so
 * this app never touches Drive, Firebase, or any other third-party storage API.
 */
@Singleton
class GoogleAuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val signInClient: GoogleSignInClient by lazy {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(BuildConfig.GOOGLE_SERVER_CLIENT_ID)
            .build()
        GoogleSignIn.getClient(context, options)
    }

    fun signInIntent(): Intent = signInClient.signInIntent

    fun lastSignedInAccount(): GoogleSignInAccount? = GoogleSignIn.getLastSignedInAccount(context)

    fun signOut() {
        signInClient.signOut()
    }
}
