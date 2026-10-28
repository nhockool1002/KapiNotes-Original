package com.kapi.notes.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE isTrashed = 0 ORDER BY updatedAt DESC")
    fun observeActiveNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isTrashed = 1 ORDER BY updatedAt DESC")
    fun observeTrashedNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isFavorite = 1 AND isTrashed = 0 ORDER BY updatedAt DESC")
    fun observeFavoriteNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNote(id: String): NoteEntity?

    @Query("SELECT * FROM notes")
    suspend fun getAllNotes(): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: NoteEntity)

    @Update
    suspend fun update(note: NoteEntity)

    @Query("UPDATE notes SET isTrashed = 1, updatedAt = :now WHERE id = :id")
    suspend fun moveToTrash(id: String, now: Long)

    @Query("UPDATE notes SET isTrashed = 0, updatedAt = :now WHERE id = :id")
    suspend fun restoreFromTrash(id: String, now: Long)

    @Query("UPDATE notes SET isFavorite = :favorite, updatedAt = :now WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean, now: Long)

    @Delete
    suspend fun delete(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM notes")
    suspend fun deleteAll()

    @Query("SELECT * FROM pages WHERE noteId = :noteId ORDER BY pageIndex ASC")
    suspend fun getPages(noteId: String): List<PageEntity>

    @Query("SELECT * FROM pages WHERE id = :pageId")
    suspend fun getPage(pageId: String): PageEntity?

    @Query("SELECT * FROM pages")
    suspend fun getAllPages(): List<PageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPage(page: PageEntity)

    @Update
    suspend fun updatePage(page: PageEntity)

    @Query("SELECT * FROM strokes WHERE pageId = :pageId ORDER BY createdAt ASC")
    fun observeStrokes(pageId: String): Flow<List<StrokeEntity>>

    @Query("SELECT * FROM strokes WHERE pageId = :pageId ORDER BY createdAt ASC")
    suspend fun getStrokes(pageId: String): List<StrokeEntity>

    @Query("SELECT * FROM strokes")
    suspend fun getAllStrokes(): List<StrokeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStroke(stroke: StrokeEntity)

    @Query("DELETE FROM strokes WHERE id = :strokeId")
    suspend fun deleteStroke(strokeId: String)

    @Query("DELETE FROM strokes WHERE pageId = :pageId")
    suspend fun deleteStrokesForPage(pageId: String)

    @Query("SELECT * FROM stickers WHERE pageId = :pageId ORDER BY updatedAt ASC")
    fun observeStickers(pageId: String): Flow<List<StickerEntity>>

    @Query("SELECT * FROM stickers WHERE pageId = :pageId ORDER BY updatedAt ASC")
    suspend fun getStickers(pageId: String): List<StickerEntity>

    @Query("SELECT * FROM stickers WHERE id = :id")
    suspend fun getSticker(id: String): StickerEntity?

    @Query("SELECT * FROM stickers")
    suspend fun getAllStickers(): List<StickerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSticker(sticker: StickerEntity)

    @Query("DELETE FROM stickers WHERE id = :stickerId")
    suspend fun deleteSticker(stickerId: String)

    @Query("DELETE FROM pages WHERE id = :pageId")
    suspend fun deletePage(pageId: String)

    @Query("UPDATE pages SET pageIndex = :newIndex WHERE id = :pageId")
    suspend fun setPageIndex(pageId: String, newIndex: Int)

    @Query("SELECT * FROM images WHERE pageId = :pageId ORDER BY updatedAt ASC")
    fun observeImages(pageId: String): Flow<List<ImageAttachmentEntity>>

    @Query("SELECT * FROM images WHERE pageId = :pageId ORDER BY updatedAt ASC")
    suspend fun getImages(pageId: String): List<ImageAttachmentEntity>

    @Query("SELECT * FROM images WHERE id = :id")
    suspend fun getImage(id: String): ImageAttachmentEntity?

    @Query("SELECT * FROM images")
    suspend fun getAllImages(): List<ImageAttachmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertImage(image: ImageAttachmentEntity)

    @Query("DELETE FROM images WHERE id = :id")
    suspend fun deleteImage(id: String)

    @Query("SELECT * FROM text_boxes WHERE pageId = :pageId ORDER BY updatedAt ASC")
    fun observeTextBoxes(pageId: String): Flow<List<TextBoxEntity>>

    @Query("SELECT * FROM text_boxes WHERE pageId = :pageId ORDER BY updatedAt ASC")
    suspend fun getTextBoxes(pageId: String): List<TextBoxEntity>

    @Query("SELECT * FROM text_boxes WHERE id = :id")
    suspend fun getTextBox(id: String): TextBoxEntity?

    @Query("SELECT * FROM text_boxes")
    suspend fun getAllTextBoxes(): List<TextBoxEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTextBox(textBox: TextBoxEntity)

    @Query("DELETE FROM text_boxes WHERE id = :id")
    suspend fun deleteTextBox(id: String)

    @Query("SELECT * FROM audio_clips WHERE noteId = :noteId ORDER BY createdAt ASC")
    fun observeAudioClips(noteId: String): Flow<List<AudioClipEntity>>

    @Query("SELECT * FROM audio_clips")
    suspend fun getAllAudioClips(): List<AudioClipEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAudioClip(clip: AudioClipEntity)

    @Query("DELETE FROM audio_clips WHERE id = :id")
    suspend fun deleteAudioClip(id: String)

    @Query("SELECT * FROM tombstones")
    suspend fun getAllTombstones(): List<TombstoneEntity>

    @Query("SELECT * FROM tombstones WHERE id = :id")
    suspend fun getTombstone(id: String): TombstoneEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTombstone(tombstone: TombstoneEntity)
}
