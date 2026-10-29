package com.kapi.notes.data.repository

import com.kapi.notes.data.local.ImageAttachmentEntity
import com.kapi.notes.data.local.NoteDao
import com.kapi.notes.data.local.NoteEntity
import com.kapi.notes.data.local.PageEntity
import com.kapi.notes.data.local.StickerEntity
import com.kapi.notes.data.local.TextBoxEntity
import com.kapi.notes.data.local.TombstoneEntity
import com.kapi.notes.data.local.toDomain
import com.kapi.notes.data.local.toEntity
import com.kapi.notes.domain.model.AudioClip
import com.kapi.notes.domain.model.EmojiSticker
import com.kapi.notes.domain.model.ImageAttachment
import com.kapi.notes.domain.model.Note
import com.kapi.notes.domain.model.NotePage
import com.kapi.notes.domain.model.NoteSource
import com.kapi.notes.domain.model.PageFlip
import com.kapi.notes.domain.model.PageOrientation
import com.kapi.notes.domain.model.PaperType
import com.kapi.notes.domain.model.Stroke
import com.kapi.notes.domain.model.TextBoxItem
import com.kapi.notes.domain.model.TombstoneKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** A4 at a comfortable editing resolution, matching the wireframe's 794x1123 canvas ratio. */
private const val DEFAULT_PAGE_WIDTH_PX = 1588
private const val DEFAULT_PAGE_HEIGHT_PX = 2246

