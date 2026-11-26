package com.blindnav

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val HighContrast = darkColorScheme(
    primary = Color(0xFFFFEB3B), onPrimary = Color.Black,
    background = Color.Black, onBackground = Color.White,
    surface = Color.Black, onSurface = Color.White,
)

@Composable
private fun Big(label: String, danger: Boolean = false, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
        colors = if (danger) ButtonDefaults.buttonColors(containerColor = Color(0xFFD50000), contentColor = Color.White) else ButtonDefaults.buttonColors(),
    ) { Text(label, fontSize = 26.sp) }
}

@Composable
private fun Body(text: String) = Text(text, fontSize = 20.sp, color = Color.White)

@Composable
fun AppUi(c: AppController) {
    MaterialTheme(colorScheme = HighContrast) {
        BackHandler(enabled = c.screen != Screen.HOME) { c.go(Screen.HOME) }
        Column(
            Modifier.fillMaxSize().background(Color.Black).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(title(c.screen), fontSize = 30.sp, color = Color.White, modifier = Modifier.semantics { heading() })
            when (c.screen) {
                Screen.HOME -> Home(c)
                Screen.LIVE -> {
                    Body("Obstacle detection assists only. Always use your white cane or guide dog.")
                    Big(if (c.liveOn) "Stop guidance" else "Start guidance") { c.toggleLive() }
                }
                Screen.ASK -> {
                    Big("Describe what is ahead") { c.describeScene() }
                    Big("Ask a question") { c.askQuestion() }
                }
                Screen.NAV -> {
                    Body("Walking directions only. They do not detect obstacles or crossings.")
                    if (c.navOn) Big("Stop navigation") { c.stopNavigation() } else Big("Say a destination") { c.navigateByVoice() }
                }
                Screen.READER -> {
                    Big("Read text in front of me") { c.readText() }
                    Big("Stop reading") { c.alerter.stop() }
                }
                Screen.CURRENCY -> Big("Identify this note") { c.readCurrency() }
                Screen.SOS -> {
                    Body("Starts a five second countdown, then texts and calls your saved contacts. Triple-press volume down also starts it.")
                    if (c.sosActive) Big("Cancel S O S") { c.cancelSos() } else Big("S O S", danger = true) { c.triggerSos() }
                }
                Screen.SETTINGS -> SettingsUi(c)
                Screen.PRIVACY -> PrivacyUi(c)
            }
            if (c.status.isNotEmpty()) {
                Text(c.status, fontSize = 22.sp, color = Color(0xFFFFEB3B), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            if (c.screen != Screen.HOME) Big("Back to home") { c.go(Screen.HOME) }
        }
    }
}

private fun title(s: Screen) = when (s) {
    Screen.HOME -> "BlindNav"
    Screen.LIVE -> "Live guidance"
    Screen.ASK -> "Ask BlindNav"
    Screen.NAV -> "Navigation"
    Screen.READER -> "Read text"
    Screen.CURRENCY -> "Currency"
    Screen.SOS -> "Emergency"
    Screen.SETTINGS -> "Settings"
    Screen.PRIVACY -> "Privacy"
}

@Composable
private fun Home(c: AppController) {
    Body("Prototype. Assists only. Not a replacement for your white cane or guide dog.")
    Big("Speak a command") { c.voiceCommand() }
    Big("Live guidance") { c.go(Screen.LIVE) }
    Big("Ask or describe") { c.go(Screen.ASK) }
    Big("Navigation") { c.go(Screen.NAV) }
    Big("Read text") { c.go(Screen.READER) }
    Big("Currency") { c.go(Screen.CURRENCY) }
    Big("S O S", danger = true) { c.go(Screen.SOS) }
    Big("Settings") { c.go(Screen.SETTINGS) }
    Big("Privacy") { c.go(Screen.PRIVACY) }
}

@Composable
private fun SettingsUi(c: AppController) {
    val s = c.settings
    var refresh by remember { mutableIntStateOf(0) }
    var url by remember { mutableStateOf(s.backendUrl) }
    var key by remember { mutableStateOf(s.registerKey) }
    var name by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    @Suppress("UNUSED_EXPRESSION") refresh

    Big("Language: ${Settings.nameOf(s.language)}") { c.cycleLanguage(); refresh++ }
    Big("Speech slower") { c.changeRate(-0.2f); refresh++ }
    Big("Speech faster") { c.changeRate(0.2f); refresh++ }
    Big("Cloud features: ${if (s.cloudEnabled) "on" else "off"}") { c.toggleCloud(); refresh++ }
    OutlinedTextField(url, { url = it; s.backendUrl = it }, label = { Text("Server address, for example http://192.168.1.5:8000") },
        modifier = Modifier.fillMaxWidth(), singleLine = true)
    OutlinedTextField(key, { key = it; s.registerKey = it }, label = { Text("Server key, if your server needs one") },
        modifier = Modifier.fillMaxWidth(), singleLine = true)
    Text("Emergency contacts", fontSize = 24.sp, color = Color.White, modifier = Modifier.semantics { heading() })
    s.contacts.forEach { ct ->
        Big("Remove ${ct.name}, ${ct.number}") { s.contacts = s.contacts - ct; refresh++ }
    }
    OutlinedTextField(name, { name = it }, label = { Text("Contact name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    OutlinedTextField(number, { number = it }, label = { Text("Phone number with country code, e.g. +919876543210") },
        modifier = Modifier.fillMaxWidth(), singleLine = true)
    Big("Add contact") {
        if (name.isNotBlank() && Regex("^\\+[1-9]\\d{7,14}$").matches(number.trim())) {
            s.contacts = s.contacts + Contact(name.trim(), number.trim()); name = ""; number = ""; refresh++
        } else c.alerter.say("Enter a name and a phone number starting with plus and the country code.")
    }
}

@Composable
private fun PrivacyUi(c: AppController) {
    var refresh by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_EXPRESSION") refresh
    Body("Obstacle detection runs on this phone and never sends pictures anywhere.")
    Body("Describe, ask, navigation and Kannada or Telugu reading send a photo or text to your server only when you use them, and only if cloud features are on.")
    Body("The server forwards photos to an AI provider. Do not photograph documents with private details such as bank or identity numbers.")
    Body("Nothing is stored on the server by this app. Settings and contacts stay on this phone.")
    Big("Cloud features: ${if (c.settings.cloudEnabled) "on" else "off"}") { c.toggleCloud(); refresh++ }
    Big("Delete all my data", danger = true) { c.deleteAllData(); refresh++ }
}
