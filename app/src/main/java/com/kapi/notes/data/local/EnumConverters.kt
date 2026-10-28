package com.kapi.notes.data.local

import androidx.room.TypeConverter
import com.kapi.notes.domain.model.NoteSource
import com.kapi.notes.domain.model.PageFlip
import com.kapi.notes.domain.model.PageOrientation
import com.kapi.notes.domain.model.PaperType
import com.kapi.notes.domain.model.TextFontFamily
import com.kapi.notes.domain.model.ToolType
import com.kapi.notes.domain.model.TombstoneKind

/** Room stores every enum used by the entities in [Entities.kt] as its plain `name()` string. */
class EnumConverters {
    @TypeConverter
    fun fromNoteSource(value: NoteSource): String = value.name

    @TypeConverter
    fun toNoteSource(value: String): NoteSource = NoteSource.valueOf(value)

    @TypeConverter
    fun fromPaperType(value: PaperType): String = value.name

    @TypeConverter
    fun toPaperType(value: String): PaperType = PaperType.valueOf(value)

    @TypeConverter
    fun fromOrientation(value: PageOrientation): String = value.name

    @TypeConverter
    fun toOrientation(value: String): PageOrientation = PageOrientation.valueOf(value)

    @TypeConverter
    fun fromFlip(value: PageFlip): String = value.name

    @TypeConverter
    fun toFlip(value: String): PageFlip = PageFlip.valueOf(value)

    @TypeConverter
    fun fromToolType(value: ToolType): String = value.name

    @TypeConverter
    fun toToolType(value: String): ToolType = ToolType.valueOf(value)

    @TypeConverter
    fun fromTextFontFamily(value: TextFontFamily): String = value.name

    @TypeConverter
    fun toTextFontFamily(value: String): TextFontFamily = TextFontFamily.valueOf(value)

    @TypeConverter
    fun fromTombstoneKind(value: TombstoneKind): String = value.name

    @TypeConverter
    fun toTombstoneKind(value: String): TombstoneKind = TombstoneKind.valueOf(value)
}
