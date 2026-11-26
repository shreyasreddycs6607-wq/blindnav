package com.blindnav

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.blindnav.core.HazardEngine

/**
 * Detector -> hazard rules -> alerts, plus a watchdog. Consumes frames from the shared CameraSession.
 * Runs only while the activity is visible (no foreground service yet, see README).
 */
class LiveGuidance(private val context: android.content.Context, private val alerter: Alerter, private val camera: CameraSession) {
    private val main = Handler(Looper.getMainLooper())
    private val engine = HazardEngine()
    @Volatile private var detector: Detector? = null
    private var lastWatchdogMs = 0L
    private var darkAnnounced = false
    @Volatile var active = false
        private set

    private val watchdog = object : Runnable {
        override fun run() {
            if (!active) return
            val now = SystemClock.elapsedRealtime()
            if (now - camera.lastFrameMs > STALL_MS && now - lastWatchdogMs > STALL_REPEAT_MS) {
                lastWatchdogMs = now
                alerter.say("Obstacle detection stopped. Use your cane.", Priority.HAZARD, "en-IN")
            }
            main.postDelayed(this, 1000)
        }
    }

    /** The camera must already be running. */
    fun start() {
        if (active) return
        active = true
        alerter.say("Starting. This app does not replace your cane.", Priority.USER)
        camera.executor.execute {
            try {
                detector = Detector(context)
                camera.onFrame = ::process
            } catch (e: Exception) {
                active = false
                alerter.say("Could not load the obstacle detector.", Priority.HAZARD, "en-IN")
            }
        }
        main.postDelayed(watchdog, 1000)
    }

    fun stop() {
        if (!active) return
        active = false
        main.removeCallbacks(watchdog)
        camera.onFrame = null
        camera.executor.execute { detector?.close(); detector = null } // same thread as frames: no use-after-close
        alerter.say("Obstacle detection stopped.", Priority.USER)
    }

    private fun process(bmp: Bitmap) {
        try {
            if (Detector.meanLuma(bmp) < DARK_LUMA) {
                // Silence would read as "all clear", so say it once.
                if (!darkAnnounced) { darkAnnounced = true; alerter.say("Too dark for obstacle detection.", Priority.HAZARD, "en-IN") }
                return
            }
            darkAnnounced = false
            detector?.let { d -> engine.update(d.detect(bmp)).firstOrNull()?.let(alerter::alert) }
        } catch (e: Exception) {
            // frame skipped; the watchdog reports it if frames stop completely
        }
    }

    companion object {
        const val STALL_MS = 3000L
        const val STALL_REPEAT_MS = 10_000L
        const val DARK_LUMA = 25f // uncalibrated, tune per phone
    }
}
