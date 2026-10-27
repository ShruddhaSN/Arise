package com.arise.app.ui

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.arise.app.data.AppData
import com.arise.app.data.Done
import com.arise.app.data.Store
import com.arise.app.game.Game
import com.arise.app.notify.Blocker
import com.arise.app.notify.CoachCues
import java.time.LocalDate

/**
 * Pomodoro state lives in its own preferences so the timer survives switching tabs or apps.
 * Phases: 0 = idle, 1 = focus, 2 = break.
 */
object Focus {
    private const val PREFS = "arise_focus"

    fun phase(ctx: Context): Int = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("phase", 0)
    fun endAt(ctx: Context): Long = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("endAt", 0L)
    fun length(ctx: Context): Int = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("length", 25)

    private fun set(ctx: Context, phase: Int, endAt: Long, length: Int) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("phase", phase).putLong("endAt", endAt).putInt("length", length).apply()
    }

    fun start(ctx: Context, minutes: Int, seal: Boolean) {
        val ms = minutes * 60_000L
        set(ctx, 1, System.currentTimeMillis() + ms, minutes)
        if (seal) Blocker.seal(ctx, minutes, "Focus session in progress. Stay with the task.")
        CoachCues.schedule(
            ctx,
            listOf(CoachCues.Cue(SystemClock.elapsedRealtime() + ms, "[SYSTEM] Focus session complete", "Open Arise to log it. Then take a break.")),
        )
        Voice.say("Focus session started. $minutes minutes.")
    }

    fun breakFor(ctx: Context, minutes: Int) {
        set(ctx, 2, System.currentTimeMillis() + minutes * 60_000L, minutes)
    }

    fun stop(ctx: Context) {
        set(ctx, 0, 0L, length(ctx))
        CoachCues.cancel(ctx)
    }

    /**
     * If a focus session has finished, logs it (minutes, session count, Deep work quest) and starts the
     * break. Returns the updated data, or null if nothing changed.
     */
    fun settle(ctx: Context, data: AppData): AppData? {
        val now = System.currentTimeMillis()
        when (phase(ctx)) {
            1 -> if (now >= endAt(ctx)) {
                val mins = length(ctx)
                val today = LocalDate.now()
                val rec = data.day(today)
                var done = rec.done
                if (mins >= 25 && Game.plan(data, today).quests.any { it.id == "deepwork" }) done = done + ("deepwork" to Done.FULL)
                val next = data.with(
                    rec.copy(
                        amounts = rec.amounts +
                            ("focus" to (rec.amounts["focus"] ?: 0.0) + mins) +
                            ("focus_n" to (rec.amounts["focus_n"] ?: 0.0) + 1),
                        done = done,
                    ),
                )
                val brk = if (mins >= 50) 10 else 5
                breakFor(ctx, brk)
                Voice.say("Focus session complete. Take a $brk minute break.")
                SystemBus.post(
                    SysMsg(
                        "FOCUS SESSION COMPLETE",
                        listOf("+$mins focus minutes logged.", "Break: $brk minutes. Stand up, water, look far away."),
                        accent = C.Good,
                    ),
                )
                return next
            }
            2 -> if (now >= endAt(ctx)) {
                stop(ctx)
                Voice.say("Break over.")
            }
        }
        return null
    }
}

@Composable
fun ToolsScreen(data: AppData, today: LocalDate, onUpdate: (AppData) -> Unit, onChallenges: () -> Unit) {
    val ctx = LocalContext.current
    val s = data.settings
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var resumed by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) resumed++ }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            tick = System.currentTimeMillis()
            if (Focus.phase(ctx) != 0 && tick >= Focus.endAt(ctx)) Focus.settle(ctx, Store.load(ctx))?.let(onUpdate)
            kotlinx.coroutines.delay(1000)
        }
    }
    val rec = data.day(today)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SysLabel("[System] Tools")
        val active = com.arise.app.game.Challenges.active(data.settings, today)
        SystemWindow(accent = C.Gold) {
            SysLabel("Challenges", C.Gold)
            Spacer(Modifier.height(4.dp))
            Text(
                if (active.isEmpty()) "Weekly rules, monthly skills, body programs. Take one on."
                else active.joinToString("\n") { "• " + com.arise.app.game.Challenges.title(it) },
                color = C.Text2, fontSize = 13.sp,
            )
            Spacer(Modifier.height(8.dp))
            SysButton("⚔ Open challenges", onClick = onChallenges, selected = true, color = C.Gold, modifier = Modifier.fillMaxWidth())
        }
        PomodoroCard(s.focusMin, s.blockFocus && s.blocked.isNotEmpty(), tick, (rec.amounts["focus_n"] ?: 0.0).toInt(), (rec.amounts["focus"] ?: 0.0).toInt(),
            onLength = { onUpdate(data.copy(settings = s.copy(focusMin = it))) })
        BlockerCard(data, resumed, tick, onUpdate)
    }
}

