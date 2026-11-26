package com.blindnav

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executors

/**
 * One camera feed shared by live guidance, reading, currency and describe. No preview: blind users don't need one.
 * It is bound to a LifecycleOwner: the screen for one-off reads, the foreground service while guidance runs.
 */
class CameraSession(private val context: Context) {
    val executor = Executors.newSingleThreadExecutor()

    @Volatile var latest: Bitmap? = null
        private set
    @Volatile var lastFrameMs = 0L
        private set
    @Volatile var onFrame: ((Bitmap) -> Unit)? = null
    var running = false
        private set
    private var owner: LifecycleOwner? = null
    private var provider: ProcessCameraProvider? = null

    /** Starts the camera, or re-binds it if a different owner now needs it. */
    fun start(owner: LifecycleOwner, onError: () -> Unit) {
        if (running && this.owner === owner) return
        running = true
        this.owner = owner
        lastFrameMs = SystemClock.elapsedRealtime()
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                analysis.setAnalyzer(executor, ::analyze)
                provider = future.get().also {
                    it.unbindAll()
                    it.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, analysis)
                }
            } catch (e: Exception) {
                running = false
                this.owner = null
                onError()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        running = false
        owner = null
        provider?.unbindAll()
        latest = null
    }

    private fun analyze(image: ImageProxy) {
        try {
            val raw = image.toBitmap()
            val deg = image.imageInfo.rotationDegrees
            val bmp = if (deg == 0) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(deg.toFloat()) }, true)
            latest = bmp
            lastFrameMs = SystemClock.elapsedRealtime()
            onFrame?.invoke(bmp)
        } catch (e: Exception) {
            // frame skipped; the live watchdog reports it if frames stop completely
        } finally {
            image.close()
        }
    }
}
