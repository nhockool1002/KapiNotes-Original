package com.kapi.notes.data.local

import com.kapi.notes.domain.model.AudioClip
import com.kapi.notes.domain.model.EmojiSticker
import com.kapi.notes.domain.model.ImageAttachment
import com.kapi.notes.domain.model.Note
import com.kapi.notes.domain.model.NotePage
import com.kapi.notes.domain.model.Stroke
import com.kapi.notes.domain.model.TextBoxItem
import com.kapi.notes.domain.model.Tombstone

fun NoteEntity.toDomain(pageCount: Int = 1): Note = Note(
    id = id,
    title = title,
    source = source,
    driveFileId = driveFileId,
    driveFileName = driveFileName,
    thumbnailPath = thumbnailPath,
    isFavorite = isFavorite,
    isTrashed = isTrashed,
    createdAt = createdAt,
    updatedAt = updatedAt,
    pageCount = pageCount
)

fun Note.toEntity(): NoteEntity = NoteEntity(
    id = id,
    title = title,
    source = source,
    driveFileId = driveFileId,
    driveFileName = driveFileName,
    thumbnailPath = thumbnailPath,
    isFavorite = isFavorite,
    isTrashed = isTrashed,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun PageEntity.toDomain(): NotePage = NotePage(
    id = id,
    noteId = noteId,
    pageIndex = pageIndex,
    paperType = paperType,
    orientation = orientation,
    flip = flip,
    widthPx = widthPx,
    heightPx = heightPx,
    backgroundImagePath = backgroundImagePath,
    tagColorArgb = tagColorArgb,
    isBookmarked = isBookmarked,
    updatedAt = updatedAt
)

fun NotePage.toEntity(): PageEntity = PageEntity(
    id = id,
    noteId = noteId,
    pageIndex = pageIndex,
    paperType = paperType,
    orientation = orientation,
    flip = flip,
    widthPx = widthPx,
    heightPx = heightPx,
    backgroundImagePath = backgroundImagePath,
    tagColorArgb = tagColorArgb,
    isBookmarked = isBookmarked,
    updatedAt = updatedAt
)

fun StrokeEntity.toDomain(): Stroke = Stroke(
    id = id,
    pageId = pageId,
    tool = tool,
    colorArgb = colorArgb,
    widthDp = widthDp,
    points = points,
    fillColorArgb = fillColorArgb,
    rotationDegrees = rotationDegrees,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Stroke.toEntity(): StrokeEntity = StrokeEntity(
    id = id,
    pageId = pageId,
    tool = tool,
    colorArgb = colorArgb,
    widthDp = widthDp,
    points = points,
    fillColorArgb = fillColorArgb,
    rotationDegrees = rotationDegrees,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun StickerEntity.toDomain(): EmojiSticker = EmojiSticker(
    id = id,
    pageId = pageId,
    emoji = emoji,
    x = x,
    y = y,
    sizePx = sizePx,
    rotationDegrees = rotationDegrees,
    updatedAt = updatedAt
)

fun EmojiSticker.toEntity(): StickerEntity = StickerEntity(
    id = id,
    pageId = pageId,
    emoji = emoji,
    x = x,
    y = y,
    sizePx = sizePx,
    rotationDegrees = rotationDegrees,
    updatedAt = updatedAt
)

fun TombstoneEntity.toDomain(): Tombstone = Tombstone(id = id, kind = kind, deletedAt = deletedAt)

fun Tombstone.toEntity(): TombstoneEntity = TombstoneEntity(id = id, kind = kind, deletedAt = deletedAt)

fun ImageAttachmentEntity.toDomain(): ImageAttachment = ImageAttachment(
    id = id, pageId = pageId, imagePath = imagePath, x = x, y = y, widthPx = widthPx, heightPx = heightPx,
    rotationDegrees = rotationDegrees, updatedAt = updatedAt
)

fun ImageAttachment.toEntity(): ImageAttachmentEntity = ImageAttachmentEntity(
    id = id, pageId = pageId, imagePath = imagePath, x = x, y = y, widthPx = widthPx, heightPx = heightPx,
    rotationDegrees = rotationDegrees, updatedAt = updatedAt
)

fun TextBoxEntity.toDomain(): TextBoxItem = TextBoxItem(
    id = id, pageId = pageId, text = text, x = x, y = y, widthPx = widthPx, heightPx = heightPx,
    fontSizePx = fontSizePx, colorArgb = colorArgb, isBold = isBold, isItalic = isItalic,
    fontFamily = fontFamily, rotationDegrees = rotationDegrees, updatedAt = updatedAt
)

fun TextBoxItem.toEntity(): TextBoxEntity = TextBoxEntity(
    id = id, pageId = pageId, text = text, x = x, y = y, widthPx = widthPx, heightPx = heightPx,
    fontSizePx = fontSizePx, colorArgb = colorArgb, isBold = isBold, isItalic = isItalic,
    fontFamily = fontFamily, rotationDegrees = rotationDegrees, updatedAt = updatedAt
)

fun AudioClipEntity.toDomain(): AudioClip = AudioClip(id = id, noteId = noteId, filePath = filePath, durationMs = durationMs, createdAt = createdAt)

fun AudioClip.toEntity(): AudioClipEntity = AudioClipEntity(id = id, noteId = noteId, filePath = filePath, durationMs = durationMs, createdAt = createdAt)
