package com.kapi.notes.update

import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps Google Play's official In-App Updates API. Unlike [UpdateChecker] (GitHub releases, the
 * update path while this app isn't distributed through Play Store), [AppUpdateManager] only ever
 * reports an update when the installed build actually came from Google Play - it safely returns
 * "no update" for a sideloaded APK or a build run straight from Android Studio, so this can
 * always be checked unconditionally with no install-source detection of our own.
 */
@Singleton
class PlayUpdateChecker @Inject constructor(@ApplicationContext context: Context) {

    private val manager: AppUpdateManager = AppUpdateManagerFactory.create(context)

    /** Null on any failure (no network, Play Store app missing/outdated, etc) - the caller just
     * skips showing anything, same as [UpdateChecker] does on failure. */
    suspend fun fetchUpdateInfo(): AppUpdateInfo? = runCatching { manager.appUpdateInfo.await() }.getOrNull()

    /** Launches Play's own update UI (a bottom sheet) for a FLEXIBLE update - downloads in the
     * background while the user keeps using the app; [InstallStateUpdatedListener] below reports
     * when it's done and ready for [completeUpdate]. */
    fun startFlexibleUpdate(info: AppUpdateInfo, launcher: ActivityResultLauncher<IntentSenderRequest>) {
        manager.startUpdateFlowForResult(
            info,
            launcher,
            AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
        )
    }

    /** [onStatusChanged] receives an `InstallStatus` constant (see
     * `com.google.android.play.core.install.model.InstallStatus`) every time it changes -
     * callers only need to act on [com.google.android.play.core.install.model.InstallStatus.DOWNLOADED]. */
    fun registerListener(onStatusChanged: (Int) -> Unit): InstallStateUpdatedListener {
        val listener = InstallStateUpdatedListener { state -> onStatusChanged(state.installStatus()) }
        manager.registerListener(listener)
        return listener
    }

    fun unregisterListener(listener: InstallStateUpdatedListener) {
        manager.unregisterListener(listener)
    }

    /** Finishes installing a FLEXIBLE update that has already fully downloaded - this restarts
     * the app, so only call it right after the user confirms ("Khởi động lại"). */
    fun completeUpdate() {
        manager.completeUpdate()
    }
}
