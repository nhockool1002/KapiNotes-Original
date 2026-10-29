package com.kapi.notes.domain.model

import kotlinx.serialization.Serializable

enum class ToolType {
    PEN,
    PENCIL,
    BRUSH,
    HIGHLIGHTER,
    ERASER,
    /** 2-point shapes: [Stroke.points] holds exactly [start, end] of the drag, already snapped. */
    LINE,
    RECTANGLE,
    CIRCLE,
    ARROW,
    TRIANGLE
}

/** A single sampled point of a stroke, captured from a (possibly stylus) MotionEvent. */
@Serializable
data class StrokePoint(
    val x: Float,
    val y: Float,
    /** 0f..1f, defaults to 1f for mouse/finger input which reports no pressure. */
    val pressure: Float = 1f
)

/**
 * A finished (or in-progress) pen/highlighter stroke on a page. Erasing never mutates an
 * existing stroke's points; instead whole strokes that intersect the eraser path are removed,
 * which keeps undo/redo a simple append/remove-by-id operation (see [com.kapi.notes.ui.editor.UndoRedoStack]).
 * The Lasso tool is the one thing that *does* mutate an existing stroke's points (moving it),
 * which is why [updatedAt] exists separately from [createdAt] - it is what Drive sync uses to
 * decide which device's version of a moved stroke wins.
 */
@Serializable
data class Stroke(
    val id: String,
    val pageId: String,
    val tool: ToolType,
    val colorArgb: Long,
    val widthDp: Float,
    val points: List<StrokePoint>,
    /** Fill color for closed shapes (RECTANGLE/CIRCLE/TRIANGLE); null means outline-only. */
    val fillColorArgb: Long? = null,
    /** Free-rotate angle for closed shapes (RECTANGLE/CIRCLE/TRIANGLE), applied around the
     * shape's own center at draw time - [points] always stay the original, unrotated corners,
     * since rotating a bounding-box shape's own 2 corner points would just produce a different
     * axis-aligned box instead of a rotated one. Ink/line/arrow strokes rotate by mutating
     * [points] directly instead (see the Lasso tool's rotate handle), so this stays 0 for them. */
    val rotationDegrees: Float = 0f,
    val layerId: String = DEFAULT_LAYER_ID,
    val createdAt: Long,
    val updatedAt: Long = createdAt
)
