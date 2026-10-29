package com.kapi.notes.data.repository

import android.content.Context
import com.kapi.notes.data.local.AppDatabase
import com.kapi.notes.data.local.AudioClipEntity
import com.kapi.notes.data.local.ImageAttachmentEntity
import com.kapi.notes.data.local.NoteDao
import com.kapi.notes.data.local.NoteEntity
import com.kapi.notes.data.local.PageEntity
import com.kapi.notes.data.local.StickerEntity
import com.kapi.notes.data.local.StrokeEntity
import com.kapi.notes.data.local.TextBoxEntity
import com.kapi.notes.data.local.TombstoneEntity
import com.kapi.notes.domain.model.DEFAULT_LAYER_ID
import com.kapi.notes.domain.model.DefaultPageLayers
import com.kapi.notes.domain.model.NoteSource
import com.kapi.notes.domain.model.PageFlip
import com.kapi.notes.domain.model.PageLayer
import com.kapi.notes.domain.model.PageOrientation
import com.kapi.notes.domain.model.PaperType
import com.kapi.notes.domain.model.StrokePoint
import com.kapi.notes.domain.model.ToolType
import com.kapi.notes.domain.model.TombstoneKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class NoteDto(
    val id: String,
    val title: String,
    val source: String,
    val driveFileId: String?,
    val driveFileName: String?,
    val thumbnailFile: String?,
    val isFavorite: Boolean,
    val isTrashed: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val labelText: String? = null,
    val labelColorArgb: Long? = null
)

@Serializable
private data class PageDto(
    val id: String,
    val noteId: String,
    val pageIndex: Int,
    val paperType: String,
    val orientation: String,
    val flip: String,
    val widthPx: Int,
    val heightPx: Int,
    val backgroundFile: String?,
    val tagColorArgb: Long? = null,
    val isBookmarked: Boolean = false,
    val updatedAt: Long = 0L,
    val layers: List<PageLayer> = DefaultPageLayers,
    val activeLayerId: String = DEFAULT_LAYER_ID
)

@Serializable
private data class StrokeDto(
    val id: String,
    val pageId: String,
    val tool: String,
    val colorArgb: Long,
    val widthDp: Float,
    val points: List<StrokePoint>,
    val fillColorArgb: Long? = null,
    val rotationDegrees: Float = 0f,
    val layerId: String = DEFAULT_LAYER_ID,
    val createdAt: Long,
    val updatedAt: Long = createdAt
)

@Serializable
private data class StickerDto(
    val id: String,
    val pageId: String,
    val emoji: String,
    val x: Float,
    val y: Float,
    val sizePx: Float,
    val rotationDegrees: Float = 0f,
    val layerId: String = DEFAULT_LAYER_ID,
    val updatedAt: Long
)

@Serializable
private data class ImageDto(
    val id: String,
    val pageId: String,
    val imageFile: String,
    val x: Float,
    val y: Float,
    val widthPx: Float,
    val heightPx: Float,
    val rotationDegrees: Float = 0f,
    val layerId: String = DEFAULT_LAYER_ID,
    val updatedAt: Long
)

@Serializable
private data class TextBoxDto(
    val id: String,
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
    val fontFamily: String = "DEFAULT",
    val rotationDegrees: Float = 0f,
    val layerId: String = DEFAULT_LAYER_ID,
    val updatedAt: Long
)

@Serializable
private data class AudioClipDto(
    val id: String,
    val noteId: String,
    val audioFile: String,
    val durationMs: Long,
    val createdAt: Long
)

@Serializable
private data class TombstoneDto(val id: String, val kind: String, val deletedAt: Long)

@Serializable
private data class BackupManifest(
    val formatVersion: Int = 4,
    val exportedAt: Long,
    val notes: List<NoteDto>,
    val pages: List<PageDto>,
    val strokes: List<StrokeDto>,
    val stickers: List<StickerDto> = emptyList(),
    val images: List<ImageDto> = emptyList(),
    val textBoxes: List<TextBoxDto> = emptyList(),
    val audioClips: List<AudioClipDto> = emptyList(),
    val tombstones: List<TombstoneDto> = emptyList()
)

