package com.kapi.notes.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        NoteEntity::class, PageEntity::class, StrokeEntity::class,
        StickerEntity::class, TombstoneEntity::class,
        ImageAttachmentEntity::class, TextBoxEntity::class, AudioClipEntity::class
    ],
    version = 9,
    exportSchema = false
)
@TypeConverters(EnumConverters::class, StrokePointsConverter::class, PageLayersConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    companion object {
        const val DB_NAME = "kapinotes.db"
    }
}
