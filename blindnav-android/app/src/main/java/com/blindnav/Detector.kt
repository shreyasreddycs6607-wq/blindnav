package com.blindnav

import android.content.Context
import android.graphics.Bitmap
import com.blindnav.core.Box
import com.blindnav.core.Detection
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector

/** On-device COCO detector. Model file: app/src/main/assets/efficientdet_lite0.tflite (see README). */
class Detector(context: Context) : AutoCloseable {
    private val detector = ObjectDetector.createFromOptions(
        context,
        ObjectDetector.ObjectDetectorOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL).build())
            .setRunningMode(RunningMode.IMAGE)
            .setScoreThreshold(0.4f)
            .setMaxResults(10)
            .build(),
    )

    fun detect(bmp: Bitmap): List<Detection> {
        val w = bmp.width.toFloat()
        val h = bmp.height.toFloat()
        return detector.detect(BitmapImageBuilder(bmp).build()).detections().mapNotNull { d ->
            val c = d.categories().firstOrNull() ?: return@mapNotNull null
            val r = d.boundingBox()
            Detection(
                c.categoryName(),
                Box(
                    (r.left / w).coerceIn(0f, 1f), (r.top / h).coerceIn(0f, 1f),
                    (r.right / w).coerceIn(0f, 1f), (r.bottom / h).coerceIn(0f, 1f),
                ),
                c.score(),
            )
        }
    }

    override fun close() = detector.close()

    companion object {
        const val MODEL = "efficientdet_lite0.tflite"

        /** Mean luma of a sparse pixel grid, 0..255. Used to refuse guidance in the dark instead of staying silent. */
        fun meanLuma(bmp: Bitmap): Float {
            var sum = 0f
            var n = 0
            for (y in 0 until 16) for (x in 0 until 16) {
                val p = bmp.getPixel((x + 0.5f).times(bmp.width / 16f).toInt().coerceAtMost(bmp.width - 1),
                    (y + 0.5f).times(bmp.height / 16f).toInt().coerceAtMost(bmp.height - 1))
                sum += 0.299f * ((p shr 16) and 0xFF) + 0.587f * ((p shr 8) and 0xFF) + 0.114f * (p and 0xFF)
                n++
            }
            return sum / n
        }
    }
}
