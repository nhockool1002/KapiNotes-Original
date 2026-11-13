package com.kapi.notes.ads

import com.kapi.notes.data.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Accounts that never see ads, independent of any purchase - dev/test accounts only. Edit this
 * set directly and ship a new build to change it; there is no remote config or in-app UI for it
 * by design (see the Ads+Billing plan this came from). */
private val AD_FREE_EMAILS: Set<String> = setOf(
    // "someone@example.com"
)

/**
 * Single source of truth for "should this account see ads right now" - combines the hardcoded
 * allowlist above with (once it exists) a "remove ads" purchase state. There is no Billing
 * integration yet, so today this is purely the allowlist check; when a purchase flow ships, add
 * its StateFlow as a second input here (`shouldShowAds = !inAllowlist && !adsRemoved`) rather than
 * replacing this class, so Dashboard/Editor never need to change how they read it.
 */
@Singleton
class AdsGate @Inject constructor(
    private val authRepository: AuthRepository
) {
    val shouldShowAds: Flow<Boolean> = authRepository.session.map { session ->
        session?.email !in AD_FREE_EMAILS
    }
}