/**
 * Backs both "Import / export / delete data" in Settings AND Google Drive sync
 * ([DriveSyncAuthRepository]) - a backup is a zip with manifest.json (notes/pages/strokes/
 * stickers + delete tombstones) plus a copy of every referenced page-background image, keyed by
 * file name.
 *
 * [importFromFile] merges rather than overwrites, because the same zip format is what two
 * devices signed into the same Drive account exchange: each row carries an `updatedAt`, and a
 * row already on this device only gets replaced when the incoming copy is newer
 * (last-write-wins). Deletions are tracked as tombstones so that merging in an older backup -
 * from a device that synced before it had a chance to see the delete - does not resurrect the
 * row.
 */
@Singleton
class BackupRepository @Inject constructor(
    private val context: Context,
    private val noteDao: NoteDao,
    private val database: AppDatabase
) {
    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }

    suspend fun exportToFile(destination: File): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val notes = noteDao.getAllNotes()
            val pages = noteDao.getAllPages()
            val strokes = noteDao.getAllStrokes()
            val stickers = noteDao.getAllStickers()
            val images = noteDao.getAllImages()
            val textBoxes = noteDao.getAllTextBoxes()
            val audioClips = noteDao.getAllAudioClips()
            val tombstones = noteDao.getAllTombstones()

            val manifest = BackupManifest(
                exportedAt = System.currentTimeMillis(),
                notes = notes.map {
                    NoteDto(
                        id = it.id,
                        title = it.title,
                        source = it.source.name,
                        driveFileId = it.driveFileId,
                        driveFileName = it.driveFileName,
                        thumbnailFile = it.thumbnailPath?.let { path -> File(path).name },
                        isFavorite = it.isFavorite,
                        isTrashed = it.isTrashed,
                        createdAt = it.createdAt,
                        updatedAt = it.updatedAt,
                        labelText = it.labelText,
                        labelColorArgb = it.labelColorArgb
                    )
                },
                pages = pages.map {
                    PageDto(
                        id = it.id,
                        noteId = it.noteId,
                        pageIndex = it.pageIndex,
                        paperType = it.paperType.name,
                        orientation = it.orientation.name,
                        flip = it.flip.name,
                        widthPx = it.widthPx,
                        heightPx = it.heightPx,
                        backgroundFile = it.backgroundImagePath?.let { path -> File(path).name },
                        tagColorArgb = it.tagColorArgb,
                        isBookmarked = it.isBookmarked,
                        updatedAt = it.updatedAt,
                        layers = it.layers,
                        activeLayerId = it.activeLayerId
                    )
                },
                strokes = strokes.map {
                    StrokeDto(
                        id = it.id,
                        pageId = it.pageId,
                        tool = it.tool.name,
                        colorArgb = it.colorArgb,
                        widthDp = it.widthDp,
                        points = it.points,
                        fillColorArgb = it.fillColorArgb,
                        rotationDegrees = it.rotationDegrees,
                        layerId = it.layerId,
                        createdAt = it.createdAt,
                        updatedAt = it.updatedAt
                    )
                },
                stickers = stickers.map {
                    StickerDto(
                        id = it.id,
                        pageId = it.pageId,
                        emoji = it.emoji,
                        x = it.x,
                        y = it.y,
                        sizePx = it.sizePx,
                        rotationDegrees = it.rotationDegrees,
                        layerId = it.layerId,
                        updatedAt = it.updatedAt
                    )
                },
                images = images.map {
                    ImageDto(
                        id = it.id,
                        pageId = it.pageId,
                        imageFile = File(it.imagePath).name,
                        x = it.x,
                        y = it.y,
                        widthPx = it.widthPx,
                        heightPx = it.heightPx,
                        rotationDegrees = it.rotationDegrees,
                        layerId = it.layerId,
                        updatedAt = it.updatedAt
                    )
                },
                textBoxes = textBoxes.map {
                    TextBoxDto(
                        id = it.id,
                        pageId = it.pageId,
                        text = it.text,
                        x = it.x,
                        y = it.y,
                        widthPx = it.widthPx,
                        heightPx = it.heightPx,
                        fontSizePx = it.fontSizePx,
                        colorArgb = it.colorArgb,
                        isBold = it.isBold,
                        isItalic = it.isItalic,
                        fontFamily = it.fontFamily.name,
                        rotationDegrees = it.rotationDegrees,
                        layerId = it.layerId,
                        updatedAt = it.updatedAt
                    )
                },
                audioClips = audioClips.map {
                    AudioClipDto(
                        id = it.id,
                        noteId = it.noteId,
                        audioFile = File(it.filePath).name,
                        durationMs = it.durationMs,
                        createdAt = it.createdAt
                    )
                },
                tombstones = tombstones.map { TombstoneDto(it.id, it.kind.name, it.deletedAt) }
            )

            val imageFiles = (
                pages.mapNotNull { it.backgroundImagePath } +
                    notes.mapNotNull { it.thumbnailPath } +
                    images.map { it.imagePath }
                )
                .distinct()
                .map { File(it) }
                .filter { it.exists() }
            val audioFiles = audioClips.map { File(it.filePath) }.filter { it.exists() }

            ZipOutputStream(destination.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(json.encodeToString(manifest).toByteArray())
                zip.closeEntry()

                imageFiles.forEach { file ->
                    zip.putNextEntry(ZipEntry("assets/${file.name}"))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
                audioFiles.forEach { file ->
                    zip.putNextEntry(ZipEntry("audio/${file.name}"))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            destination
        }
    }

    suspend fun importFromFile(source: File): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val assetsDir = File(context.filesDir, "pages").apply { mkdirs() }
            val audioDir = File(context.filesDir, "audio").apply { mkdirs() }
            var manifest: BackupManifest? = null
            val restoredPaths = mutableMapOf<String, String>()
            val restoredAudioPaths = mutableMapOf<String, String>()

            ZipInputStream(source.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    when {
                        entry.name == "manifest.json" -> {
                            manifest = json.decodeFromString(BackupManifest.serializer(), zip.readBytes().decodeToString())
                        }
                        entry.name.startsWith("assets/") -> {
                            val name = entry.name.removePrefix("assets/")
                            val outFile = File(assetsDir, name)
                            outFile.outputStream().use { out -> zip.copyTo(out) }
                            restoredPaths[name] = outFile.absolutePath
                        }
                        entry.name.startsWith("audio/") -> {
                            val name = entry.name.removePrefix("audio/")
                            val outFile = File(audioDir, name)
                            outFile.outputStream().use { out -> zip.copyTo(out) }
                            restoredAudioPaths[name] = outFile.absolutePath
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            val data = manifest ?: error("File backup không hợp lệ: thiếu manifest.json")
            mergeIn(data, restoredPaths, restoredAudioPaths)
        }
    }

    /** Last-write-wins merge of an incoming manifest into the local Room database. */
    private suspend fun mergeIn(data: BackupManifest, restoredPaths: Map<String, String>, restoredAudioPaths: Map<String, String>) {
        // 1) Tombstones first: keep whichever copy (local or incoming) deleted most recently.
        val localTombstones = noteDao.getAllTombstones().associateBy { it.id }.toMutableMap()
        data.tombstones.forEach { dto ->
            val kind = runCatching { TombstoneKind.valueOf(dto.kind) }.getOrNull() ?: return@forEach
            val existing = localTombstones[dto.id]
            if (existing == null || dto.deletedAt > existing.deletedAt) {
                val merged = TombstoneEntity(dto.id, kind, dto.deletedAt)
                noteDao.upsertTombstone(merged)
                localTombstones[dto.id] = merged
            }
        }

        fun tombstonedAfter(id: String, updatedAt: Long): Boolean {
            val t = localTombstones[id] ?: return false
            return t.deletedAt >= updatedAt
        }

        // 2) Notes: last-write-wins by updatedAt, skipping anything tombstoned since.
        data.notes.forEach { dto ->
            if (tombstonedAfter(dto.id, dto.updatedAt)) return@forEach
            val local = noteDao.getNote(dto.id)
            if (local != null && local.updatedAt >= dto.updatedAt) return@forEach
            noteDao.upsert(
                NoteEntity(
                    id = dto.id,
                    title = dto.title,
                    source = NoteSource.valueOf(dto.source),
                    driveFileId = dto.driveFileId,
                    driveFileName = dto.driveFileName,
                    thumbnailPath = dto.thumbnailFile?.let { restoredPaths[it] } ?: local?.thumbnailPath,
                    isFavorite = dto.isFavorite,
                    isTrashed = dto.isTrashed,
                    createdAt = dto.createdAt,
                    updatedAt = dto.updatedAt,
                    labelText = dto.labelText ?: local?.labelText,
                    labelColorArgb = dto.labelColorArgb ?: local?.labelColorArgb
                )
            )
        }

        // 3) Pages: same pattern. Pages have no tombstone of their own (deleting a note tombstones
        // the whole note and cascades locally; see step 5).
        data.pages.forEach { dto ->
            val local = noteDao.getPage(dto.id)
            if (local != null && local.updatedAt >= dto.updatedAt) return@forEach
            noteDao.upsertPage(
                PageEntity(
                    id = dto.id,
                    noteId = dto.noteId,
                    pageIndex = dto.pageIndex,
                    paperType = PaperType.valueOf(dto.paperType),
                    orientation = PageOrientation.valueOf(dto.orientation),
                    flip = PageFlip.valueOf(dto.flip),
                    widthPx = dto.widthPx,
                    heightPx = dto.heightPx,
                    backgroundImagePath = dto.backgroundFile?.let { restoredPaths[it] } ?: local?.backgroundImagePath,
                    tagColorArgb = dto.tagColorArgb,
                    isBookmarked = dto.isBookmarked,
                    updatedAt = dto.updatedAt,
                    layers = dto.layers,
                    activeLayerId = dto.activeLayerId
                )
            )
        }

        // 4) Strokes: last-write-wins, skipping anything erased (tombstoned) since.
        data.strokes.forEach { dto ->
            if (tombstonedAfter(dto.id, dto.updatedAt)) return@forEach
            val local = noteDao.getStrokes(dto.pageId).firstOrNull { it.id == dto.id }
            if (local != null && local.updatedAt >= dto.updatedAt) return@forEach
            noteDao.upsertStroke(
                StrokeEntity(
                    id = dto.id,
                    pageId = dto.pageId,
                    tool = ToolType.valueOf(dto.tool),
                    colorArgb = dto.colorArgb,
                    widthDp = dto.widthDp,
                    points = dto.points,
                    fillColorArgb = dto.fillColorArgb,
                    rotationDegrees = dto.rotationDegrees,
                    layerId = dto.layerId,
                    createdAt = dto.createdAt,
                    updatedAt = dto.updatedAt
                )
            )
        }

        // 5) Stickers: same pattern.
        data.stickers.forEach { dto ->
            if (tombstonedAfter(dto.id, dto.updatedAt)) return@forEach
            val local = noteDao.getSticker(dto.id)
            if (local != null && local.updatedAt >= dto.updatedAt) return@forEach
            noteDao.upsertSticker(
                StickerEntity(
                    id = dto.id,
                    pageId = dto.pageId,
                    emoji = dto.emoji,
                    x = dto.x,
                    y = dto.y,
                    sizePx = dto.sizePx,
                    rotationDegrees = dto.rotationDegrees,
                    layerId = dto.layerId,
                    updatedAt = dto.updatedAt
                )
            )
        }

        // 6) Images, text boxes, audio clips: same last-write-wins pattern.
        data.images.forEach { dto ->
            if (tombstonedAfter(dto.id, dto.updatedAt)) return@forEach
            val local = noteDao.getImage(dto.id)
            if (local != null && local.updatedAt >= dto.updatedAt) return@forEach
            val path = restoredPaths[dto.imageFile] ?: local?.imagePath ?: return@forEach
            noteDao.upsertImage(
                ImageAttachmentEntity(
                    id = dto.id, pageId = dto.pageId, imagePath = path,
                    x = dto.x, y = dto.y, widthPx = dto.widthPx, heightPx = dto.heightPx,
                    rotationDegrees = dto.rotationDegrees, layerId = dto.layerId, updatedAt = dto.updatedAt
                )
            )
        }
        data.textBoxes.forEach { dto ->
            if (tombstonedAfter(dto.id, dto.updatedAt)) return@forEach
            val local = noteDao.getTextBox(dto.id)
            if (local != null && local.updatedAt >= dto.updatedAt) return@forEach
            noteDao.upsertTextBox(
                TextBoxEntity(
                    id = dto.id, pageId = dto.pageId, text = dto.text, x = dto.x, y = dto.y,
                    widthPx = dto.widthPx, heightPx = dto.heightPx, fontSizePx = dto.fontSizePx, colorArgb = dto.colorArgb,
                    isBold = dto.isBold, isItalic = dto.isItalic,
                    fontFamily = runCatching { com.kapi.notes.domain.model.TextFontFamily.valueOf(dto.fontFamily) }.getOrDefault(com.kapi.notes.domain.model.TextFontFamily.DEFAULT),
                    rotationDegrees = dto.rotationDegrees, layerId = dto.layerId, updatedAt = dto.updatedAt
                )
            )
        }
        data.audioClips.forEach { dto ->
            if (tombstonedAfter(dto.id, dto.createdAt)) return@forEach
            if (noteDao.getAllAudioClips().any { it.id == dto.id }) return@forEach
            val path = restoredAudioPaths[dto.audioFile] ?: return@forEach
            noteDao.upsertAudioClip(AudioClipEntity(id = dto.id, noteId = dto.noteId, filePath = path, durationMs = dto.durationMs, createdAt = dto.createdAt))
        }

        // 7) Apply tombstones to local rows that weren't in the incoming lists at all (the
        // sending device omits rows it has deleted, so this is the only place that removes them).
        localTombstones.values.forEach { tombstone ->
            when (tombstone.kind) {
                TombstoneKind.NOTE -> {
                    val local = noteDao.getNote(tombstone.id)
                    if (local != null && local.updatedAt <= tombstone.deletedAt) noteDao.deleteById(local.id)
                }
                TombstoneKind.PAGE -> {
                    val local = noteDao.getPage(tombstone.id)
                    if (local != null && local.updatedAt <= tombstone.deletedAt) noteDao.deletePage(local.id)
                }
                TombstoneKind.STROKE -> {
                    // Strokes are cheap to probe directly since there's no per-id index needed here.
                    noteDao.getAllStrokes().firstOrNull { it.id == tombstone.id }?.let { local ->
                        if (local.updatedAt <= tombstone.deletedAt) noteDao.deleteStroke(local.id)
                    }
                }
                TombstoneKind.STICKER -> {
                    val local = noteDao.getSticker(tombstone.id)
                    if (local != null && local.updatedAt <= tombstone.deletedAt) noteDao.deleteSticker(local.id)
                }
                TombstoneKind.IMAGE -> {
                    val local = noteDao.getImage(tombstone.id)
                    if (local != null && local.updatedAt <= tombstone.deletedAt) noteDao.deleteImage(local.id)
                }
                TombstoneKind.TEXT_BOX -> {
                    val local = noteDao.getTextBox(tombstone.id)
                    if (local != null && local.updatedAt <= tombstone.deletedAt) noteDao.deleteTextBox(local.id)
                }
                TombstoneKind.AUDIO_CLIP -> {
                    noteDao.getAllAudioClips().firstOrNull { it.id == tombstone.id }?.let { local ->
                        if (local.createdAt <= tombstone.deletedAt) noteDao.deleteAudioClip(local.id)
                    }
                }
            }
        }
    }

    suspend fun deleteEverything() = withContext(Dispatchers.IO) {
        database.clearAllTables()
        File(context.filesDir, "pages").deleteRecursively()
        File(context.filesDir, "audio").deleteRecursively()
    }
}
