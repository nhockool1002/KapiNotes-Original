package com.kapi.notes.ui.settings

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kapi.notes.data.datastore.AppSettingsStore
import com.kapi.notes.data.repository.AuthRepository
import com.kapi.notes.data.repository.BackupRepository
import com.kapi.notes.domain.model.SyncStatus
import com.kapi.notes.drive.DriveAuthManager
import com.kapi.notes.domain.model.UserSession
import com.kapi.notes.sync.SyncLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class SettingsUiState(
    val session: UserSession? = null,
    val syncStatus: SyncStatus = SyncStatus.IDLE,
    val isLoggingIn: Boolean = false,
    val loginError: String? = null,
    val isWorkingOnBackup: Boolean = false,
    val backupMessage: String? = null,
    val lastExportedFile: File? = null,
    val showDeleteConfirm: Boolean = false,
    val lastSharedLogFile: File? = null,
    val palmRejectionEnabled: Boolean = true
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val backupRepository: BackupRepository,
    private val syncLogger: SyncLogger,
    private val appSettingsStore: AppSettingsStore,
    val driveAuthManager: DriveAuthManager
) : ViewModel() {

    private val isLoggingIn = MutableStateFlow(false)
    private val loginError = MutableStateFlow<String?>(null)
    private val isWorkingOnBackup = MutableStateFlow(false)
    private val backupMessage = MutableStateFlow<String?>(null)
    private val lastExportedFile = MutableStateFlow<File?>(null)
    private val showDeleteConfirm = MutableStateFlow(false)
    private val lastSharedLogFile = MutableStateFlow<File?>(null)

    private data class AccountPart(val session: UserSession?, val syncStatus: SyncStatus, val isLoggingIn: Boolean, val loginError: String?)
    private data class BackupPart(val isWorkingOnBackup: Boolean, val backupMessage: String?, val lastExportedFile: File?, val showDeleteConfirm: Boolean, val lastSharedLogFile: File?)

    private val accountPart = combine(authRepository.session, authRepository.syncStatus, isLoggingIn, loginError) { session, syncStatus, loggingIn, error ->
        AccountPart(session, syncStatus, loggingIn, error)
    }
    private val backupPart = combine(isWorkingOnBackup, backupMessage, lastExportedFile, showDeleteConfirm, lastSharedLogFile) { working, message, file, confirm, logFile ->
        BackupPart(working, message, file, confirm, logFile)
    }

    val uiState: StateFlow<SettingsUiState> = combine(accountPart, backupPart, appSettingsStore.palmRejectionEnabled) { account, backup, palmRejection ->
        SettingsUiState(
            session = account.session,
            syncStatus = account.syncStatus,
            isLoggingIn = account.isLoggingIn,
            loginError = account.loginError,
            isWorkingOnBackup = backup.isWorkingOnBackup,
            backupMessage = backup.backupMessage,
            lastExportedFile = backup.lastExportedFile,
            showDeleteConfirm = backup.showDeleteConfirm,
            lastSharedLogFile = backup.lastSharedLogFile,
            palmRejectionEnabled = palmRejection
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun onGoogleSignInResult(account: GoogleSignInAccount?, errorMessage: String? = null) {
        if (account == null) {
            loginError.value = errorMessage
            return
        }
        viewModelScope.launch {
            isLoggingIn.value = true
            loginError.value = null
            authRepository.completeGoogleSignIn(account)
                .onFailure { loginError.value = it.message }
            isLoggingIn.value = false
        }
    }

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }

    fun triggerSync() {
        viewModelScope.launch {
            authRepository.syncNow().onFailure { backupMessage.value = "Đồng bộ thất bại: ${it.message}" }
        }
    }

    fun exportBackup() {
        viewModelScope.launch {
            isWorkingOnBackup.value = true
            // Must land under cacheDir/exports/ (not the cache root) - that's the only cache
            // subpath file_paths.xml exposes to FileProvider; a file saved directly in cacheDir's
            // root makes the share-sheet LaunchedEffect below crash with "Failed to find
            // configured root" the moment it calls FileProvider.getUriForFile() on it.
            val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val dest = File(exportsDir, "kapinotes_backup_${UUID.randomUUID().toString().take(6)}.kapibackup")
            backupRepository.exportToFile(dest)
                .onSuccess { file ->
                    lastExportedFile.value = file
                    backupMessage.value = "Đã xuất dữ liệu ra ${file.name}"
                }
                .onFailure { backupMessage.value = "Xuất dữ liệu thất bại: ${it.message}" }
            isWorkingOnBackup.value = false
        }
    }

    fun consumeExportedFile() {
        lastExportedFile.value = null
    }

    fun importBackup(file: File) {
        viewModelScope.launch {
            isWorkingOnBackup.value = true
            backupRepository.importFromFile(file)
                .onSuccess { backupMessage.value = "Đã nhập dữ liệu thành công" }
                .onFailure { backupMessage.value = "Nhập dữ liệu thất bại: ${it.message}" }
            isWorkingOnBackup.value = false
        }
    }

    fun requestDeleteAllData() {
        showDeleteConfirm.value = true
    }

    fun cancelDeleteAllData() {
        showDeleteConfirm.value = false
    }

    fun confirmDeleteAllData() {
        viewModelScope.launch {
            showDeleteConfirm.value = false
            isWorkingOnBackup.value = true
            backupRepository.deleteEverything()
            backupMessage.value = "Đã xóa toàn bộ dữ liệu trên máy"
            isWorkingOnBackup.value = false
        }
    }

    fun dismissMessage() {
        backupMessage.value = null
    }

    /** "Chia sẻ log đồng bộ" - lets the user hand the step-by-step sync log straight to a bug
     * report or support chat when a sync failed or timed out, instead of just a generic error
     * message with no way to tell which step actually got stuck. */
    fun shareSyncLog() {
        lastSharedLogFile.value = syncLogger.file()
    }

    fun consumeSharedLogFile() {
        lastSharedLogFile.value = null
    }

    fun setPalmRejectionEnabled(enabled: Boolean) {
        viewModelScope.launch { appSettingsStore.setPalmRejectionEnabled(enabled) }
    }
}
