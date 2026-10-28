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
    val pageCount: Int = 1
)
