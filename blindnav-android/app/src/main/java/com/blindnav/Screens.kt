package com.blindnav

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val BnScheme = darkColorScheme(
    primary = Bn.Neon, onPrimary = Bn.Bg, background = Bn.Bg, onBackground = Bn.Tx, surface = Bn.Card, onSurface = Bn.Tx,
)

@Composable
fun AppUi(c: AppController) {
    MaterialTheme(colorScheme = BnScheme) {
        var splash by remember { mutableStateOf(true) }
        LaunchedEffect(Unit) { delay(1800); splash = false }
        Box(
            Modifier.fillMaxSize().background(Bn.Bg).drawBehind {
                drawRect(Brush.radialGradient(listOf(Bn.Em.copy(alpha = 0.6f), Color.Transparent), Offset(size.width / 2, 0f), size.width * 0.95f))
            },
        ) {
            if (splash) Splash() else Content(c)
        }
    }
}

@Composable
private fun Splash() {
    Column(
        Modifier.fillMaxSize().padding(28.dp).semantics { contentDescription = "BlindNav. See beyond vision." },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Emblem(230.dp)
        Spacer(Modifier.height(32.dp))
        Cinzel("BLINDNAV", 38.sp, spacing = 8.sp)
        Spacer(Modifier.height(12.dp))
        GoldRule()
        Spacer(Modifier.height(12.dp))
        Hud("See beyond vision", 15.sp, Bn.Ti)
        Spacer(Modifier.height(48.dp))
        Hud("YGGDRASIL · initialising", 12.sp)
    }
}

@Composable
private fun Content(c: AppController) {
    BackHandler(enabled = c.screen != Screen.HOME) { c.go(Screen.HOME) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (c.screen == Screen.HOME) Home(c) else {
            Header(title(c.screen)) { c.go(Screen.HOME) }
            when (c.screen) {
                Screen.LIVE -> Live(c)
                Screen.ASK -> {
                    BnButton("Describe what is ahead", icon = BnIcons.EXPLORE) { c.describeScene() }
                    BnButton("Ask a question", style = BtnStyle.Secondary, icon = BnIcons.ASK) { c.askQuestion() }
                }
                Screen.NAV -> {
                    Note("Walking directions only. They do not detect obstacles or crossings. Use your cane or guide dog.")
                    if (c.navOn) BnButton("Stop navigation", style = BtnStyle.Danger, icon = BnIcons.STOP) { c.stopNavigation() }
                    else BnButton("Say a destination", icon = BnIcons.MIC) { c.navigateByVoice() }
                }
                Screen.READER -> {
                    BnButton("Read text in front of me", icon = BnIcons.READ) { c.readText() }
                    BnButton("Stop reading", style = BtnStyle.Secondary, icon = BnIcons.STOP) { c.alerter.stop() }
                }
                Screen.CURRENCY -> {
                    Note("Hold the note flat in good light. The answer is a best guess: always confirm by feel.")
                    BnButton("Identify this note", icon = BnIcons.CURRENCY) { c.readCurrency() }
                }
                Screen.SOS -> Sos(c)
                Screen.SETTINGS -> SettingsUi(c)
                Screen.PRIVACY -> PrivacyUi(c)
                Screen.HOME -> {}
            }
        }
        if (c.status.isNotEmpty()) {
            BnCard(border = Bn.Gold.copy(alpha = 0.6f), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                Column {
                    Hud("YGGDRASIL", 11.sp, Bn.Gold)
                    BnText(c.status, 19.sp)
                }
            }
        }
        if (c.screen != Screen.HOME) BnButton("Back to home", style = BtnStyle.Secondary, icon = BnIcons.BACK) { c.go(Screen.HOME) }
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
private fun Header(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, Bn.Line, RoundedCornerShape(16.dp))
                .clickable(onClickLabel = "Back to home", onClick = onBack).semantics { contentDescription = "Back" },
            contentAlignment = Alignment.Center,
        ) { BnIcon(BnIcons.BACK, Bn.Tx, 26.dp) }
        Column(Modifier.semantics { heading() }) {
            Hud("YGGDRASIL", 11.sp, Bn.Gold)
            Text(title, color = Bn.Tx, fontFamily = BnFont.Inter, fontWeight = FontWeight.Bold, fontSize = 26.sp)
        }
    }
}

