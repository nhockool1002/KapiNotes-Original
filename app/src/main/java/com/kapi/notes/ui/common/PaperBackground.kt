package com.kapi.notes.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.kapi.notes.domain.model.PaperType

/** Draws the blank/lined/grid/dot pattern behind a page - the Compose equivalent of the
 * wireframe's `.paper-lined` / `.paper-grid` / `.paper-dot` CSS classes. */
@Composable
fun PaperBackground(paperType: PaperType, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize().background(Color.White)) {
        val lineColor = Color(0xFFE2E8F0)
        val dotColor = Color(0xFFCBD5E1)
        when (paperType) {
            PaperType.BLANK -> Unit
            PaperType.LINED -> {
                var y = 40f
                while (y < size.height) {
                    drawLine(lineColor, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = 1f)
                    y += 40f
                }
            }
            PaperType.GRID -> {
                var x = 0f
                while (x < size.width) {
                    drawLine(lineColor, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), strokeWidth = 1f)
                    x += 20f
                }
                var y = 0f
                while (y < size.height) {
                    drawLine(lineColor, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = 1f)
                    y += 20f
                }
            }
            PaperType.DOT -> {
                var y = 20f
                while (y < size.height) {
                    var x = 20f
                    while (x < size.width) {
                        drawCircle(dotColor, radius = 1.5f, center = androidx.compose.ui.geometry.Offset(x, y))
                        x += 20f
                    }
                    y += 20f
                }
            }
            PaperType.CORNELL -> {
                val boldColor = Color(0xFF94A3B8)
                val cueColumnX = size.width * 0.28f
                val summaryY = size.height * 0.85f
                drawLine(boldColor, Offset(cueColumnX, 0f), Offset(cueColumnX, summaryY), strokeWidth = 1.5f)
                drawLine(boldColor, Offset(0f, summaryY), Offset(size.width, summaryY), strokeWidth = 1.5f)
                var y = 40f
                while (y < summaryY) {
                    drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                    y += 40f
                }
                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply { color = boldColor.toArgb(); textSize = 14f; isAntiAlias = true }
                    drawText("Từ khóa", 8f, 18f, paint)
                    drawText("Ghi chú", cueColumnX + 8f, 18f, paint)
                    drawText("Tóm tắt", 8f, summaryY + 18f, paint)
                }
            }
            PaperType.CHECKLIST -> {
                val boldColor = Color(0xFF94A3B8)
                var y = 40f
                val boxSize = 14f
                while (y < size.height) {
                    drawRect(boldColor, topLeft = Offset(20f, y - boxSize), size = androidx.compose.ui.geometry.Size(boxSize, boxSize), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f))
                    drawLine(lineColor, Offset(20f + boxSize + 10f, y), Offset(size.width - 10f, y), strokeWidth = 1f)
                    y += 35f
                }
            }
            PaperType.TIMETABLE -> {
                val boldColor = Color(0xFF94A3B8)
                val days = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
                val headerH = 30f
                val rowH = (size.height - headerH) / 10f
                val colW = (size.width - 30f) / days.size
                drawLine(boldColor, Offset(30f, headerH), Offset(size.width, headerH), strokeWidth = 1.5f)
                drawLine(boldColor, Offset(30f, 0f), Offset(30f, size.height), strokeWidth = 1.5f)
                val paint = android.graphics.Paint().apply { color = boldColor.toArgb(); textSize = 12f; isAntiAlias = true }
                days.forEachIndexed { index, day ->
                    val x = 30f + colW * index
                    drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                    drawContext.canvas.nativeCanvas.drawText(day, x + colW / 2f - 8f, 20f, paint)
                }
                for (row in 0..10) {
                    val y = headerH + rowH * row
                    drawLine(lineColor, Offset(30f, y), Offset(size.width, y), strokeWidth = 1f)
                    if (row < 10) {
                        drawContext.canvas.nativeCanvas.drawText("${row + 7}h", 2f, y + rowH / 2f + 4f, paint)
                    }
                }
            }
            PaperType.CHINESE_CHARACTER_GRID -> {
                // 田字格: large squares with a dashed cross quartering each one, for practicing a
                // character's stroke proportions against the box.
                val cell = 80f
                val borderColor = Color(0xFF94A3B8)
                val crossColor = Color(0xFFCBD5E1)
                val dash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                var y = 0f
                while (y < size.height) {
                    var x = 0f
                    while (x < size.width) {
                        drawRect(
                            borderColor,
                            topLeft = Offset(x, y),
                            size = androidx.compose.ui.geometry.Size(cell, cell),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
                        )
                        drawLine(crossColor, Offset(x + cell / 2f, y), Offset(x + cell / 2f, y + cell), strokeWidth = 1f, pathEffect = dash)
                        drawLine(crossColor, Offset(x, y + cell / 2f), Offset(x + cell, y + cell / 2f), strokeWidth = 1f, pathEffect = dash)
                        x += cell
                    }
                    y += cell
                }
            }
        }
    }
}
