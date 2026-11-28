package com.kapi.notes

import android.app.Application
import com.kapi.notes.data.repository.ApiAuthRepository
import com.kapi.notes.data.repository.ApiSyncRepository
import com.kapi.notes.data.repository.AuthRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

private val SYNC_INTERVAL_MS = 3 * 60 * 1000L

/**
 * Keeps notes in sync across devices without the user needing to remember to tap "Đồng bộ
 * ngay": as long as the app process is alive (any screen, not just Dashboard), this pulls/pushes
 * every [SYNC_INTERVAL_MS]. Android has no reliable way to run a true background job this
 * frequently without a foreground service (WorkManager's own periodic jobs have a hard 15-minute
 * floor) - this covers "the app is open or recently was", which is the common case; it does not
 * sync while the app is fully killed/swiped away.
 */
@HiltAndroidApp
class KapiNotesApp : Application() {

    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var apiAuthRepository: ApiAuthRepository
    @Inject lateinit var apiSyncRepository: ApiSyncRepository
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            while (isActive) {
                delay(SYNC_INTERVAL_MS)
                if (authRepository.session.firstOrNull() != null) {
                    runCatching { authRepository.syncNow() }
                }
                // Separate, independent account - pushes local notes to R2 while the user is
                // actively editing (this loop runs the whole time the app process is alive, not
                // just on Dashboard) whenever a KapiNotes account is signed in. Never required:
                // signing in here is entirely optional, so a failure/no-session here is silent.
                if (apiAuthRepository.session.firstOrNull() != null) {
                    runCatching { apiSyncRepository.uploadNow() }
                }
            }
        }
    }
}
