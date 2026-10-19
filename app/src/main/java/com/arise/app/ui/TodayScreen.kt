package com.arise.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arise.app.data.AppData
import com.arise.app.data.DayType
import com.arise.app.data.Done
import com.arise.app.game.Block
import com.arise.app.game.Game
import com.arise.app.game.Knowledge
import com.arise.app.game.Program
import com.arise.app.game.Quest
import com.arise.app.game.fmt
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

private val dayFmt = DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.ENGLISH)
private val WORKOUT_IDS = setOf("run", "strength", "mobility", "stretch")

@Composable
fun TodayScreen(
    data: AppData,
    today: LocalDate,
    now: LocalTime,
    onUpdate: (AppData) -> Unit,
    openTrain: () -> Unit,
) {
    var date by remember { mutableStateOf(today) }
    val isToday = date == today
    val status = Game.status(data, date)
    val plan = status.plan
    val rec = data.day(date)
    val passesLeft = Game.passesLeft(data, date)
    val penalty = isToday && Game.penaltyActive(data, date)
    val nextIdx = if (isToday) plan.blocks.indexOfFirst { it.time > now } else -1

    val setDone: (Quest, Done?) -> Unit = { q, d ->
        val newDone = if (d == null) rec.done - q.id else rec.done + (q.id to d)
        onUpdate(data.with(rec.copy(done = newDone)))
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SysButton("‹", onClick = { date = date.minusDays(1) }, enabled = date > data.settings.earliest)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    SysLabel("[System] Daily quests")
                    Text(date.format(dayFmt), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Week ${Program.week(data.settings, date)} · ${plan.type.label}" + when {
                            date < data.settings.start -> " · before Day 1 (filling in)"
                            !isToday -> " · editing a past day"
                            else -> ""
                        },
                        color = C.Muted, fontSize = 13.sp,
                    )
                }
                SysButton("›", onClick = { date = date.plusDays(1) }, enabled = date < today)
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DayType.entries.forEach { t ->
                    SysButton(
                        t.label,
                        onClick = { onUpdate(data.with(rec.copy(dayType = if (t == Program.defaultType(data.settings, date)) null else t))) },
                        selected = plan.type == t,
                    )
                }
            }
        }
        item {
            Panel(accent = if (status.cleared) C.Good else C.Border) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${status.doneCount}/${status.total}", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("  quests", color = C.Text2, modifier = Modifier.padding(bottom = 5.dp))
                    Spacer(Modifier.weight(1f))
                    Text("+${status.xp} XP", color = C.Glow, fontFamily = Mono, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                XpBar(status.ratio, if (status.cleared) C.Good else C.Glow)
                Spacer(Modifier.height(8.dp))
                val need = ceil(Game.CLEAR_RATIO * status.total).toInt()
                Text(
                    if (status.cleared) "Day cleared ✓ Streak protected." else "Clear $need quests to protect your streak.",
                    color = if (status.cleared) C.Good else C.Text2, fontSize = 13.sp,
                )
                Text(
                    "Minimum passes left this week: $passesLeft/${Program.MIN_PASSES_PER_WEEK}",
                    color = C.Muted, fontSize = 12.sp,
                )
            }
        }
        if (isToday && today < data.settings.start) {
            item {
                Panel(accent = C.Gold) {
                    SysLabel("[System] Day 1 begins tomorrow", C.Gold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Tonight's only real quest: phone away by 10:30 and lights off by 11:30. Lay out your workout clothes.",
                        color = C.Text2, fontSize = 13.sp,
                    )
                }
            }
        }
        if (penalty) {
            item {
                Panel(accent = C.Danger) {
                    SysLabel("[System] Penalty quest", C.Danger)
                    Spacer(Modifier.height(4.dp))
                    Text("Yesterday wasn't cleared. Never miss twice.", color = C.Text2, fontSize = 13.sp)
                }
            }
            item { QuestCard(Game.penaltyQuest, rec.done[Game.penaltyQuest.id], passesLeft, null, false, setDone) }
        }
        if (isToday && nextIdx >= 0) {
            val nb = plan.blocks[nextIdx]
            item {
                Panel(accent = C.Glow) {
                    SysLabel("Next")
                    Text("${fmt(nb.time)} · ${nb.quest?.title ?: nb.title}", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item {
            val card = Knowledge.forDay(data.settings.earliest, date)
            Panel {
                SysLabel("Knowledge card · ${card.field}", C.Gold)
                Spacer(Modifier.height(4.dp))
                Text(card.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(card.hook, color = C.Text2, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Text("Go deeper: ${card.dig}", color = C.Muted, fontSize = 12.sp)
            }
        }
        item { SysLabel("All day") }
        items(plan.anytime, key = { "a_" + it.id }) { q ->
            QuestCard(q, rec.done[q.id], passesLeft, null, false, setDone)
        }
        item { SysLabel("Schedule") }
        items(plan.blocks, key = { "b_" + it.time.toString() + it.title }) { b ->
            val idx = plan.blocks.indexOf(b)
            val past = isToday && nextIdx >= 0 && idx < nextIdx || isToday && nextIdx < 0
            BlockRow(b, rec.done, passesLeft, past, idx == nextIdx, setDone, openTrain)
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun BlockRow(
    b: Block,
    done: Map<String, Done>,
    passesLeft: Int,
    past: Boolean,
    isNext: Boolean,
    setDone: (Quest, Done?) -> Unit,
    openTrain: () -> Unit,
) {
    val q = b.quest
    if (q != null) {
        QuestCard(
            q, done[q.id], passesLeft, fmt(b.time), isNext, setDone,
            onCoach = if (q.id in WORKOUT_IDS) openTrain else null,
        )
    } else {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)) {
            Text(fmt(b.time), color = C.Muted, fontFamily = Mono, fontSize = 12.sp, modifier = Modifier.width(72.dp))
            Column {
                Text(b.title, color = if (past) C.Muted else C.Text2, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                if (b.detail.isNotEmpty()) Text(b.detail, color = C.Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun QuestCard(
    q: Quest,
    state: Done?,
    passesLeft: Int,
    time: String?,
    isNext: Boolean,
    onSet: (Quest, Done?) -> Unit,
    onCoach: (() -> Unit)? = null,
) {
    val accent: Color = when {
        state == Done.FULL -> C.Good
        state == Done.MIN -> C.Gold
        isNext -> C.Glow
        else -> C.Border
    }
    Panel(accent = accent) {
        Row(verticalAlignment = Alignment.Top) {
            if (time != null) {
                Text(time, color = C.Muted, fontFamily = Mono, fontSize = 12.sp, modifier = Modifier.width(72.dp).padding(top = 2.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    q.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (state != null) C.Text2 else C.Text,
                    textDecoration = if (state == Done.FULL) TextDecoration.LineThrough else TextDecoration.None,
                )
                Text(q.full, color = C.Text2, fontSize = 13.sp)
            }
            Text("+${q.xp} ${q.stat.label}", color = C.Glow, fontFamily = Mono, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SysButton(
                if (state == Done.FULL) "✓ Done" else "Done",
                onClick = { onSet(q, if (state == Done.FULL) null else Done.FULL) },
                selected = state == Done.FULL,
                color = C.Good,
            )
            if (q.min != null) {
                SysButton(
                    if (state == Done.MIN) "✓ Min" else "Min",
                    onClick = { onSet(q, if (state == Done.MIN) null else Done.MIN) },
                    selected = state == Done.MIN,
                    enabled = state == Done.MIN || passesLeft > 0,
                    color = C.Gold,
                )
            }
            if (onCoach != null) SysButton("Coach ›", onClick = onCoach)
        }
        if (q.min != null) {
            Spacer(Modifier.height(6.dp))
            Text("Min: ${q.min} (20% XP, uses 1 pass)", color = C.Muted, fontSize = 11.sp)
        }
    }
}
