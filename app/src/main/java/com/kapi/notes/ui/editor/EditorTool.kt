package com.kapi.notes.ui.editor

/**
 * The toolbar's active tool. PEN/PENCIL/BRUSH/HIGHLIGHTER/LINE/RECTANGLE/CIRCLE map 1:1 to
 * [com.kapi.notes.domain.model.ToolType] and produce a persisted stroke; LASSO never produces a
 * stroke - it only selects existing strokes so they can be dragged ("cut") to a new spot; EMOJI/
 * IMAGE/TEXT place/move/remove page objects instead of ink.
 */
enum class EditorTool {
    PEN,
    PENCIL,
    BRUSH,
    HIGHLIGHTER,
    LINE,
    RECTANGLE,
    CIRCLE,
    ARROW,
    TRIANGLE,
    ERASER,
    LASSO,
    EMOJI,
    IMAGE,
    TEXT,
    /** Draws a temporary glowing dot that fades out - never persisted - for presenting/pointing. */
    LASER
}

/** The four ink styles selectable from the toolbar's pen buttons. */
val InkTools = listOf(EditorTool.PEN, EditorTool.PENCIL, EditorTool.BRUSH, EditorTool.HIGHLIGHTER)

/** The five shape tools, grouped behind one toolbar dropdown since they're used less often. */
val ShapeTools = listOf(EditorTool.LINE, EditorTool.RECTANGLE, EditorTool.CIRCLE, EditorTool.ARROW, EditorTool.TRIANGLE)

/** How the Eraser tool removes ink: a whole stroke/layer at a time, or only the exact portion
 * the eraser passes over ("xóa phẳng"), splitting the rest of the stroke into surviving pieces. */
enum class EraseMode { STROKE, FLAT }
