package com.arise.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Where each highlighted part of the screen is, reported by [tutorialTarget]. */
class TutorialTargets {
    val rects = mutableStateMapOf<String, Rect>()
}

val LocalTutorialTargets = compositionLocalOf { TutorialTargets() }

fun Modifier.tutorialTarget(key: String): Modifier = composed {
    val t = LocalTutorialTargets.current
    onGloballyPositioned { t.rects[key] = it.boundsInRoot() }
}

data class TutorialStep(val key: String?, val title: String, val text: String)

fun tutorialSteps(name: String) = listOf(
    TutorialStep(null, "[SYSTEM] Welcome, $name", "A 30-second tour of your System. Tap anywhere to continue."),
    TutorialStep("header", "The daily quest", "Your wake time, your core quests, and 📈 how many of the last 30 days were on track. Tap I'M UP the moment you get out of bed: the morning re-plans around your real wake time. ‹ › move between days."),
    TutorialStep("allday", "All-day quests", "Sleep fills itself in from your phone overnight (tap to correct it). Steps sync from Samsung Health or you type them. Water and your language are side quests."),
    TutorialStep("deck", "Cards due now", "Your core quests due now: wake, sleep, steps and today's run or strength. Swipe to see them all. Later finds a better time; Not today is fine too. Side quests sit below, folded away and optional."),
    TutorialStep("deck", "Routines", "Morning and night routines are plain checklists. Tick what you did, skip what you don't feel like, and tap \"Done for today\" when you're finished. Log shows your habit graphs."),
    TutorialStep("deck", "Emergency quests", "⚡ They appear at random times. 30 minutes to do something small. Bonus XP, no penalty."),
    TutorialStep("upnext", "Up next", "Cards join the deck when their time arrives. This shows what's coming."),
    TutorialStep("fullday", "Tabs", "Due now is the deck. Done and Skipped show what you finished or passed on. Full day is the whole schedule."),
    TutorialStep("tabs", "Your other tabs", "Train: run and strength coach. To-do: say your tasks into the mic and the System spreads them over your days. Tools: Pomodoro and the screen blocker. Log: habits, sleep, charts. Player: your times, Samsung Health, calendar and events."),
    TutorialStep(null, "The rules", "Do most of your core quests and the day is on track. A day off is allowed: the next day is just gentler, with one small comeback win. Your level never goes down.\n\nARISE."),
)

@Composable
fun TutorialOverlay(steps: List<TutorialStep>, onDone: () -> Unit) {
    val targets = LocalTutorialTargets.current
    var i by remember { mutableIntStateOf(0) }
    val step = steps[i]
    val rect = step.key?.let { targets.rects[it] }
    val screenH = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.toPx() }

    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                if (i < steps.lastIndex) i++ else onDone()
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val pad = 6.dp.toPx()
            val path = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(Offset.Zero, size))
                if (rect != null) {
                    addRoundRect(
                        RoundRect(
                            Rect(rect.left - pad, rect.top - pad, rect.right + pad, rect.bottom + pad),
                            CornerRadius(14.dp.toPx()),
                        ),
                    )
                }
            }
            drawPath(path, Color.Black.copy(alpha = 0.78f))
            if (rect != null) {
                drawRoundRect(
                    C.Glow,
                    topLeft = Offset(rect.left - pad, rect.top - pad),
                    size = Size(rect.width + 2 * pad, rect.height + 2 * pad),
                    cornerRadius = CornerRadius(14.dp.toPx()),
                    style = Stroke(2.dp.toPx()),
                )
            }
        }
        // Put the explanation on the other half of the screen from the highlight.
        val lowerHalf = rect != null && rect.center.y > screenH / 2f
        Box(
            Modifier
                .align(if (lowerHalf) Alignment.TopCenter else Alignment.BottomCenter)
                .padding(horizontal = 20.dp, vertical = 90.dp),
        ) {
            SystemWindow {
                Text(step.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = C.Glow)
                Spacer(Modifier.height(6.dp))
                Text(step.text, color = C.Text2, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                Row {
                    Text("${i + 1} / ${steps.size}", color = C.Muted, fontSize = 12.sp, fontFamily = Mono)
                    Spacer(Modifier.weight(1f))
                    Text(if (i < steps.lastIndex) "Tap to continue ›" else "Tap to begin", color = C.Glow, fontSize = 12.sp, fontFamily = Mono)
                }
            }
        }
    }
}
