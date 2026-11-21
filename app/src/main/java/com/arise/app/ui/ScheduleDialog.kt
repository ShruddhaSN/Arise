package com.arise.app.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arise.app.data.Settings
import com.arise.app.game.fmt
import java.time.LocalTime

/** Every time in the app is yours to set. Reminders can be switched off. */
@Composable
fun ScheduleDialog(s: Settings, onSave: (Settings) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var bed by remember { mutableStateOf(s.bedGoal) }
    var sleepMin by remember { mutableIntStateOf(s.sleepGoalMin) }
    var windDown by remember { mutableStateOf(s.windDown) }
    var checkIn by remember { mutableStateOf(s.checkIn) }
    var rescue by remember { mutableStateOf(s.rescue) }
    var lesson by remember { mutableStateOf(s.lessonAt) }
    var runs by remember { mutableIntStateOf(s.runsPerWeek) }
    var str by remember { mutableIntStateOf(s.strengthPerWeek) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Panel,
        title = { Text("⏰ Your times") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SysLabel("Sleep")
                TimeRow(ctx, "Bedtime goal", "Asleep by then is the goal. The phone works out when you actually slept.", bed, LocalTime.of(23, 0), { it?.let { t -> bed = t } }, canTurnOff = false)
                Stepper("Sleep goal", sleepMin / 30, 10..20, { "${it / 2}h${if (it % 2 == 1) " 30m" else ""}" }) { sleepMin = it * 30 }
                TimeRow(ctx, "Wind-down reminder", "Dim the lights, phone away.", windDown, LocalTime.of(22, 30), { windDown = it })
                SysLabel("Reminders")
                TimeRow(ctx, "Evening check-in", "A short report: what's done, what's left.", checkIn, LocalTime.of(21, 45), { checkIn = it })
                TimeRow(ctx, "Morning rescue", "Only speaks up if the morning slipped.", rescue, LocalTime.of(11, 0), { rescue = it })
                TimeRow(ctx, "Learning reminder", "The lesson is open all day anyway.", lesson, LocalTime.of(17, 0), { lesson = it })
                SysLabel("Training per week")
                Stepper("🏃 Runs", runs, 0..5, { "$it" }) { runs = it; if (runs + str > 7) str = 7 - runs }
                Stepper("⚔ Strength", str, 0..4, { "$it" }) { str = it; if (runs + str > 7) runs = 7 - str }
                Text("The System spreads them over the week. Skip one and it moves to your next free day; whatever's left at the end of the week is simply dropped.", color = C.Muted, fontSize = 11.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(s.copy(bedGoal = bed, sleepGoalMin = sleepMin, windDown = windDown, checkIn = checkIn, rescue = rescue, lessonAt = lesson, runsPerWeek = runs, strengthPerWeek = str))
            }) { Text("Save", color = C.Glow) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = C.Muted) } },
    )
}

private fun pickTime(ctx: android.content.Context, t: LocalTime, set: (LocalTime) -> Unit) =
    TimePickerDialog(ctx, { _, h, m -> set(LocalTime.of(h, m)) }, t.hour, t.minute, false).show()

@Composable
private fun TimeRow(ctx: android.content.Context, label: String, hint: String, value: LocalTime?, default: LocalTime, set: (LocalTime?) -> Unit, canTurnOff: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(hint, color = C.Muted, fontSize = 11.sp)
        }
        SysButton(value?.let { fmt(it) } ?: "Off", small = true, selected = value != null, onClick = { pickTime(ctx, value ?: default) { set(it) } })
        if (canTurnOff && value != null) {
            Spacer(Modifier.width(6.dp))
            SysButton("Off", small = true, color = C.Muted, onClick = { set(null) })
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, range: IntRange, show: (Int) -> String, set: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
        SysButton("−", small = true, enabled = value > range.first, color = C.Muted, onClick = { set(value - 1) })
        Text(show(value), fontFamily = Mono, modifier = Modifier.width(84.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        SysButton("+", small = true, enabled = value < range.last, onClick = { set(value + 1) })
    }
}
