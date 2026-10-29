package com.kapi.notes.domain.model

/** An emoji placed as a sticker on a page - positioned like a stroke point but with no ink. */
data class EmojiSticker(
    val id: String,
    val pageId: String,
    val emoji: String,
    val x: Float,
    val y: Float,
    val sizePx: Float,
    val rotationDegrees: Float = 0f,
    val layerId: String = DEFAULT_LAYER_ID,
    val updatedAt: Long = System.currentTimeMillis()
)
