package com.arise.app.ui

import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arise.app.data.AppData
import com.arise.app.data.DayRecord
import com.arise.app.data.Done
import com.arise.app.game.Game
import com.arise.app.game.Program
import com.arise.app.game.fmt
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val shortFmt = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val longFmt = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)

/** Minutes slept, from last night's bed time to this morning's wake time. */
private fun sleepMinutes(r: DayRecord?): Int? {
    val b = r?.bedTime?.let(LocalTime::parse) ?: return null
    val w = r?.wakeTime?.let(LocalTime::parse) ?: return null
    var mins = w.toSecondOfDay() / 60 - b.toSecondOfDay() / 60
    if (mins <= 0) mins += 24 * 60
    return mins
}

/** Bedtime as minutes after 6 PM, so 11 PM and 1 AM sit on one continuous scale. */
private fun bedScale(t: LocalTime): Float {
    var m = t.toSecondOfDay() / 60 - 18 * 60
    if (m < 0) m += 24 * 60
    return m.toFloat()
}

private fun bedLabel(v: Float): String = fmt(LocalTime.of(18, 0).plusMinutes(v.toLong()))

@Composable
fun LogScreen(data: AppData, today: LocalDate, onUpdate: (AppData) -> Unit) {
    val ctx = LocalContext.current
    var date by remember { mutableStateOf(today) }
    val rec = data.day(date)

    var weight by remember(date) { mutableStateOf(rec.weight?.toString() ?: "") }
    var waist by remember(date) { mutableStateOf(rec.waist?.toString() ?: "") }
    var steps by remember(date) { mutableStateOf(rec.steps?.toString() ?: "") }
    var runMin by remember(date) { mutableStateOf(rec.longestRunSec?.let { (it / 60).toString() } ?: "") }

    fun pickTime(current: String?, fallback: LocalTime, onPick: (String) -> Unit) {
        val t = current?.let(LocalTime::parse) ?: fallback
        TimePickerDialog(ctx, { _, h, m -> onPick("%02d:%02d".format(h, m)) }, t.hour, t.minute, false).show()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SysButton("‹", onClick = { date = date.minusDays(1) }, enabled = date > data.settings.earliest)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                SysLabel("[System] Body log")
                Text(date.format(longFmt), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            SysButton("›", onClick = { date = date.plusDays(1) }, enabled = date < today)
        }

        Panel {
            SysLabel("Last night's sleep")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SysButton("Lights off: ${rec.bedTime?.let { fmt(LocalTime.parse(it)) } ?: "set"}", onClick = {
                    pickTime(rec.bedTime, LocalTime.of(23, 0)) { onUpdate(data.with(rec.copy(bedTime = it))) }
                })
                SysButton("Woke: ${rec.wakeTime?.let { fmt(LocalTime.parse(it)) } ?: "set"}", onClick = {
                    pickTime(rec.wakeTime, LocalTime.of(6, 30)) { onUpdate(data.with(rec.copy(wakeTime = it))) }
                })
            }
            sleepMinutes(rec)?.let {
                Spacer(Modifier.height(6.dp))
                Text("Slept ${it / 60} h ${it % 60} min", color = C.Text2, fontSize = 13.sp)
            }
        }

        Panel {
            SysLabel("Energy today")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..5).forEach { e ->
                    SysButton(
                        e.toString(),
                        onClick = { onUpdate(data.with(rec.copy(energy = if (rec.energy == e) null else e))) },
                        selected = rec.energy == e,
                    )
                }
            }
            Text("1 = drained · 5 = great. Helps us spot patterns with sleep and your cycle.", color = C.Muted, fontSize = 11.sp)
        }

        Panel {
            SysLabel("Protein today")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(1 to "Low", 2 to "Okay", 3 to "Good").forEach { (v, label) ->
                    SysButton(
                        label,
                        onClick = { onUpdate(data.with(rec.copy(protein = if (rec.protein == v) null else v))) },
                        selected = rec.protein == v,
                        color = C.Gold,
                    )
                }
            }
            Text("A rough feel is enough: eggs, dal, paneer, curd, chana, chicken. No counting.", color = C.Muted, fontSize = 11.sp)
        }

        Panel {
            SysLabel("⚖ Weight & waist · weekly")
            Spacer(Modifier.height(4.dp))
            Text("Morning, after the bathroom, before food or water.", color = C.Muted, fontSize = 12.sp)
            NumField("Weight (kg)", weight) { weight = it }
            NumField("Waist (cm), at the belly button", waist) { waist = it }
            Spacer(Modifier.height(10.dp))
            SysButton("Save", selected = true, small = true, onClick = {
                val w = weight.replace(',', '.').toDoubleOrNull()
                // Logging a weight on a weigh-in day completes that quest.
                val done = if (w != null && Program.isWeighDay(data.settings, date)) rec.done + ("weigh" to Done.FULL) else rec.done
                onUpdate(data.with(rec.copy(done = done, weight = w, waist = waist.replace(',', '.').toDoubleOrNull())))
                Toast.makeText(ctx, "[SYSTEM] Weight saved", Toast.LENGTH_SHORT).show()
            })
        }

        Panel {
            SysLabel("👣 Steps")
            Spacer(Modifier.height(4.dp))
            Text("From Samsung Health. Also editable in Today's all-day box.", color = C.Muted, fontSize = 12.sp)
            NumField("Steps", steps, decimal = false) { steps = it }
            Spacer(Modifier.height(10.dp))
            SysButton("Save", selected = true, small = true, onClick = {
                onUpdate(
                    data.with(
                        rec.copy(amounts = steps.toIntOrNull()?.let { rec.amounts + ("steps" to it.toDouble()) } ?: (rec.amounts - "steps")),
                    ),
                )
                Toast.makeText(ctx, "[SYSTEM] Steps saved", Toast.LENGTH_SHORT).show()
            })
        }

        Panel {
            SysLabel("🏃 Longest non-stop run")
            Spacer(Modifier.height(4.dp))
            Text("The run coach fills this in. Edit it if you ran outside the app.", color = C.Muted, fontSize = 12.sp)
            NumField("Minutes", runMin, decimal = false) { runMin = it }
            Spacer(Modifier.height(10.dp))
            SysButton("Save", selected = true, small = true, onClick = {
                onUpdate(data.with(rec.copy(longestRunSec = runMin.toIntOrNull()?.times(60) ?: rec.longestRunSec)))
                Toast.makeText(ctx, "[SYSTEM] Run saved", Toast.LENGTH_SHORT).show()
            })
        }

        SysLabel("Trends")
        Charts(data, today)
        SkipPatterns(data, today)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun NumField(label: String, value: String, decimal: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 12.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = C.Glow,
            unfocusedBorderColor = C.Border,
            focusedLabelColor = C.Glow,
            unfocusedLabelColor = C.Muted,
            cursorColor = C.Glow,
        ),
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    )
}

