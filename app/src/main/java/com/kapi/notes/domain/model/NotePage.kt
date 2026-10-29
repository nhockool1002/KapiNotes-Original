package com.kapi.notes.domain.model

/**
 * One page of a note. [backgroundImagePath] is set when the page is a rendered bitmap of an
 * imported Drive document (PDF page / image) that the user is annotating on top of; it is null
 * for a plain paper page where [paperType] supplies the background pattern instead.
 */
data class NotePage(
    val id: String,
    val noteId: String,
    val pageIndex: Int,
    val paperType: PaperType,
    val orientation: PageOrientation,
    val flip: PageFlip,
    val widthPx: Int,
    val heightPx: Int,
    val backgroundImagePath: String? = null,
    /** Optional color dot shown on the page switcher, for grouping/filtering pages at a glance. */
    val tagColorArgb: Long? = null,
    /** Starred so it shows up in the page switcher's quick-jump list - for finding a spot fast in
     * a long note without scrolling through every page. */
    val isBookmarked: Boolean = false,
    /** Used for last-write-wins merging when two devices sync the same account's Drive backup. */
    val updatedAt: Long = System.currentTimeMillis(),
    /** This page's drawing layers (see [PageLayer]) - every stroke/image/sticker/text box on the
     * page points at one of these by id. Always has at least one entry. */
    val layers: List<PageLayer> = DefaultPageLayers,
    /** Newly drawn/placed content is stamped with this layer's id. */
    val activeLayerId: String = DEFAULT_LAYER_ID
)
