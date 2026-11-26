package com.blindnav

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService

/**
 * Keeps obstacle guidance and navigation alive with the screen off or the app in the background.
 * Android only allows this with a visible notification, and the camera type must be started while the app is on screen.
 * The service owns the camera binding while guidance runs. Start and stop it only through sync().
 * ponytail: START_NOT_STICKY, so if Android kills the process guidance does not restart itself. Some phone makers
 * kill background apps aggressively; users may need to exempt BlindNav from battery optimisation.
 */
class GuidanceService : LifecycleService() {
    private var wake: PowerManager.WakeLock? = null

    private fun has(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val app = application as BlindNavApp
        if (intent?.action == ACTION_STOP_GUIDANCE) app.liveOn = false

        val wantCamera = app.liveOn && has(Manifest.permission.CAMERA)
        val sharing = app.share.active
        val wantLocation = (app.navOn || sharing) && has(Manifest.permission.ACCESS_FINE_LOCATION)
        var types = 0
        if (wantCamera) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        if (wantLocation) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION

        if (types == 0) { stopAll(app); return START_NOT_STICKY }
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(wantCamera, app.navOn && wantLocation, sharing && wantLocation), types)
        } catch (e: Exception) {
            // Android refused (e.g. camera type started from the background). Say so rather than fail silently.
            app.liveOn = false
            app.alerter.say("Android did not allow guidance to run in the background. Open the app and start it again.", Priority.HAZARD, "en-IN")
            stopAll(app)
            return START_NOT_STICKY
        }

        if (sharing && wantLocation) app.share.ensureRunning()

        if (wantCamera) {
            app.camera.start(this) { app.alerter.say("Could not start the camera.", Priority.HAZARD, "en-IN") }
            app.live.start()
            holdWake()
        } else {
            app.live.stop()
            app.camera.stop()
            releaseWake()
        }
        return START_NOT_STICKY
    }

    private fun stopAll(app: BlindNavApp) {
        app.live.stop()
        app.camera.stop()
        releaseWake()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun holdWake() {
        if (wake?.isHeld == true) return
        wake = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "blindnav:guidance").apply { acquire(4 * 60 * 60 * 1000L) }
    }

    private fun releaseWake() { wake?.takeIf { it.isHeld }?.release(); wake = null }

    private fun notification(camera: Boolean, navigation: Boolean, sharing: Boolean): android.app.Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Guidance", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, GuidanceService::class.java).setAction(ACTION_STOP_GUIDANCE), PendingIntent.FLAG_IMMUTABLE)
        val parts = buildList {
            if (camera) add("obstacle guidance")
            if (navigation) add("navigation")
            if (sharing) add("live location sharing")
        }
        val text = parts.joinToString(", ").replaceFirstChar { it.uppercase() } + " running." +
            if (camera) " This does not replace your cane." else ""
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("BlindNav")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Stop guidance", stop)
            .build()
    }

    override fun onDestroy() {
        val app = application as BlindNavApp
        if (app.liveOn) { // killed by the system while guidance was meant to run
            app.liveOn = false
            app.alerter.say("Obstacle guidance stopped. Use your cane.", Priority.HAZARD, "en-IN")
        }
        app.live.stop()
        app.camera.stop()
        releaseWake()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    companion object {
        const val ACTION_STOP_GUIDANCE = "com.blindnav.STOP_GUIDANCE"
        private const val CHANNEL = "guidance"
        private const val NOTIFICATION_ID = 1

        /** Reconciles the service with app.liveOn / app.navOn. Call after either changes, from a visible activity. */
        fun sync(context: Context) {
            val app = context.applicationContext as BlindNavApp
            val intent = Intent(context, GuidanceService::class.java)
            if (app.liveOn || app.navOn || app.share.active) {
                // Android refuses to start a foreground service from the background (e.g. while the screen is closing).
                // Then the running service just keeps its current state.
                try { ContextCompat.startForegroundService(context, intent) } catch (e: IllegalStateException) { }
            } else context.stopService(intent)
        }
    }
}
