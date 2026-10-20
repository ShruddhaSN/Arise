package com.arise.app.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arise.app.data.AppData
import com.arise.app.data.DayType
import com.arise.app.game.Game
import com.arise.app.game.Program
import java.time.LocalDate
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
            "Tap a day to change just that day: taking WFH, going to the office, leave or sick. " +
                "Your normal week is in Edit setup.",
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
            DayType.entries.forEach { t -> Text("${t.icon}  ${t.label}", color = C.Text2, fontSize = 13.sp) }
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
                        "↺  Normal (${normal.icon} ${normal.label})", modifier = Modifier.fillMaxWidth(),
                        selected = data.records[d]?.dayType == null,
                        onClick = {
                            onUpdate(data.with(data.day(d).copy(dayType = null)))
                            picking = null
                        },
                    )
                    DayType.entries.forEach { t ->
                        SysButton(
                            "${t.icon}  ${t.label}", modifier = Modifier.fillMaxWidth(),
                            selected = data.records[d]?.dayType != null && current == t,
                            onClick = {
                                onUpdate(data.with(data.day(d).copy(dayType = if (t == normal) null else t)))
                                picking = null
                            },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picking = null }) { Text("Cancel", color = C.Muted) } },
        )
    }
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
        Text(type.icon, fontSize = 14.sp)
    }
}
