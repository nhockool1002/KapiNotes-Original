# Add project specific ProGuard rules here.
-keep class com.kapi.notes.data.** { *; }

# --- kotlinx.serialization ---
# Standard rules from kotlinx.serialization's own docs: every @Serializable class gets a
# generated $serializer companion that R8 can't see is used purely via reflection/the Json{}
# machinery unless told to keep it explicitly. Scoped to this app's own packages (not a blanket
# kotlinx.serialization.** keep) since the library's own consumer rules already cover its
# internals - this only protects OUR @Serializable data classes from being stripped/renamed.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class com.kapi.notes.** {
    *** Companion;
}
-keepclasseswithmembers class com.kapi.notes.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.kapi.notes.**$$serializer { *; }
# Covers domain/model/*.kt (Stroke.kt, PageLayer.kt), update/GithubRelease.kt and
# sync/SyncApi.kt's ShareUploadResponse specifically - the blanket com.kapi.notes.data.** rule
# above doesn't reach these (different packages), and all are round-tripped through
# kotlinx.serialization (backup export/import, GitHub release version-check JSON, the sync
# Worker's /share response) where a renamed field would silently break JSON key matching.
-keep class com.kapi.notes.domain.model.** { *; }
-keep class com.kapi.notes.update.** { *; }
-keep class com.kapi.notes.sync.** { *; }

# --- Room ---
# Room's own generated _Impl/_Dao classes are already covered by the library's bundled consumer
# rules; the entities/DAOs themselves live under com.kapi.notes.data.local, already covered by
# the blanket com.kapi.notes.data.** keep above.

# --- ML Kit Digital Ink Recognition ---
# The native (.so) side calls back into these Java/Kotlin model classes by name via JNI - R8
# can't see that usage statically, so without this the recognizer silently fails at runtime
# instead of failing to compile.
-keep class com.google.mlkit.vision.digitalink.** { *; }
-keep class com.google.mlkit.common.** { *; }

# --- Hilt / Dagger ---
# Hilt ships its own consumer-proguard-rules.pro bundled in the library AARs (handles
# @HiltAndroidApp, @AndroidEntryPoint, generated *_HiltModules, *_Factory classes etc.
# automatically) - nothing extra needed here.
