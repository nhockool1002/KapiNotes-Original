package com.kapi.notes.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import android.graphics.BitmapFactory
import android.text.StaticLayout
import android.text.TextPaint
import com.kapi.notes.domain.model.EmojiSticker
import com.kapi.notes.domain.model.ImageAttachment
import com.kapi.notes.domain.model.NotePage
import com.kapi.notes.domain.model.PageFlip
import com.kapi.notes.domain.model.Stroke
import com.kapi.notes.domain.model.StrokePoint
import com.kapi.notes.domain.model.TextBoxItem
import com.kapi.notes.domain.model.TextFontFamily
import com.kapi.notes.domain.model.ToolType
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max

private const val TAP_SLOP_PX = 10f
private const val HANDLE_HIT_RADIUS_PX = 36f

private enum class TransformMode { MOVE, RESIZE, ROTATE }

/** Palm rejection: while a stylus pointer is pressed, any simultaneous finger/palm (Touch-type)
 * pointer doesn't count as "a second pointer" for gesture purposes - it is simply invisible to
 * the drawing loop below, so it can neither start a stray stroke nor get treated as the second
 * finger of a pinch/pan gesture. A pure finger-drawing session (no stylus involved at all) is
 * completely unaffected: this only ever filters anything once a Stylus pointer is actually down,
 * which is the one reliable signal that "this is a pen in hand, so the other touch is a palm". */
internal fun effectivePressed(changes: List<PointerInputChange>, palmRejectionEnabled: Boolean): List<PointerInputChange> {
    val pressed = changes.filter { it.pressed }
    if (!palmRejectionEnabled) return pressed
    val stylusOnly = pressed.filter { it.type == PointerType.Stylus }
    return stylusOnly.ifEmpty { pressed }
}

/**
 * The page workspace from the wireframe (`#paperContainer` + `#drawingCanvas`): paper background
 * (or imported document image) at the bottom, every finished stroke above it, and the
 * in-progress gesture drawn live on top. Pointer input reads stylus pressure directly from
 * [androidx.compose.ui.input.pointer.PointerInputChange.pressure] so a real pen on the Xiaomi
 * Pad Pro produces pressure-sensitive strokes; a finger/mouse falls back to a constant 1f.
 *
 * The Lasso tool is a small state machine on top of the same pointer loop: a drag that starts
 * outside the current selection's bounding box draws a new selection shape - a freeform loop, or
 * (when [lassoRectMode] is on) an axis-aligned rectangle from the drag's two corners - closed on
 * release into [onLassoFinished]; a drag that starts *inside* the bounding box instead drags
 * ("cuts") the selected strokes to a new spot, committed on release via [onSelectionMoved].
 *
 * The Image/Text tools are a second state machine, each with a resize handle (bottom-right
 * corner) and a rotate handle (top-right corner): a drag starting on one of those handles
 * resizes/rotates the item; starting elsewhere inside it moves it; a short tap deletes it; a tap
 * on empty canvas places a new one.
 */
