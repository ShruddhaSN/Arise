package com.arise.app.ui

import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.arise.app.data.AppData
import com.arise.app.data.DayRecord
import com.arise.app.data.Done
import com.arise.app.data.ExSkip
import com.arise.app.data.ExSkipLog
import com.arise.app.game.Challenges
import com.arise.app.game.Exercise
import com.arise.app.game.Exercises
import com.arise.app.game.Game
import com.arise.app.game.Kind
import com.arise.app.game.Prescribed
import com.arise.app.game.Program
import com.arise.app.game.Profile
import com.arise.app.game.RunPlan
import com.arise.app.game.SegKind
import com.arise.app.game.dur
import com.arise.app.notify.CoachCues
import kotlinx.coroutines.delay
import java.net.URLEncoder
import java.time.LocalDate
import java.util.Locale

/** One timed step of a session: run segments, flow moves, strength sets, rests, "get ready" gaps. */
private data class Step(
    val label: String,
    val sub: String,
    val sec: Int,
    val hot: Boolean,
    val prep: Boolean = false,
    val seg: SegKind? = null,
    /** Strength: reps to count out loud, one every [tempo] seconds. 0 = a timed hold or segment. */
    val reps: Int = 0,
    val tempo: Int = 3,
    /** What the System voice says as the step starts. */
    val say: String = "",
    val moveId: String? = null,
    /** Steps of one exercise share a group: skipping skips the whole exercise. */
    val group: Int = -1,
    /** Finishing this step completes one set. */
    val countsSet: Boolean = false,
    val demo: String? = null,
    /** Skipping this asks why. Rests and "get ready" just skip. */
    val askSkip: Boolean = false,
)

/** Where a session ended: how far in, whether it ran to the end, and which steps were skipped. */
private data class SessionEnd(val elapsedMs: Long, val finished: Boolean, val skipped: Set<Int>)

private const val PREP_SEC = 5

private fun demoUrl(name: String): String =
    "https://www.youtube.com/results?search_query=" + URLEncoder.encode("$name how to do it properly", "UTF-8")

/** Mobility/stretch flows as a continuous timeline, with a short "get ready" gap between moves. */
private fun flowSteps(flow: List<Exercise>): List<Step> {
    var g = 0
    return flow.flatMap { ex ->
        val group = g++
        val sides = if (ex.perSide) listOf(" · left", " · right") else listOf("")
        sides.flatMap { side ->
            val label = ex.name + side
            listOf(
                Step("GET READY", "Next: $label", PREP_SEC, false, prep = true, say = "Next: $label. ${ex.cue}", group = group),
                Step(label, ex.cue, ex.sec, true, say = "Begin.", group = group, demo = demoUrl(ex.name + " stretch"), askSkip = true),
            )
        }
    }
}

/** "90" → "1 minute 30 seconds", for the voice. */
private fun spoken(sec: Int): String {
    val m = sec / 60
    val r = sec % 60
    val mins = if (m == 1) "1 minute" else "$m minutes"
    return when {
        m == 0 -> "$r seconds"
        r == 0 -> mins
        else -> "$mins $r seconds"
    }
}

private fun runSteps(plan: RunPlan): List<Step> = plan.segments.mapIndexed { i, s ->
    Step(
        s.kind.label,
        if (s.kind == SegKind.RUN) "Easy jog. You should still be able to talk." else "Brisk walk, breathe.",
        s.sec, s.kind == SegKind.RUN, seg = s.kind,
        say = when (s.kind) {
            SegKind.WARMUP -> "Warm up walk. ${spoken(s.sec)}."
            SegKind.RUN -> "Run. ${spoken(s.sec)}. Easy pace."
            SegKind.WALK -> "Walk. Breathe."
            SegKind.COOLDOWN -> "Cool down walk. You did it."
        },
        group = i, askSkip = s.kind == SegKind.RUN,
    )
}

