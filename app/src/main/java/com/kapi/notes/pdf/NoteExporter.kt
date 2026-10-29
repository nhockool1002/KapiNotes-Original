package com.kapi.notes.pdf

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.text.StaticLayout
import android.text.TextPaint
import com.kapi.notes.domain.model.EmojiSticker
import com.kapi.notes.domain.model.ImageAttachment
import com.kapi.notes.domain.model.NotePage
import com.kapi.notes.domain.model.PageFlip
import com.kapi.notes.domain.model.PaperType
import com.kapi.notes.domain.model.Stroke
import com.kapi.notes.domain.model.TextBoxItem
import com.kapi.notes.domain.model.TextFontFamily
import com.kapi.notes.domain.model.ToolType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class PageContent(
    val strokes: List<Stroke> = emptyList(),
    val stickers: List<EmojiSticker> = emptyList(),
    val images: List<ImageAttachment> = emptyList(),
    val textBoxes: List<TextBoxItem> = emptyList()
)

/**
 * Flattens a note's pages (paper background + imported-document background + every stroke,
 * shape, sticker, image and text box) into a single PDF - the "Export" action in the editor's
 * top-right toolbar.
 */
@Singleton
class NoteExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun exportToPdf(
        noteTitle: String,
        pages: List<NotePage>,
        contentByPage: Map<String, PageContent>
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val document = PdfDocument()

            pages.sortedBy { it.pageIndex }.forEach { page ->
                val pageInfo = PdfDocument.PageInfo.Builder(page.widthPx, page.heightPx, page.pageIndex).create()
                val pdfPage = document.startPage(pageInfo)
                renderPage(pdfPage.canvas, page, contentByPage[page.id] ?: PageContent())
                document.finishPage(pdfPage)
            }

            val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val safeTitle = noteTitle.ifBlank { "KapiNote" }.replace(Regex("[^A-Za-z0-9_\\-]"), "_")
            val outFile = File(exportsDir, "${safeTitle}_${UUID.randomUUID().toString().take(6)}.pdf")
            outFile.outputStream().use { document.writeTo(it) }
            document.close()
            outFile
        }
    }

    /** "Xuất ảnh PNG" - renders a single page (same [renderPage] used for PDF pages) to a flat
     * PNG file, for sharing just one page without generating a whole document. */
    suspend fun exportToPng(
        pageTitle: String,
        page: NotePage,
        content: PageContent
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = android.graphics.Bitmap.createBitmap(page.widthPx, page.heightPx, android.graphics.Bitmap.Config.ARGB_8888)
            renderPage(Canvas(bitmap), page, content)
            val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val safeTitle = pageTitle.ifBlank { "KapiNote" }.replace(Regex("[^A-Za-z0-9_\\-]"), "_")
            val outFile = File(exportsDir, "${safeTitle}_${UUID.randomUUID().toString().take(6)}.png")
            outFile.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            outFile
        }
    }

    /** Renders [page] (same [renderPage] used for PDF/PNG export) at a small, fixed size and
     * writes it to a stable per-note path (overwriting any previous thumbnail) - the dashboard's
     * note card thumbnail, kept in sync with the page's actual strokes/images/text instead of
     * always showing a blank placeholder. */
    suspend fun generateThumbnail(
        noteId: String,
        page: NotePage,
        content: PageContent,
        maxWidthPx: Int = 480
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val fullBitmap = android.graphics.Bitmap.createBitmap(page.widthPx, page.heightPx, android.graphics.Bitmap.Config.ARGB_8888)
            renderPage(Canvas(fullBitmap), page, content)

            val scale = (maxWidthPx.toFloat() / page.widthPx).coerceAtMost(1f)
            val outW = (page.widthPx * scale).toInt().coerceAtLeast(1)
            val outH = (page.heightPx * scale).toInt().coerceAtLeast(1)
            val thumbBitmap = android.graphics.Bitmap.createScaledBitmap(fullBitmap, outW, outH, true)
            fullBitmap.recycle()

            val thumbDir = File(context.filesDir, "thumbnails").apply { mkdirs() }
            val outFile = File(thumbDir, "$noteId.png")
            outFile.outputStream().use { thumbBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, it) }
            thumbBitmap.recycle()
            outFile
        }
    }

    private fun renderPage(canvas: Canvas, page: NotePage, content: PageContent) {
        canvas.drawColor(Color.WHITE)

        if (page.flip == PageFlip.HORIZONTAL) {
            canvas.save()
            canvas.scale(-1f, 1f, page.widthPx / 2f, page.heightPx / 2f)
        }

        page.backgroundImagePath?.let { path ->
            BitmapFactory.decodeFile(path)?.let { bmp ->
                val dst = android.graphics.Rect(0, 0, page.widthPx, page.heightPx)
                canvas.drawBitmap(bmp, null, dst, null)
                bmp.recycle()
            }
        } ?: drawPaperPattern(canvas, page)

        content.images.forEach { image -> drawImageAttachment(canvas, image) }
        content.strokes.forEach { stroke -> drawStroke(canvas, stroke) }
        content.stickers.forEach { sticker -> drawSticker(canvas, sticker) }
        content.textBoxes.forEach { textBox -> drawTextBox(canvas, textBox) }

        if (page.flip == PageFlip.HORIZONTAL) {
            canvas.restore()
        }
    }

    private fun drawPaperPattern(canvas: Canvas, page: NotePage) {
        val w = page.widthPx.toFloat()
        val h = page.heightPx.toFloat()
        val linePaint = Paint().apply {
            color = Color.argb(60, 148, 163, 184)
            strokeWidth = 2f
        }
        val boldPaint = Paint().apply {
            color = Color.argb(110, 100, 116, 139)
            strokeWidth = 3f
        }
        val labelPaint = Paint().apply {
            color = Color.argb(140, 100, 116, 139)
            textSize = 24f
            isAntiAlias = true
        }
        when (page.paperType) {
            PaperType.BLANK -> Unit
            PaperType.LINED -> {
                var y = 80f
                while (y < h) {
                    canvas.drawLine(0f, y, w, y, linePaint)
                    y += 80f
                }
            }
            PaperType.GRID -> {
                var x = 0f
                while (x < w) {
                    canvas.drawLine(x, 0f, x, h, linePaint)
                    x += 40f
                }
                var y = 0f
                while (y < h) {
                    canvas.drawLine(0f, y, w, y, linePaint)
                    y += 40f
                }
            }
            PaperType.DOT -> {
                val dotPaint = Paint().apply { color = Color.argb(120, 148, 163, 184) }
                var y = 40f
                while (y < h) {
                    var x = 40f
                    while (x < w) {
                        canvas.drawCircle(x, y, 3f, dotPaint)
                        x += 40f
                    }
                    y += 40f
                }
            }
            PaperType.CORNELL -> {
                val cueColumnX = w * 0.28f
                val summaryY = h * 0.85f
                canvas.drawLine(cueColumnX, 0f, cueColumnX, summaryY, boldPaint)
                canvas.drawLine(0f, summaryY, w, summaryY, boldPaint)
                var y = 80f
                while (y < summaryY) {
                    canvas.drawLine(0f, y, w, y, linePaint)
                    y += 80f
                }
                canvas.drawText("Từ khóa", 16f, 32f, labelPaint)
                canvas.drawText("Ghi chú", cueColumnX + 16f, 32f, labelPaint)
                canvas.drawText("Tóm tắt", 16f, summaryY + 32f, labelPaint)
            }
            PaperType.CHECKLIST -> {
                var y = 80f
                val boxSize = 28f
                while (y < h) {
                    canvas.drawRect(40f, y - boxSize, 40f + boxSize, y, boldPaint)
                    canvas.drawLine(40f + boxSize + 20f, y, w - 20f, y, linePaint)
                    y += 70f
                }
            }
            PaperType.TIMETABLE -> {
                val days = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
                val headerH = 60f
                val rowH = (h - headerH) / 10f
                val colW = (w - 60f) / days.size
                canvas.drawLine(60f, headerH, w, headerH, boldPaint)
                canvas.drawLine(60f, 0f, 60f, h, boldPaint)
                days.forEachIndexed { index, day ->
                    val x = 60f + colW * index
                    canvas.drawLine(x, 0f, x, h, linePaint)
                    canvas.drawText(day, x + colW / 2f - 12f, 36f, labelPaint)
                }
                for (row in 0..10) {
                    val y = headerH + rowH * row
                    canvas.drawLine(60f, y, w, y, linePaint)
                    if (row < 10) canvas.drawText("${row + 7}h", 8f, y + rowH / 2f + 8f, labelPaint)
                }
            }
            PaperType.CHINESE_CHARACTER_GRID -> {
                val cell = 160f
                val borderPaint = Paint().apply { color = Color.argb(140, 100, 116, 139); strokeWidth = 2f; style = Paint.Style.STROKE }
                val crossPaint = Paint().apply {
                    color = Color.argb(90, 148, 163, 184)
                    strokeWidth = 2f
                    pathEffect = android.graphics.DashPathEffect(floatArrayOf(12f, 12f), 0f)
                }
                var y = 0f
                while (y < h) {
                    var x = 0f
                    while (x < w) {
                        canvas.drawRect(x, y, x + cell, y + cell, borderPaint)
                        canvas.drawLine(x + cell / 2f, y, x + cell / 2f, y + cell, crossPaint)
                        canvas.drawLine(x, y + cell / 2f, x + cell, y + cell / 2f, crossPaint)
                        x += cell
                    }
                    y += cell
                }
            }
            PaperType.GENKOU_YOUSHI -> {
                val cell = 80f
                val borderPaint = Paint().apply { color = Color.argb(140, 100, 116, 139); strokeWidth = 2f; style = Paint.Style.STROKE }
                var y = 0f
                while (y < h) {
                    var x = 0f
                    while (x < w) {
                        canvas.drawRect(x, y, x + cell, y + cell, borderPaint)
                        x += cell
                    }
                    y += cell
                }
            }
            PaperType.MUSIC_STAFF -> {
                val lineGap = 20f
                val staffGap = 100f
                var staffTop = 60f
                while (staffTop + lineGap * 4 < h) {
                    repeat(5) { i ->
                        val y = staffTop + lineGap * i
                        canvas.drawLine(40f, y, w - 40f, y, boldPaint)
                    }
                    staffTop += lineGap * 4 + staffGap
                }
            }
            PaperType.TABLE -> {
                val cols = 4
                val rowH = 120f
                val colW = w / cols
                var y = 0f
                while (y <= h) {
                    canvas.drawLine(0f, y, w, y, boldPaint)
                    y += rowH
                }
                for (c in 0..cols) {
                    val x = colW * c
                    canvas.drawLine(x, 0f, x, h, boldPaint)
                }
            }
            PaperType.ISOMETRIC_DOT -> {
                val dotPaint = Paint().apply { color = Color.argb(120, 148, 163, 184) }
                val spacing = 48f
                val rowH = spacing * 0.866f
                var row = 0
                var y = 0f
                while (y < h) {
                    val offset = if (row % 2 == 1) spacing / 2f else 0f
                    var x = offset
                    while (x < w) {
                        canvas.drawCircle(x, y, 3f, dotPaint)
                        x += spacing
                    }
                    y += rowH
                    row++
                }
            }
            PaperType.HANDWRITING_GUIDE -> {
                val dashPaint = Paint().apply { color = Color.argb(60, 148, 163, 184); strokeWidth = 2f; pathEffect = android.graphics.DashPathEffect(floatArrayOf(12f, 12f), 0f) }
                val groupH = 140f
                var top = 60f
                while (top + groupH < h) {
                    canvas.drawLine(0f, top, w, top, linePaint)
                    canvas.drawLine(0f, top + groupH * 0.4f, w, top + groupH * 0.4f, dashPaint)
                    canvas.drawLine(0f, top + groupH * 0.75f, w, top + groupH * 0.75f, boldPaint)
                    canvas.drawLine(0f, top + groupH, w, top + groupH, linePaint)
                    top += groupH + 40f
                }
            }
        }
    }

    private fun drawStroke(canvas: Canvas, stroke: Stroke) {
        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            color = stroke.colorArgb.toInt()
        }
        when (stroke.tool) {
            ToolType.PEN -> {
                if (stroke.points.size < 2) return
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeWidth = stroke.widthDp * 2.5f
            }
            ToolType.PENCIL -> {
                if (stroke.points.size < 2) return
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeWidth = stroke.widthDp * 1.8f
                paint.alpha = 215
            }
            ToolType.BRUSH -> {
                if (stroke.points.size < 2) return
                drawVariableWidthStroke(canvas, stroke.points, stroke.widthDp * 4f, stroke.colorArgb)
                return
            }
            ToolType.HIGHLIGHTER -> {
                if (stroke.points.size < 2) return
                paint.strokeCap = Paint.Cap.SQUARE
                paint.strokeWidth = (stroke.widthDp * 10f).coerceAtLeast(45f)
                paint.alpha = 90
            }
            ToolType.LINE -> {
                if (stroke.points.size < 2) return
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeWidth = stroke.widthDp * 2.5f
                val p0 = stroke.points[0]
                val p1 = stroke.points[1]
                canvas.drawLine(p0.x, p0.y, p1.x, p1.y, paint)
                return
            }
            ToolType.RECTANGLE -> {
                if (stroke.points.size < 2) return
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeWidth = stroke.widthDp * 2.5f
                val p0 = stroke.points[0]
                val p1 = stroke.points[1]
                val left = minOf(p0.x, p1.x); val top = minOf(p0.y, p1.y)
                val right = maxOf(p0.x, p1.x); val bottom = maxOf(p0.y, p1.y)
                canvas.save()
                canvas.rotate(stroke.rotationDegrees, (left + right) / 2f, (top + bottom) / 2f)
                stroke.fillColorArgb?.let { fill ->
                    canvas.drawRect(left, top, right, bottom, fillPaint(fill))
                }
                canvas.drawRect(left, top, right, bottom, paint)
                canvas.restore()
                return
            }
            ToolType.CIRCLE -> {
                if (stroke.points.size < 2) return
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeWidth = stroke.widthDp * 2.5f
                val p0 = stroke.points[0]
                val p1 = stroke.points[1]
                val oval = android.graphics.RectF(
                    minOf(p0.x, p1.x), minOf(p0.y, p1.y),
                    maxOf(p0.x, p1.x), maxOf(p0.y, p1.y)
                )
                canvas.save()
                canvas.rotate(stroke.rotationDegrees, oval.centerX(), oval.centerY())
                stroke.fillColorArgb?.let { fill -> canvas.drawOval(oval, fillPaint(fill)) }
                canvas.drawOval(oval, paint)
                canvas.restore()
                return
            }
            ToolType.TRIANGLE -> {
                if (stroke.points.size < 2) return
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeWidth = stroke.widthDp * 2.5f
                val p0 = stroke.points[0]
                val p1 = stroke.points[1]
                val left = minOf(p0.x, p1.x); val right = maxOf(p0.x, p1.x)
                val top = minOf(p0.y, p1.y); val bottom = maxOf(p0.y, p1.y)
                val trianglePath = android.graphics.Path().apply {
                    moveTo((left + right) / 2f, top)
                    lineTo(right, bottom)
                    lineTo(left, bottom)
                    close()
                }
                canvas.save()
                canvas.rotate(stroke.rotationDegrees, (left + right) / 2f, (top + bottom) / 2f)
                stroke.fillColorArgb?.let { fill -> canvas.drawPath(trianglePath, fillPaint(fill)) }
                canvas.drawPath(trianglePath, paint)
                canvas.restore()
                return
            }
            ToolType.ARROW -> {
                if (stroke.points.size < 2) return
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeWidth = stroke.widthDp * 2.5f
                val p0 = stroke.points[0]
                val p1 = stroke.points[1]
                canvas.drawLine(p0.x, p0.y, p1.x, p1.y, paint)
                val angle = kotlin.math.atan2((p1.y - p0.y).toDouble(), (p1.x - p0.x).toDouble())
                val headLen = (paint.strokeWidth * 5f).coerceIn(18f, 60f)
                val leftAngle = angle + Math.toRadians(150.0)
                val rightAngle = angle - Math.toRadians(150.0)
                val headPath = android.graphics.Path().apply {
                    moveTo(p1.x, p1.y)
                    lineTo(p1.x + (headLen * kotlin.math.cos(leftAngle)).toFloat(), p1.y + (headLen * kotlin.math.sin(leftAngle)).toFloat())
                    lineTo(p1.x + (headLen * kotlin.math.cos(rightAngle)).toFloat(), p1.y + (headLen * kotlin.math.sin(rightAngle)).toFloat())
                    close()
                }
                val headPaint = Paint(paint).apply { style = Paint.Style.FILL }
                canvas.drawPath(headPath, headPaint)
                return
            }
            ToolType.ERASER -> return
        }
        val path = android.graphics.Path()
        path.moveTo(stroke.points.first().x, stroke.points.first().y)
        stroke.points.drop(1).forEach { path.lineTo(it.x, it.y) }
        canvas.drawPath(path, paint)
    }

    /** PDF/PNG mirror of the Compose canvas's brush ribbon - see [com.kapi.notes.ui.editor] for
     * the on-screen version; same per-point pressure + local-speed taper math. */
    private fun drawVariableWidthStroke(canvas: Canvas, points: List<com.kapi.notes.domain.model.StrokePoint>, baseWidthPx: Float, colorArgb: Long) {
        val fill = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL; color = colorArgb.toInt() }
        if (points.size == 1) {
            val w = baseWidthPx * (0.9f + 2.2f * points[0].pressure.coerceIn(0.3f, 1f))
            canvas.drawCircle(points[0].x, points[0].y, w / 2f, fill)
            return
        }
        val widths = FloatArray(points.size)
        for (i in points.indices) {
            val pressure = points[i].pressure.coerceIn(0.3f, 1f)
            val speed = if (i == 0) 0f else kotlin.math.hypot((points[i].x - points[i - 1].x).toDouble(), (points[i].y - points[i - 1].y).toDouble()).toFloat()
            val speedFactor = 1.15f - (speed / 40f).coerceIn(0f, 0.55f)
            widths[i] = baseWidthPx * (0.9f + 2.2f * pressure) * speedFactor
        }
        val left = ArrayList<FloatArray>(points.size)
        val right = ArrayList<FloatArray>(points.size)
        for (i in points.indices) {
            val p = points[i]
            val dx: Float
            val dy: Float
            when (i) {
                0 -> { dx = points[1].x - p.x; dy = points[1].y - p.y }
                points.size - 1 -> { dx = p.x - points[i - 1].x; dy = p.y - points[i - 1].y }
                else -> { dx = points[i + 1].x - points[i - 1].x; dy = points[i + 1].y - points[i - 1].y }
            }
            val len = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat().takeIf { it > 0.0001f } ?: 1f
            val nx = -dy / len
            val ny = dx / len
            val half = widths[i] / 2f
            left += floatArrayOf(p.x + nx * half, p.y + ny * half)
            right += floatArrayOf(p.x - nx * half, p.y - ny * half)
        }
        val ribbon = android.graphics.Path().apply {
            moveTo(left[0][0], left[0][1])
            for (i in 1 until left.size) lineTo(left[i][0], left[i][1])
            for (i in right.indices.reversed()) lineTo(right[i][0], right[i][1])
            close()
        }
        canvas.drawPath(ribbon, fill)
        canvas.drawCircle(points.first().x, points.first().y, widths.first() / 2f, fill)
        canvas.drawCircle(points.last().x, points.last().y, widths.last() / 2f, fill)
    }

    private fun fillPaint(colorArgb: Long): Paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = colorArgb.toInt()
        alpha = (255 * 0.35f).toInt()
    }

    private fun drawSticker(canvas: Canvas, sticker: EmojiSticker) {
        val paint = Paint().apply {
            isAntiAlias = true
            textSize = sticker.sizePx
            textAlign = Paint.Align.CENTER
        }
        canvas.save()
        if (sticker.rotationDegrees != 0f) canvas.rotate(sticker.rotationDegrees, sticker.x, sticker.y)
        canvas.drawText(sticker.emoji, sticker.x, sticker.y + sticker.sizePx * 0.35f, paint)
        canvas.restore()
    }

    private fun drawImageAttachment(canvas: Canvas, image: ImageAttachment) {
        val bmp = BitmapFactory.decodeFile(image.imagePath) ?: return
        canvas.save()
        if (image.rotationDegrees != 0f) canvas.rotate(image.rotationDegrees, image.x, image.y)
        val dst = android.graphics.RectF(
            image.x - image.widthPx / 2f, image.y - image.heightPx / 2f,
            image.x + image.widthPx / 2f, image.y + image.heightPx / 2f
        )
        canvas.drawBitmap(bmp, null, dst, null)
        canvas.restore()
        bmp.recycle()
    }

    private fun typefaceFor(fontFamily: TextFontFamily, isBold: Boolean, isItalic: Boolean): android.graphics.Typeface {
        val base = when (fontFamily) {
            TextFontFamily.DEFAULT -> android.graphics.Typeface.SANS_SERIF
            TextFontFamily.SERIF -> android.graphics.Typeface.SERIF
            TextFontFamily.MONOSPACE -> android.graphics.Typeface.MONOSPACE
            TextFontFamily.HANDWRITING -> android.graphics.Typeface.SANS_SERIF
        }
        val style = when {
            isBold && isItalic -> android.graphics.Typeface.BOLD_ITALIC
            isBold -> android.graphics.Typeface.BOLD
            isItalic -> android.graphics.Typeface.ITALIC
            else -> android.graphics.Typeface.NORMAL
        }
        return android.graphics.Typeface.create(base, style)
    }

    private fun drawTextBox(canvas: Canvas, textBox: TextBoxItem) {
        val paint = TextPaint().apply {
            isAntiAlias = true
            textSize = textBox.fontSizePx
            color = textBox.colorArgb.toInt()
            typeface = typefaceFor(textBox.fontFamily, textBox.isBold, textBox.isItalic)
        }
        val width = textBox.widthPx.toInt().coerceAtLeast(50)
        val layout = StaticLayout.Builder
            .obtain(textBox.text, 0, textBox.text.length, paint, width)
            .build()
        val h = if (textBox.heightPx > 0f) textBox.heightPx else textBox.fontSizePx * 1.4f
        canvas.save()
        if (textBox.rotationDegrees != 0f) {
            canvas.rotate(textBox.rotationDegrees, textBox.x + textBox.widthPx / 2f, textBox.y + h / 2f)
        }
        canvas.translate(textBox.x, textBox.y)
        layout.draw(canvas)
        canvas.restore()
    }
}
