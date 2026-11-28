package com.kapi.notes.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kapi.notes.apicenter.ApiCenterException
import com.kapi.notes.data.repository.ApiAuthRepository
import com.kapi.notes.data.repository.ApiSyncRepository
import com.kapi.notes.data.repository.BackendConnectivity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val connectivity: BackendConnectivity = BackendConnectivity.UNKNOWN
) {
    val canSubmit: Boolean
        get() = !isLoading && username.isNotBlank() && password.isNotBlank() && connectivity != BackendConnectivity.OFFLINE
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: ApiAuthRepository,
    private val syncRepository: ApiSyncRepository
) : ViewModel() {

    private val username = MutableStateFlow("")
    private val password = MutableStateFlow("")
    private val isLoading = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<LoginUiState> = combine(
        username, password, isLoading, error, repository.connectivity
    ) { u, p, loading, err, connectivity ->
        LoginUiState(u, p, loading, err, connectivity)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LoginUiState())

    fun onUsernameChange(value: String) { username.value = value; error.value = null }
    fun onPasswordChange(value: String) { password.value = value; error.value = null }

    fun retryConnectivity() {
        viewModelScope.launch { repository.checkConnectivity() }
    }

    fun login(onSuccess: () -> Unit) {
        if (!uiState.value.canSubmit) return
        viewModelScope.launch {
            isLoading.value = true
            error.value = null
            repository.login(username.value.trim(), password.value)
                .onSuccess {
                    // Best-effort, never blocks landing the user back on Settings - merging in
                    // whatever's already on R2 (if anything) is a nice-to-have on login, not a
                    // precondition for having successfully signed in.
                    runCatching { syncRepository.downloadAndMerge() }
                    onSuccess()
                }
                .onFailure { e ->
                    error.value = (e as? ApiCenterException)?.message ?: e.message
                }
            isLoading.value = false
        }
    }
}
