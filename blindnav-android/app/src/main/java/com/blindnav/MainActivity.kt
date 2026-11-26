package com.blindnav

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.blindnav.core.MultiPress

class MainActivity : ComponentActivity() {
    private lateinit var controller: AppController
    private var pendingPermission: ((Boolean) -> Unit)? = null
    private val multiPress = MultiPress(3, 1500)

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        pendingPermission?.invoke(result.values.all { it })
        pendingPermission = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = AppController(
            this,
            askPermission = { perms, cb -> pendingPermission = cb; permissionLauncher.launch(perms) },
            keepScreenOn = { on ->
                if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            },
        )
        setContent { AppUi(controller) }
    }

    /** Any volume key cancels a running SOS countdown; three volume-down presses start one. Works while the app is open. */
    @SuppressLint("RestrictedApi") // known lint false positive on ComponentActivity.dispatchKeyEvent
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val vol = event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || event.keyCode == KeyEvent.KEYCODE_VOLUME_UP
        if (vol && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            if (controller.sosCounting) { controller.cancelSos(); return true }
            if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && multiPress.press(SystemClock.elapsedRealtime())) {
                controller.go(Screen.SOS)
                controller.triggerSos()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onStop() {
        controller.onBackground()
        super.onStop()
    }

    override fun onDestroy() {
        controller.shutdown()
        super.onDestroy()
    }
}
