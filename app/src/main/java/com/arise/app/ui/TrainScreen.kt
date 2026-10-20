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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arise.app.data.AppData
import com.arise.app.data.Done
import com.arise.app.game.Exercise
import com.arise.app.game.Game
import com.arise.app.game.Program
import com.arise.app.game.SegKind
import com.arise.app.game.dur
import kotlinx.coroutines.delay
import java.time.LocalDate

/** One timed step of a session: run segments, flow moves, or a "get ready" gap. */
private data class Step(val label: String, val sub: String, val sec: Int, val hot: Boolean, val prep: Boolean = false)

private const val PREP_SEC = 5

/** Mobility/stretch flows as a continuous timeline, with a short "get ready" gap between moves. */
private fun flowSteps(flow: List<Exercise>): List<Step> {
    val moves = flow.flatMap { ex ->
        if (ex.perSide) listOf(Step("${ex.name} · left", ex.cue, ex.sec, true), Step("${ex.name} · right", ex.cue, ex.sec, true))
        else listOf(Step(ex.name, ex.cue, ex.sec, true))
    }
    return moves.flatMapIndexed { i, s ->
        listOf(Step("GET READY", "Next: ${s.label}", PREP_SEC, false, prep = true), s)
    }
}

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
    val week = Program.week(data.settings, today)
    val runWeek = (week + data.settings.runWeekShift).coerceAtLeast(1)
    val plan = Program.runPlan(runWeek)
    val todaysQuests = Game.plan(data, today).quests
    var running by remember { mutableStateOf<String?>(null) } // "run", "mobility", "stretch"
    var hold by remember { mutableStateOf<Exercise?>(null) }

    LaunchedEffect(autoFlow) {
        if (autoFlow != null) {
            running = autoFlow
            onAutoFlowConsumed()
        }
    }

    fun complete(id: String) {
        val q = todaysQuests.firstOrNull { it.id == id }
        val rec = data.day(today)
        if (q == null) {
            Toast.makeText(ctx, "Nice work! This isn't on today's plan, so it doesn't earn XP.", Toast.LENGTH_LONG).show()
            return
        }
        val r = when (q.kind) {
            com.arise.app.game.Kind.AMOUNT -> rec.copy(amounts = rec.amounts + (id to q.target), skipped = rec.skipped - id)
            else -> rec.copy(done = rec.done + (id to Done.FULL), skipped = rec.skipped - id)
        }
        onUpdate(data.with(r))
        Toast.makeText(ctx, "[SYSTEM] Quest complete · +${q.xp} ${q.stat.label} XP", Toast.LENGTH_SHORT).show()
    }

    when (running) {
        "run" -> {
            val steps = plan.segments.map { s ->
                Step(s.kind.label, if (s.kind == SegKind.RUN) "Easy jog. You should still be able to talk." else "Brisk walk, breathe.", s.sec, s.kind == SegKind.RUN)
            }
            TimedSession("Run coach · ${plan.title}", steps, C.RunHot, onFinish = {
                val rec = data.day(today)
                val hasRun = todaysQuests.any { it.id == "run" }
                onUpdate(
                    data.with(
                        rec.copy(
                            done = if (hasRun) rec.done + ("run" to Done.FULL) else rec.done,
                            skipped = rec.skipped - "run",
                            longestRunSec = maxOf(rec.longestRunSec ?: 0, plan.longestRun),
                            runMinutes = (rec.runMinutes ?: 0) + plan.runSec / 60,
                        ),
                    ),
                )
                running = null
            }, onCancel = { running = null })
            return
        }
        "mobility", "stretch" -> {
            val id = running!!
            val flow = if (id == "mobility") Program.mobilityFlow else Program.stretchFlow
            TimedSession(if (id == "mobility") "Morning mobility" else "Evening stretch", flowSteps(flow), C.Glow,
                onFinish = { complete(id); running = null }, onCancel = { running = null })
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
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SysButton("− Easier", small = true, enabled = runWeek > 1, onClick = {
                            onUpdate(data.copy(settings = data.settings.copy(runWeekShift = data.settings.runWeekShift - 1)))
                        })
                        SysButton("Harder +", small = true, onClick = {
                            onUpdate(data.copy(settings = data.settings.copy(runWeekShift = data.settings.runWeekShift + 1)))
                        })
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(Program.RUN_TIPS, color = C.Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp))
                    SysButton("▶  START SESSION", onClick = { running = "run" }, selected = true, modifier = Modifier.fillMaxWidth())
                }
                Panel {
                    SysLabel("Segments")
                    plan.segments.forEach { s ->
                        Row(Modifier.padding(vertical = 2.dp)) {
                            Text(
                                s.kind.label, color = if (s.kind == SegKind.RUN) C.RunHot else C.Text2,
                                fontFamily = Mono, fontSize = 12.sp, modifier = Modifier.width(150.dp),
                            )
                            Text(dur(s.sec), color = C.Text2, fontSize = 12.sp)
                        }
                    }
                }
            }
            TrainTarget.STRENGTH -> {
                ExerciseChecklist(
                    "Strength · week $week", Program.strengthPlan(week), Program.STRENGTH_NOTE, "Complete session",
                    onHold = { hold = it },
                ) { complete("strength") }
            }
            else -> {
                FlowCard("Morning mobility · 8 min", "Office mornings. Slow and easy.", Program.mobilityFlow,
                    onStart = { running = "mobility" }, onDone = { complete("mobility") })
                FlowCard("Evening stretch · 10 min", "WFH and day-off evenings.", Program.stretchFlow,
                    onStart = { running = "stretch" }, onDone = { complete("stretch") })
            }
        }
    }

    hold?.let { ex -> HoldTimer(ex, onClose = { hold = null }) }
}

