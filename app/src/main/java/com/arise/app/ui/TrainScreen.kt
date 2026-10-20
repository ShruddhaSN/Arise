package com.arise.app.ui

import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.arise.app.data.AppData
import com.arise.app.data.Done
import com.arise.app.game.Exercise
import com.arise.app.game.Game
import com.arise.app.game.Kind
import com.arise.app.game.Program
import com.arise.app.game.RunPlan
import com.arise.app.game.SegKind
import com.arise.app.game.dur
import com.arise.app.notify.CoachCues
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.util.Locale

/** One timed step of a session: run segments, flow moves, or a "get ready" gap. */
private data class Step(
    val label: String,
    val sub: String,
    val sec: Int,
    val hot: Boolean,
    val prep: Boolean = false,
    val seg: SegKind? = null,
)

/** Where a session ended: how far in, and whether it ran to the end. */
private data class SessionEnd(val elapsedMs: Long, val finished: Boolean)

private const val PREP_SEC = 5

/** Mobility/stretch flows as a continuous timeline, with a short "get ready" gap between moves. */
private fun flowSteps(flow: List<Exercise>): List<Step> {
    val moves = flow.flatMap { ex ->
        if (ex.perSide) listOf(Step("${ex.name} · left", ex.cue, ex.sec, true), Step("${ex.name} · right", ex.cue, ex.sec, true))
        else listOf(Step(ex.name, ex.cue, ex.sec, true))
    }
    return moves.flatMap { s -> listOf(Step("GET READY", "Next: ${s.label}", PREP_SEC, false, prep = true), s) }
}

private fun runSteps(plan: RunPlan): List<Step> = plan.segments.map { s ->
    Step(
        s.kind.label,
        if (s.kind == SegKind.RUN) "Easy jog. You should still be able to talk." else "Brisk walk, breathe.",
        s.sec, s.kind == SegKind.RUN, seg = s.kind,
    )
}

/** Seconds of each step that were actually done by [elapsedMs]. */
private fun doneSecs(steps: List<Step>, elapsedMs: Long): List<Int> {
    var t = 0L
    return steps.map { s ->
        val start = t
        t += s.sec * 1000L
        (((elapsedMs - start).coerceIn(0L, s.sec * 1000L)) / 1000L).toInt()
    }
}

private fun km(v: Double?): String = v?.let { "%.1f".format(Locale.ENGLISH, it) } ?: ""

