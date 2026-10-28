package com.kapi.notes.ui.editor

import com.kapi.notes.domain.model.EmojiSticker
import com.kapi.notes.domain.model.ImageAttachment
import com.kapi.notes.domain.model.Stroke
import com.kapi.notes.domain.model.TextBoxItem

/**
 * One undoable unit. A pen/highlighter stroke is "added one stroke"; an eraser pass can wipe
 * several strokes in a single gesture, so it is recorded as "removed N strokes" and undoing it
 * restores all of them at once - matching how the wireframe's Undo button is expected to behave
 * per gesture, not per erased stroke.
 */
sealed class EditorAction {
    data class StrokeAdded(val stroke: Stroke) : EditorAction()
    data class StrokesErased(val strokes: List<Stroke>) : EditorAction()
    /** One "Xóa phẳng" eraser pass: [before] are the original strokes it cut into, [after] the
     * resulting surviving pieces (some originals may have no surviving piece at all). */
    data class StrokesFlatErased(val before: List<Stroke>, val after: List<Stroke>) : EditorAction()
    /** One Lasso drag that moved strokes to a new spot ("cut" in the toolbar). */
    data class StrokesMoved(val before: List<Stroke>, val after: List<Stroke>) : EditorAction()

    data class StickerAdded(val sticker: EmojiSticker) : EditorAction()
    data class StickerRemoved(val sticker: EmojiSticker) : EditorAction()
    data class StickerMoved(val before: EmojiSticker, val after: EmojiSticker) : EditorAction()
    /** Emoji rotate handle: free rotation, as opposed to the plain drag [StickerMoved] covers. */
    data class StickerTransformed(val before: EmojiSticker, val after: EmojiSticker) : EditorAction()

    data class ImageAdded(val image: ImageAttachment) : EditorAction()
    data class ImageRemoved(val image: ImageAttachment) : EditorAction()
    data class ImageChanged(val before: ImageAttachment, val after: ImageAttachment) : EditorAction()

    data class TextBoxAdded(val textBox: TextBoxItem) : EditorAction()
    data class TextBoxRemoved(val textBox: TextBoxItem) : EditorAction()
    data class TextBoxChanged(val before: TextBoxItem, val after: TextBoxItem) : EditorAction()
}

/** Per-page (reset when switching pages) undo/redo history, capped to avoid unbounded memory use. */
class UndoRedoStack(private val maxSize: Int = 100) {
    private val undo = ArrayDeque<EditorAction>()
    private val redo = ArrayDeque<EditorAction>()

    val canUndo: Boolean get() = undo.isNotEmpty()
    val canRedo: Boolean get() = redo.isNotEmpty()

    fun push(action: EditorAction) {
        undo.addLast(action)
        if (undo.size > maxSize) undo.removeFirst()
        redo.clear()
    }

    fun popUndo(): EditorAction? {
        val action = undo.removeLastOrNull() ?: return null
        redo.addLast(action)
        return action
    }

    fun popRedo(): EditorAction? {
        val action = redo.removeLastOrNull() ?: return null
        undo.addLast(action)
        return action
    }

    fun clear() {
        undo.clear()
        redo.clear()
    }
}
