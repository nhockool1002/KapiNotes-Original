package com.kapi.notes.domain.model

enum class DriveFileKind {
    PDF,
    IMAGE,
    FOLDER,
    OTHER
}

data class DriveFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val kind: DriveFileKind,
    val modifiedTime: Long,
    val sizeBytes: Long?,
    val iconLink: String?
)
