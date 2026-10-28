package com.kapi.notes.domain.model

/** An audio recording attached to a note (e.g. a lecture recorded while taking notes). */
data class AudioClip(
    val id: String,
    val noteId: String,
    val filePath: String,
    val durationMs: Long,
    val createdAt: Long
)
