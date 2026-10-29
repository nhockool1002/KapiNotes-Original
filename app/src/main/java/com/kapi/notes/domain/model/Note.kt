package com.kapi.notes.domain.model

/** Where a note's content originally came from. */
enum class NoteSource {
    BLANK,
    DRIVE_IMPORT
}

data class Note(
    val id: String,
    val title: String,
    val source: NoteSource,
    val driveFileId: String? = null,
    val driveFileName: String? = null,
    val thumbnailPath: String? = null,
    val isFavorite: Boolean = false,
    val isTrashed: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val pageCount: Int = 1,
    /** Short free-text label ("Công việc", "Ôn thi"...) with its own color, shown as a colored
     * chip on the dashboard card - lets notes be grouped/filtered by category, distinct from a
     * single page's own tag-color dot inside the editor. */
    val labelText: String? = null,
    val labelColorArgb: Long? = null
)
