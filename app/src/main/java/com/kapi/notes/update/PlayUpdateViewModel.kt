package com.kapi.notes.update

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayUpdateUiState(
    val availableUpdate: AppUpdateInfo? = null,
    val isDownloadReady: Boolean = false,
    val dismissed: Boolean = false,
    val readyDismissed: Boolean = false
)

@HiltViewModel
class PlayUpdateViewModel @Inject constructor(
    private val checker: PlayUpdateChecker
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayUpdateUiState())
    val uiState: StateFlow<PlayUpdateUiState> = _uiState.asStateFlow()

    private val listener: InstallStateUpdatedListener = checker.registerListener { status ->
        if (status == InstallStatus.DOWNLOADED) {
            _uiState.update { it.copy(isDownloadReady = true) }
        }
    }

    init {
        viewModelScope.launch {
            val info = checker.fetchUpdateInfo() ?: return@launch
            when {
                // Covers the app being relaunched after a flexible update finished downloading
                // while it was in the background/killed - the listener above only catches a
                // DOWNLOADED transition that happens while this ViewModel is alive.
                info.installStatus() == InstallStatus.DOWNLOADED ->
                    _uiState.update { it.copy(isDownloadReady = true) }
                info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                    info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) ->
                    _uiState.update { it.copy(availableUpdate = info) }
            }
        }
    }

    fun requestUpdate(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        val info = _uiState.value.availableUpdate ?: return
        checker.startFlexibleUpdate(info, launcher)
    }

    fun dismiss() {
        _uiState.update { it.copy(dismissed = true) }
    }

    /** "Để sau" on the ready-to-restart prompt - purely postpones the dialog for this session;
     * [InstallStatus.DOWNLOADED] has already happened, so the next launch's [fetchUpdateInfo]
     * check will offer it again rather than silently losing track of the pending update. */
    fun dismissReady() {
        _uiState.update { it.copy(readyDismissed = true) }
    }

    fun completeUpdate() {
        checker.completeUpdate()
    }

    override fun onCleared() {
        super.onCleared()
        checker.unregisterListener(listener)
    }
}
