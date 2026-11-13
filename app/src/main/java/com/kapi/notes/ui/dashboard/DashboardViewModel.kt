package com.kapi.notes.ui.dashboard

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kapi.notes.R
import com.kapi.notes.ads.AdsGate
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

enum class DashboardSection(val labelRes: Int) {
    MY_NOTES(R.string.dashboard_section_my_notes),
    FAVORITES(R.string.dashboard_section_favorites),
    TRASH(R.string.dashboard_section_trash)
}

/** A note's label as it appears in the dashboard's filter row - just the distinct (text, color)
 * pairs currently in use in this section, so the row only ever shows labels that actually exist. */
data class NoteLabelFilter(val text: String, val colorArgb: Long)

data class DashboardUiState(
    val section: DashboardSection = DashboardSection.MY_NOTES,
    val query: String = "",
    val notes: List<Note> = emptyList(),
    val session: UserSession? = null,
    val syncStatus: SyncStatus = SyncStatus.IDLE,
    val isCreatingNote: Boolean = false,
    val isImportingLocalFile: Boolean = false,
    val importError: String? = null,
    val versionStatus: VersionStatus? = null,
    val availableLabels: List<NoteLabelFilter> = emptyList(),
    val selectedLabel: String? = null,
    val shouldShowAds: Boolean = false
)