/** A full strength session: explain, then set by set with counted reps and timed rests. */
private fun strengthSteps(list: List<Prescribed>): List<Step> = buildList {
    list.forEachIndexed { gi, p ->
        val m = p.move
        val amount = if (p.hold > 0) "${p.hold} seconds" else "${p.reps}"
        add(
            Step(
                "GET READY", "Next: ${m.name} · ${p.dose}", 15, false, prep = true,
                say = "Next: ${m.name}. ${p.sets} sets of $amount${if (m.perSide) " each side" else ""}. ${m.how}",
                moveId = m.id, group = gi, demo = m.demoUrl,
            ),
        )
        for (set in 1..p.sets) {
            val sides = if (m.perSide) listOf("left", "right") else listOf<String?>(null)
            sides.forEachIndexed { si, side ->
                val secs = if (p.hold > 0) p.hold else p.reps * m.tempo
                add(
                    Step(
                        m.name + (side?.let { " · $it" } ?: ""),
                        "Set $set of ${p.sets} · " + (if (p.hold > 0) "${p.hold}s hold" else "${p.reps} reps") + "\n${m.cue}",
                        secs, true,
                        reps = if (p.hold > 0) 0 else p.reps, tempo = m.tempo,
                        say = (if (si == 0) "Set $set. " else "") + (side?.let { "$it side. " } ?: "") + "Go.",
                        moveId = m.id, group = gi, countsSet = si == sides.lastIndex, demo = m.demoUrl, askSkip = true,
                    ),
                )
                if (side != null && si == 0) {
                    add(Step("SWITCH SIDES", "Other side next", 5, false, prep = true, say = "Switch sides.", group = gi))
                }
            }
            val last = set == p.sets && gi == list.lastIndex
            if (!last) {
                val rest = if (set == p.sets) 45 else 60
                add(
                    Step(
                        "REST", if (set == p.sets) "Next exercise coming up" else "Set ${set + 1} next. Shake it out, breathe.",
                        rest, false, say = "Rest. $rest seconds.", group = gi,
                    ),
                )
            }
        }
    }
}

