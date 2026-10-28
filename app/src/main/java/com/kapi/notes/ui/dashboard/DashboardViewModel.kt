package com.kapi.notes.ui.dashboard

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kapi.notes.data.repository.AuthRepository
import com.kapi.notes.data.repository.NotesRepository
import com.kapi.notes.domain.model.Note
import com.kapi.notes.domain.model.PaperType
import com.kapi.notes.domain.model.SyncStatus
import com.kapi.notes.domain.model.UserSession
import com.kapi.notes.pdf.DocumentPageRenderer
import com.kapi.notes.update.UpdateChecker
import com.kapi.notes.update.VersionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

enum class DashboardSection(val labelVi: String) {
    MY_NOTES("Ghi chú của tôi"),
    FAVORITES("Mục yêu thích"),
    TRASH("Thùng rác")
}

data class DashboardUiState(
    val section: DashboardSection = DashboardSection.MY_NOTES,
    val query: String = "",
    val notes: List<Note> = emptyList(),
    val session: UserSession? = null,
    val syncStatus: SyncStatus = SyncStatus.IDLE,
    val isCreatingNote: Boolean = false,
    val isImportingLocalFile: Boolean = false,
    val importError: String? = null,
    val versionStatus: VersionStatus? = null
)

@OptIn(FlowPreview::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val notesRepository: NotesRepository,
    private val authRepository: AuthRepository,
    private val pageRenderer: DocumentPageRenderer,
    private val updateChecker: UpdateChecker,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val section = MutableStateFlow(DashboardSection.MY_NOTES)
    private val query = MutableStateFlow("")
    private val isCreatingNote = MutableStateFlow(false)
    private val isImportingLocalFile = MutableStateFlow(false)
    private val importError = MutableStateFlow<String?>(null)
    private val versionStatus = MutableStateFlow<VersionStatus?>(null)

    init {
        // Not on Play Store yet, so there is no store-driven update prompt - check the GitHub
        // repo's releases once per app session. Drives both the mandatory "update available"
        // dialog (when outdated) and the small version badge in the header. Silently does
        // nothing on failure/offline - the badge just stays hidden until the next launch.
        viewModelScope.launch {
            updateChecker.checkStatus().onSuccess { status -> versionStatus.value = status }
        }
    }

    private val notesForSection = section.flatMapLatest {
        when (it) {
            DashboardSection.MY_NOTES -> notesRepository.observeActiveNotes()
            DashboardSection.FAVORITES -> notesRepository.observeFavoriteNotes()
            DashboardSection.TRASH -> notesRepository.observeTrashedNotes()
        }
    }

    /** Debounced full-text search (title + any text box content) across active notes - null
     * means "no search in flight", so the UI falls back to the plain title filter below. */
    private val contentSearchResults: StateFlow<List<Note>?> = query
        .debounce(250)
        .map { q -> if (q.isBlank()) null else runCatching { notesRepository.searchNotes(q) }.getOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val uiState: StateFlow<DashboardUiState> = combine(
        section, query, notesForSection, authRepository.session, isCreatingNote, isImportingLocalFile, importError, authRepository.syncStatus, contentSearchResults, versionStatus
    ) { values ->
        val q = values[1] as String
        val sectionNotes = (values[2] as List<*>).filterIsInstance<Note>()
        val titleFiltered = if (q.isBlank()) sectionNotes else sectionNotes.filter { it.title.contains(q, ignoreCase = true) }
        @Suppress("UNCHECKED_CAST")
        val contentMatches = values[8] as List<Note>?
        DashboardUiState(
            section = values[0] as DashboardSection,
            query = q,
            notes = if (!q.isBlank() && contentMatches != null) {
                val sectionIds = sectionNotes.map { it.id }.toSet()
                (titleFiltered + contentMatches.filter { it.id in sectionIds }).distinctBy { it.id }
            } else titleFiltered,
            session = values[3] as UserSession?,
            isCreatingNote = values[4] as Boolean,
            isImportingLocalFile = values[5] as Boolean,
            importError = values[6] as String?,
            syncStatus = values[7] as SyncStatus,
            versionStatus = values[9] as VersionStatus?
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    /** Called once each time the Dashboard becomes visible (including coming back from the
     * Editor after editing a note). A session that's already signed in - restored from a
     * previous launch, not a fresh Google Sign-In - otherwise never triggers a pull: the only
     * things that used to call [AuthRepository.syncNow] were the manual "Đồng bộ ngay" button and
     * completeGoogleSignIn() right after signing in, so a second device signed in days ago never
     * saw the first device's notes (or vice versa) until someone remembered to tap sync by hand.
     * This is the fix for notes not syncing between two devices on the same Google account. */
    fun syncOnResume() {
        viewModelScope.launch {
            if (authRepository.session.firstOrNull() != null) {
                authRepository.syncNow()
            }
        }
    }

    fun syncNow() {
        viewModelScope.launch { authRepository.syncNow() }
    }

    fun selectSection(newSection: DashboardSection) {
        section.value = newSection
    }

    fun onQueryChange(newQuery: String) {
        query.value = newQuery
    }

    fun createBlankNote(title: String, paperType: PaperType, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            isCreatingNote.value = true
            val id = notesRepository.createBlankNote(title.ifBlank { "Ghi chú mới" }, paperType)
            isCreatingNote.value = false
            onCreated(id)
        }
    }

    /**
     * "Tải ảnh/PDF từ thiết bị" - lets the user pick a local PDF or image (via the system file
     * picker) and annotate on top of it, the same way a Drive import works but without Drive.
     * Word/.docx and other document formats have no renderer here, so they are rejected with an
     * explicit error rather than silently producing a blank note.
     */
    fun importLocalDocument(uri: Uri, displayName: String, mimeType: String?, onImported: (String) -> Unit) {
        viewModelScope.launch {
            isImportingLocalFile.value = true
            importError.value = null
            runCatching {
                val isPdf = mimeType == "application/pdf" || displayName.endsWith(".pdf", ignoreCase = true)
                val isImage = mimeType?.startsWith("image/") == true
                check(isPdf || isImage) {
                    "Chỉ hỗ trợ ghi chú trên PDF hoặc ảnh. Các định dạng khác (Word, Excel...) chưa hỗ trợ."
                }

                val extension = if (isPdf) "pdf" else "img"
                val tempFile = File(context.cacheDir, "local_import_${UUID.randomUUID()}.$extension")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                } ?: error("Không đọc được tệp đã chọn")

                val noteIdPlaceholder = UUID.randomUUID().toString()
                val pages = if (isPdf) {
                    pageRenderer.renderPdf(tempFile, noteIdPlaceholder).getOrThrow()
                } else {
                    listOf(pageRenderer.renderImage(tempFile, noteIdPlaceholder).getOrThrow())
                }
                tempFile.delete()

                notesRepository.createImportedNote(
                    title = displayName.substringBeforeLast('.'),
                    driveFileId = null,
                    driveFileName = displayName,
                    pageBackgroundPaths = pages.map { it.filePath },
                    pageWidthPx = pages.first().widthPx,
                    pageHeightPx = pages.first().heightPx
                )
            }.onSuccess { noteId ->
                isImportingLocalFile.value = false
                onImported(noteId)
            }.onFailure { e ->
                isImportingLocalFile.value = false
                importError.value = e.message ?: "Nhập tệp thất bại"
            }
        }
    }

    fun dismissImportError() {
        importError.value = null
    }

    fun toggleFavorite(note: Note) {
        viewModelScope.launch { notesRepository.setFavorite(note.id, !note.isFavorite) }
    }

    fun moveToTrash(note: Note) {
        viewModelScope.launch { notesRepository.moveToTrash(note.id) }
    }

    fun restoreFromTrash(note: Note) {
        viewModelScope.launch { notesRepository.restoreFromTrash(note.id) }
    }

    fun deletePermanently(note: Note) {
        viewModelScope.launch { notesRepository.deletePermanently(note.id) }
    }

    fun renameNote(note: Note, newTitle: String) {
        viewModelScope.launch { notesRepository.renameNote(note.id, newTitle) }
    }

    /** Duplicates the whole note (every page + its strokes/stickers/images/text boxes). */
    fun duplicateNote(note: Note) {
        viewModelScope.launch { notesRepository.duplicateNote(note.id) }
    }
}