@Composable
private fun Note(text: String) = BnCard(border = Bn.Line) { BnText(text, 15.sp, Bn.Tx2) }

@Composable
private fun Home(c: AppController) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Cinzel("BLINDNAV", 19.sp, Modifier.semantics { heading() }, 3.sp)
        Pill(if (c.liveOn) "Guidance running" else "System ready", dot = if (c.liveOn) Bn.Neon else Bn.Ti)
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Emblem(236.dp, description = if (c.liveOn) "YGGDRASIL core. Obstacle guidance is running." else "YGGDRASIL core. Ready.")
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Hud("YGGDRASIL", 14.sp, Bn.Gold)
        BnText(if (c.liveOn) "Obstacle guidance is running." else "Tap Activate to start obstacle guidance.", 15.sp, Bn.Tx2, modifier = Modifier.padding(top = 4.dp))
    }
    if (c.liveOn) BnButton("STOP ASSISTANCE", style = BtnStyle.Danger, icon = BnIcons.STOP, height = 72.dp) { c.toggleLive() }
    else BnButton("ACTIVATE ASSISTANCE", icon = BnIcons.MIC, height = 72.dp) { c.toggleLive() }
    BnButton("SPEAK A COMMAND", style = BtnStyle.Secondary, icon = BnIcons.SPEAKER, height = 56.dp) { c.voiceCommand() }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BnTile("Explore", "Describe, ask", BnIcons.EXPLORE, Modifier.weight(1f)) { c.go(Screen.ASK) }
        BnTile("Navigate", "Walk to a place", BnIcons.NAVIGATE, Modifier.weight(1f)) { c.go(Screen.NAV) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BnTile("Read", "Text and labels", BnIcons.READ, Modifier.weight(1f)) { c.go(Screen.READER) }
        BnTile("Currency", "Which note", BnIcons.CURRENCY, Modifier.weight(1f)) { c.go(Screen.CURRENCY) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BnTile("Live", "Guidance screen", BnIcons.SHIELD, Modifier.weight(1f)) { c.go(Screen.LIVE) }
        BnTile("S O S", "Emergency", BnIcons.PHONE, Modifier.weight(1f)) { c.go(Screen.SOS) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        BarItem("Settings", BnIcons.GEAR) { c.go(Screen.SETTINGS) }
        BarItem("Privacy", BnIcons.SHIELD) { c.go(Screen.PRIVACY) }
    }
}

@Composable
private fun BarItem(label: String, icon: String, onClick: () -> Unit) {
    Column(
        Modifier.heightIn(min = 64.dp).clip(RoundedCornerShape(16.dp)).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick).padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BnIcon(icon, Bn.Tx, 26.dp)
        Hud(label, 12.sp, Bn.Tx)
    }
}

@Composable
private fun Live(c: AppController) {
    BnCard(border = if (c.liveOn) Bn.Neon.copy(alpha = 0.5f) else Bn.Line) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(if (c.liveOn) "Obstacle watch: on" else "Obstacle watch: off", dot = if (c.liveOn) Bn.Neon else Bn.Ti)
            BnText("Alerts arrive as a tone, a vibration and a short spoken phrase. Everything is spoken: you never need the screen.", 15.sp, Bn.Tx2)
        }
    }
    if (c.liveOn) BnButton("Stop guidance", style = BtnStyle.Danger, icon = BnIcons.STOP, height = 72.dp) { c.toggleLive() }
    else BnButton("Start guidance", icon = BnIcons.SHIELD, height = 72.dp) { c.toggleLive() }
    BnCard(border = Bn.Gold.copy(alpha = 0.6f)) { BnText("Assists only. Always use your white cane or guide dog. Obstacle detection can miss things.", 15.sp, Bn.Tx2) }
}

@Composable
private fun Sos(c: AppController) {
    BnText("A five second countdown, then your first contact gets a WhatsApp message with your location, other contacts get a text if the server is set up, and your live location is shared. You must press send in WhatsApp. Triple-press volume down also starts it.", 15.sp, Bn.Tx2)
    if (c.sosActive) BnButton("Cancel S O S", style = BtnStyle.Secondary, height = 96.dp) { c.cancelSos() }
    else BnButton("S O S", style = BtnStyle.Danger, icon = BnIcons.PHONE, height = 96.dp) { c.triggerSos() }
    if (c.sharing) {
        val until = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(c.shareUntilMs))
        BnCard(border = Bn.Neon.copy(alpha = 0.5f)) { BnText("Sharing your live location until $until.", 17.sp) }
        BnButton("Stop sharing location", style = BtnStyle.Secondary) { c.stopSharing() }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Bn.Tx, unfocusedTextColor = Bn.Tx, focusedBorderColor = Bn.Neon, unfocusedBorderColor = Bn.Ti,
    cursorColor = Bn.Neon, focusedLabelColor = Bn.Neon, unfocusedLabelColor = Bn.Tx2,
)

