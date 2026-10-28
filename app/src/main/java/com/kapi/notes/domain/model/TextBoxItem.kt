package com.kapi.notes.domain.model

/** A typed (non-handwritten) text box placed on a page. */
data class TextBoxItem(
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
    val fontFamily: TextFontFamily = TextFontFamily.DEFAULT,
    /** Clockwise rotation in degrees around the box's center. */
    val rotationDegrees: Float = 0f,
    val updatedAt: Long = System.currentTimeMillis()
)

enum class TextFontFamily {
    DEFAULT,
    SERIF,
    MONOSPACE,
    HANDWRITING
}
