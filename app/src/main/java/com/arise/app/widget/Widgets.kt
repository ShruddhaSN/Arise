package com.arise.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.arise.app.MainActivity
import com.arise.app.data.Store
import com.arise.app.game.Game
import com.arise.app.game.Kind
import com.arise.app.game.Program
import com.arise.app.game.Season
import com.arise.app.game.fmt
import com.arise.app.notify.QuestActions
import com.arise.app.notify.Reminders
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

private val WBg = Color(0xFF0A0F1C)
private val WGlow = Color(0xFF4DA3FF)
private val WGold = Color(0xFFE8B84A)
private val WText = Color(0xFFF0EFEC)
private val WDim = Color(0xFF9AA3B5)
private val WGood = Color(0xFF3FBF7F)
private val WTrack = Color(0xFF1C2433)

private fun style(color: Color, size: Int, bold: Boolean = false) =
    TextStyle(color = ColorProvider(color), fontSize = size.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)

/** A small pill button for widgets. */
@Composable
internal fun WidgetButton(text: String, color: Color, action: Action) {
    Box(
        GlanceModifier.background(color).cornerRadius(8.dp).padding(horizontal = 12.dp, vertical = 6.dp).clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = style(WBg, 12, bold = true))
    }
}

private fun intensityName(i: Int) = when (i) {
    1 -> "STEADY"
    2 -> "HARD MODE"
    else -> "DISAPPEAR MODE"
}

private fun daysLeft(today: LocalDate) = (ChronoUnit.DAYS.between(today, LocalDate.of(today.year, 12, 31)) + 1).toInt()

// ---- Actions: tick things off from the home screen ----

val QuestKey = ActionParameters.Key<String>("quest")

class WakeCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        QuestActions.wake(context)
        Reminders.updateWidget(context)
    }
}

class DoneCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[QuestKey] ?: return
        QuestActions.done(context, id, LocalDate.now())
        Reminders.updateWidget(context)
    }
}

// ---- Status: rank, level, streak, today's progress (small) ----

class StatusWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = Store.load(context)
        val today = LocalDate.now()
        val on = data.settings.onboarded
        val p = if (on) Game.progress(data, today) else null
        val st = if (on) Game.status(data, today) else null
        provideContent {
            Column(
                GlanceModifier.fillMaxSize().background(WBg).cornerRadius(16.dp).padding(12.dp).clickable(actionStartActivity<MainActivity>()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("ARISE", style = style(WGlow, 11, bold = true))
                Text(p?.rank ?: "?", style = style(WGold, 40, bold = true))
                Text(if (p != null) "Lv ${p.level} · 📈${p.consistency}/${p.consistencyOf}" else "Open Arise", style = style(WText, 12))
                Spacer(GlanceModifier.height(6.dp))
                LinearProgressIndicator(
                    progress = if (st == null || st.total == 0) 0f else st.doneCount.toFloat() / st.total,
                    modifier = GlanceModifier.fillMaxWidth().height(5.dp),
                    color = ColorProvider(if (st?.cleared == true) WGood else WGlow),
                    backgroundColor = ColorProvider(WTrack),
                )
                Spacer(GlanceModifier.height(3.dp))
                Text(st?.let { "${it.doneCount}/${it.total} today" + if (it.cleared) " ✓" else "" } ?: "", style = style(WDim, 11))
            }
        }
    }
}

class StatusWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StatusWidget()
}

// ---- Quest list: everything left today, tick from the home screen (large) ----

private data class ListRow(val id: String, val time: String, val title: String, val kind: Kind, val due: Boolean)

class QuestListWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = Store.load(context)
        val today = LocalDate.now()
        val now = LocalTime.now()
        val on = data.settings.onboarded && today >= data.settings.start
        val st = if (on) Game.status(data, today) else null
        val rec = data.records[today]
        val rows = if (st == null) emptyList() else st.plan.blocks.mapNotNull { b ->
            val q = b.quest ?: return@mapNotNull null
            if (Game.settled(q, rec)) return@mapNotNull null
            if (q.expires && now.isAfter(b.time.plusMinutes((q.limitMin ?: 30).toLong()))) return@mapNotNull null
            ListRow(q.id, fmt(b.time), "${q.icon} ${q.title}", q.kind, !b.time.isAfter(now))
        }
        val left = 24 * 3600 - now.toSecondOfDay()
        provideContent {
            Column(GlanceModifier.fillMaxSize().background(WBg).cornerRadius(16.dp).padding(12.dp)) {
                Row(GlanceModifier.fillMaxWidth().clickable(actionStartActivity<MainActivity>())) {
                    Text("DAILY QUEST", style = style(WGlow, 12, bold = true))
                    Spacer(GlanceModifier.defaultWeight())
                    Text(
                        if (st != null) "${st.doneCount}/${st.total} · ⏳ ${left / 3600}h ${(left / 60) % 60}m" else "Open Arise",
                        style = style(if (st?.cleared == true) WGood else WDim, 12),
                    )
                }
                Spacer(GlanceModifier.height(6.dp))
                if (rows.isEmpty()) {
                    Text(if (st?.cleared == true) "All clear. Rest well." else "Nothing left on the list.", style = style(WDim, 13))
                } else {
                    LazyColumn {
                        items(rows, itemId = { it.id.hashCode().toLong() }) { r ->
                            Row(GlanceModifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(r.time, style = style(if (r.due) WGold else WDim, 11), modifier = GlanceModifier.width(62.dp))
                                Text(r.title, style = style(if (r.due) WText else WDim, 13), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                                when {
                                    r.kind == Kind.WAKE && r.due -> WidgetButton("I'M UP", WGood, actionRunCallback<WakeCallback>())
                                    r.kind == Kind.CHECK && r.due && !r.id.startsWith("todo_") && r.id != "learn" -> WidgetButton("✓", WGood, actionRunCallback<DoneCallback>(actionParametersOf(QuestKey to r.id)))
                                    r.due -> WidgetButton("Open", WGlow, actionStartActivity<MainActivity>())
                                    else -> Unit
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

class QuestListWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuestListWidget()
}

// ---- Countdown: days left in the year (wide, thin) ----

class CountdownWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = Store.load(context)
        val today = LocalDate.now()
        val s = data.settings
        val p = if (s.onboarded) Game.progress(data, today) else null
        provideContent {
            Column(
                GlanceModifier.fillMaxSize().background(WBg).cornerRadius(16.dp).padding(horizontal = 14.dp, vertical = 10.dp)
                    .clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val left = if (s.challengeDays > 0) s.challengeDays - Season.day(s, today) + 1 else 0
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (left > 0) "$left" else "${daysLeft(today)}", style = style(WGold, 30, bold = true))
                    Spacer(GlanceModifier.width(8.dp))
                    Column {
                        Text(if (left > 0) "DAYS LEFT IN YOUR CHALLENGE" else "DAYS LEFT IN ${today.year}", style = style(WText, 12, bold = true))
                        Text(intensityName(s.intensity), style = style(WGlow, 10, bold = true))
                    }
                }
                Text(
                    if (p != null) "${Season.label(s, today)} · Week ${Program.week(s, today)} · Lv ${p.level} ${p.rank}-Rank · 📈${p.consistency}/${p.consistencyOf} on track" else "Disappear. Come back unrecognisable.",
                    style = style(WDim, 11),
                )
            }
        }
    }
}

class CountdownWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CountdownWidget()
}
