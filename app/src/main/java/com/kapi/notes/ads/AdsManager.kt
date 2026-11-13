package com.kapi.notes.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.kapi.notes.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

/** Show an interstitial at most once every this many Editor -> Dashboard back navigations, so it
 * never fires on every single back press. Resets each time it fires, not each time it loads. */
private const val EDITOR_RETURNS_PER_INTERSTITIAL = 4

/**
 * Loads and shows the interstitial ad, with its own frequency cap. The counter is in-memory only
 * (resets if the process dies) - this is a per-session pacing cap, not a persisted limit, so that
 * is an acceptable simplification. Banner ads don't need a manager: DashboardScreen just creates
 * an AdView directly, gated by [AdsGate].
 */
@Singleton
class AdsManager @Inject constructor() {
    private var interstitialAd: InterstitialAd? = null
    private var isLoading = false
    private var editorBackCount = 0

    fun preloadInterstitial(context: Context) {
        if (interstitialAd != null || isLoading) return
        isLoading = true
        InterstitialAd.load(
            context,
            BuildConfig.ADMOB_INTERSTITIAL_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isLoading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    isLoading = false
                }
            }
        )
    }

    /** Call on every Editor -> Dashboard back navigation (after [AdsGate] already says ads should
     * show) - only actually shows the ad every [EDITOR_RETURNS_PER_INTERSTITIAL]th call, and only
     * if one finished loading in time; otherwise it's a no-op so back navigation is never blocked
     * waiting on an ad. */
    fun maybeShowOnEditorBack(activity: Activity) {
        editorBackCount++
        if (editorBackCount < EDITOR_RETURNS_PER_INTERSTITIAL) return
        val ad = interstitialAd ?: run { preloadInterstitial(activity); return }
        editorBackCount = 0
        interstitialAd = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() = preloadInterstitial(activity)
            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) = preloadInterstitial(activity)
        }
        ad.show(activity)
    }
}
