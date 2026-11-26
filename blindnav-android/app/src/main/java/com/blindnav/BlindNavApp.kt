package com.blindnav

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Process-wide pieces shared by the screen and the foreground service, so there is one camera, one voice, one detector. */
class BlindNavApp : Application() {
    val settings by lazy { Settings(this) }
    val alerter by lazy { Alerter(this, settings) }
    val camera by lazy { CameraSession(this) }
    val live by lazy { LiveGuidance(this, alerter, camera) }
    val share by lazy { LiveShare(this) }

    /** True while obstacle guidance is meant to be running. The service acts on this. */
    var liveOn by mutableStateOf(false)

    /** True while navigation is running (mirrors AppController, so the service can hold a location lock). */
    var navOn = false
}
