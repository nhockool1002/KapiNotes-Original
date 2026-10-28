package com.kapi.notes.domain.model

enum class TombstoneKind {
    NOTE,
    PAGE,
    STROKE,
    STICKER,
    IMAGE,
    TEXT_BOX,
    AUDIO_CLIP
}

/**
 * A record that something was deleted, kept around so that merging in an older copy of it from
 * another device's Drive sync doesn't resurrect it. Without this, pulling a backup that still
 * has a stroke/note you erased on this device (before that device had a chance to sync) would
 * bring it back - see [com.kapi.notes.data.repository.BackupRepository.importFromFile].
 */
data class Tombstone(
    val id: String,
    val kind: TombstoneKind,
    val deletedAt: Long
)
