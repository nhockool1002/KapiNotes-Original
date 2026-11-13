import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Kotlin 2.0+ no longer bundles the Compose compiler with the Kotlin distribution - this
    // plugin replaces composeOptions.kotlinCompilerExtensionVersion below (removed).
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

// Upload-key signing for Play Console release builds - key.properties + the keystore it points at
// are both gitignored (never committed) and only need to exist on a machine that actually builds
// a release AAB to upload. A fresh clone without this file still builds fine (release just stays
// unsigned, same as before this was added) - signingConfig is only wired up when the properties
// file is actually present, so CI/other contributors are never broken by its absence.
val keystorePropertiesFile = rootProject.file("key.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.kapi.notes"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kapi.notes"
        // Xiaomi Pad Pro 8 ships with Android 13+ (API 33+); minSdk 26 covers the pen/stylus
        // MotionEvent APIs (TOOL_TYPE_STYLUS, pressure, tilt) we rely on everywhere else too.
        minSdk = 26
        targetSdk = 36
        versionCode = 18
        versionName = "1.6.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        // Sync now goes through our own Cloudflare Worker (see sync-worker/) instead of each
        // user's Google Drive - this avoids the drive.readonly *restricted* scope entirely, so
        // publishing to production only needs Google's lightweight standard verification, not a
        // paid CASA security assessment. The Worker only accepts Google ID tokens whose "aud"
        // matches this web client id (sync-worker/wrangler.toml's ALLOWED_AUDIENCES).
        buildConfigField("String", "SYNC_BASE_URL", "\"https://kapinotes-sync.kapinotes.workers.dev\"")
        buildConfigField(
            "String",
            "GOOGLE_SERVER_CLIENT_ID",
            "\"440629560986-7itdq5l08fde0nfp4c9ci01a1nhq0v4b.apps.googleusercontent.com\""
        )

        // AdMob - ads only for now, no paid "remove ads" tier yet (see AdsGate). The App ID also
        // needs to be duplicated into AndroidManifest.xml's APPLICATION_ID meta-data since the SDK
        // reads it from there at startup, not from BuildConfig.
        buildConfigField("String", "ADMOB_APP_ID", "\"ca-app-pub-2312438042296128~7670996988\"")
        buildConfigField("String", "ADMOB_BANNER_AD_UNIT_ID", "\"ca-app-pub-2312438042296128/6444565289\"")
        buildConfigField("String", "ADMOB_INTERSTITIAL_AD_UNIT_ID", "\"ca-app-pub-2312438042296128/7566075268\"")
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Was false ("DEX code optimization is below our threshold / Obfuscation 0%" in Play
            // Console's pre-launch report). Paired with the keep rules in proguard-rules.pro for
            // every reflection-dependent spot in this app (kotlinx.serialization data classes,
            // ML Kit's native->Java callback classes) - Room/Hilt are covered by their own
            // bundled consumer rules. shrinkResources requires isMinifyEnabled too.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            // Would bundle native debug symbols into the AAB so Play Console can symbolicate
            // native crashes/ANRs - in practice this is a no-op here: the only .so files in the
            // app (libdatastore_shared_counter.so from AndroidX DataStore, libdigitalink.so from
            // ML Kit) ship pre-stripped by their vendors with no debug info to extract (confirmed
            // via `gradle :app:stripReleaseDebugSymbols` logging "Unable to strip ... packaging
            // them as they are"). Left set in case a future native dependency DOES ship symbols,
            // but Play Console's "no native debug symbols uploaded" warning on this project is
            // expected and not fixable from our side - informational only, never blocks publishing.
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
        debug {
            // Restored (was briefly dropped in 1.4.5, which broke sign-in worse): the ONE OAuth
            // Android client that actually exists in Google Cloud Console is registered for
            // "com.kapi.notes.debug", not "com.kapi.notes" - confirmed directly in the console.
            // Every debug APK is what's actually been tested with Google Sign-In working, so the
            // debug applicationId has to keep matching that registration. The plain "com.kapi.notes"
            // release package (what GitHub releases ship) has never had its own registered client,
            // so Drive sign-in in a release build still needs a SECOND OAuth Android client created
            // for "com.kapi.notes" with the same SHA-1 - see README.md section 4.
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }
}

dependencies {
    // Core / Compose
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-android-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Room (local persistence - notes, pages, strokes live only on-device for now)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // DataStore (session / settings)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Kotlinx serialization (backup export/import, stroke point payloads)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Google Sign-In (identity only - email + ID token, no Drive/API scope at all; see
    // auth/GoogleAuthManager.kt). Sync and sharing both go through our own Cloudflare Worker
    // (sync/SyncApi.kt) instead of any Google storage API, so none of the old
    // google-api-client-android / google-apis:google-api-services-drive /
    // google-http-client-android dependencies are needed anymore - dropping them also removes
    // the org.apache.httpcomponents-exclusion dance they used to need for R8.
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // AdMob - banner (Dashboard) + interstitial (Editor -> Dashboard), see AdsGate/AdsManager.
    // No Billing library yet: there is no paid "remove ads" tier, only the hardcoded allowlist.
    implementation("com.google.android.gms:play-services-ads:23.6.0")

    // Image loading for thumbnails / imported pages
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Google Play In-App Updates - checks the Play Store listing itself for a newer published
    // version and lets the user update without leaving the app. No-ops safely (reports no
    // update) on a build not installed through Play Store, e.g. a debug build or a GitHub-
    // distributed APK - UpdateChecker above remains the update path for those installs.
    implementation("com.google.android.play:app-update:2.1.0")

    // On-device handwriting-to-text ("Nhận dạng chữ viết tay") - recognizes ink straight from
    // the same StrokePoint coordinates already captured for drawing, no network/cloud needed.
    // 19.0.0 (not 18.1.0): the older release's libdigitalink.so is only 4KB-page-aligned, which
    // Play Console flags as "Recompile your app with 16 KB native library alignment" - 19.0.0's
    // native libs are built with the required 16KB alignment (verified via its ELF program
    // headers), fixing the warning without any API-level source changes.
    implementation("com.google.mlkit:digital-ink-recognition:19.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // Plain OkHttp client for the sync Worker (sync/SyncApi.kt) - no Retrofit, just a couple of
    // GET/PUT/POST calls to one endpoint, not worth the extra abstraction.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
