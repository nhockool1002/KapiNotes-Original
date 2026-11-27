package com.kapi.notes.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class RenderedPage(val filePath: String, val widthPx: Int, val heightPx: Int)

/** Progress through a multi-step import (rendering a PDF's pages, or writing out a long text
 * document's pages) - [current] and [total] are both 1-based/counting, e.g. (3, 10). */
data class ImportProgress(val current: Int, val total: Int)

/**
 * Turns a downloaded Drive document (PDF or image) into one PNG per page, stored under
 * filesDir/pages/. Those PNGs become each [com.kapi.notes.domain.model.NotePage]'s
 * backgroundImagePath that the editor draws strokes on top of.
 */
@Singleton
class DocumentPageRenderer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val pagesDir: File by lazy { File(context.filesDir, "pages").apply { mkdirs() } }

    /** Render factor applied to the PDF's own point size for a crisp result on a tablet screen. */
    private val renderScale = 2.0f

    /** [onPageRendered] is called after each page finishes (1-based index, total page count) so a
     * caller can drive a progress bar - a multi-page PDF used to render with zero feedback beyond
     * an indefinite spinner, which looked indistinguishable from a hang on a large file. Pages are
     * written out as JPEG instead of lossless PNG: these are background reference images the user
     * draws over, not the source of truth, and PNG's compression was the single biggest cost for a
     * big scanned PDF - JPEG at this quality is visually indistinguishable here but encodes several
     * times faster and produces much smaller files, directly cutting import time. */
    suspend fun renderPdf(
        sourceFile: File,
        noteId: String,
        onPageRendered: (rendered: Int, total: Int) -> Unit = { _, _ -> }
    ): Result<List<RenderedPage>> =
        withContext(Dispatchers.IO) {
            runCatching {
                ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    PdfRenderer(fd).use { renderer ->
                        val total = renderer.pageCount
                        (0 until total).map { index ->
                            renderer.openPage(index).use { page ->
                                val width = (page.width * renderScale).toInt()
                                val height = (page.height * renderScale).toInt()
                                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                bitmap.eraseColor(android.graphics.Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                val outFile = File(pagesDir, "${noteId}_$index.jpg")
                                outFile.outputStream().use { out ->
                                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                                }
                                bitmap.recycle()
                                onPageRendered(index + 1, total)
                                RenderedPage(outFile.absolutePath, width, height)
                            }
                        }
                    }
                }
            }
        }

    suspend fun renderImage(sourceFile: File, noteId: String): Result<RenderedPage> =
        withContext(Dispatchers.IO) {
            runCatching {
                val bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath)
                    ?: error("Không đọc được ảnh")
                val outFile = File(pagesDir, "${noteId}_0.jpg")
                outFile.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                val result = RenderedPage(outFile.absolutePath, bitmap.width, bitmap.height)
                bitmap.recycle()
                result
            }
        }

    fun newTempSourceFile(extension: String): File =
        File(context.cacheDir, "drive_import_${UUID.randomUUID()}.$extension")
}
