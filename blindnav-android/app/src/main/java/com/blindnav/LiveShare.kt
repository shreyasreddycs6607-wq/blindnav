package com.blindnav

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.blindnav.core.Fix

/**
 * Sends the phone's position to the server every ~15 s so a contact can watch it on the share link until it expires.
 * Runs inside the foreground service (location type), so it continues with the screen off.
 * If the phone loses signal or Android kills the app, updates stop; the link shows how old the last position is.
 */
class LiveShare(private val app: BlindNavApp) {
    var active by mutableStateOf(false)
        private set
    var untilMs by mutableLongStateOf(0L)
        private set

    private var track: Track? = null
    private val loc = LocationProvider(app)
    private val backend by lazy { Backend(app.settings) }
    private var lastPostMs = 0L

    fun begin(t: Track) {
        track = t
        untilMs = t.expiresAtSec * 1000
        lastPostMs = 0
        active = true
        loc.lastKnown()?.let { onFix(Fix(it, 0f)) } // first position right away, before the first GPS fix
        GuidanceService.sync(app)
    }

    /** Called by the service once it holds the location foreground type. */
    fun ensureRunning() { if (active) loc.start(::onFix) }

    private fun onFix(fix: Fix) {
        val t = track ?: return
        if (System.currentTimeMillis() > untilMs) {
            stop()
            app.alerter.say("Location sharing has ended.", Priority.USER)
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastPostMs < 15_000) return
        lastPostMs = now
        Bg.run({ backend.trackUpdate(t, fix) }) { /* transient failures are fine: the link shows the age of the last position */ }
    }

    fun stop() {
        val t = track
        track = null
        active = false
        loc.stop()
        if (t != null) Bg.run({ backend.trackStop(t) }) { }
        GuidanceService.sync(app)
    }
}
