package com.arise.app.ui

import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arise.app.data.AppData
import com.arise.app.data.DayType
import com.arise.app.data.Event
import com.arise.app.game.Game
import com.arise.app.game.Program
import com.arise.app.game.fmt
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
private val pickFmt = DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.ENGLISH)

/** Mark single days as WFH, office, leave or sick. Every other day follows the normal week. */
@Composable
fun CalendarScreen(data: AppData, today: LocalDate, onUpdate: (AppData) -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var picking by remember { mutableStateOf<LocalDate?>(null) }
    // The day, and which of its events (-1 = a new one).
    var eventFor by remember { mutableStateOf<Pair<LocalDate, Int>?>(null) }
    val s = data.settings

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SysButton("‹ Back", onClick = onClose, small = true)
            Spacer(Modifier.weight(1f))
            SysLabel("[System] Calendar")
        }
        Text(
            "Tap a day to change just that day: WFH, office, leave or sick, or to add an event " +
                "(dinner, wedding, graduation). Quests move around events. Your normal week is in Edit setup.",
            color = C.Text2, fontSize = 13.sp,
        )
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SysButton("‹", small = true, onClick = { month = month.minusMonths(1) })
                Text(
                    month.atDay(1).format(monthFmt), fontWeight = FontWeight.Bold, fontSize = 17.sp,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                SysButton("›", small = true, onClick = { month = month.plusMonths(1) })
            }
            Spacer(Modifier.height(10.dp))
            Row {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                    Text(it, color = C.Muted, fontFamily = Mono, fontSize = 12.sp, modifier = Modifier.weight(1f).padding(start = 4.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            val first = month.atDay(1)
            val lead = first.dayOfWeek.value % 7
            val cells = List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (0 until 7).forEach { i ->
                        val d = week.getOrNull(i)
                        Box(Modifier.weight(1f).aspectRatio(0.85f)) {
                            if (d != null) DayCell(d, data, today) { picking = d }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
        Panel {
            SysLabel("Legend")
            Spacer(Modifier.height(6.dp))
            DayType.entries.forEach { t -> Text("${t.icon}  ${Program.dayLabel(s, t)}", color = C.Text2, fontSize = 13.sp) }
            Spacer(Modifier.height(4.dp))
            Text("🎉  Event", color = C.Text2, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text("Gold border = changed from your normal week. Blue = today.", color = C.Muted, fontSize = 12.sp)
            Text("Sick days never break your streak.", color = C.Muted, fontSize = 12.sp)
        }
    }

    picking?.let { d ->
        val normal = Program.defaultType(s, d)
        AlertDialog(
            onDismissRequest = { picking = null },
            containerColor = C.Panel,
            title = { Text(d.format(pickFmt)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val current = Game.typeFor(data, d)
                    SysButton(
                        "↺  Normal (${normal.icon} ${Program.dayLabel(s, normal)})", modifier = Modifier.fillMaxWidth(),
                        selected = data.records[d]?.dayType == null,
                        onClick = {
                            onUpdate(data.with(data.day(d).copy(dayType = null)))
                            picking = null
                        },
                    )
                    DayType.entries.forEach { t ->
                        SysButton(
                            "${t.icon}  ${Program.dayLabel(s, t)}", modifier = Modifier.fillMaxWidth(),
                            selected = data.records[d]?.dayType != null && current == t,
                            onClick = {
                                onUpdate(data.with(data.day(d).copy(dayType = if (t == normal) null else t)))
                                picking = null
                            },
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    val evs = data.records[d]?.events.orEmpty()
                    if (current == DayType.OFFICE || current == DayType.SICK) {
                        Text(
                            "🎉 Events go on WFH days, days off and leave. Taking this day off or working from home? Switch it above first.",
                            color = C.Muted, fontSize = 12.sp,
                        )
                    } else {
                        if (current == DayType.WFH) {
                            Text("It stays a WFH day: the event sits on top, and quests move around it.", color = C.Muted, fontSize = 12.sp)
                        }
                        evs.forEachIndexed { i, e ->
                            SysButton(
                                "🎉 ${e.title} · ${fmt(e.start)}–${fmt(e.realEnd)} · edit",
                                modifier = Modifier.fillMaxWidth(), color = C.Gold, selected = true,
                                onClick = {
                                    eventFor = d to i
                                    picking = null
                                },
                            )
                        }
                        SysButton(
                            if (evs.isEmpty()) "🎉  Add an event…" else "＋  Add another event",
                            modifier = Modifier.fillMaxWidth(), color = C.Gold,
                            onClick = {
                                eventFor = d to -1
                                picking = null
                            },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picking = null }) { Text("Cancel", color = C.Muted) } },
        )
    }
    eventFor?.let { (d, i) ->
        val evs = data.records[d]?.events.orEmpty()
        EventDialog(
            d, evs.getOrNull(i),
            onSave = { e ->
                val next = when {
                    e == null -> evs.filterIndexed { j, _ -> j != i }
                    i in evs.indices -> evs.toMutableList().also { it[i] = e }
                    else -> evs + e
                }.sortedBy { it.start }
                onUpdate(data.with(data.day(d).copy(events = next)))
                eventFor = null
            },
            onDismiss = { eventFor = null },
        )
    }
}

private data class Preset(val label: String, val title: String, val start: LocalTime, val end: LocalTime, val ready: Int)

private val presets = listOf(
    Preset("🍽 Dinner / party", "Dinner", LocalTime.of(19, 30), LocalTime.of(23, 30), 45),
    Preset("💍 Wedding / function", "Wedding", LocalTime.of(9, 0), LocalTime.of(21, 0), 90),
    Preset("🎓 Graduation", "Graduation", LocalTime.of(9, 0), LocalTime.of(21, 0), 60),
    Preset("🎬 Outing", "Outing", LocalTime.of(14, 0), LocalTime.of(19, 0), 30),
)

/** Add or edit an event: what, from when to when, and how long to get ready. */
@Composable
private fun EventDialog(date: LocalDate, current: Event?, onSave: (Event?) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var title by remember { mutableStateOf(current?.title ?: "") }
    var start by remember { mutableStateOf(current?.start ?: LocalTime.of(19, 0)) }
    var end by remember { mutableStateOf(current?.end ?: LocalTime.of(23, 0)) }
    var ready by remember { mutableStateOf(current?.readyMin ?: 45) }

    fun pick(t: LocalTime, set: (LocalTime) -> Unit) {
        TimePickerDialog(ctx, { _, h, m -> set(LocalTime.of(h, m)) }, t.hour, t.minute, false).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Panel,
        title = {
            Column {
                SysLabel("[System] Event", C.Gold)
                Text(date.format(pickFmt), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { p ->
                            SysButton(p.label, small = true, modifier = Modifier.weight(1f), selected = title == p.title, color = C.Gold, onClick = {
                                title = p.title
                                start = p.start
                                end = p.end
                                ready = p.ready
                            })
                        }
                    }
                }
                OutlinedTextField(
                    value = title, onValueChange = { title = it }, singleLine = true,
                    label = { Text("What is it?", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = C.Gold, unfocusedBorderColor = C.Border, cursorColor = C.Gold,
                        focusedTextColor = C.Text, unfocusedTextColor = C.Text, focusedLabelColor = C.Gold, unfocusedLabelColor = C.Muted,
                    ),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("From", color = C.Text2, modifier = Modifier.width(44.dp))
                    SysButton(fmt(start), small = true, onClick = { pick(start) { start = it } })
                    Text("to", color = C.Text2)
                    SysButton(fmt(end), small = true, onClick = { pick(end) { end = it } })
                }
                SysButton("☀ Whole day (8:30 AM – 9:00 PM)", small = true, color = C.Muted, onClick = {
                    start = LocalTime.of(8, 30)
                    end = LocalTime.of(21, 0)
                })
                Text("Time to get ready before it starts", color = C.Text2, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0, 30, 45, 60, 90).forEach { m ->
                        SysButton(if (m == 0) "None" else "$m m", small = true, selected = ready == m, onClick = { ready = m })
                    }
                }
                val preview = Event(title.ifBlank { "Event" }, start, end, ready)
                Text(
                    if (preview.wholeDay) "Long event: training and the lesson are excused, the System only asks for the basics. No penalty."
                    else "Quests between ${fmt(preview.readyStart)} and ${fmt(preview.realEnd)} move before or after it.",
                    color = C.Muted, fontSize = 12.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(Event(title.trim().ifBlank { "Event" }, start, end, ready)) }) { Text("Save", color = C.Gold) }
        },
        dismissButton = {
            Row {
                if (current != null) TextButton(onClick = { onSave(null) }) { Text("Remove", color = C.Danger) }
                TextButton(onClick = onDismiss) { Text("Cancel", color = C.Muted) }
            }
        },
    )
}

@Composable
private fun DayCell(d: LocalDate, data: AppData, today: LocalDate, onClick: () -> Unit) {
    val type = Game.typeFor(data, d)
    val changed = data.records[d]?.dayType != null
    val shape = RoundedCornerShape(8.dp)
    val border = when {
        d == today -> C.Glow
        changed -> C.Gold
        else -> C.Border
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(if (d < today) C.Bg else C.PanelHi, shape)
            .border(if (d == today || changed) 1.5.dp else 1.dp, border, shape)
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(d.dayOfMonth.toString(), fontSize = 13.sp, color = if (d < today) C.Muted else C.Text, fontWeight = FontWeight.SemiBold)
        Text(if (data.records[d]?.events.orEmpty().isNotEmpty()) "🎉" else type.icon, fontSize = 14.sp)
    }
}
