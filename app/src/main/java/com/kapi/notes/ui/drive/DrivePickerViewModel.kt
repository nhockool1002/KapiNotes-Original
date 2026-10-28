package com.kapi.notes.ui.drive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.kapi.notes.data.repository.NotesRepository
import com.kapi.notes.domain.model.DriveFile
import com.kapi.notes.domain.model.DriveFileKind
import com.kapi.notes.drive.DriveAuthManager
import com.kapi.notes.drive.DriveRepository
import com.kapi.notes.pdf.DocumentPageRenderer as PageRenderer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DrivePickerUiState(
    val account: GoogleSignInAccount? = null,
    val currentFolderId: String? = null,
    val breadcrumb: List<Pair<String, String>> = emptyList(), // folderId -> name, root excluded
    val files: List<DriveFile> = emptyList(),
    val isLoading: Boolean = false,
    val isImporting: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class DrivePickerViewModel @Inject constructor(
    val driveAuthManager: DriveAuthManager,
    private val driveRepository: DriveRepository,
    private val pageRenderer: PageRenderer,
    private val notesRepository: NotesRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DrivePickerUiState())
    val state: StateFlow<DrivePickerUiState> = _state.asStateFlow()

    init {
        driveAuthManager.lastSignedInAccount()?.let { account ->
            _state.value = _state.value.copy(account = account)
            refresh()
        }
    }

    fun onSignInResult(account: GoogleSignInAccount?, errorMessage: String? = null) {
        _state.value = _state.value.copy(
            account = account,
            error = if (account == null) errorMessage ?: "Đăng nhập Google Drive thất bại" else null
        )
        if (account != null) refresh()
    }

    fun refresh() {
        val account = _state.value.account ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            driveRepository.listFiles(account, _state.value.currentFolderId)
                .onSuccess { files -> _state.value = _state.value.copy(files = files, isLoading = false) }
                .onFailure { e -> _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Không tải được danh sách tệp") }
        }
    }

    fun openFolder(folder: DriveFile) {
        _state.value = _state.value.copy(
            currentFolderId = folder.id,
            breadcrumb = _state.value.breadcrumb + (folder.id to folder.name)
        )
        refresh()
    }

    fun navigateToBreadcrumb(index: Int) {
        val crumbs = _state.value.breadcrumb
        if (index < 0) {
            _state.value = _state.value.copy(currentFolderId = null, breadcrumb = emptyList())
        } else {
            _state.value = _state.value.copy(
                currentFolderId = crumbs[index].first,
                breadcrumb = crumbs.take(index + 1)
            )
        }
        refresh()
    }

    fun signOut() {
        driveAuthManager.signOut()
        _state.value = DrivePickerUiState()
    }

    /** Downloads the file, rasterizes every page, and creates a new note on top of it. */
    fun importFile(file: DriveFile, onImported: (String) -> Unit) {
        val account = _state.value.account ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isImporting = true, error = null)
            runCatching {
                val extension = if (file.kind == DriveFileKind.PDF) "pdf" else "img"
                val tempFile = pageRenderer.newTempSourceFile(extension)
                tempFile.outputStream().use { out ->
                    driveRepository.downloadFile(account, file.id, out).getOrThrow()
                }

                val noteIdPlaceholder = java.util.UUID.randomUUID().toString()
                val pages = if (file.kind == DriveFileKind.PDF) {
                    pageRenderer.renderPdf(tempFile, noteIdPlaceholder).getOrThrow()
                } else {
                    listOf(pageRenderer.renderImage(tempFile, noteIdPlaceholder).getOrThrow())
                }
                tempFile.delete()

                notesRepository.createImportedNote(
                    title = file.name,
                    driveFileId = file.id,
                    driveFileName = file.name,
                    pageBackgroundPaths = pages.map { it.filePath },
                    pageWidthPx = pages.first().widthPx,
                    pageHeightPx = pages.first().heightPx
                )
            }.onSuccess { noteId ->
                _state.value = _state.value.copy(isImporting = false)
                onImported(noteId)
            }.onFailure { e ->
                _state.value = _state.value.copy(isImporting = false, error = e.message ?: "Nhập tài liệu thất bại")
            }
        }
    }
}
