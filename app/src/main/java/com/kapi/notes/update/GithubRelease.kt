package com.kapi.notes.update

import kotlinx.serialization.Serializable

/** Minimal slice of GitHub's "get latest release" API response (the rest is ignored). */
@Serializable
data class GithubRelease(
    val tag_name: String,
    val name: String? = null,
    val body: String? = null,
    val html_url: String,
    val prerelease: Boolean = false,
    val draft: Boolean = false
)
