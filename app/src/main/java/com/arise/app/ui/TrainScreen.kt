package com.arise.app.ui

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.arise.app.game.RunPlan
import com.arise.app.game.SegKind
import com.arise.app.game.dur
import kotlinx.coroutines.delay
import java.time.LocalDate

@Composable
fun TrainScreen(data: AppData, today: LocalDate, onUpdate: (AppData) -> Unit) {
    val ctx = LocalContext.current
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    val week = Program.week(data.settings, today)
    val runWeek = (week + data.settings.runWeekShift).coerceAtLeast(1)
    val plan = Program.runPlan(runWeek)
    val todaysIds = Game.plan(data, today).quests.map { it.id }.toSet()

    fun complete(id: String) {
        if (id in todaysIds) {
            val rec = data.day(today)
            onUpdate(data.with(rec.copy(done = rec.done + (id to Done.FULL))))
            Toast.makeText(ctx, "[SYSTEM] Quest complete", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(ctx, "Nice work! This isn't on today's plan, so it doesn't earn XP.", Toast.LENGTH_LONG).show()
        }
    }

    if (running) {
        RunSession(
            plan,
            onFinish = {
                val rec = data.day(today)
                val done = if ("run" in todaysIds) rec.done + ("run" to Done.FULL) else rec.done
                onUpdate(
                    data.with(
                        rec.copy(
                            done = done,
                            longestRunSec = maxOf(rec.longestRunSec ?: 0, plan.longestRun),
                            runMinutes = (rec.runMinutes ?: 0) + plan.runSec / 60,
                        ),
                    ),
                )
                running = false
            },
            onCancel = { running = false },
        )
        return
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
                SysButton(label, onClick = { mode = i }, selected = mode == i)
            }
        }
        when (mode) {
            0 -> {
                Panel(accent = C.Glow) {
                    SysLabel("Run coach · plan week $runWeek")
                    Spacer(Modifier.height(4.dp))
                    Text(plan.title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(plan.summary, color = C.Text2, fontSize = 13.sp)
                    Text("Total ${plan.totalSec / 60} min", color = C.Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SysButton("− Easier", enabled = runWeek > 1, onClick = {
                            onUpdate(data.copy(settings = data.settings.copy(runWeekShift = data.settings.runWeekShift - 1)))
                        })
                        SysButton("Harder +", onClick = {
                            onUpdate(data.copy(settings = data.settings.copy(runWeekShift = data.settings.runWeekShift + 1)))
                        })
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(Program.RUN_TIPS, color = C.Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp))
                    SysButton("▶  START SESSION", onClick = { running = true }, selected = true, modifier = Modifier.fillMaxWidth())
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
            1 -> {
                val list = Program.strengthPlan(week)
                ExerciseChecklist("Strength · week $week", list, Program.STRENGTH_NOTE, "Complete session") {
                    complete("strength")
                }
            }
            else -> {
                ExerciseChecklist("Morning mobility · 8 min", Program.mobilityFlow, "Office mornings. Slow and easy.", "Complete mobility") {
                    complete("mobility")
                }
                ExerciseChecklist("Evening stretch · 10 min", Program.stretchFlow, "WFH and weekend evenings.", "Complete stretch") {
                    complete("stretch")
                }
            }
        }
    }
}

@Composable
private fun ExerciseChecklist(title: String, list: List<Exercise>, note: String, button: String, onComplete: () -> Unit) {
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
                verticalAlignment = Alignment.Top,
            ) {
                Text(if (checked[i]) "☑" else "☐", fontSize = 18.sp, color = if (checked[i]) C.Good else C.Muted, modifier = Modifier.width(30.dp))
                Column(Modifier.weight(1f)) {
                    Text("${ex.name} · ${ex.dose}", fontWeight = FontWeight.SemiBold, color = if (checked[i]) C.Text2 else C.Text)
                    Text(ex.cue, color = C.Muted, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(note, color = C.Muted, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        SysButton(button, onClick = onComplete, color = C.Good, selected = checked.all { it }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun RunSession(plan: RunPlan, onFinish: () -> Unit, onCancel: () -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    BackHandler(onBack = onCancel)

    val segs = plan.segments
    val starts = remember(plan) { segs.runningFold(0) { acc, s -> acc + s.sec } }
    val totalMs = plan.totalSec * 1000L
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
    val idx = if (finished) segs.lastIndex else starts.indexOfLast { it * 1000L <= elapsed }.coerceIn(0, segs.lastIndex)
    LaunchedEffect(idx, finished) {
        if (finished) Cues.finish(ctx) else if (elapsed > 500) Cues.change(ctx, segs[idx].kind)
    }

    val seg = segs[idx]
    val remainingSec = ((starts[idx + 1] * 1000L - elapsed) / 1000L).toInt().coerceAtLeast(0)
    val runNumber = segs.take(idx + 1).count { it.kind == SegKind.RUN }
    val color = if (seg.kind == SegKind.RUN) C.RunHot else C.Glow

    Column(
        Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SysLabel("[System] Run coach · ${plan.title}")
        Spacer(Modifier.weight(1f))
        if (finished) {
            Text("QUEST COMPLETE", color = C.Gold, fontFamily = Mono, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("You ran ${dur(plan.runSec)} today. Your stamina is growing.", color = C.Text2)
            Spacer(Modifier.weight(1f))
            SysButton("CLAIM REWARD", onClick = onFinish, selected = true, color = C.Gold, modifier = Modifier.fillMaxWidth())
        } else {
            Text(seg.kind.label, color = color, fontFamily = Mono, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(
                "%d:%02d".format(remainingSec / 60, remainingSec % 60),
                fontSize = 84.sp, fontWeight = FontWeight.Bold, fontFamily = Mono,
            )
            if (seg.kind == SegKind.RUN) {
                Text("Run $runNumber of ${plan.runs.size}", color = C.Text2)
            }
            segs.getOrNull(idx + 1)?.let { n ->
                Text("Next: ${n.kind.label} ${dur(n.sec)}", color = C.Muted)
            }
            XpBar(elapsed.toFloat() / totalMs, color)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SysButton(if (paused) "▶ Resume" else "❚❚ Pause", onClick = { paused = !paused })
                SysButton("Skip ›", onClick = { elapsed = starts[idx + 1] * 1000L })
                SysButton("End", onClick = onCancel, color = C.Danger)
            }
        }
    }
}

private object Cues {
    fun change(ctx: Context, kind: SegKind) {
        if (kind == SegKind.RUN) {
            vibrate(ctx, longArrayOf(0, 400, 150, 400))
            beep(2)
        } else {
            vibrate(ctx, longArrayOf(0, 700))
            beep(1)
        }
    }

    fun finish(ctx: Context) {
        vibrate(ctx, longArrayOf(0, 300, 100, 300, 100, 600))
        beep(3)
    }

    @Suppress("DEPRECATION")
    private fun vibrate(ctx: Context, pattern: LongArray) {
        runCatching {
            val v: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
                ctx.getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            v.vibrate(VibrationEffect.createWaveform(pattern, -1))
        }
    }

    private fun beep(times: Int) {
        runCatching {
            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 90)
            val h = Handler(Looper.getMainLooper())
            repeat(times) { i -> h.postDelayed({ tg.startTone(ToneGenerator.TONE_PROP_BEEP, 200) }, i * 350L) }
            h.postDelayed({ tg.release() }, times * 350L + 400L)
        }
    }
}
