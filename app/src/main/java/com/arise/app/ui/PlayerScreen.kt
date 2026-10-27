package com.arise.app.ui

import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arise.app.data.AppData
import com.arise.app.data.Stat
import com.arise.app.data.Store
import com.arise.app.game.Feats
import com.arise.app.game.Profile
import com.arise.app.game.Season
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
    onRestart: () -> Unit,
) {
    val ctx = LocalContext.current
    var claiming by remember { mutableStateOf<com.arise.app.game.Boss?>(null) }
    var restarting by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<com.arise.app.game.Achievement?>(null) }
    // Re-check permissions when coming back from the Settings app.
    var resumed by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) resumed++ }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    val titles = remember(data, today) { Feats.unlocked(data, today) }
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
                    titles.lastOrNull()?.let { Text("Title: “${it.name}”", color = C.Gold, fontSize = 13.sp) }
                }
            }
            Spacer(Modifier.height(12.dp))
            XpBar(p.xpInLevel.toFloat() / p.xpLevelSpan)
            Spacer(Modifier.height(4.dp))
            Text("${p.xpInLevel} / ${p.xpLevelSpan} XP to next level · ${p.totalXp} total", color = C.Muted, fontSize = 12.sp)
        }

        RatingPanel(data, p)

        Panel(accent = C.Gold) {
            SysLabel("Challenge · season ${data.settings.season}", C.Gold)
            Spacer(Modifier.height(4.dp))
            val s = data.settings
            Text(Season.label(s, today), fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
            if (s.challengeDays > 0) {
                XpBar((Season.day(s, today).toFloat() / s.challengeDays).coerceIn(0f, 1f), C.Gold)
                Text("Started ${s.currentSeasonStart}. Change the length in Edit setup.", color = C.Muted, fontSize = 12.sp)
            } else {
                Text("No end date. Just keep going.", color = C.Muted, fontSize = 12.sp)
            }
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
            SysLabel("Achievements · ${titles.size}/${Feats.all.size}", C.Gold)
            Spacer(Modifier.height(8.dp))
            Feats.all.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    row.forEach { a ->
                        val got = titles.any { it.id == a.id }
                        Column(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .background(if (got) C.Gold.copy(alpha = 0.14f) else C.Bg, RoundedCornerShape(10.dp))
                                .border(1.dp, if (got) C.Gold else C.Border, RoundedCornerShape(10.dp))
                                .clickable { viewing = a }
                                .padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(if (got) "✦" else "?", fontSize = 26.sp, color = if (got) C.Gold else C.Muted, fontWeight = FontWeight.Bold)
                            if (got) Text(a.name, fontSize = 10.sp, color = C.Text, textAlign = TextAlign.Center, maxLines = 2)
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
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
            SysButton("📅 Calendar: WFH, leave, sick days and events", onClick = onCalendar, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            SysButton("🎓 Replay the tutorial", onClick = onTutorial, modifier = Modifier.fillMaxWidth(), color = C.Muted)

        }

        Panel {
            SysLabel("How hard the System pushes")
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(1 to "Steady", 2 to "Hard", 3 to "Disappear").forEach { (v, label) ->
                    SysButton(
                        label, small = true, selected = data.settings.intensity == v, modifier = Modifier.weight(1f),
                        color = if (v == 3) C.Gold else C.Glow,
                        onClick = { onUpdate(data.copy(settings = data.settings.copy(intensity = v))) },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                when (data.settings.intensity) {
                    1 -> "1 emergency quest a day, base step goal."
                    2 -> "2 emergency quests a day, +1,000 steps, a finisher in strength sessions."
                    else -> "3 emergency quests a day, +2,000 steps, a finisher in every strength session. Sleep and rest stay protected."
                },
                color = C.Muted, fontSize = 12.sp,
            )
        }

        Panel {
            SysLabel("System pop-ups & voice")
            Spacer(Modifier.height(6.dp))
            val overlay = resumed >= 0 && android.provider.Settings.canDrawOverlays(ctx)
            Text(
                "Let the System pop up over other apps (like Instagram) for wake-up, emergency quests and the night report.",
                color = C.Text2, fontSize = 13.sp,
            )
            Spacer(Modifier.height(6.dp))
            SysButton(
                if (overlay) "✓ Pop-ups over other apps: allowed" else "Allow pop-ups over other apps",
                selected = overlay, color = C.Good, modifier = Modifier.fillMaxWidth(),
                onClick = {
                    runCatching {
                        ctx.startActivity(
                            Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")),
                        )
                    }
                },
            )
            if (Build.VERSION.SDK_INT >= 34) {
                val nm = ctx.getSystemService(NotificationManager::class.java)
                val full = resumed >= 0 && nm.canUseFullScreenIntent()
                Spacer(Modifier.height(6.dp))
                SysButton(
                    if (full) "✓ Full-screen alerts on the lock screen: allowed" else "Allow full-screen alerts on the lock screen",
                    selected = full, color = C.Good, modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        runCatching {
                            ctx.startActivity(
                                Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${ctx.packageName}")),
                            )
                        }
                    },
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("System voice")
                    Text("Counts reps, explains moves, speaks pop-ups", color = C.Muted, fontSize = 12.sp)
                }
                Switch(
                    checked = data.settings.voice,
                    onCheckedChange = { onUpdate(data.copy(settings = data.settings.copy(voice = it))) },
                    colors = SwitchDefaults.colors(checkedTrackColor = C.Glow, checkedThumbColor = C.Bg),
                )
            }
            Spacer(Modifier.height(6.dp))
            SysButton("🔊 Test the voice", small = true, color = C.Muted, onClick = {
                Voice.say("Player. The daily quest has arrived.")
            })
        }

        Panel {
            SysLabel("Settings")
            SysButton("Edit setup: work, meals, medicines, routines, goals", onClick = onEditSetup, modifier = Modifier.fillMaxWidth())
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

        Panel(accent = C.Danger) {
            SysLabel("Restart", C.Danger)
            Spacer(Modifier.height(4.dp))
            Text(
                "Wipe every day, XP, level, streak and title, retake the assessment and start again from Day 1. " +
                    "A copy is kept so you can undo it from the start screen.",
                color = C.Text2, fontSize = 13.sp,
            )
            Spacer(Modifier.height(8.dp))
            SysButton("⟲ Restart the System", onClick = { restarting = true }, color = C.Danger, modifier = Modifier.fillMaxWidth())
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


    if (restarting) {
        AlertDialog(
            onDismissRequest = { restarting = false },
            containerColor = C.Panel,
            title = { Text("Restart the System?") },
            text = {
                Text(
                    "Level ${p.level}, ${p.totalXp} XP, a ${p.streak}-day streak and ${data.records.size} logged days will be wiped. " +
                        "You'll retake the assessment and Day 1 starts fresh.\n\nWant a backup file first? Use Save backup file below.",
                    color = C.Text2,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    restarting = false
                    onRestart()
                }) { Text("Restart", color = C.Danger) }
            },
            dismissButton = { TextButton(onClick = { restarting = false }) { Text("Cancel", color = C.Muted) } },
        )
    }

    viewing?.let { a ->
        val got = titles.any { it.id == a.id }
        AlertDialog(
            onDismissRequest = { viewing = null },
            containerColor = C.Panel,
            title = { Text(if (got) "✦ ${a.name}" else "🔒 Locked", color = if (got) C.Gold else C.Text) },
            text = {
                Column {
                    if (got) Text("“${a.quote}”", color = C.Text, fontSize = 15.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("★ ${a.goal}", color = C.Text2, fontSize = 13.sp)
                }
            },
            confirmButton = { TextButton(onClick = { viewing = null }) { Text("OK", color = C.Glow) } },
        )
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

/** "Your rating": where you started (from the assessment) and where you are now. */
@Composable
private fun RatingPanel(data: AppData, p: Progress) {
    var showDay1 by remember { mutableStateOf(false) }
    val now = remember(data, p) { Feats.ratingsNow(data, p) }
    val day1 = remember(data) { Profile.ratings(data.settings.profile, 66) }
    Panel(accent = C.Glow) {
        SysLabel("Your rating")
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SysButton("Now", small = true, selected = !showDay1, onClick = { showDay1 = false })
            SysButton("Day 1", small = true, selected = showDay1, onClick = { showDay1 = true })
        }
        Spacer(Modifier.height(8.dp))
        now.zip(day1).chunked(2).forEachIndexed { row, pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                pair.forEachIndexed { i, (n, d) ->
                    RatingTile(
                        n.label, n.icon,
                        if (showDay1) d.now else n.now,
                        if (showDay1) null else n.now - d.now,
                        highlight = row == 0 && i == 0,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Text("Ratings rise with every cleared quest. Day 1 comes from your assessment.", color = C.Muted, fontSize = 11.sp)
    }
}
