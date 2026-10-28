package com.kapi.notes.ocr

import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.Ink
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.kapi.notes.domain.model.Stroke
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Language the Lasso tool's "Nhận dạng chữ" button recognizes ink as - picked by the user next
 * to that button, defaulting to [CHINESE] on 田字格 (Chinese-character-grid) paper and [ENGLISH]
 * everywhere else. Vietnamese isn't in ML Kit's ink model catalogue at all, so it isn't offered
 * here; the English model still reads unaccented Vietnamese strokes reasonably. */
enum class RecognitionLanguage(val languageTag: String, val labelVi: String) {
    ENGLISH("en-US", "Tiếng Anh"),
    CHINESE("zh-Hani-CN", "Chữ Hán");

    val modelIdentifier: DigitalInkRecognitionModelIdentifier
        get() = requireNotNull(DigitalInkRecognitionModelIdentifier.fromLanguageTag(languageTag)) {
            "No ink model for $languageTag"
        }
}

/**
 * Wraps ML Kit's on-device Digital Ink Recognition - "Nhận dạng chữ viết tay" in the Lasso
 * toolbar: converts a lasso-selected group of strokes straight from their [StrokePoint]
 * coordinates (no bitmap/OCR round-trip needed) into plain text, which the caller then inserts
 * as a [com.kapi.notes.domain.model.TextBoxItem]. A separate on-device model is downloaded (once,
 * cached thereafter) per [RecognitionLanguage] the user has actually used.
 */
@Singleton
class HandwritingRecognizer @Inject constructor() {
    private val modelManager = RemoteModelManager.getInstance()
    private val recognizers = mutableMapOf<RecognitionLanguage, DigitalInkRecognizer>()

    private suspend fun ensureModelReady(language: RecognitionLanguage): DigitalInkRecognizer {
        recognizers[language]?.let { return it }
        val model = DigitalInkRecognitionModel.builder(language.modelIdentifier).build()
        val isDownloaded = modelManager.isModelDownloaded(model).await()
        if (!isDownloaded) {
            modelManager.download(model, DownloadConditions.Builder().build()).await()
        }
        val r = DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(model).build())
        recognizers[language] = r
        return r
    }

    /** Recognizes the text drawn by [strokes] (sorted by creation time so stroke order is
     * preserved) as [language] - returns null if nothing was recognized or the model couldn't be
     * prepared. */
    suspend fun recognize(strokes: List<Stroke>, language: RecognitionLanguage = RecognitionLanguage.ENGLISH): String? {
        if (strokes.isEmpty()) return null
        return runCatching {
            val builder = Ink.builder()
            strokes.sortedBy { it.createdAt }.forEach { stroke ->
                val strokeBuilder = Ink.Stroke.builder()
                stroke.points.forEach { p -> strokeBuilder.addPoint(Ink.Point.create(p.x, p.y)) }
                builder.addStroke(strokeBuilder.build())
            }
            val ink = builder.build()
            val recognizer = ensureModelReady(language)
            val result = recognizer.recognize(ink).await()
            result.candidates.firstOrNull()?.text
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }
}