/** Seconds of each step that were actually done by [end]. Skipped steps count as zero. */
private fun doneSecs(steps: List<Step>, end: SessionEnd): List<Int> {
    var t = 0L
    return steps.mapIndexed { i, s ->
        val start = t
        t += s.sec * 1000L
        if (i in end.skipped) 0 else (((end.elapsedMs - start).coerceIn(0L, s.sec * 1000L)) / 1000L).toInt()
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
    val uri = LocalUriHandler.current
    val s = data.settings
    val week = Program.week(s, today)
    val level = Game.runLevel(data, today)
    val runWeek = (level + s.runWeekShift).coerceAtLeast(1)
    val plan = Program.runPlan(runWeek)
    val todaysQuests = Game.plan(data, today).quests
    val fullThisWeek = Game.fullRunsInWeek(data, week, level)
    val strength = Exercises.session(s, today, week)
    val bodyTaken = s.challenges.firstOrNull { it.kind == com.arise.app.data.ChallengeKind.BODY && Challenges.running(it, today) }
    val bodyList = bodyTaken?.let { Challenges.bodyToday(s, it, today) }.orEmpty()
    var running by remember { mutableStateOf<String?>(null) } // "run", "strength", "mobility", "stretch"
    var runEnd by remember { mutableStateOf<SessionEnd?>(null) }

    LaunchedEffect(autoFlow) {
        if (autoFlow != null) {
            running = autoFlow
            onAutoFlowConsumed()
        }
    }

    /** Applies an amount quest at [value] (keeping the higher), or a check quest as done. */
    fun withQuest(id: String, value: Double?, extra: (DayRecord) -> DayRecord = { it }): AppData {
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

    /** Every skip is saved right away: the reason, and what the System changes because of it. */
    fun recordSkip(step: Step, reason: ExSkip) {
        val name = step.moveId?.let { Exercises.byId(it)?.name } ?: step.label.substringBefore(" · ")
        val rec = data.day(today)
        var settings = data.settings
        if (step.moveId != null) {
            when (reason) {
                ExSkip.TOO_HARD -> settings = settings.copy(easier = settings.easier + step.moveId)
                ExSkip.PAIN -> settings = settings.copy(avoid = settings.avoid + step.moveId)
                else -> Unit
            }
        }
        onUpdate(data.with(rec.copy(exSkips = rec.exSkips + ExSkipLog(name, reason))).copy(settings = settings))
        if (reason == ExSkip.CONFUSED) step.demo?.let { runCatching { uri.openUri(it) } }
    }

    runEnd?.let { end ->
        RunSummary(plan, end, s.walkSpeed, s.runSpeed, s.incline, onSave = { walk, runS, inc, tSteps, tKm ->
            val steps = runSteps(plan)
            val secs = doneSecs(steps, end)
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
                    st.seg?.takeIf { Profile.hasTreadmill(s) }?.let { k ->
                        val last = when (k) {
                            SegKind.RUN -> s.runSpeed
                            SegKind.COOLDOWN -> null
                            else -> s.walkSpeed
                        }
                        Program.treadmillGuide(k) + (last?.let { " · last time ${km(it)}" } ?: "") + "\nor whatever feels comfortable"
                    }
                },
                onSkip = { st, r -> recordSkip(st, r) },
                onEnd = { end ->
                    running = null
                    runEnd = end
                },
            )
            return
        }
        "strength" -> {
            val steps = strengthSteps(strength)
            TimedSession(
                "Strength · ${strength.size} moves", steps, C.Gold,
                guide = { null },
                onSkip = { st, r -> recordSkip(st, r) },
                onEnd = { end ->
                    running = null
                    val secs = doneSecs(steps, end)
                    val sets = steps.indices.count { steps[it].countsSet && secs[it] >= steps[it].sec }
                    val total = Exercises.totalSets(strength)
                    if (sets > 0) onUpdate(withQuest("strength", sets.toDouble()))
                    val skips = data.day(today).exSkips
                    SystemBus.post(
                        SysMsg(
                            if (sets >= total) "STRENGTH QUEST COMPLETE" else "SESSION SAVED",
                            buildList {
                                add("Sets completed: $sets of $total")
                                if (end.skipped.isNotEmpty()) {
                                    add("Skipped today: ${skips.size} · " + skips.groupingBy { it.reason }.eachCount().entries.joinToString(", ") { "${it.key.label.lowercase()} ×${it.value}" })
                                }
                                skips.filter { it.reason == ExSkip.TOO_HARD }.map { it.exercise }.distinct().forEach {
                                    add("$it was too hard: next session gets an easier version.")
                                }
                                skips.filter { it.reason == ExSkip.PAIN }.map { it.exercise }.distinct().forEach {
                                    add("$it is removed from your plan. If pain keeps coming back, see a doctor or physio.")
                                }
                                if (sets >= total && end.skipped.isEmpty()) add("Not one set skipped. The System noticed.")
                            },
                            accent = C.Gold, speak = if (sets >= total) "Strength quest complete." else "Session saved.",
                        ),
                    )
                },
            )
            return
        }
        "challenge" -> {
            val t = bodyTaken
            if (t != null && bodyList.isNotEmpty()) {
                val steps = strengthSteps(bodyList)
                val qid = Challenges.questId(t)
                TimedSession(
                    Challenges.title(t), steps, C.RunHot,
                    guide = { null },
                    onSkip = { st, r -> recordSkip(st, r) },
                    onEnd = { end ->
                        running = null
                        val secs = doneSecs(steps, end)
                        val sets = steps.indices.count { steps[it].countsSet && secs[it] >= steps[it].sec }
                        if (sets > 0) onUpdate(withQuest(qid, sets.toDouble()))
                        val total = Exercises.totalSets(bodyList)
                        SystemBus.post(
                            SysMsg(
                                if (sets >= total) "CHALLENGE DAY CLEARED" else "SESSION SAVED",
                                listOf("Sets: $sets of $total", "Day ${Challenges.day(t, today)} of ${Challenges.length(t)}."),
                                accent = C.RunHot, speak = if (sets >= total) "Challenge day cleared." else "Session saved.",
                            ),
                        )
                    },
                )
                return
            }
            running = null
        }
        "mobility", "stretch" -> {
            val id = running!!
            val flow = if (id == "mobility") Program.mobilityFlow else Program.stretchFlow
            val steps = flowSteps(flow)
            TimedSession(
                if (id == "mobility") "Morning mobility" else "Evening stretch", steps, C.Glow,
                guide = { null },
                onSkip = { st, r -> recordSkip(st, r) },
                onEnd = { end ->
                    running = null
                    val secs = doneSecs(steps, end)
                    val minutes = steps.indices.filter { !steps[it].prep }.sumOf { secs[it] } / 60
                    if (end.finished && end.skipped.isEmpty()) saveQuest(id, null) else if (minutes > 0) saveQuest(id, minutes.toDouble())
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
            (listOf("Run", "Strength", "Mobility") + if (bodyTaken != null) listOf("Challenge") else emptyList()).forEachIndexed { i, label ->
                SysButton(label, onClick = { onTargetChange(i) }, selected = target == i, small = true)
            }
        }
        when (target) {
            TrainTarget.RUN -> {
                SystemWindow {
                    SysLabel("Run coach · plan week $runWeek")
                    Spacer(Modifier.height(4.dp))
                    Text(plan.title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(plan.summary, color = C.Text2, fontSize = 13.sp)
                    Text("Total ${plan.totalSec / 60} min · the voice coach calls every change", color = C.Muted, fontSize = 12.sp)
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
                    SysLabel(if (Profile.hasTreadmill(s)) "Segments · treadmill guide" else "Segments")
                    plan.segments.forEach { seg ->
                        Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                seg.kind.label, color = if (seg.kind == SegKind.RUN) C.RunHot else C.Text2,
                                fontFamily = Mono, fontSize = 12.sp, modifier = Modifier.width(130.dp),
                            )
                            Text(dur(seg.sec), color = C.Text2, fontSize = 12.sp, modifier = Modifier.width(56.dp))
                            if (Profile.hasTreadmill(s)) Text(Program.treadmillGuide(seg.kind), color = C.Muted, fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Recommended. Whatever feels comfortable is fine.", color = C.Muted, fontSize = 11.sp)
                }
            }
            TrainTarget.STRENGTH -> {
                StrengthCard(
                    week, strength, data.day(today).exSkips.size,
                    showSpot = Profile.wantsFatLoss(s),
                    onStart = { running = "strength" },
                    onDone = { saveQuest("strength", null) },
                    onDemo = { runCatching { uri.openUri(it) } },
                )
            }
            TrainTarget.CHALLENGE -> {
                val t = bodyTaken
                val b = t?.let { Challenges.bodyById(it.id) }
                if (t == null || b == null) {
                    Text("No body challenge running. Tools → Challenges to take one.", color = C.Muted)
                } else {
                    SystemWindow(accent = C.RunHot) {
                        SysLabel("Body challenge · day ${Challenges.day(t, today)}/${b.days}", C.RunHot)
                        Text(b.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("${bodyList.size} moves · ${Exercises.totalSets(bodyList)} sets", color = C.Text2, fontSize = 13.sp)
                        Spacer(Modifier.height(10.dp))
                        SysButton("▶  START GUIDED SESSION", onClick = { running = "challenge" }, selected = true, color = C.RunHot, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        SysButton("▶ Follow-along video instead", onClick = { runCatching { uri.openUri(b.video) } }, color = C.Muted, small = true)
                        b.honest?.let {
                            Spacer(Modifier.height(6.dp))
                            Text(it, color = C.Gold, fontSize = 11.sp)
                        }
                    }
                    bodyList.forEach { p ->
                        Panel {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(p.move.name, fontWeight = FontWeight.Bold)
                                    Text(p.dose, color = C.Gold, fontSize = 12.sp, fontFamily = Mono)
                                }
                                SysButton("▶ How to", small = true, onClick = { runCatching { uri.openUri(p.move.demoUrl) } })
                            }
                            Text(p.move.how, color = C.Text2, fontSize = 12.sp)
                        }
                    }
                    SysButton("Mark done without the coach", color = C.Good, small = true, onClick = { saveQuest(Challenges.questId(t), null) })
                }
            }
            else -> {
                FlowCard("Morning mobility · 8 min", "Office mornings. Slow and easy.", Program.mobilityFlow,
                    onStart = { running = "mobility" }, onDone = { saveQuest("mobility", null) }, onDemo = { runCatching { uri.openUri(it) } })
                FlowCard("Evening stretch · 10 min", "WFH and day-off evenings.", Program.stretchFlow,
                    onStart = { running = "stretch" }, onDone = { saveQuest("stretch", null) }, onDemo = { runCatching { uri.openUri(it) } })
            }
        }
    }
}

@Composable
private fun StrengthCard(
    week: Int,
    list: List<Prescribed>,
    skipsToday: Int,
    showSpot: Boolean,
    onStart: () -> Unit,
    onDone: () -> Unit,
    onDemo: (String) -> Unit,
) {
    SystemWindow(accent = C.Gold) {
        SysLabel("Strength · week $week · today's combination", C.Gold)
        Spacer(Modifier.height(4.dp))
        Text("${list.size} moves · ${Exercises.totalSets(list)} sets", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("A different mix every session. The voice coach explains each move, counts your reps and times your rests.", color = C.Text2, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        SysButton("▶  START GUIDED SESSION", onClick = onStart, selected = true, color = C.Gold, modifier = Modifier.fillMaxWidth())
        if (skipsToday > 0) {
            Spacer(Modifier.height(6.dp))
            Text("Skips today: $skipsToday", color = C.Danger, fontSize = 12.sp, fontFamily = Mono)
        }
    }
    list.forEach { p ->
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SysLabel(p.move.group.label, C.Muted)
                    Text(p.move.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(p.dose + "  ·  level ${p.move.level}" + if (p.move.dumbbell) " · 🏋 dumbbell" else "", color = C.Gold, fontSize = 12.sp, fontFamily = Mono)
                }
                SysButton("▶ How to", small = true, onClick = { onDemo(p.move.demoUrl) })
            }
            Spacer(Modifier.height(6.dp))
            Text(p.move.how, color = C.Text2, fontSize = 13.sp)
        }
    }
    Panel {
        Text(Exercises.NOTE, color = C.Muted, fontSize = 12.sp)
        if (showSpot) {
            Spacer(Modifier.height(8.dp))
            Text(Exercises.SPOT_NOTE, color = C.Text2, fontSize = 12.sp)
        }
        Spacer(Modifier.height(10.dp))
        SysButton("Mark done without the coach", onClick = onDone, color = C.Good, small = true)
    }
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
    val secs = doneSecs(steps, end)
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
            if (end.skipped.isNotEmpty()) Text("Skipped intervals: ${end.skipped.size}", color = C.Danger, fontSize = 13.sp)
            if (!end.finished || end.skipped.isNotEmpty()) {
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
private fun FlowCard(
    title: String,
    note: String,
    flow: List<Exercise>,
    onStart: () -> Unit,
    onDone: () -> Unit,
    onDemo: (String) -> Unit,
) {
    Panel {
        SysLabel(title)
        Spacer(Modifier.height(6.dp))
        flow.forEach { ex ->
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${ex.name} · ${ex.dose}", fontWeight = FontWeight.SemiBold)
                    Text(ex.cue, color = C.Muted, fontSize = 12.sp)
                }
                SysButton("▶ How", small = true, color = C.Muted, onClick = { onDemo(demoUrl(ex.name + " stretch")) })
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(note, color = C.Muted, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SysButton("▶ Guided timer", onClick = onStart, selected = true, modifier = Modifier.weight(1f))
            SysButton("Mark done", onClick = onDone, color = C.Good, small = true)
        }
    }
}

/**
 * Runs a list of timed steps with a voice coach. In-app cues fire while the screen is visible;
 * background alarms (CoachCues) take over when you switch apps. Every skip of real work asks why.
 */
@Composable
private fun TimedSession(
    title: String,
    steps: List<Step>,
    hotColor: Color,
    guide: (Step) -> String?,
    onSkip: (Step, ExSkip) -> Unit,
    onEnd: (SessionEnd) -> Unit,
) {
    val ctx = LocalContext.current
    val uri = LocalUriHandler.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    val starts = remember(steps) { steps.runningFold(0) { acc, s -> acc + s.sec } }
    val totalMs = steps.sumOf { it.sec } * 1000L
    var elapsed by remember { mutableLongStateOf(0L) }
    var paused by remember { mutableStateOf(false) }
    var jumps by remember { mutableIntStateOf(0) }
    var asking by remember { mutableStateOf(false) }
    val skipped = remember { mutableStateListOf<Int>() }

    fun end() {
        CoachCues.cancel(ctx)
        Voice.stop()
        onEnd(SessionEnd(elapsed, elapsed >= totalMs, skipped.toSet()))
    }

    /** Jumps to the start of [target], marking real work in between as skipped. */
    fun jumpTo(from: Int, target: Int, markSkipped: Boolean) {
        if (markSkipped) (from until target).filter { steps[it].hot }.forEach { if (it !in skipped) skipped.add(it) }
        elapsed = starts[target.coerceAtMost(steps.size)] * 1000L
        jumps++
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
            Voice.stop()
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
                    listOfNotNull(st.sub.lineSequence().firstOrNull(), g).joinToString(" · "),
                )
            } + CoachCues.Cue(base + totalMs, "[SYSTEM] Session complete", "Open Arise and mark it done.")
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
    val step = steps[idx]
    val inStepMs = elapsed - starts[idx] * 1000L
    val repsDone = if (step.reps > 0) (inStepMs / (step.tempo * 1000L)).toInt().coerceIn(0, step.reps) else 0
    val remainingSec = ((starts[idx + 1] * 1000L - elapsed) / 1000L).toInt().coerceAtLeast(0)

    // Voice: announce each step, count reps, count down the end of holds and rests.
    LaunchedEffect(idx, finished) {
        when {
            finished -> {
                Cues.finish(ctx)
                Voice.say("Session complete. Well done.")
            }
            else -> {
                if (elapsed > 500) {
                    if (step.hot) Cues.go(ctx) else Cues.rest(ctx)
                }
                Voice.say(step.say.ifBlank { step.label }, now = false)
            }
        }
    }
    LaunchedEffect(idx, repsDone) {
        if (!finished && !paused && step.reps > 0 && repsDone > 0) {
            Voice.say(if (repsDone == step.reps) "$repsDone. Done." else "$repsDone")
        }
    }
    LaunchedEffect(idx, remainingSec) {
        if (finished || paused || step.reps > 0) return@LaunchedEffect
        when {
            step.hot && step.sec >= 20 && remainingSec == 10 -> Voice.say("Ten seconds.")
            step.hot && remainingSec in 1..3 -> Voice.say("$remainingSec")
            !step.hot && !step.prep && remainingSec == 5 -> Voice.say("Get ready.")
        }
    }

    val color = if (step.hot) hotColor else C.Muted
    val workSteps = steps.count { it.hot }
    val workNumber = steps.take(idx + 1).count { it.hot }

    Column(
        Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SysLabel("[System] $title")
        Spacer(Modifier.weight(1f))
        if (finished) {
            Text("QUEST COMPLETE", color = C.Gold, fontFamily = Mono, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Well done. Your body thanks you.", color = C.Text2)
            if (skipped.isNotEmpty()) Text("Skipped: ${skipped.size}", color = C.Danger, fontFamily = Mono)
            Spacer(Modifier.weight(1f))
            SysButton("MARK DONE", onClick = { end() }, selected = true, color = C.Gold, modifier = Modifier.fillMaxWidth())
        } else {
            Text(step.label, color = color, fontFamily = Mono, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            if (step.reps > 0) {
                Text("$repsDone / ${step.reps}", fontSize = 84.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
                Text("reps · ${step.tempo}s each · count with the voice", color = C.Muted, fontSize = 12.sp)
            } else {
                Text(
                    "%d:%02d".format(remainingSec / 60, remainingSec % 60),
                    fontSize = 84.sp, fontWeight = FontWeight.Bold, fontFamily = Mono,
                )
            }
            guide(step)?.let { Text(it, color = C.Gold, fontSize = 14.sp, fontFamily = Mono, textAlign = TextAlign.Center) }
            Text(step.sub, color = C.Text2, fontSize = 14.sp, textAlign = TextAlign.Center)
            step.demo?.let { url ->
                SysButton("▶ How to do this", small = true, color = C.Muted, onClick = {
                    paused = true
                    runCatching { uri.openUri(url) }
                })
            }
            if (step.hot) Text("$workNumber of $workSteps", color = C.Muted, fontSize = 13.sp)
            steps.getOrNull(idx + 1)?.let { n ->
                val next = if (n.prep || (!n.hot && !step.hot)) steps.drop(idx + 1).firstOrNull { it.hot } else n
                if (next != null) Text("Next: ${next.label} ${dur(next.sec)}", color = C.Muted)
            }
            XpBar(elapsed.toFloat() / totalMs, if (step.hot) hotColor else C.Glow)
            if (skipped.isNotEmpty()) Text("Skips: ${skipped.size}", color = C.Danger, fontSize = 12.sp, fontFamily = Mono)
            Text("You can switch apps: the System will alert you at each change.", color = C.Muted, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SysButton(if (paused) "▶ Resume" else "❚❚ Pause", onClick = { paused = !paused })
                SysButton("Skip ›", onClick = {
                    if (step.askSkip) {
                        paused = true
                        asking = true
                    } else {
                        jumpTo(idx, idx + 1, markSkipped = false)
                    }
                })
                SysButton("End & save", onClick = { end() }, color = C.Danger)
            }
        }
    }

    if (asking) {
        AlertDialog(
            onDismissRequest = {
                asking = false
                paused = false
            },
            containerColor = C.Panel,
            title = {
                Column {
                    SysLabel("[System] Skip detected", C.Danger)
                    Spacer(Modifier.height(4.dp))
                    Text("Why are you skipping ${step.label.substringBefore(" · ")}?", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Every skip is counted. Your answer changes what the System gives you next time.", color = C.Text2, fontSize = 13.sp)
                    step.demo?.let { url ->
                        SysButton("▶ Not sure how? Watch first, then come back", modifier = Modifier.fillMaxWidth(), color = C.Muted, onClick = {
                            asking = false
                            runCatching { uri.openUri(url) }
                        })
                    }
                    ExSkip.entries.forEach { reason ->
                        SysButton("${reason.icon}  ${reason.label}", modifier = Modifier.fillMaxWidth(), color = C.Danger, onClick = {
                            asking = false
                            onSkip(step, reason)
                            // Strength skips the whole exercise; runs and flows skip just this step.
                            val target = if (step.moveId != null) {
                                (idx + 1 until steps.size).firstOrNull { steps[it].group != step.group } ?: steps.size
                            } else {
                                idx + 1
                            }
                            jumpTo(idx, target, markSkipped = true)
                            paused = false
                        })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    asking = false
                    paused = false
                }) { Text("Keep going", color = C.Glow) }
            },
        )
    }
}
