package com.kapi.notes.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.kapi.notes.domain.model.NoteSource
import com.kapi.notes.domain.model.PageFlip
import com.kapi.notes.domain.model.PageOrientation
import com.kapi.notes.domain.model.PaperType
import com.kapi.notes.domain.model.StrokePoint
import com.kapi.notes.domain.model.TextFontFamily
import com.kapi.notes.domain.model.ToolType
import com.kapi.notes.domain.model.TombstoneKind
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val source: NoteSource,
    val driveFileId: String?,
    val driveFileName: String?,
    val thumbnailPath: String?,
    val isFavorite: Boolean,
    val isTrashed: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("noteId")]
)
data class PageEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val pageIndex: Int,
    val paperType: PaperType,
    val orientation: PageOrientation,
    val flip: PageFlip,
    val widthPx: Int,
    val heightPx: Int,
    val backgroundImagePath: String?,
    val tagColorArgb: Long? = null,
    val isBookmarked: Boolean = false,
    val updatedAt: Long
)

@Entity(
    tableName = "strokes",
    foreignKeys = [
        ForeignKey(
            entity = PageEntity::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("pageId")]
)
@TypeConverters(StrokePointsConverter::class)
data class StrokeEntity(
    @PrimaryKey val id: String,
    val pageId: String,
    val tool: ToolType,
    val colorArgb: Long,
    val widthDp: Float,
    val points: List<StrokePoint>,
    val fillColorArgb: Long? = null,
    val rotationDegrees: Float = 0f,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "stickers",
    foreignKeys = [
        ForeignKey(
            entity = PageEntity::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("pageId")]
)
data class StickerEntity(
    @PrimaryKey val id: String,
    val pageId: String,
    val emoji: String,
    val x: Float,
    val y: Float,
    val sizePx: Float,
    val rotationDegrees: Float = 0f,
    val updatedAt: Long
)

@Entity(
    tableName = "images",
    foreignKeys = [
        ForeignKey(
            entity = PageEntity::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("pageId")]
)
data class ImageAttachmentEntity(
    @PrimaryKey val id: String,
    val pageId: String,
    val imagePath: String,
    val x: Float,
    val y: Float,
    val widthPx: Float,
    val heightPx: Float,
    val rotationDegrees: Float = 0f,
    val updatedAt: Long
)

@Entity(
    tableName = "text_boxes",
    foreignKeys = [
        ForeignKey(
            entity = PageEntity::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("pageId")]
)
data class TextBoxEntity(
    @PrimaryKey val id: String,
    val pageId: String,
    val text: String,
    val x: Float,
    val y: Float,
    val widthPx: Float,
    val heightPx: Float = 0f,
    val fontSizePx: Float,
    val colorArgb: Long,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val fontFamily: TextFontFamily = TextFontFamily.DEFAULT,
    val rotationDegrees: Float = 0f,
    val updatedAt: Long
)

@Entity(
    tableName = "audio_clips",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("noteId")]
)
data class AudioClipEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val filePath: String,
    val durationMs: Long,
    val createdAt: Long
)

@Entity(tableName = "tombstones")
data class TombstoneEntity(
    @PrimaryKey val id: String,
    val kind: TombstoneKind,
    val deletedAt: Long
)

class StrokePointsConverter {
    @TypeConverter
    fun fromPoints(points: List<StrokePoint>): String = Json.encodeToString(points)

    @TypeConverter
    fun toPoints(raw: String): List<StrokePoint> = Json.decodeFromString(raw)
}