@Composable
private fun FlowCard(title: String, note: String, flow: List<Exercise>, onStart: () -> Unit, onDone: () -> Unit) {
    Panel {
        SysLabel(title)
        Spacer(Modifier.height(6.dp))
        flow.forEach { ex ->
            Row(Modifier.padding(vertical = 4.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("${ex.name} · ${ex.dose}", fontWeight = FontWeight.SemiBold)
                    Text(ex.cue, color = C.Muted, fontSize = 12.sp)
                }
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

/** Runs a list of timed steps with cues at each change. Used by the run coach and the guided flows. */
@Composable
private fun TimedSession(title: String, steps: List<Step>, hotColor: Color, onFinish: () -> Unit, onCancel: () -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    BackHandler(onBack = onCancel)

    val starts = remember(steps) { steps.runningFold(0) { acc, s -> acc + s.sec } }
    val totalMs = steps.sumOf { it.sec } * 1000L
    var elapsed by remember { mutableLongStateOf(0L) }
    var paused by remember { mutableStateOf(false) }

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
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SysLabel("[System] $title")
        Spacer(Modifier.weight(1f))
        if (finished) {
            Text("QUEST COMPLETE", color = C.Gold, fontFamily = Mono, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Well done. Your body thanks you.", color = C.Text2)
            Spacer(Modifier.weight(1f))
            SysButton("CLAIM REWARD", onClick = onFinish, selected = true, color = C.Gold, modifier = Modifier.fillMaxWidth())
        } else {
            Text(step.label, color = color, fontFamily = Mono, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(
                "%d:%02d".format(remainingSec / 60, remainingSec % 60),
                fontSize = 84.sp, fontWeight = FontWeight.Bold, fontFamily = Mono,
            )
            Text(step.sub, color = C.Text2, fontSize = 14.sp)
            if (!step.prep) Text("$moveNumber of $moveCount", color = C.Muted, fontSize = 13.sp)
            steps.getOrNull(idx + 1)?.takeIf { !step.prep }?.let { n ->
                val next = if (n.prep) steps.getOrNull(idx + 2) else n
                if (next != null) Text("Next: ${next.label} ${dur(next.sec)}", color = C.Muted)
            }
            XpBar(elapsed.toFloat() / totalMs, if (step.hot) hotColor else C.Glow)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SysButton(if (paused) "▶ Resume" else "❚❚ Pause", onClick = { paused = !paused })
                SysButton("Skip ›", onClick = { elapsed = starts[idx + 1] * 1000L })
                SysButton("End", onClick = onCancel, color = C.Danger)
            }
        }
    }
}
