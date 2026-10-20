package com.arise.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** "System window" palette: near-black navy, glowing blue panels. */
object C {
    val Bg = Color(0xFF05070D)
    val Panel = Color(0xFF0B1120)
    val PanelHi = Color(0xFF111A2E)
    val Border = Color(0xFF1E3358)
    val Glow = Color(0xFF4DA3FF)
    val Accent = Color(0xFF3987E5) // chart series
    val SeqLow = Color(0xFF15294A) // heatmap low end, same hue as Accent
    val Text = Color(0xFFF0EFEC)
    val Text2 = Color(0xFFC3C2B7)
    val Muted = Color(0xFF8A90A0)
    val Grid = Color(0xFF1A2335)
    val Axis = Color(0xFF39445A)
    val Gold = Color(0xFFE8B84A)
    val Good = Color(0xFF3FBF7F)
    val Danger = Color(0xFFE66767)
    val RunHot = Color(0xFFFF8A3D)
}

val Mono = FontFamily.Monospace

@Composable
fun AriseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Glow,
            onPrimary = C.Bg,
            secondary = C.Gold,
            background = C.Bg,
            onBackground = C.Text,
            surface = C.Panel,
            onSurface = C.Text,
            surfaceVariant = C.PanelHi,
            onSurfaceVariant = C.Text2,
            outline = C.Border,
            error = C.Danger,
        ),
    ) {
        // A Surface sets the default text colour for everything inside, so no screen is black-on-black.
        Surface(color = C.Bg, contentColor = C.Text) { content() }
    }
}

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    accent: Color = C.Border,
    glow: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .fillMaxWidth()
            .then(if (glow) Modifier.shadow(14.dp, shape, ambientColor = accent, spotColor = accent) else Modifier)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(C.PanelHi, C.Panel)))
            .border(1.dp, accent, shape)
            .padding(14.dp),
        content = content,
    )
}

/** A System popup window: glowing border plus corner brackets. For the important things. */
@Composable
fun SystemWindow(
    modifier: Modifier = Modifier,
    accent: Color = C.Glow,
    content: @Composable ColumnScope.() -> Unit,
) {
    Panel(
        modifier.drawWithContent {
            drawContent()
            val l = 14.dp.toPx()
            val w = 2.dp.toPx()
            val inset = 1.dp.toPx()
            val (r, b) = size.width - inset to size.height - inset
            listOf(
                Offset(inset, inset) to Pair(Offset(l, 0f), Offset(0f, l)),
                Offset(r, inset) to Pair(Offset(-l, 0f), Offset(0f, l)),
                Offset(inset, b) to Pair(Offset(l, 0f), Offset(0f, -l)),
                Offset(r, b) to Pair(Offset(-l, 0f), Offset(0f, -l)),
            ).forEach { (p, d) ->
                drawLine(accent, p, p + d.first, w, StrokeCap.Square)
                drawLine(accent, p, p + d.second, w, StrokeCap.Square)
            }
        },
        accent = accent.copy(alpha = 0.55f),
        glow = true,
        content = content,
    )
}

@Composable
fun SysLabel(text: String, color: Color = C.Glow) {
    Text(
        text.uppercase(),
        color = color,
        fontFamily = Mono,
        fontSize = 11.sp,
        letterSpacing = 2.sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
fun SysButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    color: Color = C.Glow,
    small: Boolean = false,
) {
    val shape = RoundedCornerShape(8.dp)
    val fg = when {
        !enabled -> C.Muted.copy(alpha = 0.5f)
        selected -> C.Bg
        else -> color
    }
    Box(
        modifier
            .clip(shape)
            .background(if (selected && enabled) color else Color.Transparent)
            .border(1.dp, if (enabled) color else C.Muted.copy(alpha = 0.25f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (small) 9.dp else 12.dp, vertical = if (small) 6.dp else 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, fontSize = if (small) 12.sp else 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = Mono)
    }
}

@Composable
fun XpBar(fraction: Float, color: Color = C.Glow, height: Dp = 8.dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(C.Bg),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.55f), color))),
        )
    }
}
