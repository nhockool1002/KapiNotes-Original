package com.kapi.notes.ui.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

private const val HANDLE_TOUCH_RADIUS_PX = 48f

/**
 * "Cắt ảnh": lets the user drag a crop rectangle over the full-resolution source image, then
 * writes the cropped region out as a new PNG file. [onCropped] receives the new file's path and
 * pixel dimensions; the caller (the editor) decides how to resize the on-page box to match.
 */
@Composable
fun ImageCropDialog(
    imagePath: String,
    onDismiss: () -> Unit,
    onCropped: (path: String, widthPx: Int, heightPx: Int) -> Unit
) {
    val bitmap = remember(imagePath) { BitmapFactory.decodeFile(imagePath) }
    if (bitmap == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Cắt ảnh") },
            text = { Text("Không đọc được ảnh này.") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Đóng") } }
        )
        return
    }

    // Normalized (0f..1f) crop rect in the bitmap's own coordinate space, starting inset so the
    // handles are immediately visible/draggable without the user hunting for the edge.
    var cropRect by remember(imagePath) { mutableStateOf(Rect(0.1f, 0.1f, 0.9f, 0.9f)) }
    var boxSizePx by remember { mutableStateOf(IntSize.Zero) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cắt ảnh") },
        text = {
            Column {
                Text(
                    "Kéo các góc để chọn vùng cần giữ lại.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                        .clipToBounds()
                        .onSizeChanged { boxSizePx = it }
                ) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.FillWidth
                    )
                    Canvas(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        if (boxSizePx.width == 0 || boxSizePx.height == 0) return@detectDragGestures
                                        val dxN = dragAmount.x / boxSizePx.width
                                        val dyN = dragAmount.y / boxSizePx.height
                                        val startPoint = change.position - dragAmount
                                        val r = cropRect
                                        val corners = listOf(
                                            Offset(r.left, r.top), Offset(r.right, r.top),
                                            Offset(r.left, r.bottom), Offset(r.right, r.bottom)
                                        )
                                        val touchPx = Offset(startPoint.x, startPoint.y)
                                        val nearest = corners.minByOrNull {
                                            val cx = it.x * boxSizePx.width
                                            val cy = it.y * boxSizePx.height
                                            (cx - touchPx.x) * (cx - touchPx.x) + (cy - touchPx.y) * (cy - touchPx.y)
                                        }
                                        val nearestDistPx = nearest?.let {
                                            val cx = it.x * boxSizePx.width
                                            val cy = it.y * boxSizePx.height
                                            kotlin.math.hypot((cx - touchPx.x).toDouble(), (cy - touchPx.y).toDouble())
                                        } ?: Double.MAX_VALUE

                                        cropRect = when {
                                            nearestDistPx > HANDLE_TOUCH_RADIUS_PX -> {
                                                // Not near a corner: drag the whole rect.
                                                val w = r.width
                                                val h = r.height
                                                val newLeft = (r.left + dxN).coerceIn(0f, 1f - w)
                                                val newTop = (r.top + dyN).coerceIn(0f, 1f - h)
                                                Rect(newLeft, newTop, newLeft + w, newTop + h)
                                            }
                                            nearest == Offset(r.left, r.top) ->
                                                Rect(min(r.left + dxN, r.right - 0.05f).coerceIn(0f, 1f), min(r.top + dyN, r.bottom - 0.05f).coerceIn(0f, 1f), r.right, r.bottom)
                                            nearest == Offset(r.right, r.top) ->
                                                Rect(r.left, min(r.top + dyN, r.bottom - 0.05f).coerceIn(0f, 1f), max(r.right + dxN, r.left + 0.05f).coerceIn(0f, 1f), r.bottom)
                                            nearest == Offset(r.left, r.bottom) ->
                                                Rect(min(r.left + dxN, r.right - 0.05f).coerceIn(0f, 1f), r.top, r.right, max(r.bottom + dyN, r.top + 0.05f).coerceIn(0f, 1f))
                                            else ->
                                                Rect(r.left, r.top, max(r.right + dxN, r.left + 0.05f).coerceIn(0f, 1f), max(r.bottom + dyN, r.top + 0.05f).coerceIn(0f, 1f))
                                        }
                                    }
                                )
                            }
                    ) {
                        val w = size.width
                        val h = size.height
                        val rectPx = Rect(cropRect.left * w, cropRect.top * h, cropRect.right * w, cropRect.bottom * h)
                        val scrim = Color.Black.copy(alpha = 0.5f)
                        drawRect(scrim, topLeft = Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(w, rectPx.top))
                        drawRect(scrim, topLeft = Offset(0f, rectPx.bottom), size = androidx.compose.ui.geometry.Size(w, h - rectPx.bottom))
                        drawRect(scrim, topLeft = Offset(0f, rectPx.top), size = androidx.compose.ui.geometry.Size(rectPx.left, rectPx.height))
                        drawRect(scrim, topLeft = Offset(rectPx.right, rectPx.top), size = androidx.compose.ui.geometry.Size(w - rectPx.right, rectPx.height))
                        drawRect(
                            color = Color.White,
                            topLeft = rectPx.topLeft,
                            size = androidx.compose.ui.geometry.Size(rectPx.width, rectPx.height),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                        )
                        listOf(rectPx.topLeft, Offset(rectPx.right, rectPx.top), Offset(rectPx.left, rectPx.bottom), rectPx.bottomRight).forEach { corner ->
                            drawCircle(Color.White, radius = 12f, center = corner)
                            drawCircle(MaterialColorPrimary, radius = 8f, center = corner)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val px = (cropRect.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
                val py = (cropRect.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
                val pw = ((cropRect.width * bitmap.width).toInt()).coerceIn(1, bitmap.width - px)
                val ph = ((cropRect.height * bitmap.height).toInt()).coerceIn(1, bitmap.height - py)
                val cropped = Bitmap.createBitmap(bitmap, px, py, pw, ph)
                val outFile = File(File(imagePath).parentFile, "crop_${UUID.randomUUID().toString().take(8)}.png")
                outFile.outputStream().use { out -> cropped.compress(Bitmap.CompressFormat.PNG, 100, out) }
                onCropped(outFile.absolutePath, pw, ph)
            }) { Text("Áp dụng") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } }
    )
}

private val MaterialColorPrimary = Color(0xFF14B8A6)
