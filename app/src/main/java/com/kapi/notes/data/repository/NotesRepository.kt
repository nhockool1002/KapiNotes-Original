package com.kapi.notes.data.repository

import com.kapi.notes.domain.model.AudioClip
import com.kapi.notes.domain.model.EmojiSticker
import com.kapi.notes.domain.model.ImageAttachment
import com.kapi.notes.domain.model.Note
import com.kapi.notes.domain.model.NotePage
import com.kapi.notes.domain.model.PageFlip
import com.kapi.notes.domain.model.PageOrientation
import com.kapi.notes.domain.model.PaperType
import com.kapi.notes.domain.model.Stroke
import com.kapi.notes.domain.model.TextBoxItem
import kotlinx.coroutines.flow.Flow

interface NotesRepository {
    fun observeActiveNotes(): Flow<List<Note>>
    fun observeFavoriteNotes(): Flow<List<Note>>
    fun observeTrashedNotes(): Flow<List<Note>>

    suspend fun getNote(noteId: String): Note?
    suspend fun getPages(noteId: String): List<NotePage>
    fun observeStrokes(pageId: String): Flow<List<Stroke>>
    suspend fun getStrokes(pageId: String): List<Stroke>
    fun observeStickers(pageId: String): Flow<List<EmojiSticker>>
    suspend fun getStickers(pageId: String): List<EmojiSticker>

    /** Creates a blank note with a single blank page and returns its id. */
    suspend fun createBlankNote(title: String, paperType: PaperType = PaperType.LINED): String

    /** Creates a note backed by an already-downloaded/rendered document.
     * [driveFileId] is null for a document imported from local device storage rather than Drive. */
    suspend fun createImportedNote(
        title: String,
        driveFileId: String?,
        driveFileName: String,
        pageBackgroundPaths: List<String>,
        pageWidthPx: Int,
        pageHeightPx: Int
    ): String

    suspend fun renameNote(noteId: String, newTitle: String)
    suspend fun setFavorite(noteId: String, favorite: Boolean)
    /** Sets or clears (both null) the note's colored label used to group/filter it on the dashboard. */
    suspend fun setNoteLabel(noteId: String, text: String?, colorArgb: Long?)
    suspend fun moveToTrash(noteId: String)
    suspend fun restoreFromTrash(noteId: String)

    /** Permanently deletes a note and records a tombstone so Drive sync does not resurrect it. */
    suspend fun deletePermanently(noteId: String)

    suspend fun addStroke(stroke: Stroke)

    /** Removes a stroke and records a tombstone so Drive sync does not resurrect it. */
    suspend fun removeStroke(strokeId: String)

    suspend fun addSticker(sticker: EmojiSticker)
    suspend fun moveSticker(stickerId: String, x: Float, y: Float)

    /** Removes a sticker and records a tombstone so Drive sync does not resurrect it. */
    suspend fun removeSticker(stickerId: String)

    fun observeImages(pageId: String): Flow<List<ImageAttachment>>
    suspend fun getImages(pageId: String): List<ImageAttachment>
    suspend fun addImage(image: ImageAttachment)
    suspend fun moveImage(imageId: String, x: Float, y: Float)

    /** Updates size/rotation in one write - used by the resize and rotate handles. */
    suspend fun transformImage(imageId: String, widthPx: Float, heightPx: Float, rotationDegrees: Float)
    suspend fun removeImage(imageId: String)

    fun observeTextBoxes(pageId: String): Flow<List<TextBoxItem>>
    suspend fun getTextBoxes(pageId: String): List<TextBoxItem>
    suspend fun addTextBox(textBox: TextBoxItem)
    suspend fun updateTextBox(
        textBoxId: String,
        text: String,
        fontSizePx: Float,
        colorArgb: Long,
        isBold: Boolean = false,
        isItalic: Boolean = false,
        fontFamily: com.kapi.notes.domain.model.TextFontFamily = com.kapi.notes.domain.model.TextFontFamily.DEFAULT
    )
    suspend fun moveTextBox(textBoxId: String, x: Float, y: Float)

    /** Updates size/rotation in one write - used by the resize and rotate handles. */
    suspend fun transformTextBox(textBoxId: String, widthPx: Float, heightPx: Float, rotationDegrees: Float)
    suspend fun removeTextBox(textBoxId: String)

    fun observeAudioClips(noteId: String): Flow<List<AudioClip>>
    suspend fun addAudioClip(clip: AudioClip)
    suspend fun removeAudioClip(clipId: String)

    suspend fun updatePageLayout(
        pageId: String,
        paperType: PaperType? = null,
        orientation: PageOrientation? = null,
        flip: PageFlip? = null
    )

    /** Sets or clears (null) the page's color tag dot shown on the page switcher. */
    suspend fun setPageTagColor(pageId: String, tagColorArgb: Long?)

    /** Sets or clears (null) a page's background image - used to annotate an imported photo/PDF
     * page right on top of it, from inside an already-open note. */
    suspend fun setPageBackground(pageId: String, backgroundImagePath: String?, widthPx: Int? = null, heightPx: Int? = null)

    /** Inserts one new page per rendered image in [paths], right after [afterPageId] (or at the
     * end when null), each with that image as its background - used to append imported PDF pages
     * into an existing note. Returns the new page ids in order. */
    suspend fun insertBackgroundPages(noteId: String, afterPageId: String?, paths: List<String>, widthPx: Int, heightPx: Int): List<String>

    /** Stars/unstars a page for the page switcher's quick-jump list. */
    suspend fun setPageBookmark(pageId: String, bookmarked: Boolean)

    /** Duplicates an entire note (every page + its strokes/stickers/images/text boxes) and returns the new note's id. */
    suspend fun duplicateNote(noteId: String): String

    /** Notes whose title or any text box content contains [query] (case-insensitive). */
    suspend fun searchNotes(query: String): List<Note>

    /** Appends a new blank page right after [afterPageId] (or at the end when null) and returns its id. */
    suspend fun addPage(noteId: String, afterPageId: String?, paperType: PaperType): String

    /** Duplicates a page (paper settings + strokes + stickers) right after it and returns the new id. */
    suspend fun duplicatePage(pageId: String): String

    /** Deletes a page (must not be a note's only page) and records tombstones for it and its content. */
    suspend fun deletePage(pageId: String)

    /** Swaps a page with its neighbour in the given direction. */
    suspend fun movePage(pageId: String, direction: Int)

    suspend fun touchNote(noteId: String)

    /** Points the dashboard card's thumbnail at a freshly rendered snapshot of the note's first
     * page - called after leaving the editor so the thumbnail reflects actual content. */
    suspend fun updateThumbnail(noteId: String, thumbnailPath: String)

    suspend fun deleteAllData()
}