@Composable
fun DrawingCanvas(
    page: NotePage,
    strokes: List<Stroke>,
    stickers: List<EmojiSticker>,
    images: List<ImageAttachment> = emptyList(),
    textBoxes: List<TextBoxItem> = emptyList(),
    tool: EditorTool,
    colorArgb: Long,
    strokeWidthDp: Float,
    isShapeFillEnabled: Boolean = false,
    selectedStrokeIds: Set<String>,
    lassoRectMode: Boolean,
    rulerMode: Boolean = false,
    compassMode: Boolean = false,
    palmRejectionEnabled: Boolean = true,
    backgroundBitmap: ImageBitmap?,
    onStrokeFinished: (List<StrokePoint>) -> Unit,
    onEraseAt: (Offset) -> Unit,
    onEraseFinished: () -> Unit,
    onLassoFinished: (List<Offset>) -> Unit,
    onSelectionMoved: (Float, Float) -> Unit,
    onSelectionRotated: (Float) -> Unit = {},
    onEmptyTapForEmoji: (Offset) -> Unit,
    onStickerTapped: (String) -> Unit,
    onStickerDragged: (String, Float, Float) -> Unit,
    onStickerTransformed: (String, Float) -> Unit = { _, _ -> },
    onEmptyTapForImage: (Offset) -> Unit = {},
    onImageTapped: (String) -> Unit = {},
    onImageDragged: (String, Float, Float) -> Unit = { _, _, _ -> },
    onImageTransformed: (String, Float, Float, Float) -> Unit = { _, _, _, _ -> },
    onEmptyTapForText: (Offset) -> Unit = {},
    onTextBoxTapped: (String) -> Unit = {},
    onTextBoxDragged: (String, Float, Float) -> Unit = { _, _, _ -> },
    onTextBoxTransformed: (String, Float, Float, Float) -> Unit = { _, _, _, _ -> },
    onLaserPoint: (Offset?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var inProgressPoints by remember(page.id) { mutableStateOf(listOf<StrokePoint>()) }
    var lassoPoints by remember(page.id) { mutableStateOf(listOf<Offset>()) }
    var isMovingSelection by remember(page.id) { mutableStateOf(false) }
    var dragDelta by remember(page.id) { mutableStateOf(Offset.Zero) }
    var isRotatingSelection by remember(page.id) { mutableStateOf(false) }
    var selectionRotateCenter by remember(page.id) { mutableStateOf(Offset.Zero) }
    var selectionRotateStartAngle by remember(page.id) { mutableStateOf(0f) }
    var selectionRotationDegrees by remember(page.id) { mutableStateOf(0f) }
    var draggingStickerId by remember(page.id) { mutableStateOf<String?>(null) }
    var stickerDragDelta by remember(page.id) { mutableStateOf(Offset.Zero) }
    var stickerTransformMode by remember(page.id) { mutableStateOf(TransformMode.MOVE) }
    var stickerLiveRotation by remember(page.id) { mutableStateOf(0f) }

    var draggingImageId by remember(page.id) { mutableStateOf<String?>(null) }
    var imageDragDelta by remember(page.id) { mutableStateOf(Offset.Zero) }
    var imageTransformMode by remember(page.id) { mutableStateOf(TransformMode.MOVE) }
    var imageLiveWidth by remember(page.id) { mutableStateOf(0f) }
    var imageLiveHeight by remember(page.id) { mutableStateOf(0f) }
    var imageLiveRotation by remember(page.id) { mutableStateOf(0f) }

    var draggingTextBoxId by remember(page.id) { mutableStateOf<String?>(null) }
    var textBoxDragDelta by remember(page.id) { mutableStateOf(Offset.Zero) }
    var textBoxTransformMode by remember(page.id) { mutableStateOf(TransformMode.MOVE) }
    var textLiveWidth by remember(page.id) { mutableStateOf(0f) }
    var textLiveHeight by remember(page.id) { mutableStateOf(0f) }
    var textLiveRotation by remember(page.id) { mutableStateOf(0f) }

    val density = LocalDensity.current

    val scaleX = if (page.flip == PageFlip.HORIZONTAL) -1f else 1f

    fun resizeHandleOf(cx: Float, cy: Float, w: Float, h: Float, rot: Float): Offset =
        rotateAround(Offset(cx + w / 2f, cy + h / 2f), Offset(cx, cy), rot)

    fun rotateHandleOf(cx: Float, cy: Float, w: Float, h: Float, rot: Float): Offset =
        rotateAround(Offset(cx + w / 2f, cy - h / 2f - 28f), Offset(cx, cy), rot)

    // Rotate handle for a Lasso selection: a small floating grip above the dashed bounding box
    // (which itself is drawn 12px larger than the strokes' bounds), so shapes/ink the user
    // selected can be spun to any angle instead of only moved.
    fun selectionRotateHandleOf(bounds: androidx.compose.ui.geometry.Rect): Offset =
        Offset(bounds.center.x, bounds.top - 40f)

    fun stickerRotateHandleOf(sticker: EmojiSticker): Offset =
        rotateAround(Offset(sticker.x, sticker.y - sticker.sizePx * 0.75f - 28f), Offset(sticker.x, sticker.y), sticker.rotationDegrees)

    // No opaque background here on purpose: the paper pattern (or imported document bitmap) is
    // painted by EditorScreen's Box *behind* this Canvas, and drawing white here would hide it.
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer(scaleX = scaleX)
            .pointerInput(page.id, tool, colorArgb, strokeWidthDp, selectedStrokeIds, lassoRectMode, rulerMode, compassMode, palmRejectionEnabled, stickers, images, textBoxes) {
                // Ink/shape/eraser input never records a point outside the page itself, so a drag
                // that runs off the edge (or a stray coordinate from a zoom-gesture handoff) can't
                // leave marks - or stray strokes - beyond the paper bounds.
                fun Offset.clampToPage(): Offset = Offset(
                    x.coerceIn(0f, size.width.toFloat()),
                    y.coerceIn(0f, size.height.toFloat())
                )
                awaitPointerEventScope {
                    while (true) {
                        val firstEvent = awaitPointerEvent(PointerEventPass.Main)
                        val firstPressed = effectivePressed(firstEvent.changes, palmRejectionEnabled)
                        // A second finger landing while the first is already down means the user is
                        // starting a pinch-zoom/pan gesture (handled by an ancestor Modifier) - never
                        // start a fresh draw/erase/etc gesture from that first finger's own down event.
                        // (With palm rejection on and a stylus involved, a palm touching down beside
                        // it does NOT count as that second finger - see effectivePressed above.)
                        if (firstPressed.size > 1) continue
                        val down = firstPressed.firstOrNull() ?: continue
                        var lastPos = down.position.clampToPage()
                        val downPos = lastPos
                        var totalMoved = 0f

                        when (tool) {
                            EditorTool.ERASER -> onEraseAt(lastPos)
                            EditorTool.LASSO -> {
                                val bounds = strokesBounds(strokes.filter { it.id in selectedStrokeIds })
                                val rotateHandle = bounds?.let { selectionRotateHandleOf(it) }
                                if (bounds != null && rotateHandle != null && (lastPos - rotateHandle).getDistance() < HANDLE_HIT_RADIUS_PX) {
                                    isRotatingSelection = true
                                    selectionRotateCenter = bounds.center
                                    selectionRotateStartAngle = Math.toDegrees(
                                        atan2((lastPos.y - bounds.center.y).toDouble(), (lastPos.x - bounds.center.x).toDouble())
                                    ).toFloat()
                                    selectionRotationDegrees = 0f
                                } else if (bounds != null && bounds.contains(lastPos)) {
                                    isMovingSelection = true
                                    dragDelta = Offset.Zero
                                } else {
                                    isMovingSelection = false
                                    isRotatingSelection = false
                                    lassoPoints = listOf(lastPos)
                                }
                            }
                            EditorTool.EMOJI -> {
                                val rotateHit = stickers.firstOrNull { (lastPos - stickerRotateHandleOf(it)).getDistance() < HANDLE_HIT_RADIUS_PX }
                                // Generous radius (bigger than the glyph itself) so a fingertip
                                // doesn't need to land pixel-perfect on the emoji to grab it.
                                val hit = rotateHit ?: stickers
                                    .filter { hypot((it.x - lastPos.x).toDouble(), (it.y - lastPos.y).toDouble()) < it.sizePx * 0.75 + 32 }
                                    .minByOrNull { hypot((it.x - lastPos.x).toDouble(), (it.y - lastPos.y).toDouble()) }
                                draggingStickerId = hit?.id
                                stickerDragDelta = Offset.Zero
                                stickerTransformMode = if (rotateHit != null) TransformMode.ROTATE else TransformMode.MOVE
                                stickerLiveRotation = hit?.rotationDegrees ?: 0f
                            }
                            EditorTool.IMAGE -> {
                                val hit = images.firstOrNull {
                                    lastPos.x in (it.x - it.widthPx / 2)..(it.x + it.widthPx / 2) &&
                                        lastPos.y in (it.y - it.heightPx / 2)..(it.y + it.heightPx / 2)
                                } ?: images.firstOrNull {
                                    val r = resizeHandleOf(it.x, it.y, it.widthPx, it.heightPx, it.rotationDegrees)
                                    val rt = rotateHandleOf(it.x, it.y, it.widthPx, it.heightPx, it.rotationDegrees)
                                    (lastPos - r).getDistance() < HANDLE_HIT_RADIUS_PX || (lastPos - rt).getDistance() < HANDLE_HIT_RADIUS_PX
                                }
                                draggingImageId = hit?.id
                                imageDragDelta = Offset.Zero
                                if (hit != null) {
                                    imageLiveWidth = hit.widthPx
                                    imageLiveHeight = hit.heightPx
                                    imageLiveRotation = hit.rotationDegrees
                                    val r = resizeHandleOf(hit.x, hit.y, hit.widthPx, hit.heightPx, hit.rotationDegrees)
                                    val rt = rotateHandleOf(hit.x, hit.y, hit.widthPx, hit.heightPx, hit.rotationDegrees)
                                    imageTransformMode = when {
                                        (lastPos - r).getDistance() < HANDLE_HIT_RADIUS_PX -> TransformMode.RESIZE
                                        (lastPos - rt).getDistance() < HANDLE_HIT_RADIUS_PX -> TransformMode.ROTATE
                                        else -> TransformMode.MOVE
                                    }
                                }
                            }
                            EditorTool.TEXT -> {
                                val estH = { t: TextBoxItem -> if (t.heightPx > 0f) t.heightPx else t.fontSizePx * 1.4f }
                                val hit = textBoxes.firstOrNull {
                                    lastPos.x in (it.x - 16f)..(it.x + it.widthPx + 16f) &&
                                        lastPos.y in (it.y - 16f)..(it.y + estH(it) + 16f)
                                } ?: textBoxes.firstOrNull {
                                    val cx = it.x + it.widthPx / 2f
                                    val cy = it.y + estH(it) / 2f
                                    val r = resizeHandleOf(cx, cy, it.widthPx, estH(it), it.rotationDegrees)
                                    val rt = rotateHandleOf(cx, cy, it.widthPx, estH(it), it.rotationDegrees)
                                    (lastPos - r).getDistance() < HANDLE_HIT_RADIUS_PX || (lastPos - rt).getDistance() < HANDLE_HIT_RADIUS_PX
                                }
                                draggingTextBoxId = hit?.id
                                textBoxDragDelta = Offset.Zero
                                if (hit != null) {
                                    val h = estH(hit)
                                    textLiveWidth = hit.widthPx
                                    textLiveHeight = h
                                    textLiveRotation = hit.rotationDegrees
                                    val cx = hit.x + hit.widthPx / 2f
                                    val cy = hit.y + h / 2f
                                    val r = resizeHandleOf(cx, cy, hit.widthPx, h, hit.rotationDegrees)
                                    val rt = rotateHandleOf(cx, cy, hit.widthPx, h, hit.rotationDegrees)
                                    textBoxTransformMode = when {
                                        (lastPos - r).getDistance() < HANDLE_HIT_RADIUS_PX -> TransformMode.RESIZE
                                        (lastPos - rt).getDistance() < HANDLE_HIT_RADIUS_PX -> TransformMode.ROTATE
                                        else -> TransformMode.MOVE
                                    }
                                }
                            }
                            EditorTool.LASER -> onLaserPoint(lastPos)
                            else -> inProgressPoints = listOf(StrokePoint(lastPos.x, lastPos.y, down.pressure))
                        }
                        down.consume()

                        val pointerId = down.id
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                            // A second finger touching down mid-gesture hands off to the ancestor's
                            // pinch-zoom/pan handler - finish this gesture now instead of continuing to
                            // feed it coordinates that are about to be thrown off by the zoom transform.
                            // (Same palm-aware count as above: a palm beside an active stylus doesn't
                            // trigger this early finish.)
                            if (!change.pressed || effectivePressed(event.changes, palmRejectionEnabled).size > 1) {
                                when (tool) {
                                    EditorTool.ERASER -> onEraseFinished()
                                    EditorTool.LASSO -> {
                                        if (isRotatingSelection) {
                                            onSelectionRotated(selectionRotationDegrees)
                                            isRotatingSelection = false
                                            selectionRotationDegrees = 0f
                                        } else if (isMovingSelection) {
                                            onSelectionMoved(dragDelta.x, dragDelta.y)
                                            dragDelta = Offset.Zero
                                        } else {
                                            onLassoFinished(lassoPoints)
                                            lassoPoints = emptyList()
                                        }
                                    }
                                    EditorTool.EMOJI -> {
                                        val id = draggingStickerId
                                        if (id != null) {
                                            val original = stickers.firstOrNull { it.id == id }
                                            when {
                                                stickerTransformMode == TransformMode.ROTATE && original != null ->
                                                    onStickerTransformed(id, stickerLiveRotation)
                                                totalMoved < TAP_SLOP_PX -> onStickerTapped(id)
                                                original != null ->
                                                    onStickerDragged(id, original.x + stickerDragDelta.x, original.y + stickerDragDelta.y)
                                            }
                                        } else if (totalMoved < TAP_SLOP_PX) {
                                            onEmptyTapForEmoji(downPos)
                                        }
                                        draggingStickerId = null
                                        stickerDragDelta = Offset.Zero
                                        stickerTransformMode = TransformMode.MOVE
                                    }
                                    EditorTool.IMAGE -> {
                                        val id = draggingImageId
                                        if (id != null) {
                                            val original = images.firstOrNull { it.id == id }
                                            when {
                                                imageTransformMode == TransformMode.RESIZE && original != null ->
                                                    onImageTransformed(id, imageLiveWidth, imageLiveHeight, imageLiveRotation)
                                                imageTransformMode == TransformMode.ROTATE && original != null ->
                                                    onImageTransformed(id, imageLiveWidth, imageLiveHeight, imageLiveRotation)
                                                totalMoved < TAP_SLOP_PX -> onImageTapped(id)
                                                original != null ->
                                                    onImageDragged(id, original.x + imageDragDelta.x, original.y + imageDragDelta.y)
                                            }
                                        } else if (totalMoved < TAP_SLOP_PX) {
                                            onEmptyTapForImage(downPos)
                                        }
                                        draggingImageId = null
                                        imageDragDelta = Offset.Zero
                                        imageTransformMode = TransformMode.MOVE
                                    }
                                    EditorTool.TEXT -> {
                                        val id = draggingTextBoxId
                                        if (id != null) {
                                            val original = textBoxes.firstOrNull { it.id == id }
                                            when {
                                                textBoxTransformMode == TransformMode.RESIZE && original != null ->
                                                    onTextBoxTransformed(id, textLiveWidth, textLiveHeight, textLiveRotation)
                                                textBoxTransformMode == TransformMode.ROTATE && original != null ->
                                                    onTextBoxTransformed(id, textLiveWidth, textLiveHeight, textLiveRotation)
                                                totalMoved < TAP_SLOP_PX -> onTextBoxTapped(id)
                                                original != null ->
                                                    onTextBoxDragged(id, original.x + textBoxDragDelta.x, original.y + textBoxDragDelta.y)
                                            }
                                        } else if (totalMoved < TAP_SLOP_PX) {
                                            onEmptyTapForText(downPos)
                                        }
                                        draggingTextBoxId = null
                                        textBoxDragDelta = Offset.Zero
                                        textBoxTransformMode = TransformMode.MOVE
                                    }
                                    EditorTool.LASER -> onLaserPoint(null)
                                    else -> {
                                        onStrokeFinished(inProgressPoints)
                                        inProgressPoints = emptyList()
                                    }
                                }
                                break
                            }
                            val newPos = change.position.clampToPage()
                            totalMoved += (newPos - lastPos).getDistance()
                            when (tool) {
                                EditorTool.ERASER -> onEraseAt(newPos)
                                EditorTool.LASSO -> {
                                    if (isRotatingSelection) {
                                        val angle = Math.toDegrees(
                                            atan2((newPos.y - selectionRotateCenter.y).toDouble(), (newPos.x - selectionRotateCenter.x).toDouble())
                                        ).toFloat()
                                        selectionRotationDegrees = angle - selectionRotateStartAngle
                                    } else if (isMovingSelection) {
                                        dragDelta += (newPos - lastPos)
                                    } else if (lassoRectMode) {
                                        lassoPoints = rectCorners(downPos, newPos)
                                    } else {
                                        lassoPoints = lassoPoints + newPos
                                    }
                                }
                                EditorTool.EMOJI -> {
                                    val id = draggingStickerId
                                    val original = stickers.firstOrNull { it.id == id }
                                    if (original != null) {
                                        if (stickerTransformMode == TransformMode.ROTATE) {
                                            val angle = Math.toDegrees(
                                                atan2((newPos.y - original.y).toDouble(), (newPos.x - original.x).toDouble())
                                            ).toFloat()
                                            stickerLiveRotation = angle + 90f
                                        } else {
                                            stickerDragDelta += (newPos - lastPos)
                                        }
                                    }
                                }
                                EditorTool.IMAGE -> {
                                    val id = draggingImageId
                                    val original = images.firstOrNull { it.id == id }
                                    if (original != null) {
                                        when (imageTransformMode) {
                                            TransformMode.MOVE -> imageDragDelta += (newPos - lastPos)
                                            TransformMode.RESIZE -> {
                                                val center = Offset(original.x, original.y)
                                                val dist = (newPos - center).getDistance().coerceAtLeast(20f)
                                                val origDist = max(hypot(original.widthPx / 2.0, original.heightPx / 2.0).toFloat(), 1f)
                                                val scale = (dist / origDist).coerceIn(0.2f, 6f)
                                                imageLiveWidth = original.widthPx * scale
                                                imageLiveHeight = original.heightPx * scale
                                            }
                                            TransformMode.ROTATE -> {
                                                val center = Offset(original.x, original.y)
                                                val angle = Math.toDegrees(atan2((newPos.y - center.y).toDouble(), (newPos.x - center.x).toDouble())).toFloat()
                                                imageLiveRotation = angle + 90f
                                            }
                                        }
                                    }
                                }
                                EditorTool.TEXT -> {
                                    val id = draggingTextBoxId
                                    val original = textBoxes.firstOrNull { it.id == id }
                                    if (original != null) {
                                        val h = if (original.heightPx > 0f) original.heightPx else original.fontSizePx * 1.4f
                                        val center = Offset(original.x + original.widthPx / 2f, original.y + h / 2f)
                                        when (textBoxTransformMode) {
                                            TransformMode.MOVE -> textBoxDragDelta += (newPos - lastPos)
                                            TransformMode.RESIZE -> {
                                                textLiveWidth = (2f * (newPos.x - center.x)).coerceAtLeast(80f)
                                                textLiveHeight = (2f * (newPos.y - center.y)).coerceAtLeast(original.fontSizePx * 1.2f)
                                            }
                                            TransformMode.ROTATE -> {
                                                val angle = Math.toDegrees(atan2((newPos.y - center.y).toDouble(), (newPos.x - center.x).toDouble())).toFloat()
                                                textLiveRotation = angle + 90f
                                            }
                                        }
                                    }
                                }
                                EditorTool.LINE -> {
                                    val snapped = snapLineEnd(downPos, newPos)
                                    inProgressPoints = listOf(
                                        StrokePoint(downPos.x, downPos.y, down.pressure),
                                        StrokePoint(snapped.x, snapped.y, change.pressure)
                                    )
                                }
                                EditorTool.RECTANGLE, EditorTool.CIRCLE, EditorTool.ARROW, EditorTool.TRIANGLE -> {
                                    val snapped = snapBoundsCorner(downPos, newPos)
                                    inProgressPoints = listOf(
                                        StrokePoint(downPos.x, downPos.y, down.pressure),
                                        StrokePoint(snapped.x, snapped.y, change.pressure)
                                    )
                                }
                                EditorTool.LASER -> onLaserPoint(newPos)
                                else -> inProgressPoints = when {
                                    // "Thước" (ruler): same angle-snapped single segment as the LINE
                                    // shape tool, but keeps rendering with the active ink tool's own
                                    // style (pencil/brush/highlighter) instead of a generic outline.
                                    tool in InkTools && rulerMode -> {
                                        val snapped = snapLineEnd(downPos, newPos)
                                        listOf(
                                            StrokePoint(downPos.x, downPos.y, down.pressure),
                                            StrokePoint(snapped.x, snapped.y, change.pressure)
                                        )
                                    }
                                    // "Compa" (compass): a true center(downPos)+radius circle, same idea.
                                    tool in InkTools && compassMode ->
                                        circlePoints(downPos, newPos, down.pressure, change.pressure)
                                    else -> inProgressPoints + StrokePoint(newPos.x, newPos.y, change.pressure)
                                }
                            }
                            lastPos = newPos
                            change.consume()
                        }
                    }
                }
            }
    ) {
      // Never let a stroke render past the page edge - guards against both new drags that run off
      // the paper and any already-saved stroke with stray out-of-bounds points.
      clipRect(0f, 0f, size.width, size.height) {
        if (backgroundBitmap != null) {
            drawImage(backgroundBitmap, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
        }

        val draggingSelection = isMovingSelection && dragDelta != Offset.Zero
        val rotatingSelectionLive = isRotatingSelection && selectionRotationDegrees != 0f
        strokes.forEach { stroke ->
            if ((draggingSelection || rotatingSelectionLive) && stroke.id in selectedStrokeIds) return@forEach
            drawStroke(stroke, density)
        }

        if (draggingSelection) {
            strokes.filter { it.id in selectedStrokeIds }.forEach { stroke ->
                val translated = stroke.copy(
                    points = stroke.points.map { it.copy(x = it.x + dragDelta.x, y = it.y + dragDelta.y) }
                )
                drawStroke(translated, density)
            }
        } else if (rotatingSelectionLive) {
            val selected = strokes.filter { it.id in selectedStrokeIds }
            val singleBoundingBoxShape = selected.size == 1 && selected[0].tool in setOf(ToolType.RECTANGLE, ToolType.CIRCLE, ToolType.TRIANGLE)
            selected.forEach { stroke ->
                val rotated = if (singleBoundingBoxShape) {
                    stroke.copy(rotationDegrees = stroke.rotationDegrees + selectionRotationDegrees)
                } else {
                    stroke.copy(
                        points = stroke.points.map {
                            val p = rotateAround(Offset(it.x, it.y), selectionRotateCenter, selectionRotationDegrees)
                            it.copy(x = p.x, y = p.y)
                        }
                    )
                }
                drawStroke(rotated, density)
            }
        }
        if (selectedStrokeIds.isNotEmpty() && !draggingSelection && !rotatingSelectionLive) {
            strokesBounds(strokes.filter { it.id in selectedStrokeIds })?.let { bounds ->
                drawRect(
                    color = Color(0xFF14B8A6),
                    topLeft = Offset(bounds.left - 12f, bounds.top - 12f),
                    size = androidx.compose.ui.geometry.Size(bounds.width + 24f, bounds.height + 24f),
                    style = DrawStroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 10f)))
                )
                val handle = selectionRotateHandleOf(bounds)
                drawCircle(color = Color(0xFF16A34A), radius = 14f, center = handle)
                drawCircle(color = Color.White, radius = 7f, center = handle)
            }
        }

        val isRotatingSticker = draggingStickerId != null && stickerTransformMode == TransformMode.ROTATE
        val draggingSticker = draggingStickerId != null && stickerDragDelta != Offset.Zero && !isRotatingSticker
        if (tool == EditorTool.EMOJI) {
            // Faint grab handle behind every sticker so it's obvious they can be dragged.
            stickers.forEach { sticker ->
                drawCircle(
                    color = Color(0xFF14B8A6).copy(alpha = if (sticker.id == draggingStickerId) 0.18f else 0.08f),
                    radius = sticker.sizePx * 0.65f,
                    center = Offset(sticker.x, sticker.y)
                )
            }
        }
        stickers.forEach { sticker ->
            if ((draggingSticker || isRotatingSticker) && sticker.id == draggingStickerId) return@forEach
            drawSticker(sticker)
        }
        if (draggingSticker) {
            stickers.firstOrNull { it.id == draggingStickerId }?.let { sticker ->
                drawSticker(sticker.copy(x = sticker.x + stickerDragDelta.x, y = sticker.y + stickerDragDelta.y))
            }
        }
        if (isRotatingSticker) {
            stickers.firstOrNull { it.id == draggingStickerId }?.let { sticker ->
                val shown = sticker.copy(rotationDegrees = stickerLiveRotation)
                drawSticker(shown)
                val handle = stickerRotateHandleOf(shown)
                drawCircle(color = Color(0xFF16A34A), radius = 14f, center = handle)
                drawCircle(color = Color.White, radius = 7f, center = handle)
            }
        }
        if (tool == EditorTool.EMOJI && !isRotatingSticker) {
            stickers.forEach { sticker ->
                val handle = stickerRotateHandleOf(sticker)
                drawCircle(color = Color(0xFF16A34A), radius = 14f, center = handle)
                drawCircle(color = Color.White, radius = 7f, center = handle)
            }
        }

        val draggingImage = draggingImageId != null
        images.forEach { image ->
            if (draggingImage && image.id == draggingImageId) return@forEach
            drawImageAttachment(image)
        }
        if (draggingImage) {
            images.firstOrNull { it.id == draggingImageId }?.let { image ->
                val shown = when (imageTransformMode) {
                    TransformMode.MOVE -> image.copy(x = image.x + imageDragDelta.x, y = image.y + imageDragDelta.y)
                    TransformMode.RESIZE -> image.copy(widthPx = imageLiveWidth, heightPx = imageLiveHeight)
                    TransformMode.ROTATE -> image.copy(rotationDegrees = imageLiveRotation)
                }
                drawImageAttachment(shown)
                drawTransformHandles(shown.x, shown.y, shown.widthPx, shown.heightPx, shown.rotationDegrees)
            }
        }
        if (tool == EditorTool.IMAGE && !draggingImage) {
            images.forEach { image -> drawTransformHandles(image.x, image.y, image.widthPx, image.heightPx, image.rotationDegrees) }
        }

        val draggingTextBox = draggingTextBoxId != null
        textBoxes.forEach { textBox ->
            if (draggingTextBox && textBox.id == draggingTextBoxId) return@forEach
            drawTextBoxItem(textBox)
        }
        if (draggingTextBox) {
            textBoxes.firstOrNull { it.id == draggingTextBoxId }?.let { textBox ->
                val h = if (textBox.heightPx > 0f) textBox.heightPx else textBox.fontSizePx * 1.4f
                val shown = when (textBoxTransformMode) {
                    TransformMode.MOVE -> textBox.copy(x = textBox.x + textBoxDragDelta.x, y = textBox.y + textBoxDragDelta.y)
                    TransformMode.RESIZE -> textBox.copy(widthPx = textLiveWidth, heightPx = textLiveHeight)
                    TransformMode.ROTATE -> textBox.copy(rotationDegrees = textLiveRotation)
                }
                drawTextBoxItem(shown)
                val sh = if (shown.heightPx > 0f) shown.heightPx else h
                drawTransformHandles(shown.x + shown.widthPx / 2f, shown.y + sh / 2f, shown.widthPx, sh, shown.rotationDegrees)
            }
        }
        if (tool == EditorTool.TEXT && !draggingTextBox) {
            textBoxes.forEach { t ->
                val h = if (t.heightPx > 0f) t.heightPx else t.fontSizePx * 1.4f
                drawTransformHandles(t.x + t.widthPx / 2f, t.y + h / 2f, t.widthPx, h, t.rotationDegrees)
            }
        }

        if (inProgressPoints.size > 1) {
            drawStroke(
                Stroke(
                    id = "preview",
                    pageId = page.id,
                    tool = when (tool) {
                        EditorTool.HIGHLIGHTER -> ToolType.HIGHLIGHTER
                        EditorTool.PENCIL -> ToolType.PENCIL
                        EditorTool.BRUSH -> ToolType.BRUSH
                        EditorTool.LINE -> ToolType.LINE
                        EditorTool.RECTANGLE -> ToolType.RECTANGLE
                        EditorTool.CIRCLE -> ToolType.CIRCLE
                        EditorTool.ARROW -> ToolType.ARROW
                        EditorTool.TRIANGLE -> ToolType.TRIANGLE
                        else -> ToolType.PEN
                    },
                    colorArgb = colorArgb,
                    widthDp = strokeWidthDp,
                    fillColorArgb = if (isShapeFillEnabled && tool in setOf(EditorTool.RECTANGLE, EditorTool.CIRCLE, EditorTool.TRIANGLE)) colorArgb else null,
                    points = inProgressPoints,
                    createdAt = 0L
                ),
                density
            )
        }

        if (lassoPoints.size > 1) {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(lassoPoints.first().x, lassoPoints.first().y)
                lassoPoints.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }
            drawPath(
                path = path,
                color = Color(0xFF14B8A6),
                style = DrawStroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 10f)))
            )
        }
      }
    }
}