@Composable
private fun SettingsUi(c: AppController) {
    val s = c.settings
    var refresh by remember { mutableIntStateOf(0) }
    var url by remember { mutableStateOf(s.backendUrl) }
    var key by remember { mutableStateOf(s.registerKey) }
    var name by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    @Suppress("UNUSED_EXPRESSION") refresh

    SectionLabel("Voice and language")
    BnRow("Language", BnIcons.EXPLORE, Settings.nameOf(s.language)) { c.cycleLanguage(); refresh++ }
    BnRow("Speech slower", BnIcons.SPEAKER, "%.1f×".format(s.speechRate)) { c.changeRate(-0.2f); refresh++ }
    BnRow("Speech faster", BnIcons.SPEAKER, "%.1f×".format(s.speechRate)) { c.changeRate(0.2f); refresh++ }
    SectionLabel("Privacy and AI")
    BnRow("Cloud features", BnIcons.SHIELD, if (s.cloudEnabled) "On" else "Off") { c.toggleCloud(); refresh++ }
    OutlinedTextField(url, { url = it; s.backendUrl = it }, label = { Text("Server address, e.g. http://192.168.1.5:8000") },
        modifier = Modifier.fillMaxWidth(), singleLine = true, colors = fieldColors())
    OutlinedTextField(key, { key = it; s.registerKey = it }, label = { Text("Server key, if your server needs one") },
        modifier = Modifier.fillMaxWidth(), singleLine = true, colors = fieldColors())
    SectionLabel("Emergency")
    BnRow("S O S opens", BnIcons.PHONE, if (s.sosMethod == "whatsapp") "WhatsApp" else "Phone call", danger = true) { c.toggleSosMethod(); refresh++ }
    BnRow("Share live location", BnIcons.NAVIGATE, "${s.shareHours} hours") { c.cycleShareHours(); refresh++ }
    s.contacts.forEach { ct -> BnRow("Remove ${ct.name}, ${ct.number}", BnIcons.USER, danger = true) { s.contacts = s.contacts - ct; refresh++ } }
    OutlinedTextField(name, { name = it }, label = { Text("Contact name") }, modifier = Modifier.fillMaxWidth(), singleLine = true, colors = fieldColors())
    OutlinedTextField(number, { number = it }, label = { Text("Phone number with country code, e.g. +919876543210") },
        modifier = Modifier.fillMaxWidth(), singleLine = true, colors = fieldColors())
    BnButton("Add contact", style = BtnStyle.Gold, icon = BnIcons.USER) {
        if (name.isNotBlank() && Regex("^\\+[1-9]\\d{7,14}$").matches(number.trim())) {
            s.contacts = s.contacts + Contact(name.trim(), number.trim()); name = ""; number = ""; refresh++
        } else c.alerter.say("Enter a name and a phone number starting with plus and the country code.")
    }
}

@Composable
private fun PrivacyUi(c: AppController) {
    var refresh by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_EXPRESSION") refresh
    Note("Obstacle detection runs on this phone and never sends pictures anywhere.")
    Note("Describe, ask, navigation and Kannada or Telugu reading send a photo or text to your server only when you use them, and only if cloud features are on.")
    Note("The server forwards photos to an AI provider. Do not photograph documents with private details such as bank or identity numbers.")
    Note("After an S O S, your position is sent to your server every 15 seconds. Anyone who has the share link can see where you are until it expires or you stop sharing. The link is sent only to your emergency contact.")
    Note("Settings and contacts stay on this phone. The server keeps only your latest shared position, in memory, until the share ends.")
    BnRow("Cloud features", BnIcons.SHIELD, if (c.settings.cloudEnabled) "On" else "Off") { c.toggleCloud(); refresh++ }
    BnButton("Delete all my data", style = BtnStyle.Danger, icon = BnIcons.STOP) { c.deleteAllData(); refresh++ }
}