@OptIn(FlowPreview::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val notesRepository: NotesRepository,
    private val authRepository: AuthRepository,
    private val pageRenderer: DocumentPageRenderer,
    private val documentTextImporter: com.kapi.notes.pdf.DocumentTextImporter,
    private val updateChecker: UpdateChecker,
    private val adsGate: AdsGate,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val section = MutableStateFlow(DashboardSection.MY_NOTES)
    private val query = MutableStateFlow("")
    private val isCreatingNote = MutableStateFlow(false)
    private val isImportingLocalFile = MutableStateFlow(false)
    private val importError = MutableStateFlow<String?>(null)
    private val versionStatus = MutableStateFlow<VersionStatus?>(null)
    private val selectedLabel = MutableStateFlow<String?>(null)

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
        section, query, notesForSection, authRepository.session, isCreatingNote, isImportingLocalFile, importError, authRepository.syncStatus, contentSearchResults, versionStatus, selectedLabel, adsGate.shouldShowAds
    ) { values ->
        val q = values[1] as String
        val sectionNotes = (values[2] as List<*>).filterIsInstance<Note>()
        val titleFiltered = if (q.isBlank()) sectionNotes else sectionNotes.filter { it.title.contains(q, ignoreCase = true) }
        @Suppress("UNCHECKED_CAST")
        val contentMatches = values[8] as List<Note>?
        val searchFiltered = if (!q.isBlank() && contentMatches != null) {
            val sectionIds = sectionNotes.map { it.id }.toSet()
            (titleFiltered + contentMatches.filter { it.id in sectionIds }).distinctBy { it.id }
        } else titleFiltered

        val label = values[10] as String?
        val labelFiltered = if (label == null) searchFiltered else searchFiltered.filter { it.labelText == label }

        // Only labels actually present in THIS section (not every label ever created), so the
        // filter row never shows a chip that would immediately produce an empty grid.
        val labels = sectionNotes.mapNotNull { note ->
            note.labelText?.let { text -> NoteLabelFilter(text, note.labelColorArgb ?: 0xFF64748BL) }
        }.distinctBy { it.text }

        DashboardUiState(
            section = values[0] as DashboardSection,
            query = q,
            notes = labelFiltered,
            session = values[3] as UserSession?,
            isCreatingNote = values[4] as Boolean,
            isImportingLocalFile = values[5] as Boolean,
            importError = values[6] as String?,
            syncStatus = values[7] as SyncStatus,
            versionStatus = values[9] as VersionStatus?,
            availableLabels = labels,
            selectedLabel = label,
            shouldShowAds = values[11] as Boolean
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
            val id = notesRepository.createBlankNote(title.ifBlank { context.getString(R.string.dashboard_default_note_title) }, paperType)
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
                val isTxt = mimeType == "text/plain" || displayName.endsWith(".txt", ignoreCase = true)
                val isDocx = mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
                    displayName.endsWith(".docx", ignoreCase = true)
                check(isPdf || isImage || isTxt || isDocx) {
                    context.getString(R.string.dashboard_import_unsupported_format)
                }

                if (isTxt || isDocx) {
                    importTextDocument(uri, displayName, isDocx)
                } else {
                    importVisualDocument(uri, displayName, isPdf)
                }
            }.onSuccess { noteId ->
                isImportingLocalFile.value = false
                onImported(noteId)
            }.onFailure { e ->
                isImportingLocalFile.value = false
                importError.value = e.message ?: context.getString(R.string.dashboard_import_generic_failed)
            }
        }
    }

    private suspend fun importVisualDocument(uri: Uri, displayName: String, isPdf: Boolean): String {
        val extension = if (isPdf) "pdf" else "img"
        val tempFile = File(context.cacheDir, "local_import_${UUID.randomUUID()}.$extension")
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output -> input.copyTo(output) }
        } ?: error(context.getString(R.string.dashboard_import_read_failed))

        val noteIdPlaceholder = UUID.randomUUID().toString()
        val pages = if (isPdf) {
            pageRenderer.renderPdf(tempFile, noteIdPlaceholder).getOrThrow()
        } else {
            listOf(pageRenderer.renderImage(tempFile, noteIdPlaceholder).getOrThrow())
        }
        tempFile.delete()

        return notesRepository.createImportedNote(
            title = displayName.substringBeforeLast('.'),
            driveFileId = null,
            driveFileName = displayName,
            pageBackgroundPaths = pages.map { it.filePath },
            pageWidthPx = pages.first().widthPx,
            pageHeightPx = pages.first().heightPx
        )
    }

    /** `.txt`/`.docx` have no fixed page layout of their own - unlike PDFs/images (rendered as a
     * page BACKGROUND image), their text is placed as an editable [com.kapi.notes.domain.model.TextBoxItem]
     * on one or more blank pages, split at paragraph/word boundaries by
     * [com.kapi.notes.pdf.paginateImportedText] so a long document doesn't overflow a single page. */
    private suspend fun importTextDocument(uri: Uri, displayName: String, isDocx: Boolean): String {
        val text = documentTextImporter.extractPlainText(context, uri, isDocx).getOrThrow()
        val pages = com.kapi.notes.pdf.paginateImportedText(text)

        val noteId = notesRepository.createBlankNote(displayName.substringBeforeLast('.'), PaperType.BLANK)
        val note = notesRepository.getNote(noteId) ?: error("Note vanished right after creation")
        val firstPageId = notesRepository.getPages(noteId).first().id

        val marginPx = com.kapi.notes.pdf.IMPORTED_TEXT_MARGIN_PX
        val fontSizePx = com.kapi.notes.pdf.IMPORTED_TEXT_FONT_SIZE_PX
        // 1588x2246: NotesRepositoryImpl's DEFAULT_PAGE_WIDTH_PX/HEIGHT_PX (private to that file,
        // can't reference directly) - both createBlankNote and addPage(..., null, ...) with no
        // PDF template to inherit from use exactly these for a BLANK page, so this stays correct
        // as long as that default doesn't change independently of this file.
        pages.forEachIndexed { index, pageText ->
            val pageId = if (index == 0) firstPageId else notesRepository.addPage(noteId, null, PaperType.BLANK)
            notesRepository.addTextBox(
                com.kapi.notes.domain.model.TextBoxItem(
                    id = UUID.randomUUID().toString(),
                    pageId = pageId,
                    text = pageText,
                    x = marginPx,
                    y = marginPx,
                    widthPx = 1588f - marginPx * 2,
                    heightPx = 2246f - marginPx * 2,
                    fontSizePx = fontSizePx,
                    colorArgb = 0xFF1E293BL
                )
            )
        }
        return note.id
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

    /** Sets/clears a note's colored label (passing a blank text clears it). */
    fun setNoteLabel(note: Note, text: String, colorArgb: Long) {
        viewModelScope.launch {
            notesRepository.setNoteLabel(note.id, text.trim().ifBlank { null }, if (text.isBlank()) null else colorArgb)
        }
    }

    /** Tapping an already-selected filter chip clears the filter again. */
    fun toggleLabelFilter(label: String) {
        selectedLabel.value = if (selectedLabel.value == label) null else label
    }
}