@Singleton
class NotesRepositoryImpl @Inject constructor(
    private val dao: NoteDao
) : NotesRepository {

    override fun observeActiveNotes(): Flow<List<Note>> =
        dao.observeActiveNotes().map { list -> list.map { it.toDomain() } }

    override fun observeFavoriteNotes(): Flow<List<Note>> =
        dao.observeFavoriteNotes().map { list -> list.map { it.toDomain() } }

    override fun observeTrashedNotes(): Flow<List<Note>> =
        dao.observeTrashedNotes().map { list -> list.map { it.toDomain() } }

    override suspend fun getNote(noteId: String): Note? {
        val entity = dao.getNote(noteId) ?: return null
        val pageCount = dao.getPages(noteId).size
        return entity.toDomain(pageCount)
    }

    override suspend fun getPages(noteId: String): List<NotePage> =
        dao.getPages(noteId).map { it.toDomain() }

    override fun observeStrokes(pageId: String): Flow<List<Stroke>> =
        dao.observeStrokes(pageId).map { list -> list.map { it.toDomain() } }

    override suspend fun getStrokes(pageId: String): List<Stroke> =
        dao.getStrokes(pageId).map { it.toDomain() }

    override fun observeStickers(pageId: String): Flow<List<EmojiSticker>> =
        dao.observeStickers(pageId).map { list -> list.map { it.toDomain() } }

    override suspend fun getStickers(pageId: String): List<EmojiSticker> =
        dao.getStickers(pageId).map { it.toDomain() }

    override suspend fun createBlankNote(title: String, paperType: PaperType): String {
        val now = System.currentTimeMillis()
        val noteId = UUID.randomUUID().toString()
        val pageId = UUID.randomUUID().toString()

        dao.upsert(
            NoteEntity(
                id = noteId,
                title = title,
                source = NoteSource.BLANK,
                driveFileId = null,
                driveFileName = null,
                thumbnailPath = null,
                isFavorite = false,
                isTrashed = false,
                createdAt = now,
                updatedAt = now
            )
        )
        dao.upsertPage(
            PageEntity(
                id = pageId,
                noteId = noteId,
                pageIndex = 0,
                paperType = paperType,
                orientation = PageOrientation.PORTRAIT,
                flip = PageFlip.NONE,
                widthPx = DEFAULT_PAGE_WIDTH_PX,
                heightPx = DEFAULT_PAGE_HEIGHT_PX,
                backgroundImagePath = null,
                updatedAt = now
            )
        )
        return noteId
    }

    override suspend fun createImportedNote(
        title: String,
        driveFileId: String?,
        driveFileName: String,
        pageBackgroundPaths: List<String>,
        pageWidthPx: Int,
        pageHeightPx: Int
    ): String {
        val now = System.currentTimeMillis()
        val noteId = UUID.randomUUID().toString()

        dao.upsert(
            NoteEntity(
                id = noteId,
                title = title,
                source = NoteSource.DRIVE_IMPORT,
                driveFileId = driveFileId,
                driveFileName = driveFileName,
                thumbnailPath = pageBackgroundPaths.firstOrNull(),
                isFavorite = false,
                isTrashed = false,
                createdAt = now,
                updatedAt = now
            )
        )
        pageBackgroundPaths.forEachIndexed { index, path ->
            dao.upsertPage(
                PageEntity(
                    id = UUID.randomUUID().toString(),
                    noteId = noteId,
                    pageIndex = index,
                    paperType = PaperType.BLANK,
                    orientation = PageOrientation.PORTRAIT,
                    flip = PageFlip.NONE,
                    widthPx = pageWidthPx,
                    heightPx = pageHeightPx,
                    backgroundImagePath = path,
                    updatedAt = now
                )
            )
        }
        return noteId
    }

    override suspend fun renameNote(noteId: String, newTitle: String) {
        val entity = dao.getNote(noteId) ?: return
        dao.update(entity.copy(title = newTitle, updatedAt = System.currentTimeMillis()))
    }

    override suspend fun setFavorite(noteId: String, favorite: Boolean) {
        dao.setFavorite(noteId, favorite, System.currentTimeMillis())
    }

    override suspend fun setNoteLabel(noteId: String, text: String?, colorArgb: Long?) {
        val entity = dao.getNote(noteId) ?: return
        dao.update(entity.copy(labelText = text, labelColorArgb = colorArgb, updatedAt = System.currentTimeMillis()))
    }

    override suspend fun moveToTrash(noteId: String) {
        dao.moveToTrash(noteId, System.currentTimeMillis())
    }

    override suspend fun restoreFromTrash(noteId: String) {
        dao.restoreFromTrash(noteId, System.currentTimeMillis())
    }

    override suspend fun deletePermanently(noteId: String) {
        dao.deleteById(noteId)
        dao.upsertTombstone(TombstoneEntity(id = noteId, kind = TombstoneKind.NOTE, deletedAt = System.currentTimeMillis()))
    }

    override suspend fun addStroke(stroke: Stroke) {
        dao.upsertStroke(stroke.toEntity())
        touchNote(noteIdForPage(stroke.pageId))
    }

    override suspend fun removeStroke(strokeId: String) {
        dao.deleteStroke(strokeId)
        dao.upsertTombstone(TombstoneEntity(id = strokeId, kind = TombstoneKind.STROKE, deletedAt = System.currentTimeMillis()))
    }

    override suspend fun addSticker(sticker: EmojiSticker) {
        dao.upsertSticker(sticker.toEntity())
        touchNote(noteIdForPage(sticker.pageId))
    }

    override suspend fun moveSticker(stickerId: String, x: Float, y: Float) {
        val existing = dao.getSticker(stickerId) ?: return
        val updated = existing.copy(x = x, y = y, updatedAt = System.currentTimeMillis())
        dao.upsertSticker(updated)
        touchNote(noteIdForPage(existing.pageId))
    }

    override suspend fun removeSticker(stickerId: String) {
        dao.deleteSticker(stickerId)
        dao.upsertTombstone(TombstoneEntity(id = stickerId, kind = TombstoneKind.STICKER, deletedAt = System.currentTimeMillis()))
    }

    override suspend fun addPage(noteId: String, afterPageId: String?, paperType: PaperType): String {
        val now = System.currentTimeMillis()
        val pages = dao.getPages(noteId)
        val template = pages.lastOrNull()
        val insertAt = afterPageId?.let { id -> pages.indexOfFirst { it.id == id } + 1 } ?: pages.size
        val newPageId = UUID.randomUUID().toString()

        // Re-number existing pages to make room, then insert.
        pages.drop(insertAt).forEach { dao.updatePage(it.copy(pageIndex = it.pageIndex + 1)) }
        dao.upsertPage(
            PageEntity(
                id = newPageId,
                noteId = noteId,
                pageIndex = insertAt,
                paperType = paperType,
                orientation = template?.orientation ?: PageOrientation.PORTRAIT,
                flip = PageFlip.NONE,
                widthPx = template?.widthPx ?: DEFAULT_PAGE_WIDTH_PX,
                heightPx = template?.heightPx ?: DEFAULT_PAGE_HEIGHT_PX,
                backgroundImagePath = null,
                updatedAt = now
            )
        )
        touchNote(noteId)
        return newPageId
    }

    override suspend fun duplicatePage(pageId: String): String {
        val now = System.currentTimeMillis()
        val source = dao.getPage(pageId) ?: error("Trang không tồn tại")
        val pages = dao.getPages(source.noteId)
        val insertAt = pages.indexOfFirst { it.id == pageId } + 1
        val newPageId = UUID.randomUUID().toString()

        pages.drop(insertAt).forEach { dao.updatePage(it.copy(pageIndex = it.pageIndex + 1)) }
        dao.upsertPage(source.copy(id = newPageId, pageIndex = insertAt, updatedAt = now))

        dao.getStrokes(pageId).forEach { s -> dao.upsertStroke(s.copy(id = UUID.randomUUID().toString(), pageId = newPageId, updatedAt = now)) }
        dao.getStickers(pageId).forEach { s -> dao.upsertSticker(s.copy(id = UUID.randomUUID().toString(), pageId = newPageId, updatedAt = now)) }
        dao.getImages(pageId).forEach { i -> dao.upsertImage(i.copy(id = UUID.randomUUID().toString(), pageId = newPageId, updatedAt = now)) }
        dao.getTextBoxes(pageId).forEach { t -> dao.upsertTextBox(t.copy(id = UUID.randomUUID().toString(), pageId = newPageId, updatedAt = now)) }

        touchNote(source.noteId)
        return newPageId
    }

    override suspend fun deletePage(pageId: String) {
        val page = dao.getPage(pageId) ?: return
        val pages = dao.getPages(page.noteId)
        if (pages.size <= 1) return // a note always keeps at least one page

        dao.deletePage(pageId)
        dao.upsertTombstone(TombstoneEntity(id = pageId, kind = TombstoneKind.PAGE, deletedAt = System.currentTimeMillis()))
        pages.filter { it.pageIndex > page.pageIndex }.forEach { dao.updatePage(it.copy(pageIndex = it.pageIndex - 1)) }
        touchNote(page.noteId)
    }

    override suspend fun movePage(pageId: String, direction: Int) {
        val page = dao.getPage(pageId) ?: return
        val pages = dao.getPages(page.noteId)
        val currentIndex = pages.indexOfFirst { it.id == pageId }
        val targetIndex = currentIndex + direction
        if (targetIndex !in pages.indices) return
        val other = pages[targetIndex]
        dao.updatePage(page.copy(pageIndex = other.pageIndex))
        dao.updatePage(other.copy(pageIndex = page.pageIndex))
        touchNote(page.noteId)
    }

    override fun observeImages(pageId: String): Flow<List<ImageAttachment>> =
        dao.observeImages(pageId).map { list -> list.map { it.toDomain() } }

    override suspend fun getImages(pageId: String): List<ImageAttachment> =
        dao.getImages(pageId).map { it.toDomain() }

    override suspend fun addImage(image: ImageAttachment) {
        dao.upsertImage(image.toEntity())
        touchNote(noteIdForPage(image.pageId))
    }

    override suspend fun moveImage(imageId: String, x: Float, y: Float) {
        val existing = dao.getImage(imageId) ?: return
        dao.upsertImage(existing.copy(x = x, y = y, updatedAt = System.currentTimeMillis()))
        touchNote(noteIdForPage(existing.pageId))
    }

    override suspend fun transformImage(imageId: String, widthPx: Float, heightPx: Float, rotationDegrees: Float) {
        val existing = dao.getImage(imageId) ?: return
        dao.upsertImage(existing.copy(widthPx = widthPx, heightPx = heightPx, rotationDegrees = rotationDegrees, updatedAt = System.currentTimeMillis()))
        touchNote(noteIdForPage(existing.pageId))
    }

    override suspend fun removeImage(imageId: String) {
        dao.deleteImage(imageId)
        dao.upsertTombstone(TombstoneEntity(id = imageId, kind = TombstoneKind.IMAGE, deletedAt = System.currentTimeMillis()))
    }

    override fun observeTextBoxes(pageId: String): Flow<List<TextBoxItem>> =
        dao.observeTextBoxes(pageId).map { list -> list.map { it.toDomain() } }

    override suspend fun getTextBoxes(pageId: String): List<TextBoxItem> =
        dao.getTextBoxes(pageId).map { it.toDomain() }

    override suspend fun addTextBox(textBox: TextBoxItem) {
        dao.upsertTextBox(textBox.toEntity())
        touchNote(noteIdForPage(textBox.pageId))
    }

    override suspend fun updateTextBox(
        textBoxId: String,
        text: String,
        fontSizePx: Float,
        colorArgb: Long,
        isBold: Boolean,
        isItalic: Boolean,
        fontFamily: com.kapi.notes.domain.model.TextFontFamily
    ) {
        val existing = dao.getTextBox(textBoxId) ?: return
        dao.upsertTextBox(
            existing.copy(
                text = text, fontSizePx = fontSizePx, colorArgb = colorArgb,
                isBold = isBold, isItalic = isItalic, fontFamily = fontFamily,
                updatedAt = System.currentTimeMillis()
            )
        )
        touchNote(noteIdForPage(existing.pageId))
    }

    override suspend fun moveTextBox(textBoxId: String, x: Float, y: Float) {
        val existing = dao.getTextBox(textBoxId) ?: return
        dao.upsertTextBox(existing.copy(x = x, y = y, updatedAt = System.currentTimeMillis()))
        touchNote(noteIdForPage(existing.pageId))
    }

    override suspend fun transformTextBox(textBoxId: String, widthPx: Float, heightPx: Float, rotationDegrees: Float) {
        val existing = dao.getTextBox(textBoxId) ?: return
        dao.upsertTextBox(existing.copy(widthPx = widthPx, heightPx = heightPx, rotationDegrees = rotationDegrees, updatedAt = System.currentTimeMillis()))
        touchNote(noteIdForPage(existing.pageId))
    }

    override suspend fun removeTextBox(textBoxId: String) {
        dao.deleteTextBox(textBoxId)
        dao.upsertTombstone(TombstoneEntity(id = textBoxId, kind = TombstoneKind.TEXT_BOX, deletedAt = System.currentTimeMillis()))
    }

    override fun observeAudioClips(noteId: String): Flow<List<AudioClip>> =
        dao.observeAudioClips(noteId).map { list -> list.map { it.toDomain() } }

    override suspend fun addAudioClip(clip: AudioClip) {
        dao.upsertAudioClip(clip.toEntity())
        touchNote(clip.noteId)
    }

    override suspend fun removeAudioClip(clipId: String) {
        dao.deleteAudioClip(clipId)
        dao.upsertTombstone(TombstoneEntity(id = clipId, kind = TombstoneKind.AUDIO_CLIP, deletedAt = System.currentTimeMillis()))
    }

    override suspend fun updatePageLayout(
        pageId: String,
        paperType: PaperType?,
        orientation: PageOrientation?,
        flip: PageFlip?
    ) {
        val page = dao.getPage(pageId) ?: return
        val updated = page.copy(
            paperType = paperType ?: page.paperType,
            orientation = orientation ?: page.orientation,
            flip = flip ?: page.flip,
            widthPx = if (orientation != null && orientation != page.orientation) page.heightPx else page.widthPx,
            heightPx = if (orientation != null && orientation != page.orientation) page.widthPx else page.heightPx,
            updatedAt = System.currentTimeMillis()
        )
        dao.updatePage(updated)
        touchNote(page.noteId)
    }

    override suspend fun setPageTagColor(pageId: String, tagColorArgb: Long?) {
        val page = dao.getPage(pageId) ?: return
        dao.updatePage(page.copy(tagColorArgb = tagColorArgb, updatedAt = System.currentTimeMillis()))
        touchNote(page.noteId)
    }

    override suspend fun setPageBackground(pageId: String, backgroundImagePath: String?, widthPx: Int?, heightPx: Int?) {
        val page = dao.getPage(pageId) ?: return
        dao.updatePage(
            page.copy(
                backgroundImagePath = backgroundImagePath,
                widthPx = widthPx ?: page.widthPx,
                heightPx = heightPx ?: page.heightPx,
                updatedAt = System.currentTimeMillis()
            )
        )
        touchNote(page.noteId)
    }

    override suspend fun insertBackgroundPages(noteId: String, afterPageId: String?, paths: List<String>, widthPx: Int, heightPx: Int): List<String> {
        val now = System.currentTimeMillis()
        val pages = dao.getPages(noteId)
        val insertAt = afterPageId?.let { id -> pages.indexOfFirst { it.id == id } + 1 } ?: pages.size

        pages.drop(insertAt).forEach { dao.updatePage(it.copy(pageIndex = it.pageIndex + paths.size)) }
        val newIds = paths.mapIndexed { offset, path ->
            val newPageId = UUID.randomUUID().toString()
            dao.upsertPage(
                PageEntity(
                    id = newPageId,
                    noteId = noteId,
                    pageIndex = insertAt + offset,
                    paperType = PaperType.BLANK,
                    orientation = PageOrientation.PORTRAIT,
                    flip = PageFlip.NONE,
                    widthPx = widthPx,
                    heightPx = heightPx,
                    backgroundImagePath = path,
                    updatedAt = now
                )
            )
            newPageId
        }
        touchNote(noteId)
        return newIds
    }

    override suspend fun setPageBookmark(pageId: String, bookmarked: Boolean) {
        val page = dao.getPage(pageId) ?: return
        dao.updatePage(page.copy(isBookmarked = bookmarked, updatedAt = System.currentTimeMillis()))
        touchNote(page.noteId)
    }

    override suspend fun duplicateNote(noteId: String): String {
        val now = System.currentTimeMillis()
        val source = dao.getNote(noteId) ?: error("Ghi chú không tồn tại")
        val newNoteId = UUID.randomUUID().toString()
        dao.upsert(
            source.copy(
                id = newNoteId,
                title = "${source.title} (bản sao)",
                isFavorite = false,
                isTrashed = false,
                createdAt = now,
                updatedAt = now
            )
        )
        dao.getPages(noteId).forEach { page ->
            val newPageId = UUID.randomUUID().toString()
            dao.upsertPage(page.copy(id = newPageId, noteId = newNoteId, updatedAt = now))
            dao.getStrokes(page.id).forEach { s -> dao.upsertStroke(s.copy(id = UUID.randomUUID().toString(), pageId = newPageId, updatedAt = now)) }
            dao.getStickers(page.id).forEach { s -> dao.upsertSticker(s.copy(id = UUID.randomUUID().toString(), pageId = newPageId, updatedAt = now)) }
            dao.getImages(page.id).forEach { i -> dao.upsertImage(i.copy(id = UUID.randomUUID().toString(), pageId = newPageId, updatedAt = now)) }
            dao.getTextBoxes(page.id).forEach { t -> dao.upsertTextBox(t.copy(id = UUID.randomUUID().toString(), pageId = newPageId, updatedAt = now)) }
        }
        return newNoteId
    }

    override suspend fun searchNotes(query: String): List<Note> {
        if (query.isBlank()) return emptyList()
        val needle = query.trim().lowercase()
        return dao.getAllNotes().filter { !it.isTrashed }.filter { note ->
            note.title.lowercase().contains(needle) ||
                dao.getPages(note.id).any { page -> dao.getTextBoxes(page.id).any { it.text.lowercase().contains(needle) } }
        }.map { it.toDomain(dao.getPages(it.id).size) }
    }

    override suspend fun touchNote(noteId: String) {
        val entity = dao.getNote(noteId) ?: return
        dao.update(entity.copy(updatedAt = System.currentTimeMillis()))
    }

    override suspend fun updateThumbnail(noteId: String, thumbnailPath: String) {
        val entity = dao.getNote(noteId) ?: return
        dao.update(entity.copy(thumbnailPath = thumbnailPath, updatedAt = System.currentTimeMillis()))
    }

    override suspend fun deleteAllData() {
        dao.deleteAll()
    }

    private suspend fun noteIdForPage(pageId: String): String =
        dao.getPage(pageId)?.noteId ?: ""
}
