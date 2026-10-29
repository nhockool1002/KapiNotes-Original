package com.kapi.notes.domain.model

/** An inline image placed on a page - distinct from [NotePage.backgroundImagePath], which is
 * the imported document the whole page sits on top of. */
data class ImageAttachment(
    val id: String,
    val pageId: String,
    val imagePath: String,
    val x: Float,
    val y: Float,
    val widthPx: Float,
    val heightPx: Float,
    /** Clockwise rotation in degrees around the image's center. */
    val rotationDegrees: Float = 0f,
    val layerId: String = DEFAULT_LAYER_ID,
    val updatedAt: Long = System.currentTimeMillis()
)
