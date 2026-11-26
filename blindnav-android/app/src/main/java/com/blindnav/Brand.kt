package com.blindnav

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Palette and type from the BlindNav design system ("Sovereign Intelligence"). */
object Bn {
    val Bg = Color(0xFF090B0A)
    val Bg2 = Color(0xFF022C22)
    val Card = Color(0xFF101815)
    val Em = Color(0xFF064E3B)
    val Neon = Color(0xFF00FF9C)
    val Gold = Color(0xFFD4AF37)
    val Ti = Color(0xFFA7ADB4)
    val Tx = Color(0xFFFFFFFF)
    val Tx2 = Color(0xFFA7B0AA)
    val Warn = Color(0xFFF59E0B)
    val Crit = Color(0xFFEF4444)
    val Ok = Color(0xFF22C55E)
    val Line = Color(0x40A7ADB4)
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
object BnFont {
    private fun variable(res: Int, w: Int) = Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    val Inter = FontFamily(variable(R.font.inter, 400), variable(R.font.inter, 500), variable(R.font.inter, 600), variable(R.font.inter, 700))
    val Cinzel = FontFamily(variable(R.font.cinzel, 500), variable(R.font.cinzel, 700))
    val Orbitron = FontFamily(variable(R.font.orbitron, 500), variable(R.font.orbitron, 700))
    val Rajdhani = FontFamily(Font(R.font.rajdhani_semibold, FontWeight.SemiBold))
}

/** True when the user switched animations off in Android settings. All motion here respects it. */
@Composable
fun rememberReducedMotion(): Boolean {
    val ctx = LocalContext.current
    return remember {
        android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

@Composable
fun Hud(text: String, size: TextUnit = 12.sp, color: Color = Bn.Tx2, modifier: Modifier = Modifier) =
    Text(text.uppercase(), modifier, color = color, fontFamily = BnFont.Rajdhani, fontWeight = FontWeight.SemiBold, fontSize = size, letterSpacing = 1.6.sp)

@Composable
fun BnText(text: String, size: TextUnit = 18.sp, color: Color = Bn.Tx, weight: FontWeight = FontWeight.Normal, modifier: Modifier = Modifier) =
    Text(text, modifier, color = color, fontFamily = BnFont.Inter, fontWeight = weight, fontSize = size, lineHeight = size * 1.45f)

@Composable
fun Cinzel(text: String, size: TextUnit, modifier: Modifier = Modifier, spacing: TextUnit = 2.sp) =
    Text(text, modifier, color = Bn.Tx, fontFamily = BnFont.Cinzel, fontWeight = FontWeight.Bold, fontSize = size, letterSpacing = spacing)

// ---------- icons: 24x24 stroke paths shared with the design canvas ----------
object BnIcons {
    const val EXPLORE = "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0 M15.5 8.5l-2 5-5 2 2-5z"
    const val NAVIGATE = "M12 3l7 17-7-4-7 4z"
    const val READ = "M4 7V5a1 1 0 0 1 1 -1h2M17 4h2a1 1 0 0 1 1 1v2M20 17v2a1 1 0 0 1 -1 1h-2M7 20H5a1 1 0 0 1 -1 -1v-2M8 9h8M8 12h8M8 15h5"
    const val ASK = "M4 5h16v11H9l-5 4z M12 8v5M9.5 10.5h5"
    const val USER = "M16 8a4 4 0 1 0 -8 0a4 4 0 1 0 8 0 M4 21c1-4 4-6 8-6s7 2 8 6"
    const val HISTORY = "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0 M12 7v5l3 2"
    const val GEAR = "M15 12a3 3 0 1 0 -6 0a3 3 0 1 0 6 0 M12 2v3M12 19v3M2 12h3M19 12h3M4.9 4.9l2.1 2.1M17 17l2.1 2.1M4.9 19.1L7 17M17 7l2.1-2.1"
    const val MIC = "M9 6a3 3 0 0 1 6 0v5a3 3 0 0 1 -6 0z M5 11a7 7 0 0 0 14 0 M12 18v3"
    const val BACK = "M15 6l-6 6 6 6"
    const val SPEAKER = "M4 9v6h4l5 4V5L8 9z M16 9a4 4 0 0 1 0 6"
    const val PHONE = "M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1 -2 2A16 16 0 0 1 3 6a2 2 0 0 1 2 -2z"
    const val SHIELD = "M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z"
    const val CURRENCY = "M4 7h16v10H4z M12 9.5a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0 -5 M7 12h.01M17 12h.01"
    const val STOP = "M6 6h12v12H6z"
}

@Composable
fun BnIcon(path: String, tint: Color = Bn.Tx, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    val p = remember(path) { PathParser().parsePathString(path).toPath() }
    Canvas(modifier.size(size).clearAndSetSemantics { }) { // decorative: the label next to it carries the meaning
        scale(this.size.minDimension / 24f, pivot = Offset.Zero) {
            drawPath(p, tint, style = Stroke(1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

// ---------- the YGGDRASIL emblem: world tree inside rotating gold and titanium rings ----------
@Composable
fun Emblem(size: Dp, modifier: Modifier = Modifier, description: String = "BlindNav emblem") {
    val reduced = rememberReducedMotion()
    val t = rememberInfiniteTransition(label = "emblem")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(28000, easing = LinearEasing)), label = "spin")
    val spinBack by t.animateFloat(0f, -360f, infiniteRepeatable(tween(42000, easing = LinearEasing)), label = "spinBack")
    val breath by t.animateFloat(0.85f, 1f, infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breath")
    val a = if (reduced) 0f else spin
    val b = if (reduced) 0f else spinBack
    val g = if (reduced) 1f else breath

    Canvas(modifier.size(size).semantics { contentDescription = description }) {
        val s = this.size.minDimension / 200f
        val c = Offset(100f * s, 100f * s)
        fun o(x: Float, y: Float) = Offset(x * s, y * s)

        drawCircle(Brush.radialGradient(listOf(Bn.Neon.copy(alpha = 0.45f * g), Color.Transparent), c, 66f * s * (0.96f + 0.08f * g)), 66f * s * (0.96f + 0.08f * g), c)

        rotate(a, c) {
            drawCircle(Bn.Gold, 94f * s, c, style = Stroke(2f * s, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.5f * s, 11.4f * s))))
            drawCircle(Bn.Gold.copy(alpha = 0.7f), 86f * s, c, style = Stroke(1f * s))
        }
        rotate(b, c) {
            val oct = Path().apply {
                val pts = listOf(173.9f to 130.6f, 130.6f to 173.9f, 69.4f to 173.9f, 26.1f to 130.6f, 26.1f to 69.4f, 69.4f to 26.1f, 130.6f to 26.1f, 173.9f to 69.4f)
                moveTo(pts[0].first * s, pts[0].second * s); pts.drop(1).forEach { lineTo(it.first * s, it.second * s) }; close()
            }
            drawPath(oct, Bn.Ti.copy(alpha = 0.8f), style = Stroke(1.2f * s))
            val dia = Path().apply { moveTo(100f * s, 32f * s); lineTo(168f * s, 100f * s); lineTo(100f * s, 168f * s); lineTo(32f * s, 100f * s); close() }
            drawPath(dia, Bn.Gold.copy(alpha = 0.6f), style = Stroke(1f * s))
        }
        drawCircle(Bn.Em, 58f * s, c, style = Stroke(3f * s))
        drawArc(Bn.Neon, -90f, 89f, false, o(42f, 42f), androidx.compose.ui.geometry.Size(116f * s, 116f * s), style = Stroke(1.5f * s))
        val tree = Stroke(3f * s, cap = StrokeCap.Round)
        listOf(100f to 134f to (100f to 72f), 100f to 100f to (80f to 80f), 100f to 100f to (120f to 80f), 100f to 120f to (84f to 136f), 100f to 120f to (116f to 136f))
            .forEach { (from, to) -> drawLine(Bn.Neon, o(from.first, from.second), o(to.first, to.second), tree.width, StrokeCap.Round) }
        listOf(100f to 68f, 78f to 78f, 122f to 78f).forEach { drawCircle(Bn.Gold, 3.8f * s, o(it.first, it.second)) }
    }
}

// ---------- shared components ----------
enum class BtnStyle { Primary, Secondary, Gold, Danger }

@Composable
fun BnButton(label: String, modifier: Modifier = Modifier, style: BtnStyle = BtnStyle.Primary, icon: String? = null, height: Dp = 64.dp, onClick: () -> Unit) {
    val (bg, fg, border) = when (style) {
        BtnStyle.Primary -> Triple(Bn.Neon, Bn.Bg, Bn.Neon)
        BtnStyle.Secondary -> Triple(Color.Transparent, Bn.Tx, Bn.Ti)
        BtnStyle.Gold -> Triple(Color.Transparent, Bn.Gold, Bn.Gold)
        BtnStyle.Danger -> Triple(Bn.Crit.copy(alpha = 0.14f), Bn.Tx, Bn.Crit)
    }
    Row(
        modifier.fillMaxWidth().heightIn(min = height).clip(RoundedCornerShape(18.dp)).background(bg)
            .border(1.5.dp, border, RoundedCornerShape(18.dp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { BnIcon(icon, fg, 26.dp); Spacer(Modifier.width(10.dp)) }
        Text(label, color = fg, fontFamily = BnFont.Inter, fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 0.7.sp)
    }
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, color: Color = Bn.Tx, dot: Color? = Bn.Neon) {
    Row(
        modifier.heightIn(min = 32.dp).clip(CircleShape).background(Bn.Card.copy(alpha = 0.85f)).border(1.dp, Bn.Line, CircleShape).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (dot != null) Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Hud(text, 12.sp, color)
    }
}

@Composable
fun BnCard(modifier: Modifier = Modifier, border: Color = Bn.Line, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Bn.Card).border(1.dp, border, RoundedCornerShape(20.dp)).padding(16.dp)) { content() }
}

/** A tappable row: icon, label, optional right-hand value. 64 dp tall. */
@Composable
fun BnRow(label: String, icon: String, value: String? = null, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(16.dp)).background(Bn.Card)
            .border(1.dp, if (danger) Bn.Crit.copy(alpha = 0.6f) else Bn.Line, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BnIcon(icon, if (danger) Bn.Tx else Bn.Neon, 26.dp)
        BnText(label, 17.sp, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (value != null) BnText(value, 15.sp, Bn.Tx2)
    }
}

@Composable
fun BnTile(label: String, hint: String, icon: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.heightIn(min = 76.dp).clip(RoundedCornerShape(16.dp)).background(Bn.Card).border(1.dp, Bn.Line, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BnIcon(icon, Bn.Neon, 30.dp)
        androidx.compose.foundation.layout.Column {
            Hud(label, 16.sp, Bn.Tx)
            BnText(hint, 12.sp, Bn.Tx2)
        }
    }
}

@Composable
fun GoldRule(modifier: Modifier = Modifier) = Box(modifier.width(56.dp).size(width = 56.dp, height = 2.dp).background(Bn.Gold))

@Composable
fun SectionLabel(text: String) = Hud(text, 12.sp, Bn.Gold, Modifier.padding(top = 6.dp))