private fun rotateAround(point: Offset, center: Offset, degrees: Float): Offset {
    if (degrees == 0f) return point
    val rad = Math.toRadians(degrees.toDouble())
    val dx = point.x - center.x
    val dy = point.y - center.y
    val cos = kotlin.math.cos(rad).toFloat()
    val sin = kotlin.math.sin(rad).toFloat()
    return Offset(center.x + dx * cos - dy * sin, center.y + dx * sin + dy * cos)
}

private fun DrawScope.drawTransformHandles(cx: Float, cy: Float, w: Float, h: Float, rotationDegrees: Float) {
    val resize = rotateAround(Offset(cx + w / 2f, cy + h / 2f), Offset(cx, cy), rotationDegrees)
    val rotate = rotateAround(Offset(cx + w / 2f, cy - h / 2f - 28f), Offset(cx, cy), rotationDegrees)
    drawCircle(color = Color(0xFF14B8A6), radius = 14f, center = resize)
    drawCircle(color = Color.White, radius = 7f, center = resize)
    drawCircle(color = Color(0xFF16A34A), radius = 14f, center = rotate)
    drawCircle(color = Color.White, radius = 7f, center = rotate)
}

private fun rectCorners(a: Offset, b: Offset): List<Offset> = listOf(
    Offset(a.x, a.y), Offset(b.x, a.y), Offset(b.x, b.y), Offset(a.x, b.y)
)