@Composable
private fun Charts(data: AppData, today: LocalDate) {
    val days14 = (13 downTo 0).map { today.minusDays(it.toLong()) }
    val labels14 = days14.map { it.format(shortFmt) }
    val recs = data.records.values
    val sleepNights = recs.count { sleepMinutes(it) != null }
    val bedNights = recs.count { it.bedTime != null }
    val proteinDays = recs.count { it.protein != null }
    val runSessions = recs.count { (it.runMinutes ?: 0) > 0 || (it.amounts["run"] ?: 0.0) > 0.0 }
    val weighed = data.records.values.filter { it.weight != null }.sortedBy { it.date }.takeLast(30)
    val trackedDays = (java.time.temporal.ChronoUnit.DAYS.between(data.settings.earliest, today) + 1).toInt().coerceAtLeast(0)

    if (sleepNights >= 3) {
        BarChart(
            "Sleep · last 14 nights", "Hours slept. Gold line = 8 h target.",
            labels14, days14.map { d -> sleepMinutes(data.records[d])?.let { it / 60f } },
            format = { "%.1f h".format(it) }, target = 8f,
        )
    } else {
        LockedChart("Sleep", sleepNights, 3, "nights logged")
    }

    if (bedNights >= 3) {
        LineChart(
            "Bedtime · last 14 nights", "Lights-off time. Lower is earlier. Gold line = 11:00 PM.",
            labels14, days14.map { d -> data.records[d]?.bedTime?.let { bedScale(LocalTime.parse(it)) } },
            format = ::bedLabel, target = bedScale(LocalTime.of(23, 0)),
        )
    } else {
        LockedChart("Bedtime", bedNights, 3, "nights logged")
    }

    if (proteinDays >= 3) {
        BarChart(
            "Protein · last 14 days", "1 = low · 2 = okay · 3 = good",
            labels14, days14.map { d -> data.records[d]?.protein?.toFloat() },
            format = { when (it.toInt()) { 1 -> "Low"; 2 -> "Okay"; else -> "Good" } },
        )
    } else {
        LockedChart("Protein", proteinDays, 3, "days logged")
    }

    val s = data.settings
    val weeks = Program.week(s, today)
    val weekNums = (1..weeks).toList()
    val runDays = generateSequence(s.earliest) { it.plusDays(1) }.takeWhile { it <= today }.toList()
    if (runSessions >= 2) {
        BarChart(
            "Running · minutes per week", "Total minutes actually run (not walking)",
            weekNums.map { "W$it" },
            weekNums.map { w ->
                runDays.filter { Program.week(s, it) == w }.sumOf { data.records[it]?.runMinutes ?: 0 }.toFloat()
            },
            format = { "${it.toInt()} min" },
        )
    } else {
        LockedChart("Running", runSessions, 2, "run sessions")
    }

    if (weighed.size >= 3) {
        LineChart(
            "Weight", "Morning weigh-ins (last 30). Expect wiggles, watch the trend.",
            weighed.map { it.date.format(shortFmt) }, weighed.map { it.weight!!.toFloat() },
            format = { "%.1f kg".format(it) },
        )
    } else {
        LockedChart("Weight", weighed.size, 3, "weigh-ins")
    }

    val gridStart = Program.weekStart(s.earliest)
    val cols = maxOf((java.time.temporal.ChronoUnit.DAYS.between(gridStart, today) / 7).toInt() + 1, 10)
    if (trackedDays >= 7) {
        Heatmap(
            "Consistency", "Each square is a day (Sun → Sat, top to bottom). Brighter = more quests done.",
            cols,
            ratio = { w, d ->
                val day = gridStart.plusDays(w * 7L + d)
                if (day > today || day < s.earliest) null else Game.status(data, day).ratio
            },
            describe = { w, d ->
                val day = gridStart.plusDays(w * 7L + d)
                if (day > today) "${day.format(longFmt)} · upcoming"
                else if (day < s.earliest) "${day.format(longFmt)} · before you started"
                else {
                    val s = Game.status(data, day)
                    "${day.format(longFmt)} · ${s.doneCount}/${s.total} quests" + if (s.cleared) " · cleared" else ""
                }
            },
        )
    } else {
        LockedChart("Consistency", trackedDays, 7, "days tracked")
    }
}

