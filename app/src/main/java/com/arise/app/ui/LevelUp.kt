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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arise.app.data.Stat
import com.arise.app.game.Game
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Full-screen level-up: fade to black, a light pulse, "[SYSTEM]" typed out, the level counting up,
 * then the stats that grew. A rank-up is the gold, slightly longer version.
 */
@Composable
fun LevelUpOverlay(
    name: String,
    fromLevel: Int,
    toLevel: Int,
    statsBefore: Map<Stat, Int>?,
    statsAfter: Map<Stat, Int>,
    onDone: () -> Unit,
) {
    val ctx = LocalContext.current
    val rankUp = Game.rankFor(fromLevel) != Game.rankFor(toLevel)
    val color = if (rankUp) C.Gold else C.Glow

    val bg = remember { Animatable(0f) }
    val headline = remember { Animatable(0f) }
    var typed by remember { mutableIntStateOf(0) }
    var shownLevel by remember { mutableIntStateOf(fromLevel) }
    var statsShown by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    val system = "[SYSTEM]"
    val grown = Stat.entries.filter { s -> statsBefore != null && (statsAfter[s] ?: 0) > (statsBefore[s] ?: 0) }

    LaunchedEffect(Unit) {
        launch { bg.animateTo(0.94f, tween(500)) }
        delay(350)
        Cues.levelUp(ctx)
        repeat(system.length) {
            typed++
            delay(70)
        }
        delay(200)
        headline.animateTo(1f, tween(if (rankUp) 900 else 600))
        delay(300)
        // Count up: quick at first, slower for the last step, so it lands with weight.
        while (shownLevel < toLevel) {
            delay(if (toLevel - shownLevel == 1) 650 else 220)
            shownLevel++
        }
        delay(400)
        repeat(grown.size) {
            statsShown++
            delay(350)
        }
        delay(if (rankUp) 700 else 400)
        finished = true
    }

    val pulse = rememberInfiniteTransition(label = "pulse")
    val ring by pulse.animateFloat(
        initialValue = 0.6f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse), label = "ring",
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = bg.value))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                if (finished) onDone()
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize().alpha(bg.value)) {
            val r = size.minDimension * 0.55f * ring
            drawCircle(
                Brush.radialGradient(
                    listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0.08f), Color.Transparent),
                    center = center, radius = r,
                ),
                radius = r, center = center,
            )
        }
        Column(
            Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(system.take(typed), color = color, fontFamily = Mono, fontSize = 16.sp, letterSpacing = 4.sp)
            Text(
                if (rankUp) "RANK UP" else "LEVEL UP",
                color = color, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 40.sp, letterSpacing = 6.sp,
                modifier = Modifier.alpha(headline.value).scale(0.85f + 0.15f * headline.value),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Lv $shownLevel",
                fontSize = 64.sp, fontWeight = FontWeight.Bold, fontFamily = Mono, color = C.Text,
                modifier = Modifier.alpha(headline.value),
            )
            if (rankUp) {
                Text(
                    "${Game.rankFor(toLevel)}-Rank · ${Game.titleFor(Game.rankFor(toLevel))}",
                    color = C.Gold, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.alpha(headline.value),
                )
            }
            Spacer(Modifier.height(10.dp))
            grown.take(statsShown).forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.icon, modifier = Modifier.width(30.dp))
                    Text(s.label, color = color, fontFamily = Mono, fontWeight = FontWeight.Bold, modifier = Modifier.width(52.dp))
                    Text("${statsBefore?.get(s) ?: 0}  →  ${statsAfter[s] ?: 0}", fontFamily = Mono, color = C.Text)
                }
            }
            Spacer(Modifier.height(16.dp))
            if (finished) {
                Text(
                    "Congratulations, $name. You have grown stronger.",
                    color = C.Text2, fontSize = 15.sp,
                )
                Spacer(Modifier.height(10.dp))
                SysButton("ARISE", onClick = onDone, selected = true, color = color, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
