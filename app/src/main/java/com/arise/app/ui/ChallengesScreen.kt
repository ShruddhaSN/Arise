package com.arise.app.ui

import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arise.app.data.AppData
import com.arise.app.data.ChallengeKind
import com.arise.app.data.Taken
import com.arise.app.game.Challenges
import com.arise.app.game.Mark
import com.arise.app.game.fmt
import java.time.LocalDate
import java.time.LocalTime

/** One pending "accept" decision: what, and (for skill/body) when it goes in the day. */
private data class Offer(val kind: ChallengeKind, val id: String, val title: String, val needsTime: Boolean, val defaultTime: LocalTime)

/**
 * Challenges: a weekly rule, a monthly skill, an optional body program. Taking one adds its daily
 * quest to the schedule.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChallengesScreen(data: AppData, today: LocalDate, onUpdate: (AppData) -> Unit, onTrain: () -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val ctx = LocalContext.current
    val uri = LocalUriHandler.current
    val s = data.settings
    var offer by remember { mutableStateOf<Offer?>(null) }
    var quitting by remember { mutableStateOf<Taken?>(null) }
    var allWeekly by remember { mutableStateOf(false) }

    val running = s.challenges.filter { Challenges.running(it, today) }
    // Finished in the last 7 days, or a kept skill: still worth showing.
    val recent = s.challenges.filter { t ->
        !Challenges.running(t, today) && t.quit == null &&
            (today.isAfter(Challenges.endDate(t)) && today.minusDays(7).isBefore(Challenges.endDate(t)) ||
                t.kind == ChallengeKind.SKILL && t.keep && today.isAfter(Challenges.endDate(t)))
    }
    fun has(kind: ChallengeKind) = running.any { it.kind == kind }

    fun update(t: Taken, f: (Taken) -> Taken) {
        onUpdate(data.copy(settings = s.copy(challenges = s.challenges.map { if (it === t) f(it) else it })))
    }

    fun pickTime(init: LocalTime, set: (LocalTime) -> Unit) {
        TimePickerDialog(ctx, { _, h, m -> set(LocalTime.of(h, m)) }, init.hour, init.minute, false).show()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SysButton("‹ Back", onClick = onClose, small = true)
            Spacer(Modifier.weight(1f))
            SysLabel("[System] Challenges")
        }
        Text(
            "Take on a challenge and its daily quest joins your schedule. Clear it for bonus XP and a title.",
            color = C.Text2, fontSize = 13.sp,
        )

        (running + recent).forEach { t ->
            ActiveCard(data, t, today, onTrain,
                onTime = { pickTime(t.time ?: LocalTime.of(19, 0)) { nt -> update(t) { it.copy(time = nt) } } },
                onKeep = { k -> update(t) { it.copy(keep = k) } },
                onQuit = { quitting = t },
                open = { runCatching { uri.openUri(it) } },
            )
        }

        // ---- Weekly ----
        SysLabel("Weekly challenge · every day counts", C.Gold)
        if (has(ChallengeKind.WEEKLY)) {
            Text("One weekly challenge at a time. Finish this one first.", color = C.Muted, fontSize = 12.sp)
        } else {
            val list = if (allWeekly) Challenges.weekly else Challenges.offers(today)
            list.forEach { w ->
                Panel(accent = C.Gold) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(w.icon, fontSize = 24.sp, modifier = Modifier.width(40.dp))
                        Column(Modifier.weight(1f)) {
                            Text(w.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(w.rule, color = C.Text2, fontSize = 12.sp)
                        }
                        SysButton("Take it", small = true, color = C.Gold, onClick = {
                            offer = Offer(ChallengeKind.WEEKLY, w.id, w.title, false, LocalTime.of(21, 30))
                        })
                    }
                }
            }
            SysButton(if (allWeekly) "Show this week's 3 picks" else "See all ${Challenges.weekly.size} weekly challenges", small = true, color = C.Muted,
                onClick = { allWeekly = !allWeekly })
        }

        // ---- Skills ----
        Spacer(Modifier.height(4.dp))
        SysLabel("Monthly skill · 30 days, then spaced recall", C.Glow)
        if (has(ChallengeKind.SKILL)) {
            Text("One skill month at a time. Finish this one first.", color = C.Muted, fontSize = 12.sp)
        } else {
            Challenges.skills.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { k ->
                        Column(
                            Modifier.weight(1f).background(C.Panel, RoundedCornerShape(12.dp)).clickable {
                                offer = Offer(ChallengeKind.SKILL, k.id, k.name, true, LocalTime.of(19, 30))
                            }.padding(12.dp),
                        ) {
                            Text(k.icon, fontSize = 26.sp)
                            Text(k.name, fontWeight = FontWeight.Bold)
                            Text(k.resources.joinToString(" · ") { it.name }, color = C.Muted, fontSize = 11.sp, maxLines = 2)
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        // ---- Body ----
        Spacer(Modifier.height(4.dp))
        SysLabel("Body challenges · optional, with video", C.RunHot)
        if (has(ChallengeKind.BODY)) {
            Text("One body challenge at a time. Finish this one first.", color = C.Muted, fontSize = 12.sp)
        } else {
            Challenges.body.forEach { b ->
                Panel(accent = C.RunHot) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(b.icon, fontSize = 24.sp, modifier = Modifier.width(40.dp))
                        Column(Modifier.weight(1f)) {
                            Text(b.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(b.about, color = C.Text2, fontSize = 12.sp)
                        }
                    }
                    b.honest?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, color = C.Gold, fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SysButton("▶ Preview video", small = true, color = C.Muted, onClick = { runCatching { uri.openUri(b.video) } })
                        Spacer(Modifier.weight(1f))
                        SysButton("Take it", small = true, color = C.RunHot, onClick = {
                            offer = Offer(ChallengeKind.BODY, b.id, b.title, true, LocalTime.of(19, 0))
                        })
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    offer?.let { o ->
        var at by remember(o) { mutableStateOf(o.defaultTime) }
        AlertDialog(
            onDismissRequest = { offer = null },
            containerColor = C.Panel,
            title = {
                Column {
                    SysLabel("[System] Challenge", C.Gold)
                    Text(o.title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        when (o.kind) {
                            ChallengeKind.WEEKLY -> "The next 7 days. Every night at 9:30 PM the System asks: kept it today? +${Challenges.WEEKLY_DAY_XP} XP per day kept, ${Challenges.WEEKLY_XP} for a perfect week. Fewer than ${Challenges.WEEKLY_PENALTY_BELOW} days = a penalty quest."
                            ChallengeKind.SKILL -> "30 days, a daily practice quest. After the month, spaced recall keeps it: do it from memory on day 1, 3, 7, 14, 30, 60, then monthly (you can switch that off)."
                            ChallengeKind.BODY -> "A guided session every day, with a follow-along video. Optional: give up any time, no penalty."
                        },
                        color = C.Text2, fontSize = 13.sp,
                    )
                    if (o.needsTime) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Daily at", modifier = Modifier.width(80.dp))
                            SysButton(fmt(at), onClick = { pickTime(at) { at = it } })
                        }
                        Text("Pick a time that's free most days. You can change it later.", color = C.Muted, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val t = Taken(o.id, o.kind, today, if (o.needsTime) at else null)
                    onUpdate(data.copy(settings = s.copy(challenges = s.challenges + t)))
                    offer = null
                    SystemBus.post(
                        SysMsg(
                            "CHALLENGE ACCEPTED",
                            listOfNotNull(
                                o.title,
                                "Day 1 is today. Ends ${Challenges.endDate(t)}.",
                                if (o.needsTime) "Added to your schedule at ${fmt(at)} every day." else "Check in every night at 9:30 PM.",
                            ),
                            accent = C.Gold, speak = "Challenge accepted.",
                        ),
                    )
                }) { Text("ACCEPT", color = C.Gold) }
            },
            dismissButton = { TextButton(onClick = { offer = null }) { Text("Not now", color = C.Muted) } },
        )
    }
    quitting?.let { t ->
        AlertDialog(
            onDismissRequest = { quitting = null },
            containerColor = C.Panel,
            title = { Text("Give up ${Challenges.title(t)}?") },
            text = { Text("Its quest leaves your schedule from today. No penalty, but no reward either.", color = C.Text2) },
            confirmButton = {
                TextButton(onClick = {
                    update(t) { it.copy(quit = today) }
                    quitting = null
                }) { Text("Give up", color = C.Danger) }
            },
            dismissButton = { TextButton(onClick = { quitting = null }) { Text("Keep going", color = C.Glow) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveCard(
    data: AppData,
    t: Taken,
    today: LocalDate,
    onTrain: () -> Unit,
    onTime: () -> Unit,
    onKeep: (Boolean) -> Unit,
    onQuit: () -> Unit,
    open: (String) -> Unit,
) {
    val marks = Challenges.marks(data, t, today)
    val len = Challenges.length(t)
    val day = Challenges.day(t, today).coerceAtMost(len)
    val over = today.isAfter(Challenges.endDate(t))
    val cleared = Challenges.cleared(data, t, today)
    val kept = marks.count { it == Mark.KEPT }
    val missed = marks.count { it == Mark.SLIPPED || it == Mark.MISSED }
    val accent = when {
        cleared -> C.Gold
        t.kind == ChallengeKind.WEEKLY && missed >= 7 - Challenges.WEEKLY_PENALTY_BELOW + 1 -> C.Danger
        t.kind == ChallengeKind.BODY -> C.RunHot
        else -> C.Glow
    }
    SystemWindow(accent = accent) {
        SysLabel(
            when (t.kind) {
                ChallengeKind.WEEKLY -> "Weekly challenge"
                ChallengeKind.SKILL -> if (over) "Skill · spaced recall" else "Skill month"
                ChallengeKind.BODY -> "Body challenge"
            } + if (over) " · finished" else " · day $day/$len",
            accent,
        )
        Text(Challenges.title(t), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            marks.forEach { m ->
                val c: Color = when (m) {
                    Mark.KEPT -> C.Good
                    Mark.SLIPPED, Mark.MISSED -> C.Danger
                    Mark.TODAY -> C.Glow
                    Mark.LATER -> C.Border
                }
                Box(Modifier.size(if (len > 14) 12.dp else 18.dp).background(c, CircleShape))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (t.kind == ChallengeKind.WEEKLY) {
                "Kept $kept of 7 · " + when {
                    over && kept >= 7 -> "PERFECT WEEK +${Challenges.WEEKLY_XP} XP"
                    over && kept < Challenges.WEEKLY_PENALTY_BELOW -> "+${Challenges.weeklyXp(kept)} XP · penalty quest"
                    over -> "+${Challenges.weeklyXp(kept)} XP"
                    else -> "worth +${Challenges.weeklyXp(kept)} XP so far (7/7 = ${Challenges.WEEKLY_XP})"
                }
            } else {
                "$kept of $len days done" + if (cleared) {
                    " · CLEARED +${if (t.kind == ChallengeKind.SKILL) Challenges.SKILL_XP else Challenges.BODY_XP} XP"
                } else ""
            },
            color = accent, fontFamily = Mono, fontSize = 12.sp,
        )
        when (t.kind) {
            ChallengeKind.WEEKLY -> {
                Challenges.weeklyById(t.id)?.let { Text("Tip: ${it.tip}", color = C.Muted, fontSize = 12.sp) }
                if (!over) {
                    Text(
                        "Every day counts: +${Challenges.WEEKLY_DAY_XP} XP per day kept. Fewer than ${Challenges.WEEKLY_PENALTY_BELOW} days kept = a penalty quest when the week ends.",
                        color = C.Muted, fontSize = 11.sp,
                    )
                }
            }
            ChallengeKind.SKILL -> {
                val k = Challenges.skillById(t.id)
                if (k != null) {
                    Spacer(Modifier.height(8.dp))
                    val week = ((day - 1) / 7).coerceIn(0, k.path.lastIndex)
                    k.path.forEachIndexed { i, step ->
                        Text(
                            "Week ${i + 1}: $step",
                            color = if (!over && i == week) C.Text else C.Muted,
                            fontWeight = if (!over && i == week) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 12.sp, modifier = Modifier.padding(vertical = 1.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        k.resources.forEach { r -> SysButton((if (r.isApp) "📲 " else "🌐 ") + r.name, small = true, onClick = { open(r.url) }) }
                        SysButton("▶ Video lessons", small = true, color = C.Muted, onClick = { open(k.video) })
                    }
                    if (over) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Keep it with spaced recall", fontSize = 13.sp)
                                Text("Do it from memory on day 1, 3, 7, 14, 30, 60, then monthly.", color = C.Muted, fontSize = 11.sp)
                            }
                            Switch(checked = t.keep, onCheckedChange = onKeep, colors = SwitchDefaults.colors(checkedTrackColor = C.Glow, checkedThumbColor = C.Bg))
                        }
                        if (t.keep) {
                            val next = if (Challenges.maintaining(t, today)) today else Challenges.nextRecall(t, today)
                            next?.let { Text("Next recall: ${if (it == today) "today" else it.toString()}", color = C.Gold, fontSize = 12.sp, fontFamily = Mono) }
                        }
                    }
                }
            }
            ChallengeKind.BODY -> {
                val b = Challenges.bodyById(t.id)
                if (b != null && !over) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SysButton("▶ Today's guided session", onClick = onTrain, selected = true, color = C.RunHot, modifier = Modifier.weight(1f))
                        SysButton("▶ Video", onClick = { open(b.video) }, color = C.Muted)
                    }
                }
            }
        }
        if (t.kind != ChallengeKind.WEEKLY && (!over || t.keep)) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SysButton("⏰ ${t.time?.let { fmt(it) } ?: "Set time"}", small = true, color = C.Muted, onClick = onTime)
                Spacer(Modifier.weight(1f))
                if (!over) SysButton("Give up", small = true, color = C.Danger, onClick = onQuit)
            }
        } else if (!over) {
            Spacer(Modifier.height(8.dp))
            SysButton("Give up", small = true, color = C.Danger, onClick = onQuit)
        }
    }
}
