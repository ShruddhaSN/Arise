package com.arise.app.ui

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arise.app.data.AppData
import com.arise.app.data.Task
import com.arise.app.data.TaskKind
import com.arise.app.data.TaskStep
import com.arise.app.game.Draft
import com.arise.app.game.Todo
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

private val dl = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = C.Glow,
    unfocusedBorderColor = C.Border,
    focusedLabelColor = C.Glow,
    unfocusedLabelColor = C.Muted,
    cursorColor = C.Glow,
    focusedTextColor = C.Text,
    unfocusedTextColor = C.Text,
)

/** The To-do tab: say everything on your mind, review it, and the System places it into your days. */
@Composable
fun TodoScreen(data: AppData, today: LocalDate, onUpdate: (AppData) -> Unit) {
    var typed by rememberSaveable { mutableStateOf("") }
    var drafts by remember { mutableStateOf<List<Draft>?>(null) }
    var editing by remember { mutableStateOf<Task?>(null) }
    var progressFor by remember { mutableStateOf<Task?>(null) }
    var showDone by rememberSaveable { mutableStateOf(false) }

    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val heard = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            if (heard.isNotBlank()) drafts = (drafts.orEmpty() + Todo.parse(heard, today))
        }
    }
    fun listen() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Tell the System everything on your mind")
        runCatching { speech.launch(i) }
    }

    val open = data.tasks.filter { !it.done }
    val todayList = Todo.forDay(data, today).filter { !it.task.done }
    val projects = open.filter { it.kind == TaskKind.PROJECT }.sortedBy { it.deadline ?: LocalDate.MAX }
    val dated = open.filter { it.kind == TaskKind.ONE_OFF && it.deadline != null }.sortedBy { it.deadline }
    val someday = open.filter { it.kind == TaskKind.ONE_OFF && it.deadline == null }
    val done = data.tasks.filter { it.done }.sortedByDescending { it.doneOn ?: it.created }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            SystemWindow {
                SysLabel("[System] To-do")
                Spacer(Modifier.height(6.dp))
                Text("Empty your head", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Say several things at once and mention dates if there are any: \"finish the crochet before November 7th, " +
                        "clean my wardrobe, and submit the assignment by Friday\". Big things get split into steps and spread over the days.",
                    color = C.Text2, fontSize = 13.sp,
                )
                Spacer(Modifier.height(10.dp))
                SysButton("🎤  Speak your tasks", onClick = { listen() }, selected = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = typed, onValueChange = { typed = it },
                    label = { Text("…or type them", fontSize = 12.sp) },
                    colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
                )
                if (typed.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    SysButton("Add", onClick = {
                        drafts = drafts.orEmpty() + Todo.parse(typed, today)
                        typed = ""
                    }, small = true)
                }
            }
        }

        if (todayList.isNotEmpty()) {
            item { SysLabel("Today") }
            items(todayList, key = { "today_" + it.task.id }) { tt ->
                Panel(accent = C.Glow) {
                    Text((if (tt.task.kind == TaskKind.PROJECT) "🧩 " else "📝 ") + tt.task.title, fontWeight = FontWeight.SemiBold)
                    if (tt.task.kind == TaskKind.PROJECT) {
                        Text("Now ${tt.from}% · aim ${tt.aim}% today" + (tt.step?.let { " · ${it.title}" } ?: ""), color = C.Text2, fontSize = 13.sp)
                    }
                    Text(tt.note, color = C.Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        SysButton(if (tt.task.kind == TaskKind.PROJECT) "Log progress" else "Done", small = true, color = C.Good, onClick = { progressFor = tt.task })
                    }
                }
            }
        }

        if (projects.isNotEmpty()) {
            item { SysLabel("Projects") }
            items(projects, key = { it.id }) { t -> TaskRow(t, today, onProgress = { progressFor = t }, onEdit = { editing = t }) }
        }
        if (dated.isNotEmpty()) {
            item { SysLabel("With a date") }
            items(dated, key = { it.id }) { t -> TaskRow(t, today, onProgress = { progressFor = t }, onEdit = { editing = t }) }
        }
        if (someday.isNotEmpty()) {
            item { SysLabel("Someday") }
            item { Text("No dates, no pressure. The System suggests one now and then on a free day.", color = C.Muted, fontSize = 12.sp) }
            items(someday, key = { it.id }) { t -> TaskRow(t, today, onProgress = { progressFor = t }, onEdit = { editing = t }) }
        }
        if (open.isEmpty()) {
            item { Text("Nothing here yet. Tap the mic and say what's on your mind.", color = C.Muted, fontSize = 13.sp) }
        }
        if (done.isNotEmpty()) {
            item {
                Text(
                    (if (showDone) "▴ " else "▾ ") + "Done (${done.size})", color = C.Good, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { showDone = !showDone }.padding(vertical = 6.dp),
                )
            }
            if (showDone) items(done, key = { "done_" + it.id }) { t -> TaskRow(t, today, onProgress = { progressFor = t }, onEdit = { editing = t }) }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    drafts?.let { list ->
        ReviewDialog(
            list, today,
            onAddMore = { listen() },
            onSave = { kept ->
                drafts = null
                if (kept.isNotEmpty()) onUpdate(Todo.add(data, kept, today))
            },
            onDismiss = { drafts = null },
        )
    }
    editing?.let { t ->
        EditTaskDialog(
            t, today,
            onSave = { nt ->
                editing = null
                onUpdate(Todo.update(data, nt))
            },
            onDelete = {
                editing = null
                onUpdate(Todo.delete(data, t.id))
            },
            onDismiss = { editing = null },
        )
    }
    progressFor?.let { t ->
        TaskProgressDialog(t, onSave = { pct ->
            progressFor = null
            var next = Todo.setProgress(data, t.id, pct, today)
            // If today has a session card for it, that card is done too.
            val qid = Todo.questId(t)
            val rec = next.day(today)
            if (com.arise.app.game.Game.plan(data, today).quests.any { it.id == qid }) {
                next = next.with(rec.copy(done = rec.done + (qid to com.arise.app.data.Done.FULL), skipped = rec.skipped - qid))
            }
            onUpdate(next)
        }, onDismiss = { progressFor = null })
    }
}

