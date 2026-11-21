package com.arise.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arise.app.data.AppData
import com.arise.app.data.RoutineDef
import com.arise.app.game.Game
import java.time.LocalDate

/**
 * One row per routine item (brush, skincare…): the last 4 weeks as dots, this week's count and the
 * current run. Grey = the item didn't apply that day (e.g. a weekday-only step). It's a mirror, not a score.
 */
@Composable
fun Habits(data: AppData, today: LocalDate) {
    val s = data.settings
    val defs = (s.morning.map { it to "☀" } + s.night.map { it to "🌙" }).distinctBy { it.first.id }
    if (defs.isEmpty()) return
    val tracked = data.records.values.count { it.routine.isNotEmpty() }
    var open by rememberSaveable { mutableStateOf(true) }
    Panel {
        Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SysLabel("Habits · last 4 weeks")
                Text("Every tick from your morning and night checklists.", color = C.Muted, fontSize = 12.sp)
            }
            Text(if (open) "▴" else "▾", color = C.Muted)
        }
        if (!open) return@Panel
        if (tracked < 3) {
            Spacer(Modifier.height(8.dp))
            Text("🔒 Unlocks after 3 days of ticking ($tracked/3). Open the morning or night checklist on Today.", color = C.Muted, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            XpBar(tracked / 3f, height = 4.dp)
            return@Panel
        }
        val days = (27 downTo 0).map { today.minusDays(it.toLong()) }.filter { it >= s.earliest }
        val weekStart = today.minusDays(6)
        Spacer(Modifier.height(10.dp))
        defs.forEach { (d, icon) ->
            HabitRow(data, d, icon, days, weekStart, today)
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun HabitRow(data: AppData, d: RoutineDef, icon: String, days: List<LocalDate>, weekStart: LocalDate, today: LocalDate) {
    fun applies(day: LocalDate): Boolean {
        val plan = Game.plan(data, day)
        return plan.quests.any { q -> q.items.any { it.id == d.id } }
    }
    fun did(day: LocalDate) = data.records[day]?.routine?.contains(d.id) == true
    val week = days.filter { it >= weekStart }
    val thisWeek = week.count { did(it) }
    val weekOf = week.count { applies(it) }
    var run = 0
    for (day in days.reversed()) {
        if (!applies(day)) continue
        if (did(day)) run++ else if (day != today) break
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${d.icon} ${d.label}", fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text("$thisWeek/$weekOf this week" + if (run >= 2) " · $run in a row" else "", color = C.Text2, fontSize = 12.sp, fontFamily = Mono)
            Spacer(Modifier.width(6.dp))
            Text(icon, fontSize = 11.sp)
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            days.forEach { day ->
                val color = when {
                    did(day) -> C.Good
                    !applies(day) -> C.Grid.copy(alpha = 0.4f)
                    else -> C.Grid
                }
                Box(Modifier.size(width = 8.dp, height = 14.dp).background(color, RoundedCornerShape(2.dp)))
            }
        }
    }
}
