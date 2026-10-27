package com.arise.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A message from the System, shown as a pop-up window over the app. */
data class SysMsg(
    val title: String,
    val lines: List<String>,
    val accent: Color = C.Glow,
    val button: String = "OK",
    /** Spoken by the System voice when shown. */
    val speak: String? = null,
)

/** Anywhere in the app can post a System message; the app shell shows them one at a time. */
object SystemBus {
    val queue = mutableStateListOf<SysMsg>()

    fun post(m: SysMsg) {
        queue.add(m)
    }
}

/** Dimmed screen, a glowing System window, the title typed out letter by letter. */
@Composable
fun SystemPopup(msg: SysMsg, onDismiss: () -> Unit) {
    val appear = remember(msg) { Animatable(0f) }
    var typed by remember(msg) { mutableIntStateOf(0) }
    val ctx = LocalContext.current
    LaunchedEffect(msg) {
        Cues.ping(ctx)
        msg.speak?.let { Voice.say(it) }
        appear.animateTo(1f, tween(260))
        while (typed < msg.title.length) {
            typed++
            delay(22)
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f * appear.value))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.padding(22.dp).alpha(appear.value).scale(0.92f + 0.08f * appear.value)) {
            SystemWindow(accent = msg.accent) {
                Text("[SYSTEM]", color = msg.accent, fontFamily = Mono, fontSize = 12.sp, letterSpacing = 4.sp)
                Spacer(Modifier.height(6.dp))
                Text(msg.title.take(typed), fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = Mono, color = C.Text)
                Spacer(Modifier.height(10.dp))
                msg.lines.forEach { line ->
                    Text(line, color = C.Text2, fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp))
                }
                Spacer(Modifier.height(14.dp))
                SysButton(msg.button, onClick = onDismiss, selected = true, color = msg.accent, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * The animated background: deep navy, a slow glow, drifting motes of light and a faint grid.
 * Drawn, not an image, so it stays sharp on any screen.
 */
@Composable
fun SystemBackdrop(modifier: Modifier = Modifier, accent: Color = C.Glow, gate: Boolean = false) {
    val t = rememberInfiniteTransition(label = "backdrop")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "phase")
    val breathe by t.animateFloat(0.7f, 1f, infiniteRepeatable(tween(3200), RepeatMode.Reverse), label = "breathe")
    val motes = remember {
        val r = java.util.Random(7)
        List(46) { Triple(r.nextFloat(), r.nextFloat(), 0.4f + r.nextFloat() * 1.6f) }
    }
    Canvas(modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF02040A), Color(0xFF07122A), Color(0xFF02040A))))
        // Faint perspective grid at the bottom.
        val horizon = size.height * 0.72f
        val gridColor = accent.copy(alpha = 0.07f)
        for (i in 0..10) {
            val y = horizon + (size.height - horizon) * (i / 10f) * (i / 10f)
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1f)
        }
        for (i in -8..8) {
            val x = size.width / 2 + i * size.width / 8
            drawLine(gridColor, Offset(size.width / 2 + i * 12f, horizon), Offset(x, size.height), 1f)
        }
        // Central glow.
        val c = Offset(size.width / 2, size.height * if (gate) 0.26f else 0.3f)
        val r = size.minDimension * 0.6f * breathe
        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.28f), accent.copy(alpha = 0.06f), Color.Transparent), c, r), r, c)
        if (gate) drawGate(this, c, size.minDimension * 0.22f, accent, breathe, phase)
        // Drifting motes.
        motes.forEach { (x, y, s) ->
            val yy = ((y - phase * (0.2f + s * 0.12f)) % 1f + 1f) % 1f
            val xx = x + 0.02f * sin((phase * 2 * PI + y * 10).toFloat())
            drawCircle(accent.copy(alpha = 0.15f + 0.35f * (s / 2f)), s * 1.6f, Offset(xx * size.width, yy * size.height))
        }
    }
}

