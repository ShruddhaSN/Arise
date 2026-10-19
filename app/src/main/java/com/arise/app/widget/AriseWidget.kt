package com.arise.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.arise.app.MainActivity
import com.arise.app.data.Store
import com.arise.app.game.Game
import com.arise.app.game.fmt
import java.time.LocalDate
import java.time.LocalTime

private data class Snap(
    val level: Int,
    val rank: String,
    val done: Int,
    val total: Int,
    val streak: Int,
    val next: String,
    val penalty: Boolean,
)

private fun snapshot(ctx: Context): Snap {
    val data = Store.load(ctx)
    if (!data.settings.onboarded) return Snap(1, "E", 0, 0, 0, "Open Arise to awaken", false)
    val today = LocalDate.now()
    val now = LocalTime.now()
    val p = Game.progress(data, today)
    val s = Game.status(data, today)
    val doneIds = data.day(today).done.keys
    val next = s.plan.blocks.firstOrNull { it.time > now && (it.quest == null || it.quest.id !in doneIds) }
    val nextText = if (next != null) {
        "${fmt(next.time)} · ${next.quest?.title ?: next.title}"
    } else {
        val tomorrow = Game.plan(data, today.plusDays(1)).blocks.first()
        "Tomorrow ${fmt(tomorrow.time)} · ${tomorrow.quest?.title ?: tomorrow.title}"
    }
    return Snap(
        level = p.level,
        rank = p.rank,
        done = s.doneCount,
        total = s.total,
        streak = p.streak,
        next = nextText,
        penalty = Game.penaltyActive(data, today) && !doneIds.contains(Game.penaltyQuest.id),
    )
}

private val Bg = Color(0xFF0A0F1C)
private val Glow = Color(0xFF4DA3FF)
private val TextMain = Color(0xFFF0EFEC)
private val TextDim = Color(0xFF9AA3B5)
private val Danger = Color(0xFFE66767)

class AriseWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = snapshot(context)
        provideContent { WidgetBody(snap) }
    }
}

@Composable
private fun WidgetBody(s: Snap) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Bg)
            .cornerRadius(16.dp)
            .padding(14.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                "ARISE",
                style = TextStyle(color = ColorProvider(Glow), fontSize = 13.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(
                "Lv ${s.level} · ${s.rank}-Rank · 🔥${s.streak}",
                style = TextStyle(color = ColorProvider(TextDim), fontSize = 12.sp),
            )
        }
        Spacer(GlanceModifier.height(8.dp))
        Text(
            if (s.penalty) "PENALTY QUEST ACTIVE" else "NEXT",
            style = TextStyle(color = ColorProvider(if (s.penalty) Danger else TextDim), fontSize = 10.sp, fontWeight = FontWeight.Bold),
        )
        Text(
            s.next,
            maxLines = 2,
            style = TextStyle(color = ColorProvider(TextMain), fontSize = 15.sp, fontWeight = FontWeight.Medium),
        )
        Spacer(GlanceModifier.height(10.dp))
        LinearProgressIndicator(
            progress = if (s.total == 0) 0f else s.done.toFloat() / s.total,
            modifier = GlanceModifier.fillMaxWidth().height(6.dp),
            color = ColorProvider(Glow),
            backgroundColor = ColorProvider(Color(0xFF1C2433)),
        )
        Spacer(GlanceModifier.height(4.dp))
        Text(
            "Daily quests ${s.done}/${s.total}",
            style = TextStyle(color = ColorProvider(TextDim), fontSize = 11.sp),
        )
    }
}

class AriseWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AriseWidget()
}