@Composable
fun TrainScreen(
    data: AppData,
    today: LocalDate,
    onUpdate: (AppData) -> Unit,
    target: Int,
    autoFlow: String?,
    onTargetChange: (Int) -> Unit,
    onAutoFlowConsumed: () -> Unit,
) {
    val ctx = LocalContext.current
    val s = data.settings
    val week = Program.week(s, today)
    val level = Game.runLevel(data, today)
    val runWeek = (level + s.runWeekShift).coerceAtLeast(1)
    val plan = Program.runPlan(runWeek)
    val todaysQuests = Game.plan(data, today).quests
    val fullThisWeek = Game.fullRunsInWeek(data, week, level)
    var running by remember { mutableStateOf<String?>(null) } // "run", "mobility", "stretch"
    var runEnd by remember { mutableStateOf<SessionEnd?>(null) }
    var hold by remember { mutableStateOf<Exercise?>(null) }

    LaunchedEffect(autoFlow) {
        if (autoFlow != null) {
            running = autoFlow
            onAutoFlowConsumed()
        }
    }

    /** Applies an amount quest at [value] (keeping the higher), or a check quest as done. */
    fun withQuest(id: String, value: Double?, extra: (com.arise.app.data.DayRecord) -> com.arise.app.data.DayRecord = { it }): AppData {
        val q = todaysQuests.firstOrNull { it.id == id }
        var rec = data.day(today)
        if (q != null) {
            rec = when (q.kind) {
                Kind.AMOUNT -> rec.copy(
                    amounts = rec.amounts + (id to maxOf(rec.amounts[id] ?: 0.0, value ?: q.target)),
                    skipped = rec.skipped - id,
                )
                else -> if (value == null || value >= 1.0) rec.copy(done = rec.done + (id to Done.FULL), skipped = rec.skipped - id) else rec
            }
        }
        return data.with(extra(rec))
    }

    fun toast(id: String) {
        val onPlan = todaysQuests.any { it.id == id }
        Toast.makeText(
            ctx,
            if (onPlan) "[SYSTEM] Progress saved" else "Nice work! This isn't on today's plan, so it doesn't earn XP.",
            Toast.LENGTH_SHORT,
        ).show()
    }

    fun saveQuest(id: String, value: Double?) {
        onUpdate(withQuest(id, value))
        toast(id)
    }

    runEnd?.let { end ->
        RunSummary(plan, end, s.walkSpeed, s.runSpeed, s.incline, onSave = { walk, runS, inc, tSteps, tKm ->
            val steps = runSteps(plan)
            val secs = doneSecs(steps, end.elapsedMs)
            val runsDone = steps.indices.count { steps[it].seg == SegKind.RUN && secs[it] >= steps[it].sec }
            val runSecDone = steps.indices.filter { steps[it].seg == SegKind.RUN }.sumOf { secs[it] }
            val longest = steps.indices.filter { steps[it].seg == SegKind.RUN && secs[it] >= steps[it].sec }.maxOfOrNull { steps[it].sec } ?: 0
            val runQ = todaysQuests.firstOrNull { it.id == "run" }
            val value = if (runQ?.kind == Kind.AMOUNT) runsDone.toDouble() else if (end.finished) 1.0 else 0.0
            val next = withQuest("run", value) { r ->
                var a = r.amounts
                if (tSteps != null && tSteps > 0) a = a + ("steps" to (a["steps"] ?: 0.0) + tSteps)
                if (tKm != null && tKm > 0) a = a + ("treadmill_km" to (a["treadmill_km"] ?: 0.0) + tKm)
                r.copy(
                    amounts = a,
                    longestRunSec = maxOf(r.longestRunSec ?: 0, longest),
                    runMinutes = (r.runMinutes ?: 0) + runSecDone / 60,
                )
            }
            // Remember the treadmill settings for next time.
            onUpdate(next.copy(settings = next.settings.copy(walkSpeed = walk ?: s.walkSpeed, runSpeed = runS ?: s.runSpeed, incline = inc ?: s.incline)))
            toast("run")
            runEnd = null
        }, onDiscard = { runEnd = null })
        return
    }

    when (running) {
        "run" -> {
            TimedSession(
                "Run coach · ${plan.title}", runSteps(plan), C.RunHot,
                guide = { st ->
                    st.seg?.let { k ->
                        val last = when (k) {
                            SegKind.RUN -> s.runSpeed
                            SegKind.COOLDOWN -> null
                            else -> s.walkSpeed
                        }
                        Program.treadmillGuide(k) + (last?.let { " · last time ${km(it)}" } ?: "") + "\nor whatever feels comfortable"
                    }
                },
                onEnd = { end ->
                    running = null
                    runEnd = end
                },
            )
            return
        }
        "mobility", "stretch" -> {
            val id = running!!
            val flow = if (id == "mobility") Program.mobilityFlow else Program.stretchFlow
            val steps = flowSteps(flow)
            TimedSession(
                if (id == "mobility") "Morning mobility" else "Evening stretch", steps, C.Glow,
                guide = { null },
                onEnd = { end ->
                    running = null
                    val secs = doneSecs(steps, end.elapsedMs)
                    val minutes = steps.indices.filter { !steps[it].prep }.sumOf { secs[it] } / 60
                    if (end.finished) saveQuest(id, null) else if (minutes > 0) saveQuest(id, minutes.toDouble())
                },
            )
            return
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SysLabel("[System] Training ground")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Run", "Strength", "Mobility").forEachIndexed { i, label ->
                SysButton(label, onClick = { onTargetChange(i) }, selected = target == i)
            }
        }
        when (target) {
            TrainTarget.RUN -> {
                SystemWindow {
                    SysLabel("Run coach · plan week $runWeek")
                    Spacer(Modifier.height(4.dp))
                    Text(plan.title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(plan.summary, color = C.Text2, fontSize = 13.sp)
                    Text("Total ${plan.totalSec / 60} min", color = C.Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Full runs this week: $fullThisWeek/${Game.FULL_RUNS_TO_LEVEL}" +
                            if (fullThisWeek >= Game.FULL_RUNS_TO_LEVEL) " ✓ next week moves up" else " to move up next week",
                        color = if (fullThisWeek >= Game.FULL_RUNS_TO_LEVEL) C.Good else C.Gold, fontSize = 13.sp,
                    )
                    if (s.walkSpeed != null || s.runSpeed != null) {
                        Text(
                            "Last time: walk ${km(s.walkSpeed)} · run ${km(s.runSpeed)} km/h" + (s.incline?.let { " · incline ${km(it)}" } ?: ""),
                            color = C.Muted, fontSize = 12.sp,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SysButton("− Easier", small = true, enabled = runWeek > 1, onClick = {
                            onUpdate(data.copy(settings = s.copy(runWeekShift = s.runWeekShift - 1)))
                        })
                        SysButton("Harder +", small = true, onClick = {
                            onUpdate(data.copy(settings = s.copy(runWeekShift = s.runWeekShift + 1)))
                        })
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(Program.RUN_TIPS, color = C.Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp))
                    SysButton("▶  START SESSION", onClick = { running = "run" }, selected = true, modifier = Modifier.fillMaxWidth())
                }
                Panel {
                    SysLabel("Segments · treadmill guide")
                    plan.segments.forEach { seg ->
                        Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                seg.kind.label, color = if (seg.kind == SegKind.RUN) C.RunHot else C.Text2,
                                fontFamily = Mono, fontSize = 12.sp, modifier = Modifier.width(130.dp),
                            )
                            Text(dur(seg.sec), color = C.Text2, fontSize = 12.sp, modifier = Modifier.width(56.dp))
                            Text(Program.treadmillGuide(seg.kind), color = C.Muted, fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Recommended. Whatever feels comfortable is fine.", color = C.Muted, fontSize = 11.sp)
                }
            }
            TrainTarget.STRENGTH -> {
                ExerciseChecklist(
                    "Strength · week $week", Program.strengthPlan(week), Program.STRENGTH_NOTE, "Complete session",
                    onHold = { hold = it },
                ) { saveQuest("strength", null) }
            }
            else -> {
                FlowCard("Morning mobility · 8 min", "Office mornings. Slow and easy.", Program.mobilityFlow,
                    onStart = { running = "mobility" }, onDone = { saveQuest("mobility", null) })
                FlowCard("Evening stretch · 10 min", "WFH and day-off evenings.", Program.stretchFlow,
                    onStart = { running = "stretch" }, onDone = { saveQuest("stretch", null) })
            }
        }
    }

    hold?.let { ex -> HoldTimer(ex, onClose = { hold = null }) }
}

@Composable
private fun RunSummary(
    plan: RunPlan,
    end: SessionEnd,
    lastWalk: Double?,
    lastRun: Double?,
    lastIncline: Double?,
    onSave: (walk: Double?, run: Double?, incline: Double?, steps: Int?, km: Double?) -> Unit,
    onDiscard: () -> Unit,
) {
    val steps = runSteps(plan)
    val secs = doneSecs(steps, end.elapsedMs)
    val runIdx = steps.indices.filter { steps[it].seg == SegKind.RUN }
    val runsDone = runIdx.count { secs[it] >= steps[it].sec }
    val runSecDone = runIdx.sumOf { secs[it] }
    var walk by remember { mutableStateOf(km(lastWalk)) }
    var run by remember { mutableStateOf(km(lastRun)) }
    var incline by remember { mutableStateOf(km(lastIncline)) }
    var tSteps by remember { mutableStateOf("") }
    var tKm by remember { mutableStateOf("") }
    fun num(t: String) = t.replace(',', '.').toDoubleOrNull()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        SystemWindow(accent = if (end.finished) C.Gold else C.Glow) {
            SysLabel(if (end.finished) "[System] Quest complete" else "[System] Session saved", if (end.finished) C.Gold else C.Glow)
            Spacer(Modifier.height(6.dp))
            Text("Runs completed: $runsDone of ${plan.runs.size}", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Running time ${dur(runSecDone)} · total ${end.elapsedMs / 60000} min", color = C.Text2, fontSize = 14.sp)
            if (!end.finished) {
                Spacer(Modifier.height(6.dp))
                Text(
                    if (runsDone * 2 >= plan.runs.size) "Over half: this counts toward clearing your day. 💪"
                    else "Every run counts. You'll earn XP for what you did.",
                    color = C.Muted, fontSize = 13.sp,
                )
            }
        }
        Panel {
            SysLabel("Treadmill (optional)")
            Spacer(Modifier.height(4.dp))
            Text("Pre-filled from last time. Leave anything blank.", color = C.Muted, fontSize = 12.sp)
            SummaryField("Walk speed (km/h)", walk) { walk = it }
            SummaryField("Run speed (km/h)", run) { run = it }
            SummaryField("Incline (%)", incline) { incline = it }
            SummaryField("Treadmill steps (added to today's steps)", tSteps, KeyboardType.Number) { tSteps = it }
            SummaryField("Distance (km)", tKm) { tKm = it }
            Text("Only add treadmill steps if your phone wasn't in your pocket counting them.", color = C.Muted, fontSize = 11.sp)
        }
        SysButton("SAVE", selected = true, color = C.Gold, modifier = Modifier.fillMaxWidth(), onClick = {
            onSave(num(walk), num(run), num(incline), tSteps.toIntOrNull(), num(tKm))
        })
        SysButton("Discard this session", color = C.Muted, small = true, onClick = onDiscard)
    }
}

@Composable
private fun SummaryField(label: String, value: String, type: KeyboardType = KeyboardType.Decimal, onChange: (String) -> Unit) {
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
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    )
}

@Composable
private fun FlowCard(title: String, note: String, flow: List<Exercise>, onStart: () -> Unit, onDone: () -> Unit) {
    Panel {
        SysLabel(title)
        Spacer(Modifier.height(6.dp))
        flow.forEach { ex ->
            Column(Modifier.padding(vertical = 4.dp)) {
                Text("${ex.name} · ${ex.dose}", fontWeight = FontWeight.SemiBold)
                Text(ex.cue, color = C.Muted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(note, color = C.Muted, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SysButton("▶ Guided timer", onClick = onStart, selected = true, modifier = Modifier.weight(1f))
            SysButton("Done without timer", onClick = onDone, color = C.Good, small = true)
        }
    }
}

@Composable
private fun ExerciseChecklist(
    title: String,
    list: List<Exercise>,
    note: String,
    button: String,
    onHold: (Exercise) -> Unit,
    onComplete: () -> Unit,
) {
    val checked = remember(title) { mutableStateListOf<Boolean>().apply { repeat(list.size) { add(false) } } }
    Panel {
        SysLabel(title)
        Spacer(Modifier.height(6.dp))
        list.forEachIndexed { i, ex ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { checked[i] = !checked[i] }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (checked[i]) "☑" else "☐", fontSize = 18.sp, color = if (checked[i]) C.Good else C.Muted, modifier = Modifier.width(30.dp))
                Column(Modifier.weight(1f)) {
                    Text("${ex.name} · ${ex.dose}", fontWeight = FontWeight.SemiBold, color = if (checked[i]) C.Text2 else C.Text)
                    Text(ex.cue, color = C.Muted, fontSize = 12.sp)
                }
                if (ex.sec > 0) SysButton("▶ ${ex.sec}s", small = true, onClick = { onHold(ex) })
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(note, color = C.Muted, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        SysButton(button, onClick = onComplete, color = C.Good, selected = checked.all { it }, modifier = Modifier.fillMaxWidth())
    }
}

/** A countdown for one hold (plank etc.): 3-second get-ready, then the hold. */
@Composable
private fun HoldTimer(ex: Exercise, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var left by remember { mutableIntStateOf(ex.sec + 3) }
    LaunchedEffect(Unit) {
        while (left > 0) {
            delay(1000)
            left--
            if (left == ex.sec) Cues.go(ctx)
        }
        Cues.finish(ctx)
    }
    val prep = left > ex.sec
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = C.Panel,
        title = { Text(ex.name + if (ex.perSide) " (one side)" else "") },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    when {
                        left == 0 -> "DONE"
                        prep -> "GET READY"
                        else -> "HOLD"
                    },
                    color = if (prep) C.Muted else C.Glow, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                )
                Text(
                    (if (prep) left - ex.sec else left).toString(),
                    fontSize = 64.sp, fontFamily = Mono, fontWeight = FontWeight.Bold,
                )
                Text(ex.cue, color = C.Text2, fontSize = 13.sp)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(if (left == 0) "Close" else "Stop", color = C.Glow) } },
    )
}

/**
 * Runs a list of timed steps. In-app cues fire while the screen is visible; background alarms
 * (CoachCues) take over when you switch apps. Ending early reports how far you got.
 */
@Composable
private fun TimedSession(
    title: String,
    steps: List<Step>,
    hotColor: Color,
    guide: (Step) -> String?,
    onEnd: (SessionEnd) -> Unit,
) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    val starts = remember(steps) { steps.runningFold(0) { acc, s -> acc + s.sec } }
    val totalMs = steps.sumOf { it.sec } * 1000L
    var elapsed by remember { mutableLongStateOf(0L) }
    var paused by remember { mutableStateOf(false) }
    var jumps by remember { mutableIntStateOf(0) }

    fun end() {
        CoachCues.cancel(ctx)
        onEnd(SessionEnd(elapsed, elapsed >= totalMs))
    }

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> CoachCues.onScreen = true
                Lifecycle.Event.ON_STOP -> CoachCues.onScreen = false
                else -> Unit
            }
        }
        lifecycle.addObserver(obs)
        CoachCues.onScreen = true
        onDispose {
            view.keepScreenOn = false
            lifecycle.removeObserver(obs)
            CoachCues.onScreen = false
            CoachCues.cancel(ctx)
        }
    }
    BackHandler(onBack = { end() })

    // Schedule background alerts for every upcoming change; redone after pause/resume and skips.
    LaunchedEffect(paused, jumps) {
        if (paused) {
            CoachCues.cancel(ctx)
        } else {
            val base = SystemClock.elapsedRealtime() - elapsed
            val cues = (1 until steps.size).map { i ->
                val st = steps[i]
                val g = guide(st)?.lineSequence()?.firstOrNull()
                CoachCues.Cue(
                    base + starts[i] * 1000L,
                    "[SYSTEM] ${st.label} now · ${dur(st.sec)}",
                    listOfNotNull(st.sub, g).joinToString(" · "),
                )
            } + CoachCues.Cue(base + totalMs, "[SYSTEM] Session complete", "Open Arise to claim your reward.")
            CoachCues.schedule(ctx, cues)
        }
    }

    LaunchedEffect(paused) {
        if (!paused) {
            var last = SystemClock.elapsedRealtime()
            while (elapsed < totalMs) {
                delay(250)
                val n = SystemClock.elapsedRealtime()
                elapsed = (elapsed + n - last).coerceAtMost(totalMs)
                last = n
            }
        }
    }

    val finished = elapsed >= totalMs
    val idx = if (finished) steps.lastIndex else starts.indexOfLast { it * 1000L <= elapsed }.coerceIn(0, steps.lastIndex)
    LaunchedEffect(idx, finished) {
        when {
            finished -> Cues.finish(ctx)
            elapsed > 500 -> if (steps[idx].hot) Cues.go(ctx) else Cues.rest(ctx)
        }
    }

    val step = steps[idx]
    val remainingSec = ((starts[idx + 1] * 1000L - elapsed) / 1000L).toInt().coerceAtLeast(0)
    val color = if (step.hot) hotColor else C.Muted
    val moveCount = steps.count { !it.prep }
    val moveNumber = steps.take(idx + 1).count { !it.prep }

    Column(
        Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SysLabel("[System] $title")
        Spacer(Modifier.weight(1f))
        if (finished) {
            Text("QUEST COMPLETE", color = C.Gold, fontFamily = Mono, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Well done. Your body thanks you.", color = C.Text2)
            Spacer(Modifier.weight(1f))
            SysButton("CLAIM REWARD", onClick = { end() }, selected = true, color = C.Gold, modifier = Modifier.fillMaxWidth())
        } else {
            Text(step.label, color = color, fontFamily = Mono, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(
                "%d:%02d".format(remainingSec / 60, remainingSec % 60),
                fontSize = 84.sp, fontWeight = FontWeight.Bold, fontFamily = Mono,
            )
            guide(step)?.let { Text(it, color = C.Gold, fontSize = 14.sp, fontFamily = Mono) }
            Text(step.sub, color = C.Text2, fontSize = 14.sp)
            if (!step.prep) Text("$moveNumber of $moveCount", color = C.Muted, fontSize = 13.sp)
            steps.getOrNull(idx + 1)?.takeIf { !step.prep }?.let { n ->
                val next = if (n.prep) steps.getOrNull(idx + 2) else n
                if (next != null) Text("Next: ${next.label} ${dur(next.sec)}", color = C.Muted)
            }
            XpBar(elapsed.toFloat() / totalMs, if (step.hot) hotColor else C.Glow)
            Text("You can switch apps: the System will alert you at each change.", color = C.Muted, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SysButton(if (paused) "▶ Resume" else "❚❚ Pause", onClick = { paused = !paused })
                SysButton("Skip ›", onClick = {
                    elapsed = starts[idx + 1] * 1000L
                    jumps++
                })
                SysButton("End & save", onClick = { end() }, color = C.Danger)
            }
        }
    }
}
