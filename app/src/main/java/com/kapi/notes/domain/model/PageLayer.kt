package com.kapi.notes.domain.model

import kotlinx.serialization.Serializable

/** The id every page is created with, and the layer new content falls back to when its own
 * [PageLayer] no longer exists (e.g. the layer was deleted) - keeps orphaned content visible
 * instead of silently disappearing. */
const val DEFAULT_LAYER_ID = "default"

/**
 * One drawing layer on a page: strokes/images/stickers/text boxes each carry a `layerId`
 * pointing at one of these. Purely an editing-time organization aid - [visible] only affects
 * what the editor renders; export/PDF always includes every layer regardless of its toggle.
 */
@Serializable
data class PageLayer(
    val id: String,
    val name: String,
    val visible: Boolean = true
)

val DefaultPageLayers = listOf(PageLayer(DEFAULT_LAYER_ID, "Lớp 1"))