@Composable
private fun TaskRow(t: Task, today: LocalDate, onProgress: () -> Unit, onEdit: () -> Unit) {
    Panel(accent = if (t.done) C.Good else C.Border) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable { onEdit() }) {
                Text((if (t.kind == TaskKind.PROJECT) "🧩 " else "📝 ") + t.title, fontWeight = FontWeight.SemiBold, color = if (t.done) C.Text2 else C.Text)
                val due = t.deadline?.let { d ->
                    val left = ChronoUnit.DAYS.between(today, d)
                    "Due ${d.format(dl)}" + when {
                        t.done -> ""
                        left < 0 -> " · date passed, no stress"
                        left == 0L -> " · today"
                        left == 1L -> " · tomorrow"
                        else -> " · $left days"
                    }
                } ?: if (t.done) "Done ${t.doneOn?.format(dl) ?: ""}" else "No deadline"
                Text(due + if (t.pairable) " · 📺 pairs with a show" else "", color = C.Muted, fontSize = 12.sp)
            }
            if (!t.done) SysButton(if (t.kind == TaskKind.PROJECT) "${t.progress}%" else "Done", small = true, color = C.Good, onClick = onProgress)
        }
        if (t.kind == TaskKind.PROJECT) {
            Spacer(Modifier.height(6.dp))
            XpBar(t.progress / 100f, if (t.done) C.Good else C.Glow, height = 5.dp)
            Todo.currentStep(t, t.progress)?.takeIf { !t.done }?.let { Text("Step: ${it.title}", color = C.Text2, fontSize = 12.sp) }
        }
    }
}

/** "How far did you get?" Projects: a slider. One-offs: done or not yet. */
@Composable
fun TaskProgressDialog(t: Task, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    if (t.kind == TaskKind.ONE_OFF) {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = C.Panel,
            title = { Text("📝 ${t.title}") },
            text = { Text("Done?", color = C.Text2) },
            confirmButton = { TextButton(onClick = { onSave(100) }) { Text("Yes, done", color = C.Good) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Not yet", color = C.Muted) } },
        )
        return
    }
    var pct by remember { mutableFloatStateOf(t.progress.toFloat()) }
    val p = (pct / 5f).roundToInt() * 5
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Panel,
        title = { Text("🧩 ${t.title}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("How much is done now, in total?", color = C.Text2)
                Text("$p%", fontSize = 34.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
                Slider(
                    value = pct, onValueChange = { pct = it }, valueRange = 0f..100f,
                    colors = SliderDefaults.colors(thumbColor = C.Glow, activeTrackColor = C.Glow),
                )
                Todo.currentStep(t, p)?.let { Text("Step: ${it.title}", color = C.Muted, fontSize = 12.sp) }
                Text("Finished more than planned? Great: the next days get lighter. Less? It's spread out again, no stress.", color = C.Muted, fontSize = 11.sp)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(p) }) { Text("Save", color = C.Glow) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = C.Muted) } },
    )
}

private fun stepsText(steps: List<TaskStep>) = steps.joinToString("\n") { "${it.title} · ${it.share}" }

