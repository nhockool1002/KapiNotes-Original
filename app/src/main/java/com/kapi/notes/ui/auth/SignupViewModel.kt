package com.kapi.notes.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kapi.notes.R
import com.kapi.notes.apicenter.ApiCenterException
import com.kapi.notes.data.repository.ApiAuthRepository
import com.kapi.notes.data.repository.BackendConnectivity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private val USERNAME_REGEX = Regex("^[A-Za-z0-9_]{3,32}$")
private const val MIN_PASSWORD_LENGTH = 8

data class SignupUiState(
    val username: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val connectivity: BackendConnectivity = BackendConnectivity.UNKNOWN
) {
    val canSubmit: Boolean
        get() = !isLoading && username.isNotBlank() && password.isNotBlank() &&
            confirmPassword.isNotBlank() && connectivity != BackendConnectivity.OFFLINE
}

@HiltViewModel
class SignupViewModel @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val repository: ApiAuthRepository
) : ViewModel() {

    private val username = MutableStateFlow("")
    private val password = MutableStateFlow("")
    private val confirmPassword = MutableStateFlow("")
    private val isLoading = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SignupUiState> = combine(
        username, password, confirmPassword, isLoading, error, repository.connectivity
    ) { values ->
        SignupUiState(
            username = values[0] as String,
            password = values[1] as String,
            confirmPassword = values[2] as String,
            isLoading = values[3] as Boolean,
            error = values[4] as String?,
            connectivity = values[5] as BackendConnectivity
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SignupUiState())

    fun onUsernameChange(value: String) { username.value = value; error.value = null }
    fun onPasswordChange(value: String) { password.value = value; error.value = null }
    fun onConfirmPasswordChange(value: String) { confirmPassword.value = value; error.value = null }

    fun retryConnectivity() {
        viewModelScope.launch { repository.checkConnectivity() }
    }

    /** Mirrors the backend's own validation (API docs §4) so the user hears about a bad
     * username/password locally, instantly, instead of waiting on a round-trip for something
     * checkable on-device. The backend still re-validates - this is only a faster first pass. */
    private fun localValidationError(): String? {
        if (!USERNAME_REGEX.matches(username.value)) return context.getString(R.string.signup_error_username_invalid)
        if (password.value.length < MIN_PASSWORD_LENGTH) return context.getString(R.string.signup_error_password_too_short)
        if (password.value != confirmPassword.value) return context.getString(R.string.signup_error_password_mismatch)
        return null
    }

    fun signup(onSuccess: () -> Unit) {
        if (!uiState.value.canSubmit) return
        localValidationError()?.let { error.value = it; return }
        viewModelScope.launch {
            isLoading.value = true
            error.value = null
            repository.signup(username.value.trim(), password.value, confirmPassword.value)
                .mapCatching {
                    // Signup doesn't return a token (see API docs §5) - log the new account in
                    // right away so a successful signup lands the user signed in, not back at
                    // another form asking for the same credentials they just typed.
                    repository.login(username.value.trim(), password.value).getOrThrow()
                }
                .onSuccess { onSuccess() }
                .onFailure { e ->
                    error.value = (e as? ApiCenterException)?.message ?: e.message
                }
            isLoading.value = false
        }
    }
}