private fun DrawScope.drawSticker(sticker: EmojiSticker) {
    drawContext.canvas.nativeCanvas.apply {
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = sticker.sizePx
            textAlign = android.graphics.Paint.Align.CENTER
        }
        save()
        if (sticker.rotationDegrees != 0f) rotate(sticker.rotationDegrees, sticker.x, sticker.y)
        drawText(sticker.emoji, sticker.x, sticker.y + sticker.sizePx * 0.35f, paint)
        restore()
    }
}

private fun DrawScope.drawImageAttachment(image: ImageAttachment) {
    val bmp = BitmapFactory.decodeFile(image.imagePath) ?: return
    drawContext.canvas.nativeCanvas.apply {
        save()
        if (image.rotationDegrees != 0f) rotate(image.rotationDegrees, image.x, image.y)
        val dst = android.graphics.RectF(
            image.x - image.widthPx / 2f, image.y - image.heightPx / 2f,
            image.x + image.widthPx / 2f, image.y + image.heightPx / 2f
        )
        drawBitmap(bmp, null, dst, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG))
        restore()
    }
}

private fun androidTypeface(fontFamily: TextFontFamily, isBold: Boolean, isItalic: Boolean): android.graphics.Typeface {
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

private fun DrawScope.drawTextBoxItem(textBox: TextBoxItem) {
    drawContext.canvas.nativeCanvas.apply {
        val paint = TextPaint().apply {
            isAntiAlias = true
            textSize = textBox.fontSizePx
            color = textBox.colorArgb.toInt()
            typeface = androidTypeface(textBox.fontFamily, textBox.isBold, textBox.isItalic)
        }
        val width = textBox.widthPx.toInt().coerceAtLeast(50)
        val layout = StaticLayout.Builder
            .obtain(textBox.text, 0, textBox.text.length, paint, width)
            .build()
        val h = if (textBox.heightPx > 0f) textBox.heightPx else textBox.fontSizePx * 1.4f
        save()
        if (textBox.rotationDegrees != 0f) {
            rotate(textBox.rotationDegrees, textBox.x + textBox.widthPx / 2f, textBox.y + h / 2f)
        }
        translate(textBox.x, textBox.y)
        layout.draw(this)
        restore()
    }
}

private fun DrawScope.drawStroke(stroke: Stroke, density: Density) {
    val path = strokeToPath(stroke.points)
    val baseWidthPx = with(density) { stroke.widthDp.toDp().toPx() }
    val color = Color(stroke.colorArgb)
    val avgPressure = (stroke.points.map { it.pressure }.average().toFloat()).coerceIn(0.3f, 1f)
    val fillColor = stroke.fillColorArgb?.let { Color(it).copy(alpha = 0.35f) }

    when (stroke.tool) {
        ToolType.PEN -> {
            drawPath(
                path = path,
                color = color,
                style = DrawStroke(width = baseWidthPx * (0.6f + 0.6f * avgPressure), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
        ToolType.PENCIL -> {
            // Thinner and slightly translucent so overlapping strokes build up like graphite.
            drawPath(
                path = path,
                color = color.copy(alpha = 0.78f),
                style = DrawStroke(width = baseWidthPx * (0.4f + 0.3f * avgPressure), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
        ToolType.BRUSH -> {
            // Nét thanh nét đậm: a real ribbon of variable width along the stroke (per-point
            // stylus pressure, plus a speed taper so even a constant-pressure finger-drawn stroke
            // still thins out on fast strokes) instead of one constant width for the whole path.
            drawVariableWidthStroke(stroke.points, baseWidthPx, color)
        }
        ToolType.HIGHLIGHTER -> {
            // Darken blend so overlapping highlighter strokes layer like a real marker instead
            // of just stacking flat alpha, and ink already on the page still shows through.
            drawPath(
                path = path,
                color = color.copy(alpha = 0.55f),
                style = DrawStroke(width = (baseWidthPx * 4f).coerceAtLeast(18f), cap = StrokeCap.Square, join = StrokeJoin.Round),
                blendMode = BlendMode.Darken
            )
        }
        ToolType.LINE -> {
            if (stroke.points.size >= 2) {
                val p0 = stroke.points[0]
                val p1 = stroke.points[1]
                drawLine(
                    color = color,
                    start = Offset(p0.x, p0.y),
                    end = Offset(p1.x, p1.y),
                    strokeWidth = baseWidthPx,
                    cap = StrokeCap.Round
                )
            }
        }
        ToolType.ARROW -> {
            if (stroke.points.size >= 2) {
                val p0 = Offset(stroke.points[0].x, stroke.points[0].y)
                val p1 = Offset(stroke.points[1].x, stroke.points[1].y)
                drawLine(color = color, start = p0, end = p1, strokeWidth = baseWidthPx, cap = StrokeCap.Round)
                val angle = atan2((p1.y - p0.y).toDouble(), (p1.x - p0.x).toDouble())
                val headLen = (baseWidthPx * 5f).coerceIn(18f, 60f)
                val leftAngle = angle + Math.toRadians(150.0)
                val rightAngle = angle - Math.toRadians(150.0)
                val left = Offset(p1.x + (headLen * kotlin.math.cos(leftAngle)).toFloat(), p1.y + (headLen * kotlin.math.sin(leftAngle)).toFloat())
                val right = Offset(p1.x + (headLen * kotlin.math.cos(rightAngle)).toFloat(), p1.y + (headLen * kotlin.math.sin(rightAngle)).toFloat())
                val headPath = androidx.compose.ui.graphics.Path().apply {
                    moveTo(p1.x, p1.y); lineTo(left.x, left.y); lineTo(right.x, right.y); close()
                }
                drawPath(path = headPath, color = color)
            }
        }
        ToolType.RECTANGLE -> {
            if (stroke.points.size >= 2) {
                val p0 = stroke.points[0]
                val p1 = stroke.points[1]
                val topLeft = Offset(minOf(p0.x, p1.x), minOf(p0.y, p1.y))
                val size = androidx.compose.ui.geometry.Size(kotlin.math.abs(p1.x - p0.x), kotlin.math.abs(p1.y - p0.y))
                // Rotated around the shape's own center at draw time - [stroke.points] stay the
                // original unrotated corners, so the Lasso rotate handle only ever has to change
                // one float instead of reshaping the whole bounding box (see [Stroke.rotationDegrees]).
                rotate(degrees = stroke.rotationDegrees, pivot = Offset(topLeft.x + size.width / 2f, topLeft.y + size.height / 2f)) {
                    if (fillColor != null) drawRect(color = fillColor, topLeft = topLeft, size = size)
                    drawRect(
                        color = color,
                        topLeft = topLeft,
                        size = size,
                        style = DrawStroke(width = baseWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }
        }
        ToolType.CIRCLE -> {
            if (stroke.points.size >= 2) {
                val p0 = stroke.points[0]
                val p1 = stroke.points[1]
                val topLeft = Offset(minOf(p0.x, p1.x), minOf(p0.y, p1.y))
                val size = androidx.compose.ui.geometry.Size(kotlin.math.abs(p1.x - p0.x), kotlin.math.abs(p1.y - p0.y))
                // A true circle looks the same at any angle, but an oval drag doesn't - rotate it
                // around its own center too, same as RECTANGLE/TRIANGLE.
                rotate(degrees = stroke.rotationDegrees, pivot = Offset(topLeft.x + size.width / 2f, topLeft.y + size.height / 2f)) {
                    if (fillColor != null) drawOval(color = fillColor, topLeft = topLeft, size = size)
                    drawOval(
                        color = color,
                        topLeft = topLeft,
                        size = size,
                        style = DrawStroke(width = baseWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }
        }
        ToolType.TRIANGLE -> {
            if (stroke.points.size >= 2) {
                val p0 = stroke.points[0]
                val p1 = stroke.points[1]
                val left = minOf(p0.x, p1.x)
                val right = maxOf(p0.x, p1.x)
                val top = minOf(p0.y, p1.y)
                val bottom = maxOf(p0.y, p1.y)
                val trianglePath = androidx.compose.ui.graphics.Path().apply {
                    moveTo((left + right) / 2f, top)
                    lineTo(right, bottom)
                    lineTo(left, bottom)
                    close()
                }
                rotate(degrees = stroke.rotationDegrees, pivot = Offset((left + right) / 2f, (top + bottom) / 2f)) {
                    if (fillColor != null) drawPath(path = trianglePath, color = fillColor)
                    drawPath(
                        path = trianglePath,
                        color = color,
                        style = DrawStroke(width = baseWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }
        }
        ToolType.ERASER -> Unit // erased strokes are removed from the list, never rendered
    }
}

/** Builds and fills a variable-width ribbon along [points] instead of a single constant-width
 * outline - each point's local width comes from its own pressure (real tapering under a stylus)
 * and local speed (a fast segment is thinner even at constant pressure, so a plain finger-drawn
 * stroke still gets a calligraphy-like taper). Used by the Brush tool ("nét thanh nét đậm"). */
private fun DrawScope.drawVariableWidthStroke(points: List<StrokePoint>, baseWidthPx: Float, color: Color) {
    if (points.isEmpty()) return
    if (points.size == 1) {
        val w = baseWidthPx * (0.9f + 2.2f * points[0].pressure.coerceIn(0.3f, 1f))
        drawCircle(color, radius = w / 2f, center = Offset(points[0].x, points[0].y))
        return
    }
    val widths = FloatArray(points.size)
    for (i in points.indices) {
        val pressure = points[i].pressure.coerceIn(0.3f, 1f)
        val speed = if (i == 0) 0f else hypot((points[i].x - points[i - 1].x).toDouble(), (points[i].y - points[i - 1].y).toDouble()).toFloat()
        val speedFactor = 1.15f - (speed / 40f).coerceIn(0f, 0.55f)
        widths[i] = baseWidthPx * (0.9f + 2.2f * pressure) * speedFactor
    }
    val left = ArrayList<Offset>(points.size)
    val right = ArrayList<Offset>(points.size)
    for (i in points.indices) {
        val p = points[i]
        val dx: Float
        val dy: Float
        when (i) {
            0 -> { dx = points[1].x - p.x; dy = points[1].y - p.y }
            points.size - 1 -> { dx = p.x - points[i - 1].x; dy = p.y - points[i - 1].y }
            else -> { dx = points[i + 1].x - points[i - 1].x; dy = points[i + 1].y - points[i - 1].y }
        }
        val len = hypot(dx.toDouble(), dy.toDouble()).toFloat().takeIf { it > 0.0001f } ?: 1f
        val nx = -dy / len
        val ny = dx / len
        val half = widths[i] / 2f
        left += Offset(p.x + nx * half, p.y + ny * half)
        right += Offset(p.x - nx * half, p.y - ny * half)
    }
    val ribbon = androidx.compose.ui.graphics.Path().apply {
        moveTo(left[0].x, left[0].y)
        for (i in 1 until left.size) lineTo(left[i].x, left[i].y)
        for (i in right.indices.reversed()) lineTo(right[i].x, right[i].y)
        close()
    }
    drawPath(path = ribbon, color = color)
    drawCircle(color, radius = widths.first() / 2f, center = Offset(points.first().x, points.first().y))
    drawCircle(color, radius = widths.last() / 2f, center = Offset(points.last().x, points.last().y))
}
