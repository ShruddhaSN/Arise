package com.arise.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import com.arise.app.data.RWhen
import com.arise.app.data.RoutineDef
import com.arise.app.game.Routines
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

private fun whenText(r: RoutineDef): String = when (r.`when`) {
    RWhen.WEEKDAY -> "Every " + (r.day?.getDisplayName(TextStyle.FULL, Locale.ENGLISH) ?: "week")
    else -> r.`when`.label
} + (if (r.optional) " · optional" else "") + (if (r.alternate) " · alternates" else "")

/**
 * Build a routine: pick common steps, add your own, and set when each one applies
 * (every day, work days, home days, one weekday...). Changes are reported through [onChange].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoutineEditor(night: Boolean, items: List<RoutineDef>, onChange: (List<RoutineDef>) -> Unit) {
    var open by remember { mutableStateOf<String?>(null) }
    var custom by remember { mutableStateOf("") }
    val catalog = if (night) Routines.nightCatalog else Routines.morningCatalog

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (items.isEmpty()) Text("Nothing yet. Add steps below.", color = C.Muted, fontSize = 13.sp)
        items.forEachIndexed { i, r ->
            val expanded = open == r.id
            Column(
                Modifier.fillMaxWidth().background(C.Bg.copy(alpha = 0.6f), RoundedCornerShape(10.dp)).padding(10.dp),
            ) {
                Row(Modifier.fillMaxWidth().clickable { open = if (expanded) null else r.id }, verticalAlignment = Alignment.CenterVertically) {
                    Text(r.icon, fontSize = 18.sp, modifier = Modifier.width(30.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(whenText(r), color = C.Muted, fontSize = 11.sp)
                    }
                    Text(if (expanded) "▴" else "✎", color = C.Muted)
                }
                if (expanded) {
                    Spacer(Modifier.height(8.dp))
                    Text("When?", color = C.Text2, fontSize = 12.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        RWhen.entries.filter { night || it != RWhen.BEFORE_HOME }.forEach { w ->
                            SysButton(w.label, small = true, selected = r.`when` == w, onClick = {
                                onChange(items.map { if (it.id == r.id) it.copy(`when` = w, day = if (w == RWhen.WEEKDAY) it.day ?: DayOfWeek.WEDNESDAY else null) else it })
                            })
                        }
                    }
                    if (r.`when` == RWhen.WEEKDAY) {
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            DayOfWeek.entries.forEach { d ->
                                SysButton(d.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).take(2), small = true, selected = r.day == d,
                                    modifier = Modifier.weight(1f), color = C.Gold,
                                    onClick = { onChange(items.map { if (it.id == r.id) it.copy(day = d) else it }) })
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Optional (doesn't block completion)", fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Switch(checked = r.optional, onCheckedChange = { v -> onChange(items.map { if (it.id == r.id) it.copy(optional = v) else it }) },
                            colors = SwitchDefaults.colors(checkedTrackColor = C.Glow, checkedThumbColor = C.Bg))
                    }
                    if ("/" in r.label) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Pick one option each day (A / B)", fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Switch(checked = r.alternate, onCheckedChange = { v -> onChange(items.map { if (it.id == r.id) it.copy(alternate = v) else it }) },
                                colors = SwitchDefaults.colors(checkedTrackColor = C.Glow, checkedThumbColor = C.Bg))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SysButton("↑", small = true, enabled = i > 0, color = C.Muted, onClick = {
                            onChange(items.toMutableList().apply { add(i - 1, removeAt(i)) })
                        })
                        SysButton("↓", small = true, enabled = i < items.lastIndex, color = C.Muted, onClick = {
                            onChange(items.toMutableList().apply { add(i + 1, removeAt(i)) })
                        })
                        Spacer(Modifier.weight(1f))
                        SysButton("Remove", small = true, color = C.Danger, onClick = {
                            open = null
                            onChange(items.filter { it.id != r.id })
                        })
                    }
                }
            }
        }

        val unused = catalog.filter { c -> items.none { it.id == c.id || it.label.equals(c.label, ignoreCase = true) } }
        if (unused.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text("Add common steps", color = C.Text2, fontSize = 13.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                unused.forEach { c -> SysButton("+ ${c.icon} ${c.label}", small = true, color = C.Muted, onClick = { onChange(items + c) }) }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = custom, onValueChange = { custom = it }, singleLine = true,
                placeholder = { Text("Your own step, e.g. Mom's seed powder", fontSize = 12.sp, color = C.Muted) },
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = C.Glow, unfocusedBorderColor = C.Border, cursorColor = C.Glow,
                    focusedTextColor = C.Text, unfocusedTextColor = C.Text,
                ),
            )
            SysButton("Add", small = true, enabled = custom.isNotBlank(), onClick = {
                val id = Routines.newId(if (night) "n" else "m")
                onChange(items + RoutineDef(id, custom.trim(), "✦", alternate = "/" in custom))
                custom = ""
                open = id
            })
        }
        Text("Tip: write \"Serum / Moisturizer\" and the System picks one each day.", color = C.Muted, fontSize = 11.sp)
    }
}