/** "Research · 30" per line → steps whose shares are scaled to add up to 100. */
private fun parseSteps(text: String): List<TaskStep> {
    val raw = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
        val m = Regex("^(.*?)\\s*[·:\\-]\\s*(\\d+)\\s*%?$").find(line)
        if (m != null) m.groupValues[1].trim() to m.groupValues[2].toInt() else line to 0
    }
    if (raw.isEmpty()) return emptyList()
    val given = raw.sumOf { it.second }
    val even = 100 / raw.size
    val shares = if (given <= 0) raw.map { even } else raw.map { (it.second * 100.0 / given).roundToInt() }
    val fixed = shares.toMutableList().also { it[it.lastIndex] += 100 - it.sum() }
    return raw.mapIndexed { i, (title, _) -> TaskStep(title, fixed[i].coerceAtLeast(0)) }
}

@Composable
private fun DraftEditor(
    d: Draft,
    today: LocalDate,
    onChange: (Draft) -> Unit,
) {
    val ctx = LocalContext.current
    var stepText by remember(d.title) { mutableStateOf(stepsText(d.steps)) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = d.title, onValueChange = { onChange(d.copy(title = it)) },
            label = { Text("Task", fontSize = 12.sp) }, singleLine = true, colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            SysButton(d.deadline?.let { "📅 ${it.format(dl)}" } ?: "📅 No date", small = true, onClick = {
                val init = d.deadline ?: today.plusDays(3)
                DatePickerDialog(ctx, { _, y, m, day -> onChange(d.copy(deadline = LocalDate.of(y, m + 1, day))) }, init.year, init.monthValue - 1, init.dayOfMonth)
                    .apply { datePicker.minDate = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
                    .show()
            })
            if (d.deadline != null) SysButton("✕", small = true, color = C.Muted, onClick = { onChange(d.copy(deadline = null)) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SysButton("One-off", small = true, selected = d.kind == TaskKind.ONE_OFF, onClick = { onChange(d.copy(kind = TaskKind.ONE_OFF, steps = emptyList())) })
            SysButton("Project", small = true, selected = d.kind == TaskKind.PROJECT, onClick = {
                val steps = d.steps.ifEmpty { Todo.planFor(d.title).second.ifEmpty { listOf(TaskStep("Start", 30), TaskStep("Keep going", 40), TaskStep("Finish", 30)) } }
                stepText = stepsText(steps)
                onChange(d.copy(kind = TaskKind.PROJECT, steps = steps))
            })
            SysButton(if (d.pairable) "📺 With a show ✓" else "📺 With a show", small = true, selected = d.pairable, color = C.Gold, onClick = { onChange(d.copy(pairable = !d.pairable)) })
        }
        if (d.kind == TaskKind.PROJECT) {
            OutlinedTextField(
                value = stepText,
                onValueChange = {
                    stepText = it
                    onChange(d.copy(steps = parseSteps(it)))
                },
                label = { Text("Steps · share of the work (one per line)", fontSize = 12.sp) },
                colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ReviewDialog(list: List<Draft>, today: LocalDate, onAddMore: () -> Unit, onSave: (List<Draft>) -> Unit, onDismiss: () -> Unit) {
    var items by remember(list) { mutableStateOf(list) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Panel,
        title = { Text("Here's what I heard (${items.size})") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (items.isEmpty()) Text("Nothing caught. Try again, a little slower.", color = C.Muted)
                items.forEachIndexed { i, d ->
                    Panel {
                        DraftEditor(d, today) { nd -> items = items.toMutableList().also { it[i] = nd } }
                        Spacer(Modifier.height(4.dp))
                        SysButton("Remove", small = true, color = C.Danger, onClick = { items = items.filterIndexed { j, _ -> j != i } })
                    }
                }
                SysButton("🎤 Add more", small = true, onClick = onAddMore)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(items.filter { it.title.isNotBlank() }) }) { Text("Save all", color = C.Glow) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Discard", color = C.Muted) } },
    )
}

@Composable
private fun EditTaskDialog(t: Task, today: LocalDate, onSave: (Task) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var d by remember(t.id) { mutableStateOf(Draft(t.title, t.deadline, t.kind, t.steps, t.pairable)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Panel,
        title = { Text("Edit task") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DraftEditor(d, today) { d = it }
                if (t.done) SysButton("Reopen", small = true, onClick = { onSave(t.copy(doneOn = null, progress = minOf(t.progress, 95))) })
                SysButton("Delete task", small = true, color = C.Danger, onClick = onDelete)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(t.copy(title = d.title.ifBlank { t.title }, deadline = d.deadline, kind = d.kind, steps = d.steps, pairable = d.pairable)) }) {
                Text("Save", color = C.Glow)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = C.Muted) } },
    )
}
