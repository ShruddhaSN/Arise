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
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
        content = content,
    )
}

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    accent: Color = C.Border,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(C.PanelHi, C.Panel)))
            .border(1.dp, accent, shape)
            .padding(14.dp),
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
) {
    val shape = RoundedCornerShape(8.dp)
    val fg = when {
        selected -> C.Bg
        enabled -> color
        else -> C.Muted
    }
    Box(
        modifier
            .clip(shape)
            .background(if (selected) color else Color.Transparent)
            .border(1.dp, if (enabled) color else C.Muted.copy(alpha = 0.4f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = Mono)
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
