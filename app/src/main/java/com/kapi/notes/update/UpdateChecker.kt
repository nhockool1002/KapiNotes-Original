package com.kapi.notes.update

import com.kapi.notes.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

private const val GITHUB_OWNER = "nhockool1002"
private const val GITHUB_REPO = "KapiNotes-Original"
private const val RELEASES_LIST_URL =
    "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases"

/**
 * Where this build stands relative to GitHub's releases (the only "store" this app has while it
 * isn't on Play Store yet). [newerCount] is how many published releases are ahead of the
 * installed [BuildConfig.VERSION_NAME] - 0 means up to date, used to color the dashboard's
 * version badge and to decide whether the mandatory update dialog should appear at all.
 */
data class VersionStatus(
    val localVersion: String,
    val newerCount: Int,
    val latestVersion: String?,
    val latestReleaseNotes: String?,
    val latestReleaseUrl: String?
) {
    val isUpToDate: Boolean get() = newerCount <= 0
}

/**
 * The app isn't on Play Store yet, so there is no store update mechanism - this polls the
 * GitHub repo's releases instead. Every new release on GitHub must bump `versionName` in
 * app/build.gradle.kts past the previous one for this comparison to mean anything.
 */
@Singleton
class UpdateChecker @Inject constructor() {

    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    /** Fetches the repo's recent releases (newest first, per GitHub's default ordering) and
     * compares each against the installed version - used both for the mandatory "update
     * available" dialog and for the dashboard's small version badge (green/orange/red). */
    suspend fun checkStatus(): Result<VersionStatus> = withContext(Dispatchers.IO) {
        runCatching {
            val local = BuildConfig.VERSION_NAME
            val request = Request.Builder()
                .url(RELEASES_LIST_URL)
                // GitHub's API rejects requests with no User-Agent.
                .header("User-Agent", "KapiNotes-UpdateChecker")
                .header("Accept", "application/vnd.github+json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use VersionStatus(local, 0, null, null, null)
                }
                val body = response.body?.string().orEmpty()
                val releases = runCatching { json.decodeFromString<List<GithubRelease>>(body) }
                    .getOrDefault(emptyList())
                    .filter { !it.draft && !it.prerelease }

                val newer = releases.filter { isNewerVersion(it.tag_name.removePrefix("v").removePrefix("V"), local) }
                val latest = releases.firstOrNull()

                VersionStatus(
                    localVersion = local,
                    newerCount = newer.size,
                    latestVersion = latest?.tag_name?.removePrefix("v")?.removePrefix("V"),
                    latestReleaseNotes = latest?.body?.takeIf { it.isNotBlank() },
                    latestReleaseUrl = latest?.html_url
                )
            }
        }
    }

    /** Compares dotted numeric versions (e.g. "1.2.0" vs "1.10.0") part by part rather than as
     * plain strings, so "1.10.0" correctly counts as newer than "1.9.0". */
    private fun isNewerVersion(remote: String, local: String): Boolean {
        val remoteParts = remote.split(".", "-").mapNotNull { it.toIntOrNull() }
        val localParts = local.split(".", "-").mapNotNull { it.toIntOrNull() }
        val maxSize = maxOf(remoteParts.size, localParts.size)
        for (i in 0 until maxSize) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r != l) return r > l
        }
        return false
    }
}