/** "Not today" reasons over the last 30 days, so patterns become visible. */
@Composable
private fun SkipPatterns(data: AppData, today: LocalDate) {
    val from = today.minusDays(30)
    val skips = data.records.values.filter { it.date > from }.flatMap { r -> r.skipped.map { it.key to it.value } }
    if (skips.isEmpty()) return
    Panel {
        SysLabel("Skip patterns · 30 days")
        Spacer(Modifier.height(6.dp))
        skips.groupingBy { it.second }.eachCount().entries.sortedByDescending { it.value }.forEach { (reason, n) ->
            Text("${reason.icon} ${reason.label}: $n", color = C.Text2, fontSize = 13.sp)
        }
        Spacer(Modifier.height(6.dp))
        val names = Game.plan(data, today).quests.associate { it.id to it.title }
        val top = skips.groupingBy { it.first }.eachCount().entries.sortedByDescending { it.value }.take(3)
        Text(
            "Most skipped: " + top.joinToString(" · ") { "${names[it.key] ?: it.key} (${it.value})" },
            color = C.Muted, fontSize = 12.sp,
        )
    }
}

/** A chart that hasn't unlocked yet: a chart with one or two dots says nothing. */
@Composable
private fun LockedChart(title: String, have: Int, need: Int, unit: String) {
    Panel(accent = C.Border) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🔒", fontSize = 18.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("$title chart", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("Unlocks after $need $unit (${minOf(have, need)}/$need)", color = C.Muted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        XpBar(minOf(have, need).toFloat() / need, height = 4.dp)
    }
}
