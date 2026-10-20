package com.arise.app.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arise.app.data.AppData
import com.arise.app.data.Stat
import com.arise.app.data.Store
import com.arise.app.game.Game
import com.arise.app.game.Program
import com.arise.app.game.Progress
import java.time.LocalDate

@Composable
fun PlayerScreen(
    data: AppData,
    today: LocalDate,
    p: Progress,
    onUpdate: (AppData) -> Unit,
    onEditSetup: () -> Unit,
    onCalendar: () -> Unit,
    onTutorial: () -> Unit,
) {
    val ctx = LocalContext.current
    var claiming by remember { mutableStateOf<com.arise.app.game.Boss?>(null) }
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(Store.toJson(data).toByteArray()) }
            }.onSuccess { Toast.makeText(ctx, "[SYSTEM] Backup saved", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(ctx, "Couldn't save the backup", Toast.LENGTH_LONG).show() }
        }
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: error("empty")
                Store.fromJson(text)
            }.onSuccess {
                onUpdate(it)
                Toast.makeText(ctx, "[SYSTEM] Backup restored", Toast.LENGTH_SHORT).show()
            }.onFailure { Toast.makeText(ctx, "That doesn't look like an Arise backup", Toast.LENGTH_LONG).show() }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Panel(accent = C.Glow) {
            SysLabel("[System] Player status")
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(64.dp)
                        .background(C.Bg, RoundedCornerShape(12.dp))
                        .border(2.dp, C.Gold, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(p.rank, color = C.Gold, fontSize = 34.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(data.settings.name, color = C.Text2, fontSize = 14.sp)
                    Text("Level ${p.level}", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("${p.rank}-Rank · ${p.title}", color = C.Text2)
                }
            }
            Spacer(Modifier.height(12.dp))
            XpBar(p.xpInLevel.toFloat() / p.xpLevelSpan)
            Spacer(Modifier.height(4.dp))
            Text("${p.xpInLevel} / ${p.xpLevelSpan} XP to next level · ${p.totalXp} total", color = C.Muted, fontSize = 12.sp)
        }

        Panel {
            SysLabel("Stats")
            Spacer(Modifier.height(6.dp))
            Stat.entries.forEach { s -> StatRow("${s.icon} ${s.label}", s.full, p.stats.getValue(s)) }
            StatRow("DSC", "Discipline · days cleared", p.discipline)
        }

        Panel {
            SysLabel("Streak")
            Text("🔥 ${p.streak} day${if (p.streak == 1) "" else "s"}", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Best ${p.bestStreak} · ${p.clearedDays} days cleared", color = C.Text2, fontSize = 13.sp)
            Text("Day 1 was ${data.settings.start}", color = C.Muted, fontSize = 12.sp)
            Text(
                "Minimum passes left this week: ${Game.passesLeft(data, today)}/${Program.MIN_PASSES_PER_WEEK}",
                color = C.Muted, fontSize = 12.sp,
            )
        }

        Panel {
            SysLabel("Boss fights", C.Gold)
            Spacer(Modifier.height(6.dp))
            p.bosses.forEach { (b, cleared) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (cleared) "⚔" else "🔒", fontSize = 18.sp, modifier = Modifier.width(32.dp))
                    Column(Modifier.weight(1f)) {
                        Text(b.name, fontWeight = FontWeight.SemiBold, color = if (cleared) C.Gold else C.Text)
                        Text(b.goal, color = C.Muted, fontSize = 12.sp)
                    }
                    if (b.claim && !cleared) {
                        SysButton("I did it", small = true, color = C.Gold, onClick = { claiming = b })
                    } else {
                        Text(if (cleared) "CLEARED" else "+${b.xp} XP", color = if (cleared) C.Good else C.Glow, fontFamily = Mono, fontSize = 11.sp)
                    }
                }
            }
            if ("kr_topik" in data.settings.claimedBosses) {
                Spacer(Modifier.height(8.dp))
                SystemWindow(accent = C.Gold) {
                    SysLabel("[System] Hidden gate opened", C.Gold)
                    Text("A new language gate has opened. Choose: 🇯🇵 Japanese or 🇨🇳 Chinese. Tell your coach which one.", color = C.Text2, fontSize = 13.sp)
                }
            }
        }

        Panel {
            SysLabel("Plan")
            Spacer(Modifier.height(6.dp))
            SysButton("📅 Calendar: mark WFH, leave or sick days", onClick = onCalendar, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            SysButton("🎓 Replay the tutorial", onClick = onTutorial, modifier = Modifier.fillMaxWidth(), color = C.Muted)
        }

        Panel {
            SysLabel("Settings")
            SysButton("Edit setup: name, work days, wake times, goal", onClick = onEditSetup, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Schedule notifications")
                    Text("A [SYSTEM] alert at each block of your day", color = C.Muted, fontSize = 12.sp)
                }
                Switch(
                    checked = data.settings.notifications,
                    onCheckedChange = { onUpdate(data.copy(settings = data.settings.copy(notifications = it))) },
                    colors = SwitchDefaults.colors(checkedTrackColor = C.Glow, checkedThumbColor = C.Bg),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SysButton("Save backup file", onClick = { exportFile.launch("arise-backup-$today.json") })
                SysButton("Restore", onClick = { importFile.launch(arrayOf("application/json", "text/plain", "*/*")) })
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "One file holds everything: every day, quest, log and setting. Save it to Google Drive " +
                    "(pick Drive in the file picker) during your Sunday review. On a new phone, install Arise and tap Restore.",
                color = C.Muted, fontSize = 12.sp,
            )
        }

        Panel {
            SysLabel("Samsung setup (for reliable reminders)", C.Gold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Samsung can put apps to sleep, which delays reminders. Once:\n" +
                    "• Settings → Battery → Background usage limits → Never sleeping apps → add Arise\n" +
                    "• Long-press the Arise icon → App info → Notifications: allow all\n" +
                    "• Add the widget: long-press the home screen → Widgets → Arise",
                color = C.Text2, fontSize = 13.sp,
            )
        }
        Spacer(Modifier.height(24.dp))
    }


    claiming?.let { b ->
        AlertDialog(
            onDismissRequest = { claiming = null },
            containerColor = C.Panel,
            title = { Text(b.name) },
            text = { Text("${b.goal}\n\nHonestly done? The System trusts you.", color = C.Text2) },
            confirmButton = {
                TextButton(onClick = {
                    onUpdate(data.copy(settings = data.settings.copy(claimedBosses = data.settings.claimedBosses + b.id)))
                    claiming = null
                }) { Text("Yes, I did it", color = C.Gold) }
            },
            dismissButton = { TextButton(onClick = { claiming = null }) { Text("Not yet", color = C.Muted) } },
        )
    }
}

@Composable
private fun StatRow(label: String, full: String, value: Int) {
    Column(Modifier.padding(vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = C.Glow, fontFamily = Mono, fontWeight = FontWeight.Bold, modifier = Modifier.width(48.dp))
            Text(full, color = C.Text2, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(value.toString(), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Spacer(Modifier.height(4.dp))
        // Bar fills over each band of 10 points, so growth is always visible.
        XpBar(((value % 10) / 10f).coerceAtLeast(0.04f), height = 4.dp)
    }
}
