package com.blindnav

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imageclassifier.ImageClassifier
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/** On-device OCR for Latin and Devanagari (English, Hindi). Kannada and Telugu go to the cloud OCR. */
object OcrReader {
    private val latin by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val devanagari by lazy { TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build()) }

    /** Runs both recognisers and keeps the one that found more text. */
    fun read(bmp: Bitmap, done: (String) -> Unit) {
        val img = InputImage.fromBitmap(bmp, 0)
        latin.process(img)
            .addOnSuccessListener { a ->
                devanagari.process(img)
                    .addOnSuccessListener { b -> done(listOf(a.text, b.text).maxByOrNull { it.trim().length }.orEmpty().trim()) }
                    .addOnFailureListener { done(a.text.trim()) }
            }
            .addOnFailureListener { done("") }
    }
}

/**
 * Banknote classifier. Needs app/src/main/assets/currency.tflite, which is NOT included: it has to be trained
 * (tools/train_currency.py). Labels expected: 10, 20, 50, 100, 200, 500, none.
 */
class CurrencyClassifier(private val context: Context) {
    val available: Boolean = runCatching { context.assets.list("")?.contains(MODEL) == true }.getOrDefault(false)

    private val classifier: ImageClassifier? by lazy {
        if (!available) null else runCatching {
            ImageClassifier.createFromOptions(
                context,
                ImageClassifier.ImageClassifierOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL).build())
                    .setRunningMode(RunningMode.IMAGE)
                    .setMaxResults(2)
                    .build(),
            )
        }.getOrNull()
    }

    /** Returns (label, score) of the top class, or null if there is no model. */
    fun classify(bmp: Bitmap): Pair<String, Float>? {
        val c = classifier ?: return null
        val top = c.classify(BitmapImageBuilder(bmp).build()).classificationResult().classifications()
            .firstOrNull()?.categories()?.firstOrNull() ?: return null
        return top.categoryName() to top.score()
    }

    companion object {
        const val MODEL = "currency.tflite"
        const val MIN_SCORE = 0.80f

        /** Confidence-gated sentence. Never claims certainty. */
        fun phrase(label: String, score: Float): String = when {
            label == "none" -> "I do not see a banknote. Hold one flat in good light and try again."
            score >= MIN_SCORE -> "Probably $label rupees. Please confirm by feel."
            else -> "I am not sure which note this is. Hold it flat in good light and try again."
        }
    }
}
