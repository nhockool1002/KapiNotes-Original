plugins {
    id("com.android.application") version "8.5.2" apply false
    // Bumped from 1.9.24 to 2.0.21 so com.google.mlkit:digital-ink-recognition:19.0.0 (needed for
    // its 16KB-page-aligned native library - see app/build.gradle.kts) can be used: 19.0.0's own
    // Kotlin metadata requires a Kotlin 2.x compiler/stdlib, which 1.9.24 rejects outright at
    // compile time ("Module was compiled with an incompatible version of Kotlin").
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    // Kotlin 2.0+ moves the Compose compiler out of the Kotlin distribution and into this plugin -
    // composeOptions.kotlinCompilerExtensionVersion in app/build.gradle.kts is removed accordingly,
    // this plugin's own version is what now pins the Compose compiler.
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.google.dagger.hilt.android") version "2.51.1" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
}
