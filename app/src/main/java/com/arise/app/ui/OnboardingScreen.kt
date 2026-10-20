package com.arise.app.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arise.app.data.AppData
import com.arise.app.game.fmt
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val niceDate = DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.ENGLISH)
private val shortDate = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private const val STEPS = 6

/**
 * First launch: a short System "registration". Finishing it sets Day 1.
 * With [editing], it skips the intro, keeps progress, and allows moving Day 1 once.
 * All answers use rememberSaveable so they survive Android recreating the screen.
 */
@Composable
fun OnboardingScreen(
    data: AppData,
    today: LocalDate,
    onUpdate: (AppData) -> Unit,
    editing: Boolean = false,
    onClose: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val d = data.settings
    val firstStep = if (editing) 1 else 0
    var step by rememberSaveable { mutableIntStateOf(firstStep) }
    var name by rememberSaveable { mutableStateOf(d.playerName) }
    var office by rememberSaveable { mutableStateOf(d.officeDays) }
    var wfh by rememberSaveable { mutableStateOf(d.wfhDays) }
    var wakeOffice by rememberSaveable { mutableStateOf(d.wakeOffice) }
    var wakeWfh by rememberSaveable { mutableStateOf(d.wakeWfh) }
    var wakeOff by rememberSaveable { mutableStateOf(d.wakeOff) }
    var paperSince by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var startTomorrow by rememberSaveable { mutableStateOf(false) }
    var newDay1 by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var goal by rememberSaveable { mutableStateOf(d.goalWeight?.toString() ?: "") }
    var current by rememberSaveable { mutableStateOf("") }
    val day1 = when {
        editing -> newDay1 ?: d.start
        startTomorrow -> today.plusDays(1)
        else -> today
    }

    fun pickTime(t: LocalTime, onPick: (LocalTime) -> Unit) {
        TimePickerDialog(ctx, { _, h, m -> onPick(LocalTime.of(h, m)) }, t.hour, t.minute, false).show()
    }

    fun pickDate(init: LocalDate, min: LocalDate, max: LocalDate, onPick: (LocalDate) -> Unit) {
        DatePickerDialog(ctx, { _, y, m, day -> onPick(LocalDate.of(y, m + 1, day)) }, init.year, init.monthValue - 1, init.dayOfMonth)
            .apply {
                datePicker.minDate = min.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                datePicker.maxDate = max.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
            .show()
    }

    fun finish() {
        val settings = d.copy(
            playerName = name.trim(),
            startDate = day1,
            backfillFrom = if (editing) d.backfillFrom else paperSince,
            officeDays = office,
            wfhDays = wfh,
            wakeOffice = wakeOffice,
            wakeWfh = wakeWfh,
            wakeOff = wakeOff,
            goalWeight = goal.replace(',', '.').toDoubleOrNull(),
            day1Changed = d.day1Changed || (editing && newDay1 != null && newDay1 != d.startDate),
        )
        var next = data.copy(settings = settings)
        current.replace(',', '.').toDoubleOrNull()?.let { w -> next = next.with(next.day(today).copy(weight = w)) }
        onUpdate(next)
        onClose()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        SysLabel(if (editing) "[System] Edit setup · ${step}/${STEPS - 1}" else "[System] Player registration · ${step + 1}/$STEPS")
        XpBar((step + 1f) / STEPS, height = 4.dp)
        Spacer(Modifier.height(8.dp))

        when (step) {
            0 -> SystemWindow {
                Text("You have been chosen.", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "From today, the System gives you daily quests: sleep, movement, food, learning. " +
                        "Complete them to earn XP, level up and rank up from E to S.\n\n" +
                        "A few quick questions and you're in. Everything stays on this phone.",
                    color = C.Text2,
                )
            }
            1 -> Panel {
                Text("What should the System call you?", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Field("Your name", name) { name = it }
            }
            2 -> Panel {
                Text("Your work week", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Tap a day to switch it: Office → WFH → Off.", color = C.Text2, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DayOfWeek.entries.forEach { day ->
                        val kind = when (day) {
                            in office -> 1
                            in wfh -> 2
                            else -> 0
                        }
                        val color: Color = when (kind) {
                            1 -> C.Glow
                            2 -> C.Gold
                            else -> C.Muted
                        }
                        SysButton(
                            day.getDisplayName(TextStyle.NARROW, Locale.ENGLISH),
                            selected = kind != 0,
                            color = color,
                            small = true,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                when (kind) {
                                    0 -> office = office + day
                                    1 -> {
                                        office = office - day
                                        wfh = wfh + day
                                    }
                                    else -> wfh = wfh - day
                                }
                            },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("🏢 Office: ${dayList(office)}", color = C.Glow, fontSize = 13.sp)
                Text("🏠 WFH: ${dayList(wfh)}", color = C.Gold, fontSize = 13.sp)
                Text("🌤 Days off: everything else", color = C.Muted, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Text("For one-off changes (a WFH day, leave), use Player → Calendar.", color = C.Muted, fontSize = 12.sp)
            }
            3 -> Panel {
                Text("Your wake-up goals", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Each morning you tap \"I'm up\" and the System scores how close you were. " +
                        "Try to keep these within about an hour of each other: a steady body clock helps your hormones and energy.",
                    color = C.Text2, fontSize = 13.sp,
                )
                Spacer(Modifier.height(12.dp))
                listOf(
                    Triple("🏢 Office days", wakeOffice, { t: LocalTime -> wakeOffice = t }),
                    Triple("🏠 WFH days", wakeWfh, { t: LocalTime -> wakeWfh = t }),
                    Triple("🌤 Days off", wakeOff, { t: LocalTime -> wakeOff = t }),
                ).forEach { (label, t, set) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(label, modifier = Modifier.width(140.dp))
                        SysButton(fmt(t), onClick = { pickTime(t, set) })
                    }
                }
            }
            4 -> Panel {
                if (!editing) {
                    Text("When is Day 1?", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Setting up at night? Pick tomorrow, and your morning reminders will be ready.", color = C.Text2, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SysButton("Today", selected = !startTomorrow, onClick = { startTomorrow = false })
                        SysButton("Tomorrow", selected = startTomorrow, onClick = { startTomorrow = true })
                    }
                    Spacer(Modifier.height(18.dp))
                    Text("Have you already started?", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "If you've been following the plan on paper, pick the day you started. You can tick off those days " +
                            "for XP and streaks. They never cause penalties.",
                        color = C.Text2, fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SysButton("No", selected = paperSince == null, onClick = { paperSince = null })
                        SysButton(
                            paperSince?.let { "Since ${it.format(shortDate)}" } ?: "Yes, since…",
                            selected = paperSince != null,
                            onClick = { pickDate(paperSince ?: today.minusDays(3), today.minusDays(60), today.minusDays(1)) { paperSince = it } },
                        )
                    }
                } else {
                    Text("Day 1", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Currently: ${day1.format(niceDate)}", color = C.Text2, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    if (d.day1Changed) {
                        Text("🔒 Day 1 is locked. You already moved it once. Your journey has begun.", color = C.Muted, fontSize = 13.sp)
                    } else {
                        Text(
                            "You can move Day 1 once. Weeks and the plan count from it. Your history stays.",
                            color = C.Muted, fontSize = 12.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        SysButton(
                            if (newDay1 != null) "New Day 1: ${newDay1!!.format(shortDate)}" else "Change Day 1 (once only)",
                            color = C.Gold,
                            selected = newDay1 != null,
                            onClick = { pickDate(day1, today.minusDays(30), today.plusDays(7)) { newDay1 = it } },
                        )
                    }
                }
            }
            else -> SystemWindow(accent = C.Gold) {
                Text(if (editing) "Your goal" else "Last one: your body", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Both optional. Weigh-ins are weekly quests after this.", color = C.Text2, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                if (!editing) {
                    Field("Current weight (kg)", current, KeyboardType.Decimal) { current = it }
                    Spacer(Modifier.height(6.dp))
                }
                Field("Goal weight (kg)", goal, KeyboardType.Decimal) { goal = it }
                Spacer(Modifier.height(14.dp))
                SysLabel("Summary", C.Gold)
                Text("Day 1: ${day1.format(niceDate)}", color = C.Text2, fontSize = 13.sp)
                if (!editing) paperSince?.let { Text("Fill-in days: ${it.format(niceDate)} → before Day 1", color = C.Text2, fontSize = 13.sp) }
                Text("Office ${dayList(office)} · WFH ${dayList(wfh)}", color = C.Text2, fontSize = 13.sp)
                Text("Wake goals: ${fmt(wakeOffice)} office · ${fmt(wakeWfh)} WFH · ${fmt(wakeOff)} off", color = C.Text2, fontSize = 13.sp)
                Text(if (editing) "Your XP and history stay as they are." else "Penalties start after Day 1.", color = C.Muted, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (step > firstStep) SysButton("‹ Back", onClick = { step-- })
            if (editing) SysButton("Cancel", color = C.Muted, onClick = onClose)
            Spacer(Modifier.weight(1f))
            if (step < STEPS - 1) {
                SysButton("Next ›", selected = true, onClick = { step++ })
            } else {
                SysButton(if (editing) "SAVE" else "ARISE", selected = true, color = C.Gold, onClick = { finish() })
            }
        }
    }
}

private fun dayList(days: Set<DayOfWeek>): String =
    if (days.isEmpty()) "none"
    else days.sorted().joinToString(" ") { it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) }

@Composable
private fun Field(label: String, value: String, type: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 12.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = type),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = C.Glow,
            unfocusedBorderColor = C.Border,
            focusedLabelColor = C.Glow,
            unfocusedLabelColor = C.Muted,
            cursorColor = C.Glow,
            focusedTextColor = C.Text,
            unfocusedTextColor = C.Text,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
