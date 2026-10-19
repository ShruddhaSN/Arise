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
import androidx.compose.runtime.remember
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
private const val STEPS = 6

/**
 * First launch: a short System "registration". Finishing it makes today Day 1.
 * With [editing], it skips the intro and start-date steps and keeps Day 1 and progress as they are.
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
    var name by remember { mutableStateOf(d.playerName) }
    var office by remember { mutableStateOf(d.officeDays) }
    var wfh by remember { mutableStateOf(d.wfhDays) }
    var wakeWork by remember { mutableStateOf(d.wakeWork) }
    var wakeFree by remember { mutableStateOf(d.wakeFree) }
    var paperSince by remember { mutableStateOf<LocalDate?>(null) }
    var startTomorrow by remember { mutableStateOf(false) }
    val day1 = if (startTomorrow) today.plusDays(1) else today
    var goal by remember { mutableStateOf(d.goalWeight?.toString() ?: "") }

    fun pickTime(t: LocalTime, onPick: (LocalTime) -> Unit) {
        TimePickerDialog(ctx, { _, h, m -> onPick(LocalTime.of(h, m)) }, t.hour, t.minute, false).show()
    }

    fun pickDate() {
        val init = paperSince ?: today.minusDays(3)
        DatePickerDialog(ctx, { _, y, m, day -> paperSince = LocalDate.of(y, m + 1, day) }, init.year, init.monthValue - 1, init.dayOfMonth)
            .apply {
                datePicker.maxDate = today.minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                datePicker.minDate = today.minusDays(60).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
            .show()
    }

    fun finish() {
        onUpdate(
            data.copy(
                settings = d.copy(
                    playerName = name.trim(),
                    startDate = if (editing) d.startDate else day1,
                    backfillFrom = if (editing) d.backfillFrom else paperSince,
                    officeDays = office,
                    wfhDays = wfh,
                    wakeWork = wakeWork,
                    wakeFree = wakeFree,
                    goalWeight = goal.replace(',', '.').toDoubleOrNull(),
                ),
            ),
        )
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
        SysLabel(if (editing) "[System] Edit setup" else "[System] Player registration · ${step + 1}/$STEPS")
        XpBar((step + 1f) / STEPS, height = 4.dp)
        Spacer(Modifier.height(8.dp))

        when (step) {
            0 -> Panel(accent = C.Glow) {
                Text("You have been chosen.", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "From today, the System gives you daily quests: sleep, movement, food, learning. " +
                        "Complete them to earn XP, level up and rank up from E to S.\n\n" +
                        "Six quick questions and you're in. Everything stays on this phone.",
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
                            modifier = Modifier.weight(1f),
                            onClick = {
                                when (kind) {
                                    0 -> office = office + day
                                    1 -> { office = office - day; wfh = wfh + day }
                                    else -> wfh = wfh - day
                                }
                            },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Office: ${dayList(office)}", color = C.Glow, fontSize = 13.sp)
                Text("WFH: ${dayList(wfh)}", color = C.Gold, fontSize = 13.sp)
                Text("Days off: everything else", color = C.Muted, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Text("You can change a single day anytime from the Today tab.", color = C.Muted, fontSize = 12.sp)
            }
            3 -> Panel {
                Text("When will you wake up?", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Keep the two times within an hour of each other. A steady wake time is one of the best things you can do for your hormones and energy.",
                    color = C.Text2, fontSize = 13.sp,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Work days", modifier = Modifier.width(110.dp))
                    SysButton(fmt(wakeWork), onClick = { pickTime(wakeWork) { wakeWork = it } })
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Days off", modifier = Modifier.width(110.dp))
                    SysButton(fmt(wakeFree), onClick = { pickTime(wakeFree) { wakeFree = it } })
                }
            }
            4 -> Panel {
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
                    "If you've been following the plan on paper, pick the day you started. You'll be able to tick off those days " +
                        "for XP and streaks. They never cause penalties.",
                    color = C.Text2, fontSize = 13.sp,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SysButton("No", selected = paperSince == null, onClick = { paperSince = null })
                    SysButton(
                        paperSince?.let { "Since ${it.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))}" } ?: "Yes, since…",
                        selected = paperSince != null,
                        onClick = { pickDate() },
                    )
                }
            }
            else -> Panel(accent = C.Gold) {
                Text("Last one: your goal", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Optional. Your first weigh-in is a Day 1 quest.", color = C.Text2, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Field("Goal weight (kg)", goal, KeyboardType.Decimal) { goal = it }
                Spacer(Modifier.height(14.dp))
                SysLabel("Summary", C.Gold)
                Text("Day 1: ${(if (editing) d.start else day1).format(niceDate)}", color = C.Text2, fontSize = 13.sp)
                if (!editing) paperSince?.let { Text("Fill-in days: ${it.format(niceDate)} → yesterday", color = C.Text2, fontSize = 13.sp) }
                Text("Office ${dayList(office)} · WFH ${dayList(wfh)}", color = C.Text2, fontSize = 13.sp)
                Text("Wake ${fmt(wakeWork)} (work) / ${fmt(wakeFree)} (off)", color = C.Text2, fontSize = 13.sp)
                Text(if (editing) "Your Day 1, XP and history stay as they are." else "Penalties start after Day 1.", color = C.Muted, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (step > firstStep) SysButton("‹ Back", onClick = { step = if (editing && step == 5) 3 else step - 1 })
            if (editing) SysButton("Cancel", color = C.Muted, onClick = onClose)
            Spacer(Modifier.weight(1f))
            if (step < STEPS - 1) {
                SysButton("Next ›", selected = true, onClick = { step = if (editing && step == 3) 5 else step + 1 })
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
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
