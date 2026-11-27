package com.kapi.notes.ads

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.kapi.notes.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

/** Show the Monetag ad at most once every this many Editor -> Dashboard back navigations, so it
 * never fires on every single back press. Resets each time it fires, not each time it's checked. */
private const val EDITOR_RETURNS_PER_AD = 4

/**
 * Monetag has no native Android ad SDK (no AdView/InterstitialAd classes like AdMob) - its ad
 * formats are web-based (a JS tag or a plain URL). The most reliable way to show one from a native
 * app, without embedding a WebView running third-party ad scripts, is to open its Direct Link URL
 * in a Chrome Custom Tab: the user sees the ad page and closing/backing out of it returns them to
 * the app, the same visible effect as an interstitial. The counter is in-memory only (resets if the
 * process dies) - this is a per-session pacing cap, not a persisted limit, which is fine here.
 */
@Singleton
class AdsManager @Inject constructor() {
    private var editorBackCount = 0

    /** Call on every Editor -> Dashboard back navigation (after [AdsGate] already says ads should
     * show) - only actually opens the ad every [EDITOR_RETURNS_PER_AD]th call. */
    fun maybeShowOnEditorBack(context: Context) {
        editorBackCount++
        if (editorBackCount < EDITOR_RETURNS_PER_AD) return
        editorBackCount = 0
        CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(BuildConfig.MONETAG_DIRECT_LINK_URL))
    }
}