/** A glowing gate (portal) with a small figure in front of it. */
private fun drawGate(scope: androidx.compose.ui.graphics.drawscope.DrawScope, c: Offset, r: Float, accent: Color, breathe: Float, phase: Float) = with(scope) {
    for (i in 0..3) {
        val rr = r * (1f + i * 0.13f) * (0.96f + 0.04f * breathe)
        drawOval(
            accent.copy(alpha = 0.55f - i * 0.12f),
            topLeft = Offset(c.x - rr * 0.62f, c.y - rr),
            size = Size(rr * 1.24f, rr * 2f),
            style = Stroke(width = (3 - i * 0.5f).coerceAtLeast(1f) * density),
        )
    }
    drawOval(
        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.35f), accent.copy(alpha = 0.25f), Color.Transparent), c, r),
        topLeft = Offset(c.x - r * 0.6f, c.y - r * 0.97f),
        size = Size(r * 1.2f, r * 1.94f),
    )
    // Swirl.
    for (k in 0 until 24) {
        val a = (k / 24f + phase) * 2 * PI
        val rr = r * (0.2f + 0.7f * (k / 24f))
        drawCircle(Color.White.copy(alpha = 0.25f), 1.4f * density, Offset(c.x + (cos(a) * rr * 0.6f).toFloat(), c.y + (sin(a) * rr).toFloat()))
    }
    // The player: a small silhouette standing before the gate.
    val fx = c.x
    val fy = c.y + r * 1.25f
    val h = r * 0.55f
    drawCircle(Color(0xFF02040A), h * 0.11f, Offset(fx, fy - h * 0.86f))
    val body = Path().apply {
        moveTo(fx - h * 0.13f, fy - h * 0.72f)
        lineTo(fx + h * 0.13f, fy - h * 0.72f)
        lineTo(fx + h * 0.17f, fy - h * 0.28f)
        lineTo(fx + h * 0.1f, fy)
        lineTo(fx + h * 0.02f, fy)
        lineTo(fx, fy - h * 0.25f)
        lineTo(fx - h * 0.02f, fy)
        lineTo(fx - h * 0.1f, fy)
        lineTo(fx - h * 0.17f, fy - h * 0.28f)
        close()
    }
    drawPath(body, Color(0xFF02040A))
    drawLine(accent.copy(alpha = 0.4f), Offset(fx - h * 0.8f, fy + 2), Offset(fx + h * 0.8f, fy + 2), 1.5f * density)
}

/** Achievement unlocked: full-screen art, the title, its quote and what you did. */
@Composable
fun AchievementOverlay(name: String, quote: String, goal: String, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val appear = remember(name) { Animatable(0f) }
    LaunchedEffect(name) {
        Cues.levelUp(ctx)
        Voice.say("Achievement unlocked. $name.")
        appear.animateTo(1f, tween(700))
    }
    Box(Modifier.fillMaxSize().background(C.Bg)) {
        SystemBackdrop(accent = C.Gold, gate = true)
        Column(
            Modifier.fillMaxSize().padding(26.dp).alpha(appear.value),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(360.dp))
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(C.Panel.copy(alpha = 0.7f)).padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text("✦ NEW TITLE", color = C.Gold, fontFamily = Mono, fontSize = 12.sp, letterSpacing = 3.sp)
            }
            Text(name, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = C.Text, textAlign = TextAlign.Center)
            Spacer(Modifier.weight(1f))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Panel.copy(alpha = 0.85f)).padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("“$quote”", fontStyle = FontStyle.Italic, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, color = C.Text)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("★ ", color = C.Gold)
                    Text(goal, color = C.Text2, fontSize = 13.sp)
                }
            }
            SysButton("GOT IT", onClick = onDone, selected = true, color = C.Gold, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** A square tile with an icon, used in the assessment's rating grid. */
@Composable
fun RatingTile(label: String, icon: String, value: Int, delta: Int?, highlight: Boolean, modifier: Modifier = Modifier) {
    val bg = if (highlight) C.Glow else C.PanelHi
    val fg = if (highlight) C.Bg else C.Text
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(12.dp),
    ) {
        Text("$icon $label", color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value.toString(), color = fg, fontSize = 26.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
            if (delta != null && delta > 0) {
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(if (highlight) C.Bg else C.Gold).padding(horizontal = 6.dp, vertical = 1.dp),
                ) {
                    Text("+$delta ▲", color = if (highlight) C.Glow else C.Bg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        XpBar(value / 100f, if (highlight) C.Bg else C.Glow, height = 4.dp)
    }
}

