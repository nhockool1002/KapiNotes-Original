package com.kapi.notes.ui.editor

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kapi.notes.audio.AudioRecorderManager
import com.kapi.notes.data.repository.NotesRepository
import com.kapi.notes.domain.model.AudioClip
import com.kapi.notes.domain.model.EmojiSticker
import com.kapi.notes.domain.model.ImageAttachment
import com.kapi.notes.domain.model.Note
import com.kapi.notes.domain.model.NotePage
import com.kapi.notes.domain.model.PageFlip
import com.kapi.notes.domain.model.PageOrientation
import com.kapi.notes.domain.model.PaperType
import com.kapi.notes.domain.model.Stroke
import com.kapi.notes.domain.model.StrokePoint
import com.kapi.notes.domain.model.TextBoxItem
import com.kapi.notes.domain.model.TextFontFamily
import com.kapi.notes.domain.model.ToolType
import com.kapi.notes.pdf.NoteExporter
import com.kapi.notes.ui.theme.PenColorSwatches
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class EditorUiState(
    val note: Note? = null,
    val pages: List<NotePage> = emptyList(),
    val currentPageIndex: Int = 0,
    val strokes: List<Stroke> = emptyList(),
    val stickers: List<EmojiSticker> = emptyList(),
    val images: List<ImageAttachment> = emptyList(),
    val textBoxes: List<TextBoxItem> = emptyList(),
    val audioClips: List<AudioClip> = emptyList(),
    val tool: EditorTool = EditorTool.PEN,
    val colorArgb: Long = PenColorSwatches.first(),
    val customColors: List<Long> = emptyList(),
    val strokeWidthDp: Float = 2f,
    val isShapeFillEnabled: Boolean = false,
    val selectedStrokeIds: Set<String> = emptySet(),
    val lassoRectMode: Boolean = false,
    val rulerMode: Boolean = false,
    val compassMode: Boolean = false,
    val eraseMode: EraseMode = EraseMode.STROKE,
    val isEmojiPickerOpen: Boolean = false,
    val pendingEmojiPlacement: Offset? = null,
    val pendingTextBoxPlacement: Offset? = null,
    val editingTextBoxId: String? = null,
    val textDialogBold: Boolean = false,
    val textDialogItalic: Boolean = false,
    val textDialogFontFamily: TextFontFamily = TextFontFamily.DEFAULT,
    val textDialogFontSize: Float = 32f,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isPaperMenuOpen: Boolean = false,
    val isShapeMenuOpen: Boolean = false,
    val isFullscreen: Boolean = false,
    val isRecording: Boolean = false,
    val recordingStartedAt: Long = 0L,
    val playingClipId: String? = null,
    val laserPoint: Offset? = null,
    val isRecognizingHandwriting: Boolean = false,
    val recognitionLanguage: com.kapi.notes.ocr.RecognitionLanguage? = null,
    val isExporting: Boolean = false,
    val exportedFile: File? = null,
    val exportedFileMimeType: String = "application/pdf",
    val isImportingBackground: Boolean = false,
    val isSharingLink: Boolean = false,
    val shareLink: String? = null,
    val error: String? = null
) {
    val currentPage: NotePage? get() = pages.getOrNull(currentPageIndex)
    val editingTextBox: TextBoxItem? get() = editingTextBoxId?.let { id -> textBoxes.firstOrNull { it.id == id } }

    /** [recognitionLanguage] if the user has explicitly picked one this session, else a sensible
     * default: Chinese on 田字格 practice paper, English everywhere else. */
    val effectiveRecognitionLanguage: com.kapi.notes.ocr.RecognitionLanguage
        get() = recognitionLanguage ?: if (currentPage?.paperType == PaperType.CHINESE_CHARACTER_GRID) {
            com.kapi.notes.ocr.RecognitionLanguage.CHINESE
        } else {
            com.kapi.notes.ocr.RecognitionLanguage.ENGLISH
        }
}

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val notesRepository: NotesRepository,
    private val noteExporter: NoteExporter,
    private val audioRecorder: AudioRecorderManager,
    private val handwritingRecognizer: com.kapi.notes.ocr.HandwritingRecognizer,
    private val documentPageRenderer: com.kapi.notes.pdf.DocumentPageRenderer,
    private val driveAuthManager: com.kapi.notes.drive.DriveAuthManager,
    private val driveRepository: com.kapi.notes.drive.DriveRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val noteId: String = checkNotNull(savedStateHandle["noteId"])

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private var undoRedo = UndoRedoStack()
    private val eraseBatch = mutableListOf<Stroke>()
    private val flatEraseOriginals = mutableMapOf<String, Stroke>()
    private val flatEraseLineage = mutableMapOf<String, String>()

    init {
        viewModelScope.launch {
            val note = notesRepository.getNote(noteId)
            val pages = notesRepository.getPages(noteId)
            val audioClips = notesRepository.observeAudioClips(noteId)
            _state.update { it.copy(note = note, pages = pages) }
            loadPageContent()
            viewModelScope.launch {
                audioClips.collect { clips -> _state.update { it.copy(audioClips = clips) } }
            }
        }
    }

    private suspend fun loadPageContent() {
        val page = _state.value.currentPage ?: return
        val strokes = notesRepository.getStrokes(page.id)
        val stickers = notesRepository.getStickers(page.id)
        val images = notesRepository.getImages(page.id)
        val textBoxes = notesRepository.getTextBoxes(page.id)
        undoRedo = UndoRedoStack()
        _state.update {
            it.copy(
                strokes = strokes, stickers = stickers, images = images, textBoxes = textBoxes,
                canUndo = false, canRedo = false, selectedStrokeIds = emptySet()
            )
        }
    }

    fun selectPage(index: Int) {
        if (index !in _state.value.pages.indices) return
        _state.update { it.copy(currentPageIndex = index) }
        viewModelScope.launch { loadPageContent() }
    }

    fun selectTool(tool: EditorTool) {
        _state.update {
            it.copy(
                tool = tool,
                selectedStrokeIds = if (tool == EditorTool.LASSO) it.selectedStrokeIds else emptySet()
            )
        }
    }

    fun toggleLassoRectMode() {
        _state.update { it.copy(lassoRectMode = !it.lassoRectMode) }
    }

    /** "Thước" (ruler): the next ink stroke snaps to a straight, angle-snapped line instead of
     * following the finger/stylus freely - mutually exclusive with Compass. */
    fun toggleRulerMode() {
        _state.update { it.copy(rulerMode = !it.rulerMode, compassMode = false) }
    }

    /** "Compa" (compass): the next ink stroke becomes a true center+radius circle. */
    fun toggleCompassMode() {
        _state.update { it.copy(compassMode = !it.compassMode, rulerMode = false) }
    }

    fun selectColor(colorArgb: Long) {
        _state.update { it.copy(colorArgb = colorArgb, tool = if (it.tool == EditorTool.ERASER) EditorTool.PEN else it.tool) }
    }

    fun setStrokeWidth(widthDp: Float) {
        _state.update { it.copy(strokeWidthDp = widthDp) }
    }

    fun togglePaperMenu(open: Boolean) {
        _state.update { it.copy(isPaperMenuOpen = open) }
    }

    fun toggleShapeMenu(open: Boolean) {
        _state.update { it.copy(isShapeMenuOpen = open) }
    }

    fun toggleFullscreen() {
        _state.update { it.copy(isFullscreen = !it.isFullscreen) }
    }

    fun toggleShapeFill() {
        _state.update { it.copy(isShapeFillEnabled = !it.isShapeFillEnabled) }
    }

    fun addCustomColor(argb: Long) {
        _state.update {
            if (argb in it.customColors) it.copy(colorArgb = argb)
            else it.copy(customColors = (listOf(argb) + it.customColors).take(12), colorArgb = argb)
        }
    }

    /** Called once a pen/pencil/brush/highlighter gesture lifts off the page. */
    fun commitStroke(points: List<StrokePoint>) {
        if (points.size < 2) return
        val page = _state.value.currentPage ?: return
        val inkTool = when (_state.value.tool) {
            EditorTool.PEN -> ToolType.PEN
            EditorTool.PENCIL -> ToolType.PENCIL
            EditorTool.BRUSH -> ToolType.BRUSH
            EditorTool.HIGHLIGHTER -> ToolType.HIGHLIGHTER
            EditorTool.LINE -> ToolType.LINE
            EditorTool.RECTANGLE -> ToolType.RECTANGLE
            EditorTool.CIRCLE -> ToolType.CIRCLE
            EditorTool.ARROW -> ToolType.ARROW
            EditorTool.TRIANGLE -> ToolType.TRIANGLE
            EditorTool.ERASER, EditorTool.LASSO, EditorTool.EMOJI, EditorTool.IMAGE, EditorTool.TEXT, EditorTool.LASER -> return // never draws a stroke
        }
        val fillableShapes = setOf(ToolType.RECTANGLE, ToolType.CIRCLE, ToolType.TRIANGLE)
        val now = System.currentTimeMillis()
        val stroke = Stroke(
            id = UUID.randomUUID().toString(),
            pageId = page.id,
            tool = inkTool,
            colorArgb = _state.value.colorArgb,
            widthDp = _state.value.strokeWidthDp,
            points = points,
            fillColorArgb = if (_state.value.isShapeFillEnabled && inkTool in fillableShapes) _state.value.colorArgb else null,
            createdAt = now,
            updatedAt = now
        )
        _state.update { it.copy(strokes = it.strokes + stroke) }
        undoRedo.push(EditorAction.StrokeAdded(stroke))
        refreshUndoRedoFlags()
        viewModelScope.launch { notesRepository.addStroke(stroke) }
    }

    fun setEraseMode(mode: EraseMode) {
        _state.update { it.copy(eraseMode = mode) }
    }

    /** Called repeatedly while the eraser is dragged across the page. */
    fun eraseAt(point: Offset, radiusDp: Float) {
        val radiusPx = radiusDp * 3f // approximate dp->px without needing density here
        when (_state.value.eraseMode) {
            EraseMode.STROKE -> eraseAtWholeStroke(point, radiusPx)
            EraseMode.FLAT -> eraseAtFlat(point, radiusPx)
        }
    }

    private fun eraseAtWholeStroke(point: Offset, radiusPx: Float) {
        val remaining = mutableListOf<Stroke>()
        var changed = false
        _state.value.strokes.forEach { stroke ->
            if (eraseBatch.none { it.id == stroke.id } && strokeIntersects(stroke, point, radiusPx)) {
                eraseBatch += stroke
                changed = true
            } else {
                remaining += stroke
            }
        }
        if (changed) {
            _state.update { it.copy(strokes = remaining) }
        }
    }

    /** "Xóa phẳng": only cuts out the portion of each stroke the eraser passes over, splitting
     * the rest into surviving pieces instead of deleting the whole stroke. [flatEraseOriginals]
     * remembers each stroke's pristine, pre-erase version (snapshotted the first time it's
     * touched this gesture) and [flatEraseLineage] maps every current fragment id back to that
     * original's id, so [finishEraseGesture] can record one clean before/after undo step. */
    private fun eraseAtFlat(point: Offset, radiusPx: Float) {
        val current = _state.value.strokes
        val updated = ArrayList<Stroke>(current.size)
        var changed = false
        for (stroke in current) {
            val pieces = splitStrokeByErase(stroke, point, radiusPx)
            if (pieces == null) {
                updated += stroke
                continue
            }
            changed = true
            if (stroke.id !in flatEraseLineage) {
                flatEraseOriginals.getOrPut(stroke.id) { stroke }
            }
            val rootId = flatEraseLineage[stroke.id] ?: stroke.id
            pieces.forEach { flatEraseLineage[it.id] = rootId }
            updated += pieces
        }
        if (changed) {
            _state.update { it.copy(strokes = updated) }
        }
    }

    /** Called when an eraser gesture lifts off, turning the batch into one undo step. */
    fun finishEraseGesture() {
        when (_state.value.eraseMode) {
            EraseMode.STROKE -> finishWholeStrokeEraseGesture()
            EraseMode.FLAT -> finishFlatEraseGesture()
        }
    }

    private fun finishWholeStrokeEraseGesture() {
        if (eraseBatch.isEmpty()) return
        val erased = eraseBatch.toList()
        eraseBatch.clear()
        undoRedo.push(EditorAction.StrokesErased(erased))
        refreshUndoRedoFlags()
        viewModelScope.launch { erased.forEach { notesRepository.removeStroke(it.id) } }
    }

    private fun finishFlatEraseGesture() {
        if (flatEraseOriginals.isEmpty()) {
            flatEraseLineage.clear()
            return
        }
        val originals = flatEraseOriginals.values.toList()
        val originalIds = originals.map { it.id }.toSet()
        val fragments = _state.value.strokes.filter { flatEraseLineage[it.id] in originalIds }
        flatEraseOriginals.clear()
        flatEraseLineage.clear()
        undoRedo.push(EditorAction.StrokesFlatErased(before = originals, after = fragments))
        refreshUndoRedoFlags()
        viewModelScope.launch {
            originals.forEach { notesRepository.removeStroke(it.id) }
            fragments.forEach { notesRepository.addStroke(it) }
        }
    }

    fun undo() {
        val action = undoRedo.popUndo() ?: return
        when (action) {
            is EditorAction.StrokeAdded -> {
                _state.update { it.copy(strokes = it.strokes.filterNot { s -> s.id == action.stroke.id }) }
                viewModelScope.launch { notesRepository.removeStroke(action.stroke.id) }
            }
            is EditorAction.StrokesErased -> {
                _state.update { it.copy(strokes = it.strokes + action.strokes) }
                viewModelScope.launch { action.strokes.forEach { notesRepository.addStroke(it) } }
            }
            is EditorAction.StrokesMoved -> {
                val beforeById = action.before.associateBy { it.id }
                _state.update { it.copy(strokes = it.strokes.map { s -> beforeById[s.id] ?: s }) }
                viewModelScope.launch { action.before.forEach { notesRepository.addStroke(it) } }
            }
            is EditorAction.StrokesFlatErased -> {
                val afterIds = action.after.map { it.id }.toSet()
                _state.update { it.copy(strokes = it.strokes.filterNot { s -> s.id in afterIds } + action.before) }
                viewModelScope.launch {
                    action.after.forEach { notesRepository.removeStroke(it.id) }
                    action.before.forEach { notesRepository.addStroke(it) }
                }
            }
            is EditorAction.StickerAdded -> {
                _state.update { it.copy(stickers = it.stickers.filterNot { s -> s.id == action.sticker.id }) }
                viewModelScope.launch { notesRepository.removeSticker(action.sticker.id) }
            }
            is EditorAction.StickerRemoved -> {
                _state.update { it.copy(stickers = it.stickers + action.sticker) }
                viewModelScope.launch { notesRepository.addSticker(action.sticker) }
            }
            is EditorAction.StickerMoved -> {
                _state.update { it.copy(stickers = it.stickers.map { s -> if (s.id == action.before.id) action.before else s }) }
                viewModelScope.launch { notesRepository.moveSticker(action.before.id, action.before.x, action.before.y) }
            }
            is EditorAction.StickerTransformed -> {
                _state.update { it.copy(stickers = it.stickers.map { s -> if (s.id == action.before.id) action.before else s }) }
                viewModelScope.launch { notesRepository.addSticker(action.before) }
            }
            is EditorAction.ImageAdded -> {
                _state.update { it.copy(images = it.images.filterNot { i -> i.id == action.image.id }) }
                viewModelScope.launch { notesRepository.removeImage(action.image.id) }
            }
            is EditorAction.ImageRemoved -> {
                _state.update { it.copy(images = it.images + action.image) }
                viewModelScope.launch { notesRepository.addImage(action.image) }
            }
            is EditorAction.ImageChanged -> {
                _state.update { it.copy(images = it.images.map { i -> if (i.id == action.before.id) action.before else i }) }
                viewModelScope.launch {
                    notesRepository.moveImage(action.before.id, action.before.x, action.before.y)
                    notesRepository.transformImage(action.before.id, action.before.widthPx, action.before.heightPx, action.before.rotationDegrees)
                }
            }
            is EditorAction.TextBoxAdded -> {
                _state.update { it.copy(textBoxes = it.textBoxes.filterNot { t -> t.id == action.textBox.id }) }
                viewModelScope.launch { notesRepository.removeTextBox(action.textBox.id) }
            }
            is EditorAction.TextBoxRemoved -> {
                _state.update { it.copy(textBoxes = it.textBoxes + action.textBox) }
                viewModelScope.launch { notesRepository.addTextBox(action.textBox) }
            }
            is EditorAction.TextBoxChanged -> {
                _state.update { it.copy(textBoxes = it.textBoxes.map { t -> if (t.id == action.before.id) action.before else t }) }
                viewModelScope.launch {
                    notesRepository.moveTextBox(action.before.id, action.before.x, action.before.y)
                    notesRepository.transformTextBox(action.before.id, action.before.widthPx, action.before.heightPx, action.before.rotationDegrees)
                    notesRepository.updateTextBox(action.before.id, action.before.text, action.before.fontSizePx, action.before.colorArgb, action.before.isBold, action.before.isItalic, action.before.fontFamily)
                }
            }
        }
        refreshUndoRedoFlags()
    }

    fun redo() {
        val action = undoRedo.popRedo() ?: return
        when (action) {
            is EditorAction.StrokeAdded -> {
                _state.update { it.copy(strokes = it.strokes + action.stroke) }
                viewModelScope.launch { notesRepository.addStroke(action.stroke) }
            }
            is EditorAction.StrokesErased -> {
                val erasedIds = action.strokes.map { it.id }.toSet()
                _state.update { it.copy(strokes = it.strokes.filterNot { s -> s.id in erasedIds }) }
                viewModelScope.launch { action.strokes.forEach { notesRepository.removeStroke(it.id) } }
            }
            is EditorAction.StrokesMoved -> {
                val afterById = action.after.associateBy { it.id }
                _state.update { it.copy(strokes = it.strokes.map { s -> afterById[s.id] ?: s }) }
                viewModelScope.launch { action.after.forEach { notesRepository.addStroke(it) } }
            }
            is EditorAction.StrokesFlatErased -> {
                val beforeIds = action.before.map { it.id }.toSet()
                _state.update { it.copy(strokes = it.strokes.filterNot { s -> s.id in beforeIds } + action.after) }
                viewModelScope.launch {
                    action.before.forEach { notesRepository.removeStroke(it.id) }
                    action.after.forEach { notesRepository.addStroke(it) }
                }
            }
            is EditorAction.StickerAdded -> {
                _state.update { it.copy(stickers = it.stickers + action.sticker) }
                viewModelScope.launch { notesRepository.addSticker(action.sticker) }
            }
            is EditorAction.StickerRemoved -> {
                _state.update { it.copy(stickers = it.stickers.filterNot { s -> s.id == action.sticker.id }) }
                viewModelScope.launch { notesRepository.removeSticker(action.sticker.id) }
            }
            is EditorAction.StickerMoved -> {
                _state.update { it.copy(stickers = it.stickers.map { s -> if (s.id == action.after.id) action.after else s }) }
                viewModelScope.launch { notesRepository.moveSticker(action.after.id, action.after.x, action.after.y) }
            }
            is EditorAction.StickerTransformed -> {
                _state.update { it.copy(stickers = it.stickers.map { s -> if (s.id == action.after.id) action.after else s }) }
                viewModelScope.launch { notesRepository.addSticker(action.after) }
            }
            is EditorAction.ImageAdded -> {
                _state.update { it.copy(images = it.images + action.image) }
                viewModelScope.launch { notesRepository.addImage(action.image) }
            }
            is EditorAction.ImageRemoved -> {
                _state.update { it.copy(images = it.images.filterNot { i -> i.id == action.image.id }) }
                viewModelScope.launch { notesRepository.removeImage(action.image.id) }
            }
            is EditorAction.ImageChanged -> {
                _state.update { it.copy(images = it.images.map { i -> if (i.id == action.after.id) action.after else i }) }
                viewModelScope.launch {
                    notesRepository.moveImage(action.after.id, action.after.x, action.after.y)
                    notesRepository.transformImage(action.after.id, action.after.widthPx, action.after.heightPx, action.after.rotationDegrees)
                }
            }
            is EditorAction.TextBoxAdded -> {
                _state.update { it.copy(textBoxes = it.textBoxes + action.textBox) }
                viewModelScope.launch { notesRepository.addTextBox(action.textBox) }
            }
            is EditorAction.TextBoxRemoved -> {
                _state.update { it.copy(textBoxes = it.textBoxes.filterNot { t -> t.id == action.textBox.id }) }
                viewModelScope.launch { notesRepository.removeTextBox(action.textBox.id) }
            }
            is EditorAction.TextBoxChanged -> {
                _state.update { it.copy(textBoxes = it.textBoxes.map { t -> if (t.id == action.after.id) action.after else t }) }
                viewModelScope.launch {
                    notesRepository.moveTextBox(action.after.id, action.after.x, action.after.y)
                    notesRepository.transformTextBox(action.after.id, action.after.widthPx, action.after.heightPx, action.after.rotationDegrees)
                    notesRepository.updateTextBox(action.after.id, action.after.text, action.after.fontSizePx, action.after.colorArgb, action.after.isBold, action.after.isItalic, action.after.fontFamily)
                }
            }
        }
        refreshUndoRedoFlags()
    }

    /** Lasso drag just closed - select every stroke mostly enclosed by the drawn loop. */
    fun finishLasso(lassoPoints: List<Offset>) {
        _state.update { it.copy(selectedStrokeIds = strokesInsideLasso(it.strokes, lassoPoints)) }
    }

    fun clearSelection() {
        _state.update { it.copy(selectedStrokeIds = emptySet()) }
    }

    /** Lasso toolbar's "Nhận dạng chữ viết tay": runs ML Kit's on-device ink recognizer over the
     * selected strokes and inserts the result as a new text box just below them. The original
     * ink is left untouched so a bad recognition can simply be deleted. */
    fun setRecognitionLanguage(language: com.kapi.notes.ocr.RecognitionLanguage) {
        _state.update { it.copy(recognitionLanguage = language) }
    }

    fun recognizeSelectedHandwriting() {
        val s = _state.value
        val page = s.currentPage ?: return
        val selected = s.strokes.filter { it.id in s.selectedStrokeIds }
        if (selected.isEmpty()) return
        val bounds = strokesBounds(selected) ?: return
        val language = s.effectiveRecognitionLanguage
        _state.update { it.copy(isRecognizingHandwriting = true) }
        viewModelScope.launch {
            val text = handwritingRecognizer.recognize(selected, language)
            _state.update { it.copy(isRecognizingHandwriting = false) }
            if (text.isNullOrBlank()) {
                _state.update { it.copy(error = "Không nhận dạng được chữ viết tay trong vùng đã chọn") }
                return@launch
            }
            val now = System.currentTimeMillis()
            val textBox = TextBoxItem(
                id = UUID.randomUUID().toString(),
                pageId = page.id,
                text = text,
                x = bounds.left,
                y = bounds.bottom + 16f,
                widthPx = (bounds.width).coerceAtLeast(200f),
                heightPx = 40f,
                fontSizePx = 32f,
                colorArgb = s.colorArgb,
                updatedAt = now
            )
            _state.update { it.copy(textBoxes = it.textBoxes + textBox, selectedStrokeIds = emptySet()) }
            undoRedo.push(EditorAction.TextBoxAdded(textBox))
            refreshUndoRedoFlags()
            notesRepository.addTextBox(textBox)
        }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    /** The "cut" action: drag that started inside the current selection just lifted off - move
     * the selected strokes by the dragged delta and record it as one undo step. */
    fun moveSelection(deltaX: Float, deltaY: Float) {
        val ids = _state.value.selectedStrokeIds
        if (ids.isEmpty() || (deltaX == 0f && deltaY == 0f)) return
        val now = System.currentTimeMillis()
        val before = _state.value.strokes.filter { it.id in ids }
        val after = before.map { s ->
            s.copy(points = s.points.map { p -> p.copy(x = p.x + deltaX, y = p.y + deltaY) }, updatedAt = now)
        }
        val afterById = after.associateBy { it.id }
        _state.update { it.copy(strokes = it.strokes.map { s -> afterById[s.id] ?: s }, selectedStrokeIds = emptySet()) }
        undoRedo.push(EditorAction.StrokesMoved(before, after))
        refreshUndoRedoFlags()
        viewModelScope.launch { after.forEach { notesRepository.addStroke(it) } }
    }

    private val boundingBoxShapes = setOf(ToolType.RECTANGLE, ToolType.CIRCLE, ToolType.TRIANGLE)

    /** Lasso's rotate handle: spins the selection by [deltaDegrees]. A lone RECTANGLE/CIRCLE/
     * TRIANGLE just gets its [Stroke.rotationDegrees] bumped - rotating a bounding-box shape's own
     * 2 corner points would reshape it into a different axis-aligned box instead of turning it, so
     * those render the angle at draw time around their own center instead (see [drawStroke]).
     * Everything else (ink/line/arrow, or any multi-stroke selection) rotates by moving the actual
     * points around the selection's shared bounding-box center, which looks correct for a real
     * path and lets a multi-stroke selection orbit together around one pivot. */
    fun rotateSelection(deltaDegrees: Float) {
        val ids = _state.value.selectedStrokeIds
        if (ids.isEmpty() || kotlin.math.abs(deltaDegrees) < 0.01f) return
        val before = _state.value.strokes.filter { it.id in ids }
        if (before.isEmpty()) return
        val now = System.currentTimeMillis()
        val after = if (before.size == 1 && before[0].tool in boundingBoxShapes) {
            listOf(before[0].copy(rotationDegrees = before[0].rotationDegrees + deltaDegrees, updatedAt = now))
        } else {
            val center = strokesBounds(before)?.center ?: return
            before.map { s ->
                s.copy(
                    points = s.points.map { p ->
                        val rotated = rotatePointAround(Offset(p.x, p.y), center, deltaDegrees)
                        p.copy(x = rotated.x, y = rotated.y)
                    },
                    updatedAt = now
                )
            }
        }
        val afterById = after.associateBy { it.id }
        _state.update { it.copy(strokes = it.strokes.map { s -> afterById[s.id] ?: s }, selectedStrokeIds = emptySet()) }
        undoRedo.push(EditorAction.StrokesMoved(before, after))
        refreshUndoRedoFlags()
        viewModelScope.launch { after.forEach { notesRepository.addStroke(it) } }
    }

    /** EMOJI tool: tapped empty canvas - open the picker, remembering where to drop the pick. */
    fun requestPlaceEmojiAt(point: Offset) {
        _state.update { it.copy(isEmojiPickerOpen = true, pendingEmojiPlacement = point) }
    }

    fun dismissEmojiPicker() {
        _state.update { it.copy(isEmojiPickerOpen = false, pendingEmojiPlacement = null) }
    }

    fun placeEmoji(emoji: String) {
        val page = _state.value.currentPage ?: return
        val point = _state.value.pendingEmojiPlacement ?: return
        val sticker = EmojiSticker(
            id = UUID.randomUUID().toString(),
            pageId = page.id,
            emoji = emoji,
            x = point.x,
            y = point.y,
            sizePx = 96f
        )
        _state.update { it.copy(stickers = it.stickers + sticker, isEmojiPickerOpen = false, pendingEmojiPlacement = null) }
        undoRedo.push(EditorAction.StickerAdded(sticker))
        refreshUndoRedoFlags()
        viewModelScope.launch { notesRepository.addSticker(sticker) }
    }

    /** EMOJI tool: tapped directly on an existing sticker without dragging - remove it. */
    fun removeStickerTap(stickerId: String) {
        val sticker = _state.value.stickers.firstOrNull { it.id == stickerId } ?: return
        _state.update { it.copy(stickers = it.stickers.filterNot { s -> s.id == stickerId }) }
        undoRedo.push(EditorAction.StickerRemoved(sticker))
        refreshUndoRedoFlags()
        viewModelScope.launch { notesRepository.removeSticker(stickerId) }
    }

    /** EMOJI tool: dragged an existing sticker to a new spot. */
    fun moveSticker(stickerId: String, newX: Float, newY: Float) {
        val before = _state.value.stickers.firstOrNull { it.id == stickerId } ?: return
        val after = before.copy(x = newX, y = newY, updatedAt = System.currentTimeMillis())
        _state.update { it.copy(stickers = it.stickers.map { s -> if (s.id == stickerId) after else s }) }
        undoRedo.push(EditorAction.StickerMoved(before, after))
        refreshUndoRedoFlags()
        viewModelScope.launch { notesRepository.moveSticker(stickerId, newX, newY) }
    }

    /** EMOJI tool's rotate handle: spins a single sticker freely around its own center. */
    fun transformSticker(stickerId: String, rotationDegrees: Float) {
        val before = _state.value.stickers.firstOrNull { it.id == stickerId } ?: return
        val after = before.copy(rotationDegrees = rotationDegrees, updatedAt = System.currentTimeMillis())
        _state.update { it.copy(stickers = it.stickers.map { s -> if (s.id == stickerId) after else s }) }
        undoRedo.push(EditorAction.StickerTransformed(before, after))
        refreshUndoRedoFlags()
        viewModelScope.launch { notesRepository.addSticker(after) }
    }

    /** IMAGE tool: just picked a photo from gallery/camera - place it centered on [point]. */
    fun placeImage(imagePath: String, point: Offset, widthPx: Float, heightPx: Float) {
        val page = _state.value.currentPage ?: return
        val now = System.currentTimeMillis()
        val image = ImageAttachment(
            id = UUID.randomUUID().toString(),
            pageId = page.id,
            imagePath = imagePath,
            x = point.x,
            y = point.y,
            widthPx = widthPx,
            heightPx = heightPx,
            updatedAt = now
        )
        _state.update { it.copy(images = it.images + image) }
        undoRedo.push(EditorAction.ImageAdded(image))
        refreshUndoRedoFlags()
        viewModelScope.launch { notesRepository.addImage(image) }
    }

    /** IMAGE tool: dragged an existing image to a new spot. */
    fun moveImage(imageId: String, newX: Float, newY: Float) {
        val before = _state.value.images.firstOrNull { it.id == imageId } ?: return
        val after = before.copy(x = newX, y = newY, updatedAt = System.currentTimeMillis())
        _state.update { it.copy(images = it.images.map { i -> if (i.id == imageId) after else i }) }
        undoRedo.push(EditorAction.ImageChanged(before, after))
        refreshUndoRedoFlags()
        viewModelScope.launch { notesRepository.moveImage(imageId, newX, newY) }
    }

    /** IMAGE tool: resize/rotate handle released - commits the new size and/or rotation. */
    fun transformImage(imageId: String, widthPx: Float, heightPx: Float, rotationDegrees: Float) {
        val before = _state.value.images.firstOrNull { it.id == imageId } ?: return
        val after = before.copy(widthPx = widthPx, heightPx = heightPx, rotationDegrees = rotationDegrees, updatedAt = System.currentTimeMillis())
        _state.update { it.copy(images = it.images.map { i -> if (i.id == imageId) after else i }) }
        undoRedo.push(EditorAction.ImageChanged(before, after))
        refreshUndoRedoFlags()
        viewModelScope.launch { notesRepository.transformImage(imageId, widthPx, heightPx, rotationDegrees) }
    }

    /** IMAGE tool: tapped directly on an image without dragging - remove it. */
    fun removeImageTap(imageId: String) {
        val image = _state.value.images.firstOrNull { it.id == imageId } ?: return
        _state.update { it.copy(images = it.images.filterNot { i -> i.id == imageId }) }
        undoRedo.push(EditorAction.ImageRemoved(image))
        refreshUndoRedoFlags()
        viewModelScope.launch { notesRepository.removeImage(imageId) }
    }

    /** TEXT tool: tapped empty canvas - open the add/edit dialog for a brand-new box. */
    fun requestAddTextBoxAt(point: Offset) {
        _state.update {
            it.copy(
                pendingTextBoxPlacement = point, editingTextBoxId = null,
                textDialogBold = false, textDialogItalic = false,
                textDialogFontFamily = TextFontFamily.DEFAULT, textDialogFontSize = 32f
            )
        }
    }

    /** TEXT tool: tapped an existing box without dragging - open it for editing. */
    fun requestEditTextBox(textBoxId: String) {
        val box = _state.value.textBoxes.firstOrNull { it.id == textBoxId }
        _state.update {
            it.copy(
                editingTextBoxId = textBoxId, pendingTextBoxPlacement = null,
                textDialogBold = box?.isBold ?: false, textDialogItalic = box?.isItalic ?: false,
                textDialogFontFamily = box?.fontFamily ?: TextFontFamily.DEFAULT,
                textDialogFontSize = box?.fontSizePx ?: 32f
            )
        }
    }

    fun dismissTextBoxDialog() {
        _state.update { it.copy(pendingTextBoxPlacement = null, editingTextBoxId = null) }
    }

    fun setTextDialogBold(bold: Boolean) { _state.update { it.copy(textDialogBold = bold) } }
    fun setTextDialogItalic(italic: Boolean) { _state.update { it.copy(textDialogItalic = italic) } }
    fun setTextDialogFontFamily(family: TextFontFamily) { _state.update { it.copy(textDialogFontFamily = family) } }
    fun setTextDialogFontSize(sizePx: Float) { _state.update { it.copy(textDialogFontSize = sizePx) } }

    /** Confirms the add/edit dialog, creating a new box or updating the one being edited. */
    fun confirmTextBox(text: String) {
        if (text.isBlank()) { dismissTextBoxDialog(); return }
        val s = _state.value
        val editingId = s.editingTextBoxId
        if (editingId != null) {
            val before = s.textBoxes.firstOrNull { it.id == editingId }
            val after = before?.copy(
                text = text, fontSizePx = s.textDialogFontSize, isBold = s.textDialogBold,
                isItalic = s.textDialogItalic, fontFamily = s.textDialogFontFamily, updatedAt = System.currentTimeMillis()
            )
            if (before != null && after != null) {
                _state.update { it.copy(textBoxes = it.textBoxes.map { t -> if (t.id == editingId) after else t }) }
                undoRedo.push(EditorAction.TextBoxChanged(before, after))
                refreshUndoRedoFlags()
                viewModelScope.launch {
                    notesRepository.updateTextBox(editingId, text, after.fontSizePx, after.colorArgb, after.isBold, after.isItalic, after.fontFamily)
                }
            }
        } else {
            val page = s.currentPage
            val point = s.pendingTextBoxPlacement
            if (page != null && point != null) {
                val now = System.currentTimeMillis()
                val textBox = TextBoxItem(
                    id = UUID.randomUUID().toString(),
                    pageId = page.id,
                    text = text,
                    x = point.x,
                    y = point.y,
                    widthPx = 320f,
                    heightPx = s.textDialogFontSize * 1.4f,
                    fontSizePx = s.textDialogFontSize,
                    colorArgb = s.colorArgb,
                    isBold = s.textDialogBold,
                    isItalic = s.textDialogItalic,
                    fontFamily = s.textDialogFontFamily,
                    updatedAt = now
                )
                _state.update { it.copy(textBoxes = it.textBoxes + textBox) }
                undoRedo.push(EditorAction.TextBoxAdded(textBox))
                refreshUndoRedoFlags()
                viewModelScope.launch { notesRepository.addTextBox(textBox) }
            }
        }
        dismissTextBoxDialog()
    }

    fun removeEditingTextBox() {
        val id = _state.value.editingTextBoxId ?: return
        val textBox = _state.value.textBoxes.firstOrNull { it.id == id } ?: return
        _state.update { it.copy(textBoxes = it.textBoxes.filterNot { t -> t.id == id }) }
        undoRedo.push(EditorAction.TextBoxRemoved(textBox))
        refreshUndoRedoFlags()
        viewModelScope.launch { notesRepository.removeTextBox(id) }
        dismissTextBoxDialog()
    }

    /** TEXT tool: dragged an existing box to a new spot. */
    fun moveTextBox(textBoxId: String, newX: Float, newY: Float) {
        val before = _state.value.textBoxes.firstOrNull { it.id == textBoxId } ?: return
        val after = before.copy(x = newX, y = newY, updatedAt = System.currentTimeMillis())
        _state.update { it.copy(textBoxes = it.textBoxes.map { t -> if (t.id == textBoxId) after else t }) }
        undoRedo.push(EditorAction.TextBoxChanged(before, after))
        refreshUndoRedoFlags()
        viewModelScope.launch { notesRepository.moveTextBox(textBoxId, newX, newY) }
    }

    /** TEXT tool: resize/rotate handle released - commits the new size and/or rotation. */
    fun transformTextBox(textBoxId: String, widthPx: Float, heightPx: Float, rotationDegrees: Float) {
        val before = _state.value.textBoxes.firstOrNull { it.id == textBoxId } ?: return
        val after = before.copy(widthPx = widthPx, heightPx = heightPx, rotationDegrees = rotationDegrees, updatedAt = System.currentTimeMillis())
        _state.update { it.copy(textBoxes = it.textBoxes.map { t -> if (t.id == textBoxId) after else t }) }
        undoRedo.push(EditorAction.TextBoxChanged(before, after))
        refreshUndoRedoFlags()
        viewModelScope.launch { notesRepository.transformTextBox(textBoxId, widthPx, heightPx, rotationDegrees) }
    }

    /** Appends a new blank page right after the current one and switches to it. */
    fun addPage() {
        val note = _state.value.note ?: return
        val afterPageId = _state.value.currentPage?.id
        val paperType = _state.value.currentPage?.paperType ?: PaperType.LINED
        viewModelScope.launch {
            notesRepository.addPage(note.id, afterPageId, paperType)
            val pages = notesRepository.getPages(note.id)
            _state.update { it.copy(pages = pages, currentPageIndex = (_state.value.currentPageIndex + 1).coerceAtMost(pages.lastIndex)) }
            loadPageContent()
        }
    }

    /** Duplicates the current page (paper settings + every stroke/sticker/image/text) and switches to the copy. */
    fun duplicateCurrentPage() {
        val note = _state.value.note ?: return
        val page = _state.value.currentPage ?: return
        viewModelScope.launch {
            notesRepository.duplicatePage(page.id)
            val pages = notesRepository.getPages(note.id)
            _state.update { it.copy(pages = pages, currentPageIndex = (_state.value.currentPageIndex + 1).coerceAtMost(pages.lastIndex)) }
            loadPageContent()
        }
    }

    /** Deletes the current page (no-op if it is the note's only page). */
    fun deleteCurrentPage() {
        val note = _state.value.note ?: return
        val page = _state.value.currentPage ?: return
        if (_state.value.pages.size <= 1) return
        viewModelScope.launch {
            notesRepository.deletePage(page.id)
            val pages = notesRepository.getPages(note.id)
            _state.update { it.copy(pages = pages, currentPageIndex = _state.value.currentPageIndex.coerceAtMost(pages.lastIndex)) }
            loadPageContent()
        }
    }

    fun movePageBy(direction: Int) {
        val page = _state.value.currentPage ?: return
        val note = _state.value.note ?: return
        viewModelScope.launch {
            notesRepository.movePage(page.id, direction)
            val pages = notesRepository.getPages(note.id)
            _state.update { it.copy(pages = pages, currentPageIndex = (_state.value.currentPageIndex + direction).coerceIn(0, pages.lastIndex)) }
        }
    }

    fun startRecording() {
        if (_state.value.isRecording) return
        runCatching { audioRecorder.startRecording() }.onSuccess {
            _state.update { it.copy(isRecording = true, recordingStartedAt = System.currentTimeMillis()) }
        }
    }

    fun stopRecording() {
        if (!_state.value.isRecording) return
        val note = _state.value.note
        val result = audioRecorder.stopRecording()
        _state.update { it.copy(isRecording = false) }
        if (note != null && result != null) {
            val (file, durationMs) = result
            val clip = AudioClip(
                id = UUID.randomUUID().toString(),
                noteId = note.id,
                filePath = file.absolutePath,
                durationMs = durationMs,
                createdAt = System.currentTimeMillis()
            )
            viewModelScope.launch { notesRepository.addAudioClip(clip) }
        }
    }

    fun togglePlayback(clipId: String) {
        if (_state.value.playingClipId == clipId) {
            audioRecorder.stopPlayback()
            _state.update { it.copy(playingClipId = null) }
            return
        }
        val clip = _state.value.audioClips.firstOrNull { it.id == clipId } ?: return
        audioRecorder.play(clip.filePath) { _state.update { it.copy(playingClipId = null) } }
        _state.update { it.copy(playingClipId = clipId) }
    }

    fun removeAudioClip(clipId: String) {
        if (_state.value.playingClipId == clipId) {
            audioRecorder.stopPlayback()
            _state.update { it.copy(playingClipId = null) }
        }
        viewModelScope.launch { notesRepository.removeAudioClip(clipId) }
    }

    override fun onCleared() {
        super.onCleared()
        audioRecorder.stopPlayback()
        if (_state.value.isRecording) audioRecorder.stopRecording()
    }

    private fun refreshUndoRedoFlags() {
        _state.update { it.copy(canUndo = undoRedo.canUndo, canRedo = undoRedo.canRedo) }
    }

    fun renameNote(newTitle: String) {
        viewModelScope.launch {
            notesRepository.renameNote(noteId, newTitle)
            _state.update { it.copy(note = it.note?.copy(title = newTitle)) }
        }
    }

    fun setPageTagColor(tagColorArgb: Long?) {
        val page = _state.value.currentPage ?: return
        viewModelScope.launch {
            notesRepository.setPageTagColor(page.id, tagColorArgb)
            updateCurrentPageLocal { it.copy(tagColorArgb = tagColorArgb) }
        }
    }

    /** LASER tool: a non-persisted glowing pointer for presenting - null hides it. */
    fun setLaserPoint(point: Offset?) {
        _state.update { it.copy(laserPoint = point) }
    }

    fun changePaperType(paperType: PaperType) {
        val page = _state.value.currentPage ?: return
        viewModelScope.launch {
            notesRepository.updatePageLayout(page.id, paperType = paperType)
            updateCurrentPageLocal { it.copy(paperType = paperType) }
        }
    }

    fun rotatePage() {
        val page = _state.value.currentPage ?: return
        val newOrientation = if (page.orientation == PageOrientation.PORTRAIT) PageOrientation.LANDSCAPE else PageOrientation.PORTRAIT
        viewModelScope.launch {
            notesRepository.updatePageLayout(page.id, orientation = newOrientation)
            updateCurrentPageLocal { it.copy(orientation = newOrientation, widthPx = it.heightPx, heightPx = it.widthPx) }
        }
    }

    fun flipPage() {
        val page = _state.value.currentPage ?: return
        val newFlip = if (page.flip == PageFlip.NONE) PageFlip.HORIZONTAL else PageFlip.NONE
        viewModelScope.launch {
            notesRepository.updatePageLayout(page.id, flip = newFlip)
            updateCurrentPageLocal { it.copy(flip = newFlip) }
        }
    }

    /** Sets the current page's background to a locally-saved image - "Chèn ảnh nền" lets the user
     * annotate a photo (e.g. a worksheet) right on top of it, without leaving the note. */
    fun setPageBackgroundImage(path: String, widthPx: Int, heightPx: Int) {
        val page = _state.value.currentPage ?: return
        viewModelScope.launch {
            notesRepository.setPageBackground(page.id, path, widthPx, heightPx)
            updateCurrentPageLocal { it.copy(backgroundImagePath = path, widthPx = widthPx, heightPx = heightPx) }
        }
    }

    fun clearPageBackground() {
        val page = _state.value.currentPage ?: return
        if (page.backgroundImagePath == null) return
        viewModelScope.launch {
            notesRepository.setPageBackground(page.id, null)
            updateCurrentPageLocal { it.copy(backgroundImagePath = null) }
        }
    }

    /** Renders every page of a locally-picked PDF and inserts them right after the current page,
     * as background images the user can write notes over - e.g. importing a textbook chapter. */
    fun importPdfPagesAsBackground(sourceFile: File) {
        val note = _state.value.note ?: return
        val afterPageId = _state.value.currentPage?.id
        viewModelScope.launch {
            _state.update { it.copy(isImportingBackground = true, error = null) }
            runCatching {
                val rendered = documentPageRenderer.renderPdf(sourceFile, note.id).getOrThrow()
                check(rendered.isNotEmpty()) { "Tệp PDF không có trang nào" }
                notesRepository.insertBackgroundPages(
                    noteId = note.id,
                    afterPageId = afterPageId,
                    paths = rendered.map { it.filePath },
                    widthPx = rendered.first().widthPx,
                    heightPx = rendered.first().heightPx
                )
            }.onSuccess {
                sourceFile.delete()
                val pages = notesRepository.getPages(note.id)
                _state.update { it.copy(isImportingBackground = false, pages = pages) }
            }.onFailure { e ->
                sourceFile.delete()
                _state.update { it.copy(isImportingBackground = false, error = e.message ?: "Nhập PDF thất bại") }
            }
        }
    }

    fun toggleCurrentPageBookmark() {
        val page = _state.value.currentPage ?: return
        val bookmarked = !page.isBookmarked
        viewModelScope.launch {
            notesRepository.setPageBookmark(page.id, bookmarked)
            updateCurrentPageLocal { it.copy(isBookmarked = bookmarked) }
        }
    }

    /** Exports the note to PDF, uploads it to the signed-in Drive account and makes it
     * "anyone with the link can view" - for sending a read-only copy to a friend/teacher. */
    fun shareNoteViewOnlyLink() {
        val note = _state.value.note ?: return
        val account = driveAuthManager.lastSignedInAccount()
        if (account == null) {
            _state.update { it.copy(error = "Hãy đăng nhập Google Drive trước (trong Dashboard) để chia sẻ link.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSharingLink = true, error = null) }
            runCatching {
                val pages = _state.value.pages
                val contentByPage = pages.associate { page ->
                    page.id to com.kapi.notes.pdf.PageContent(
                        strokes = notesRepository.getStrokes(page.id),
                        stickers = notesRepository.getStickers(page.id),
                        images = notesRepository.getImages(page.id),
                        textBoxes = notesRepository.getTextBoxes(page.id)
                    )
                }
                val pdfFile = noteExporter.exportToPdf(note.title, pages, contentByPage).getOrThrow()
                val fileId = driveRepository.uploadFile(account, "${note.title}.pdf", "application/pdf", pdfFile.readBytes()).getOrThrow()
                driveRepository.shareFilePublicly(account, fileId).getOrThrow()
            }.onSuccess { link ->
                _state.update { it.copy(isSharingLink = false, shareLink = link) }
            }.onFailure { e ->
                _state.update { it.copy(isSharingLink = false, error = e.message ?: "Chia sẻ thất bại") }
            }
        }
    }

    fun consumeShareLink() {
        _state.update { it.copy(shareLink = null) }
    }

    private fun updateCurrentPageLocal(transform: (NotePage) -> NotePage) {
        _state.update { current ->
            val index = current.currentPageIndex
            val updatedPages = current.pages.toMutableList().also { list ->
                list.getOrNull(index)?.let { list[index] = transform(it) }
            }
            current.copy(pages = updatedPages)
        }
    }

    fun exportNote() {
        viewModelScope.launch {
            _state.update { it.copy(isExporting = true, error = null) }
            val pages = _state.value.pages
            val contentByPage = pages.associate { page ->
                page.id to com.kapi.notes.pdf.PageContent(
                    strokes = notesRepository.getStrokes(page.id),
                    stickers = notesRepository.getStickers(page.id),
                    images = notesRepository.getImages(page.id),
                    textBoxes = notesRepository.getTextBoxes(page.id)
                )
            }
            noteExporter.exportToPdf(_state.value.note?.title.orEmpty(), pages, contentByPage)
                .onSuccess { file -> _state.update { it.copy(isExporting = false, exportedFile = file, exportedFileMimeType = "application/pdf") } }
                .onFailure { e -> _state.update { it.copy(isExporting = false, error = e.message ?: "Xuất file thất bại") } }
        }
    }

    /** Overflow menu's "Xuất ảnh PNG (trang hiện tại)" - exports just the page on screen, for
     * quickly sharing one page without generating a whole PDF. */
    fun exportCurrentPageAsPng() {
        val page = _state.value.currentPage ?: return
        viewModelScope.launch {
            _state.update { it.copy(isExporting = true, error = null) }
            val content = com.kapi.notes.pdf.PageContent(
                strokes = notesRepository.getStrokes(page.id),
                stickers = notesRepository.getStickers(page.id),
                images = notesRepository.getImages(page.id),
                textBoxes = notesRepository.getTextBoxes(page.id)
            )
            val pageLabel = "${_state.value.note?.title.orEmpty()}_trang${_state.value.currentPageIndex + 1}"
            noteExporter.exportToPng(pageLabel, page, content)
                .onSuccess { file -> _state.update { it.copy(isExporting = false, exportedFile = file, exportedFileMimeType = "image/png") } }
                .onFailure { e -> _state.update { it.copy(isExporting = false, error = e.message ?: "Xuất file thất bại") } }
        }
    }

    fun consumeExportedFile() {
        _state.update { it.copy(exportedFile = null) }
    }
}