@Composable
private fun PomodoroCard(minutes: Int, seals: Boolean, now: Long, sessions: Int, focusToday: Int, onLength: (Int) -> Unit) {
    val ctx = LocalContext.current
    val phase = Focus.phase(ctx)
    val end = Focus.endAt(ctx)
    val total = Focus.length(ctx) * 60_000L
    val left = (end - now).coerceAtLeast(0L)
    val frac = if (phase == 0 || total == 0L) 0f else 1f - left.toFloat() / total
    val color = when (phase) {
        1 -> C.Gold
        2 -> C.Good
        else -> C.Glow
    }
    SystemWindow(accent = color) {
        SysLabel("Pomodoro · focus timer", color)
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(190.dp)) {
                val stroke = 10.dp.toPx()
                val inset = stroke / 2
                drawArc(C.Border, 0f, 360f, false, Offset(inset, inset), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke))
                drawArc(color, -90f, 360f * frac, false, Offset(inset, inset), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val show = if (phase == 0) minutes * 60_000L else left
                Text("%d:%02d".format(show / 60_000, (show / 1000) % 60), fontSize = 44.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
                Text(
                    when (phase) {
                        1 -> "FOCUS"
                        2 -> "BREAK"
                        else -> "READY"
                    },
                    color = color, fontFamily = Mono, letterSpacing = 3.sp,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (phase == 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(25, 50).forEach { m ->
                    SysButton("$m min", small = true, selected = minutes == m, onClick = { onLength(m) }, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(8.dp))
            SysButton("▶  START FOCUS", onClick = { Focus.start(ctx, minutes, seals) }, selected = true, color = C.Gold, modifier = Modifier.fillMaxWidth())
            if (seals) Text("Your sealed apps stay locked until the session ends.", color = C.Muted, fontSize = 11.sp)
        } else {
            SysButton(if (phase == 1) "Give up this session" else "Skip break", onClick = {
                Focus.stop(ctx)
                if (phase == 1) Blocker.unseal(ctx)
            }, color = C.Muted, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(8.dp))
        Text("Today: $sessions session${if (sessions == 1) "" else "s"} · $focusToday focus minutes", color = C.Text2, fontSize = 13.sp, fontFamily = Mono)
        Text("A 25-minute session ticks off your Deep work quest.", color = C.Muted, fontSize = 11.sp)
    }
}

@Composable
private fun BlockerCard(data: AppData, resumed: Int, now: Long, onUpdate: (AppData) -> Unit) {
    val ctx = LocalContext.current
    val s = data.settings
    val on = resumed >= 0 && Blocker.serviceOn(ctx)
    var picking by remember { mutableStateOf(false) }
    val apps = remember(picking) { if (picking) Blocker.launchable(ctx) else emptyList() }
    Panel(accent = if (on) C.Good else C.Danger) {
        SysLabel("Screen blocker", if (on) C.Good else C.Danger)
        Spacer(Modifier.height(6.dp))
        Text(
            "Seal the apps that steal your time. When one opens during a sealed time, the System covers it with ACCESS DENIED.",
            color = C.Text2, fontSize = 13.sp,
        )
        Spacer(Modifier.height(8.dp))
        SysButton(
            if (on) "✓ Blocker is on" else "Turn on the blocker (Accessibility → Arise)",
            selected = on, color = if (on) C.Good else C.Danger, modifier = Modifier.fillMaxWidth(),
            onClick = { runCatching { ctx.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) } },
        )
        if (!on) {
            Text(
                "On Samsung: Settings → Accessibility → Installed apps → Arise → On. If it's greyed out: long-press the Arise icon → App info → ⋮ (top right) → Allow restricted settings, then try again.",
                color = C.Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().clickable { picking = !picking }, verticalAlignment = Alignment.CenterVertically) {
            Text("Sealed apps: ${s.blocked.size}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(if (picking) "Done ▴" else "Choose ▾", color = C.Glow, fontFamily = Mono, fontSize = 12.sp)
        }
        if (!picking && s.blocked.isNotEmpty()) {
            Text(s.blocked.joinToString(", ") { Blocker.appName(ctx, it) }, color = C.Muted, fontSize = 12.sp)
        }
        if (picking) {
            apps.forEach { (pkg, name) ->
                val sealed = pkg in s.blocked
                Row(
                    Modifier.fillMaxWidth().clickable {
                        onUpdate(data.copy(settings = s.copy(blocked = if (sealed) s.blocked - pkg else s.blocked + pkg)))
                    }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (sealed) "☑" else "☐", color = if (sealed) C.Danger else C.Muted, fontSize = 18.sp, modifier = Modifier.width(30.dp))
                    Text(name + if (pkg in Blocker.suggested) "  · common time sink" else "", color = if (sealed) C.Text else C.Text2, fontSize = 14.sp)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        ToggleRow("First 30 min after I'M UP", "Phone-free morning, enforced", s.blockMorning) {
            onUpdate(data.copy(settings = s.copy(blockMorning = it)))
        }
        ToggleRow("Night seal", "From lights-off until your earliest wake-up", s.blockNight) {
            onUpdate(data.copy(settings = s.copy(blockNight = it)))
        }
        ToggleRow("During focus sessions", "While a Pomodoro is running", s.blockFocus) {
            onUpdate(data.copy(settings = s.copy(blockFocus = it)))
        }
        Spacer(Modifier.height(8.dp))
        val sealedUntil = Blocker.sealedUntil(ctx)
        if (sealedUntil > now) {
            val mins = (sealedUntil - now) / 60_000 + 1
            Text("🔒 Sealed for $mins more min", color = C.Gold, fontFamily = Mono, fontSize = 13.sp)
        } else {
            Text("Seal now", color = C.Text2, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 60).forEach { m ->
                    SysButton("$m min", small = true, color = C.Danger, enabled = s.blocked.isNotEmpty(), modifier = Modifier.weight(1f), onClick = {
                        Blocker.seal(ctx, m, "You sealed this yourself for $m minutes.")
                    })
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp)
            Text(sub, color = C.Muted, fontSize = 11.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = C.Glow, checkedThumbColor = C.Bg))
    }
}
