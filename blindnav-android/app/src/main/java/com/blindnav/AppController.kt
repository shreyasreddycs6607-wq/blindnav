package com.blindnav

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.blindnav.core.Command
import com.blindnav.core.CommandParser
import com.blindnav.core.LatLon
import com.blindnav.core.RouteTracker
import com.blindnav.core.Script
import com.blindnav.core.SosMachine
import com.blindnav.core.SosMessage
import com.blindnav.core.Whatsapp

enum class Screen { HOME, LIVE, ASK, NAV, READER, CURRENCY, SOS, SETTINGS, PRIVACY }

/** All behaviour lives here so the UI stays a thin layer of big buttons. */
class AppController(
    private val act: ComponentActivity,
    private val askPermission: (Array<String>, (Boolean) -> Unit) -> Unit,
    private val keepScreenOn: (Boolean) -> Unit,
) {
    private val app = act.application as BlindNavApp
    val settings = app.settings
    val alerter = app.alerter
    private val backend = Backend(settings)
    private val camera = app.camera
    private val voice = VoiceInput(act, settings, alerter)
    private val location = LocationProvider(act)
    private val currency by lazy { CurrencyClassifier(act) }
    private val main = Handler(Looper.getMainLooper())
    private val sos = SosMachine(5)
    private var tracker: RouteTracker? = null
    private val history = mutableListOf<Pair<String, String>>() // short in-session memory for questions

    var screen by mutableStateOf(Screen.HOME)
        private set
    var status by mutableStateOf("")
        private set
    val liveOn get() = app.liveOn // owned by the app/service so it survives the screen closing
    var navOn by mutableStateOf(false)
        private set
    var sosActive by mutableStateOf(false)
        private set

    private val sosTick = object : Runnable {
        override fun run() {
            when (val o = sos.tick()) {
                is SosMachine.Out.Say -> alerter.say(o.text, Priority.SOS)
                SosMachine.Out.Fire -> { sosActive = false; fireSos(); return }
                SosMachine.Out.None -> {}
            }
            if (sos.active) main.postDelayed(this, 1000)
        }
    }

    fun go(s: Screen) {
        screen = s; status = ""
        // Ask now, not during an emergency: SOS needs the location and the phone-call permission.
        if (s == Screen.SOS) ensure(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.CALL_PHONE)) { }
    }

    private fun say(text: String, p: Priority = Priority.USER) { status = text; alerter.say(text, p) }

    private fun cloudReady() = settings.cloudEnabled && settings.backendUrl.isNotBlank()

    private fun needCloud(what: String): Boolean {
        if (cloudReady()) return true
        say("$what needs internet. Turn on cloud features and set the server address in Settings.")
        return false
    }

    private fun ensure(perms: Array<String>, then: () -> Unit) {
        val missing = perms.filter { ContextCompat.checkSelfPermission(act, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) then()
        else askPermission(missing.toTypedArray()) { ok -> if (ok) then() else say("Permission is needed. Allow it in the phone's app settings.") }
    }

    // ---------- camera ----------
    private fun ensureCamera(then: () -> Unit) = ensure(arrayOf(Manifest.permission.CAMERA)) {
        if (!camera.running) camera.start(act) { say("Could not start the camera.", Priority.HAZARD) }
        then()
    }

    private fun releaseCameraIfIdle() { if (!liveOn) camera.stop() }

    /** Called when the screen leaves the foreground: a screen-bound camera would stop anyway, so reset our state. */
    fun onBackground() { if (!liveOn) camera.stop() }

    private fun syncService() = GuidanceService.sync(act)

    private fun setNav(on: Boolean) { navOn = on; app.navOn = on; syncService() }

    private fun askNotifications(then: () -> Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(act, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) askPermission(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) { then() } // optional: guidance still runs if refused
        else then()
    }

    /** Waits up to ~3 s for a frame, then hands it over. */
    private fun withFrame(cb: (Bitmap) -> Unit) = ensureCamera {
        var tries = 0
        val poll = object : Runnable {
            override fun run() {
                val f = camera.latest
                when {
                    f != null -> cb(f)
                    ++tries > 30 -> { say("The camera is not ready. Try again."); releaseCameraIfIdle() }
                    else -> main.postDelayed(this, 100)
                }
            }
        }
        poll.run()
    }

    // ---------- live guidance ----------
    /** The foreground service does the actual work, so guidance keeps running with the screen off. */
    fun toggleLive() {
        if (liveOn) {
            app.liveOn = false; keepScreenOn(false); syncService(); status = "Guidance stopped."
        } else ensure(arrayOf(Manifest.permission.CAMERA)) {
            askNotifications {
                camera.stop() // drop any screen-bound session; the service re-binds the camera to itself
                app.liveOn = true; keepScreenOn(true); syncService()
                status = "Guidance running. You can turn the screen off."
            }
        }
    }

    // ---------- scene and questions ----------
    fun describeScene() {
        if (!needCloud("Scene description")) return
        say("Looking.")
        withFrame { f ->
            Bg.run({ backend.scene(f, settings.language) }) { r ->
                releaseCameraIfIdle()
                r.onSuccess { say(it) }.onFailure { say(it.message ?: "Could not describe the scene.") }
            }
        }
    }

    fun askQuestion(question: String? = null) {
        if (!needCloud("Questions")) return
        if (question == null) {
            say("Ask your question after the beep.")
            voice.listen({ askQuestion(it) }, { say(it) })
            return
        }
        say("Looking.")
        withFrame { f ->
            Bg.run({ backend.ask(f, question, settings.language, history.toList()) }) { r ->
                releaseCameraIfIdle()
                r.onSuccess {
                    history += "user" to question; history += "assistant" to it
                    while (history.size > 6) history.removeAt(0)
                    say(it)
                }.onFailure { say(it.message ?: "Could not answer.") }
            }
        }
    }

    // ---------- reading ----------
    fun readText() {
        say("Hold the phone steady over the text.")
        main.postDelayed({
            withFrame { f ->
                val tag = settings.language
                val indic = tag.startsWith("kn") || tag.startsWith("te")
                fun finish(t: String) { releaseCameraIfIdle(); status = t.take(400); alerter.speakLong(t, Script.languageTag(t)) }
                fun nothing() { releaseCameraIfIdle(); say("I could not find any text. Move closer, hold steady, and try again.") }
                fun cloud(onFail: () -> Unit) {
                    if (!cloudReady()) { onFail(); return }
                    Bg.run({ backend.ocr(f, tag) }) { r -> r.onSuccess { if (it.isBlank()) onFail() else finish(it) }.onFailure { onFail() } }
                }
                fun local(onFail: () -> Unit) = OcrReader.read(f) { t -> if (t.isBlank()) onFail() else finish(t) }
                if (indic) cloud { if (!cloudReady()) say("Kannada and Telugu reading needs cloud features. Turn them on in Settings.") else nothing() }
                else local { cloud { nothing() } }
            }
        }, 2500)
    }

    // ---------- currency ----------
    fun readCurrency() {
        say("Hold the note flat in front of the camera.")
        main.postDelayed({
            withFrame { f ->
                val r = currency.classify(f)
                when {
                    r != null -> { releaseCameraIfIdle(); say(CurrencyClassifier.phrase(r.first, r.second)) }
                    cloudReady() -> Bg.run({
                        backend.ask(f, "Which Indian rupee banknote is this? Reply with only the denomination in rupees, or say you are not sure.", "en-IN")
                    }) { res ->
                        releaseCameraIfIdle()
                        res.onSuccess { say("Cloud guess: $it. Please confirm by feel.") }.onFailure { say(it.message ?: "Could not check the note.") }
                    }
                    else -> { releaseCameraIfIdle(); say("The currency model is not installed and cloud features are off. See the README to train or enable it.") }
                }
            }
        }, 1500)
    }

    // ---------- navigation ----------
    fun navigateByVoice() {
        say("Say where you want to go, after the beep.")
        voice.listen({ navigateTo(it) }, { say(it) })
    }

    fun navigateTo(dest: String) {
        if (!needCloud("Navigation")) return
        ensure(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) {
            say("Searching for $dest.")
            Bg.run({ backend.search(dest, location.lastKnown()) }) { r ->
                r.onFailure { say(it.message ?: "Search failed.") }
                r.onSuccess { list ->
                    val p = list.firstOrNull() ?: return@onSuccess say("I could not find $dest.")
                    say("Found ${p.name}. Planning a walking route.")
                    val here = location.lastKnown()
                    if (here != null) planRoute(here, p.pos) else {
                        say("Waiting for a GPS signal. Stay outdoors.")
                        location.start { fix -> location.stop(); planRoute(fix.pos, p.pos) }
                    }
                }
            }
        }
    }

    private fun planRoute(from: LatLon, to: LatLon) {
        Bg.run({ backend.route(from, to) }) { r ->
            r.onFailure { say(it.message ?: "Could not plan a route.") }
            r.onSuccess { (route, meters) ->
                tracker = RouteTracker(route)
                setNav(true)
                say("Route ready, about $meters metres. Walking directions only: keep using your cane or guide dog. I do not detect crossings.")
                location.start { fix ->
                    tracker?.update(fix)?.let { say(it, Priority.NAV) }
                    if (tracker?.arrived == true) stopNavigation(silent = true)
                }
            }
        }
    }

    fun stopNavigation(silent: Boolean = false) {
        location.stop(); tracker = null; setNav(false)
        if (!silent) say("Navigation stopped.")
    }

    // ---------- SOS ----------
    fun triggerSos() {
        val o = sos.trigger()
        if (o is SosMachine.Out.Say) {
            sosActive = true
            say(o.text, Priority.SOS)
            main.postDelayed(sosTick, 1000)
        }
    }

    fun cancelSos() {
        val o = sos.cancel()
        main.removeCallbacks(sosTick)
        sosActive = false
        if (o is SosMachine.Out.Say) say(o.text, Priority.SOS)
    }

    val sosCounting get() = sos.active

    private fun fireSos() {
        val contacts = settings.contacts
        if (contacts.isEmpty()) {
            say("S O S could not be sent: no emergency contact is saved. Add one in Settings.", Priority.SOS)
            return
        }
        say("Sending S O S.", Priority.SOS)
        val here = location.lastKnown()
        val hours = settings.shareHours
        val first = contacts.first()

        fun callFirst(prefix: String) {
            val uri = Uri.parse("tel:" + first.number)
            val granted = ContextCompat.checkSelfPermission(act, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
            runCatching { act.startActivity(Intent(if (granted) Intent.ACTION_CALL else Intent.ACTION_DIAL, uri)) }
            say(prefix + (if (granted) "Calling ${first.name}." else "Press call to ring ${first.name}."), Priority.SOS)
        }

        // Runs once: either when the live link is ready, or after 4 s without it. An emergency must not wait on the network.
        val delivered = java.util.concurrent.atomic.AtomicBoolean(false)
        fun deliver(liveUrl: String?) {
            if (!delivered.compareAndSet(false, true)) return
            if (cloudReady()) { // text every contact from the server, in parallel; its result is spoken a little later
                val serverText = SosMessage.build(here, liveUrl, hours, includeLocation = false)
                Bg.run({ backend.notifySos(contacts.map { it.number }, here, serverText) }) { r ->
                    val note = r.fold({ "Text alert sent to ${it.size}." }, { "Text alert failed." })
                    main.postDelayed({ say(note, Priority.SOS) }, 7000)
                }
            }
            val opened = settings.sosMethod == "whatsapp" && openWhatsApp(first, SosMessage.build(here, liveUrl, hours))
            if (opened) {
                val live = if (liveUrl != null) " Your live location is being shared for $hours hours." else " Live location is not available."
                say("WhatsApp is open with your message to ${first.name}. Find the send button at the bottom right and press it.$live", Priority.SOS)
            } else callFirst(if (settings.sosMethod == "whatsapp") "WhatsApp is not installed. " else "")
        }

        if (cloudReady()) {
            main.postDelayed({ deliver(null) }, 4000)
            Bg.run({ backend.trackStart(hours) }) { r ->
                r.onSuccess { t ->
                    if (delivered.get()) Bg.run({ backend.trackStop(t) }) { } // too late for the message: don't leave an unknown live link running
                    else { app.share.begin(t); deliver(t.url) }
                }.onFailure { deliver(null) }
            }
        } else deliver(null)
    }

    private fun openWhatsApp(c: Contact, text: String): Boolean {
        val uri = Uri.parse(Whatsapp.url(c.number, text))
        for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) { // WhatsApp, WhatsApp Business
            try { act.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage(pkg)); return true }
            catch (e: android.content.ActivityNotFoundException) { }
        }
        return false
    }

    val sharing get() = app.share.active
    val shareUntilMs get() = app.share.untilMs

    fun stopSharing() { app.share.stop(); say("Location sharing stopped.") }

    fun toggleSosMethod() {
        settings.sosMethod = if (settings.sosMethod == "whatsapp") "call" else "whatsapp"
        say(if (settings.sosMethod == "whatsapp") "S O S opens WhatsApp." else "S O S calls your first contact.")
    }

    fun cycleShareHours() {
        settings.shareHours = when (settings.shareHours) { 1 -> 8; 8 -> 15; else -> 1 }
        say("Live location will be shared for ${settings.shareHours} hours after an S O S.")
    }

    // ---------- voice commands ----------
    fun voiceCommand() {
        say("Listening.")
        ensure(arrayOf(Manifest.permission.RECORD_AUDIO)) { voice.listen({ run(CommandParser.parse(it)) }, { say(it) }) }
    }

    fun run(c: Command) {
        when (c) {
            Command.StartGuidance -> if (!liveOn) toggleLive()
            Command.StopGuidance -> { if (liveOn) toggleLive(); if (navOn) stopNavigation(silent = true); alerter.stop(); status = "Stopped." }
            Command.Read -> { go(Screen.READER); readText() }
            Command.Currency -> { go(Screen.CURRENCY); readCurrency() }
            Command.Describe -> { go(Screen.ASK); describeScene() }
            Command.Sos -> { go(Screen.SOS); triggerSos() }
            Command.CancelSos -> cancelSos()
            Command.StopSharing -> stopSharing()
            Command.Repeat -> alerter.repeatLast()
            is Command.Navigate -> { go(Screen.NAV); navigateTo(c.destination) }
            is Command.Ask -> { go(Screen.ASK); askQuestion(c.question) }
        }
    }

    // ---------- settings ----------
    fun cycleLanguage() {
        val l = Settings.LANGUAGES
        val next = l[(l.indexOfFirst { it.first == settings.language } + 1) % l.size]
        settings.language = next.first
        say("Language set to ${next.second}.")
    }

    fun changeRate(delta: Float) {
        settings.speechRate = settings.speechRate + delta
        say("Speech speed ${"%.1f".format(settings.speechRate)}.")
    }

    fun toggleCloud() {
        settings.cloudEnabled = !settings.cloudEnabled
        say(if (settings.cloudEnabled) "Cloud features on. Photos you ask about are sent to the server." else "Cloud features off. Nothing is sent.")
    }

    fun deleteAllData() {
        stopNavigation(silent = true)
        app.share.stop()
        settings.clearAll(); history.clear()
        say("All saved settings, contacts and the server login were deleted.")
    }

    /** The screen is going away. Guidance (in the service) keeps running; navigation is tied to this screen and stops. */
    fun shutdown() {
        if (navOn) stopNavigation(silent = true)
        if (!liveOn) camera.stop()
        location.stop(); voice.cancel()
    }
}
