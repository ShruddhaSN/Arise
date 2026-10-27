package com.arise.app.ui

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arise.app.data.AppData
import com.arise.app.data.Meals
import com.arise.app.data.Med
import com.arise.app.data.MedTime
import com.arise.app.data.RoutineDef
import com.arise.app.data.Settings
import com.arise.app.data.Store
import com.arise.app.game.Exercises
import com.arise.app.game.Profile
import com.arise.app.game.QType
import com.arise.app.game.Question
import com.arise.app.game.fmt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

private val weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

private val languages = listOf("Korean", "Japanese", "Chinese", "Spanish", "French", "German", "Hindi", "English")

/**
 * The assessment that starts (or restarts) the System, and later edits it. Each answer decides which
 * questions come next. With [editing], it's "Edit setup": everything is pre-filled and progress stays.
 */
@Composable
fun AssessmentScreen(
    data: AppData,
    today: LocalDate,
    onUpdate: (AppData) -> Unit,
    editing: Boolean = false,
    onClose: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val old = data.settings
    var answers by remember { mutableStateOf(if (editing) old.profile else emptyMap()) }
    var name by remember { mutableStateOf(old.playerName) }
    var age by remember { mutableStateOf(old.answer("age").firstOrNull() ?: "") }
    var office by remember { mutableStateOf(old.officeDays) }
    var wfh by remember { mutableStateOf(old.wfhDays) }
    var workStart by remember { mutableStateOf(old.workStart) }
    var workEnd by remember { mutableStateOf(old.workEnd) }
    var commute by remember { mutableIntStateOf(old.commuteMin) }
    var meals by remember { mutableStateOf(old.meals) }
    var meds by remember { mutableStateOf(old.meds) }
    var language by remember { mutableStateOf(old.language) }
    var languageApp by remember { mutableStateOf(old.languageApp.ifBlank { "Duolingo" }) }
    var wakeOffice by remember { mutableStateOf(old.wakeOffice) }
    var wakeWfh by remember { mutableStateOf(old.wakeWfh) }
    var wakeOff by remember { mutableStateOf(old.wakeOff) }
    var morning by remember { mutableStateOf(old.morning) }
    var night by remember { mutableStateOf(old.night) }
    var length by remember { mutableIntStateOf(old.challengeDays) }
    var weight by remember { mutableStateOf("") }
    var goal by remember { mutableStateOf(old.goalWeight?.toString() ?: "") }
    var tomorrow by remember { mutableStateOf(false) }
    var pos by remember { mutableIntStateOf(0) }
    var refused by remember { mutableStateOf(false) }

    fun occ(o: String) = o in answers["occupation"].orEmpty()
    val works = occ("student") || occ("working") || occ("business")

    // The page list is rebuilt from the answers, so branches appear and disappear as you answer.
    val visible = Profile.visible(answers)
    val lifeQs = visible.map { it.id }.filter { it !in setOf("gender", "hormonal", "occupation", "work_mode", "study_mode", "living", "meals_fixed", "kitchen", "push") }
    val pages: List<String> = buildList {
        if (!editing) add("intro")
        add("name")
        add("age")
        add("q:gender")
        if (visible.any { it.id == "hormonal" }) add("q:hormonal")
        add("q:occupation")
        if (visible.any { it.id == "work_mode" }) add("q:work_mode")
        if (visible.any { it.id == "study_mode" }) add("q:study_mode")
        if (works) add("schedule")
        add("q:living")
        if (visible.any { it.id == "meals_fixed" }) add("q:meals_fixed")
        add("meals")
        add("q:kitchen")
        add("meds")
        add("language")
        lifeQs.forEach { add("q:$it") }
        if ("life" in lifeQs && Profile.needsSupport(answers)) add(indexOf("q:life") + 1, "support")
        add("wake")
        add("morning")
        add("night")
        add("q:push")
        add("length")
        add("body")
        if (!editing) add("day1")
        add("result")
    }
    val page = pages[pos.coerceIn(0, pages.lastIndex)]
    fun next() {
        if (pos < pages.lastIndex) pos++
    }

    /** Picking how you work sets a sensible week; the schedule page can fine-tune it. */
    fun defaultWeek(mode: String?) {
        when (mode) {
            "office" -> {
                office = weekdays
                wfh = emptySet()
            }
            "wfh" -> {
                office = emptySet()
                wfh = weekdays
            }
            "hybrid" -> if (office.isEmpty() || wfh.isEmpty()) {
                office = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY)
                wfh = setOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY)
            }
        }
    }

    fun finish() {
        val profile = answers + ("age" to listOfNotNull(age.trim().takeIf { it.isNotEmpty() }))
        val intensity = answers["push"]?.firstOrNull()?.toIntOrNull() ?: old.intensity
        val noWork = !works
        val shaped = { base: Settings ->
            base.copy(
                playerName = name.trim(),
                profile = profile,
                intensity = intensity,
                officeDays = if (noWork) emptySet() else office,
                wfhDays = if (noWork) emptySet() else wfh,
                workStart = workStart,
                workEnd = workEnd,
                commuteMin = commute,
                meals = meals.copy(fixed = "yes" in answers["meals_fixed"].orEmpty(), breakfastEnd = if ("yes" in answers["meals_fixed"].orEmpty()) meals.breakfastEnd ?: meals.breakfast.plusMinutes(30) else null),
                meds = meds.filter { it.name.isNotBlank() },
                language = language?.trim()?.takeIf { it.isNotEmpty() },
                languageApp = languageApp.trim(),
                wakeOffice = wakeOffice,
                wakeWfh = wakeWfh,
                wakeOff = wakeOff,
                morning = morning,
                night = night,
                challengeDays = length,
                goalWeight = goal.replace(',', '.').toDoubleOrNull(),
            )
        }
        if (editing) {
            onUpdate(data.copy(settings = shaped(old)))
            onClose()
            return
        }
        val settings = shaped(
            Settings(
                notifications = old.notifications,
                voice = old.voice,
                restarts = old.restarts,
                blocked = old.blocked,
                startDate = if (tomorrow) today.plusDays(1) else today,
            ),
        )
        var next = AppData(settings = settings)
        weight.replace(',', '.').toDoubleOrNull()?.let { w -> next = next.with(next.day(today).copy(weight = w)) }
        onUpdate(next)
        SystemBus.post(
            SysMsg(
                "THE SYSTEM IS ONLINE",
                listOf(
                    "Day 1: ${if (tomorrow) "tomorrow" else "today"}." + if (length > 0) " Your challenge: $length days." else "",
                    "Mode: ${intensityName(intensity)}.",
                    "So the System can pop up over other apps and on the lock screen: Player → System pop-ups → allow both.",
                    "Seal your time-wasting apps in Tools → Screen blocker.",
                    "Add the widgets: long-press your home screen → Widgets → Arise.",
                ),
                accent = C.Gold, button = "ARISE",
                speak = "The system is online. Day one begins.",
            ),
        )
    }

    Box(Modifier.fillMaxSize().background(C.Bg)) {
        SystemBackdrop(gate = page == "intro" || page == "result", accent = if (page == "result") C.Gold else C.Glow)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(20.dp))
            if (page != "intro") {
                SysLabel(if (editing) "[System] Edit setup · ${pos + 1}/${pages.size}" else "[System] Player assessment · ${pos}/${pages.size - 1}")
                XpBar(pos.toFloat() / (pages.size - 1), height = 4.dp)
            }
            AnimatedContent(targetState = page, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "page") { p ->
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    when {
                        p == "intro" -> Intro(
                            old.restarts > 0, refused, onAccept = { next() }, onRefuse = { refused = true },
                            canUndo = old.restarts > 0 && Store.loadSnapshot(ctx) != null,
                            onUndo = { Store.loadSnapshot(ctx)?.let { onUpdate(it) } },
                        )
                        p == "name" -> Panel {
                            Text("What should the System call you?", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Field("Your name", name) { name = it }
                        }
                        p == "age" -> Panel {
                            Text("How old are you?", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("It sets safe starting points for training.", color = C.Text2, fontSize = 13.sp)
                            Spacer(Modifier.height(8.dp))
                            Field("Age", age, KeyboardType.Number) { age = it.filter(Char::isDigit).take(2) }
                        }
                        p == "support" -> SupportCard(
                            lowest = "lowest" in answers["life"].orEmpty(),
                            onCall = { runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:14416"))) } },
                        )
                        p == "schedule" -> Panel {
                            SchedulePage(
                                student = occ("student"), office = office, wfh = wfh,
                                start = workStart, end = workEnd, commute = commute,
                                onWeek = { o, w ->
                                    office = o
                                    wfh = w
                                },
                                onStart = { workStart = it }, onEnd = { workEnd = it }, onCommute = { commute = it },
                            )
                        }
                        p == "meals" -> Panel {
                            MealsPage("yes" in answers["meals_fixed"].orEmpty(), meals) { meals = it }
                        }
                        p == "meds" -> Panel { MedsPage(meds) { meds = it } }
                        p == "language" -> Panel {
                            LanguagePage(language, languageApp, { language = it }, { languageApp = it })
                        }
                        p == "wake" -> Panel {
                            WakeGoalsPicker(wakeOffice, wakeWfh, wakeOff, { wakeOffice = it }, { wakeWfh = it }, { wakeOff = it })
                        }
                        p == "morning" -> Panel {
                            Text("Your morning routine", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("It unlocks when you tap I'M UP. Tick each step; it completes itself.", color = C.Text2, fontSize = 13.sp)
                            Spacer(Modifier.height(10.dp))
                            RoutineEditor(night = false, items = morning) { morning = it }
                        }
                        p == "night" -> Panel {
                            Text("Your night routine", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("It appears 40 minutes before lights-off.", color = C.Text2, fontSize = 13.sp)
                            Spacer(Modifier.height(10.dp))
                            RoutineEditor(night = true, items = night) { night = it }
                        }
                        p == "length" -> Panel { LengthPage(today, length) { length = it } }
                        p == "body" -> Panel {
                            Text("Your body, today", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("Both optional. Weigh-ins are weekly quests after this.", color = C.Text2, fontSize = 13.sp)
                            Spacer(Modifier.height(8.dp))
                            if (!editing) {
                                Field("Current weight (kg)", weight, KeyboardType.Decimal) { weight = it }
                                Spacer(Modifier.height(6.dp))
                            }
                            Field("Goal weight (kg)", goal, KeyboardType.Decimal) { goal = it }
                            if (answers["body_focus"].orEmpty().any { it == "belly" || it == "chest" }) {
                                Spacer(Modifier.height(10.dp))
                                Text(Exercises.SPOT_NOTE, color = C.Gold, fontSize = 12.sp)
                            }
                        }
                        p == "day1" -> Panel {
                            Text("When does Day 1 begin?", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("Setting up at night? Pick tomorrow, and your morning System alert will be ready.", color = C.Text2, fontSize = 13.sp)
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SysButton("Today", selected = !tomorrow, onClick = { tomorrow = false })
                                SysButton("Tomorrow", selected = tomorrow, onClick = { tomorrow = true })
                            }
                        }
                        p == "result" -> Result(answers, length, name.ifBlank { old.name }, language)
                        p.startsWith("q:") -> {
                            val q = Profile.questions.first { it.id == p.removePrefix("q:") }
                            Ask(q, answers[q.id].orEmpty()) { picked ->
                                answers = answers + (q.id to picked)
                                if (q.id == "work_mode" || q.id == "study_mode") defaultWeek(picked.firstOrNull())
                                if (q.type == QType.SINGLE) {
                                    scope.launch {
                                        delay(220)
                                        next()
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (page != "intro") {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (pos > 0) SysButton("‹ Back", onClick = { pos-- })
                    if (editing) SysButton("Cancel", color = C.Muted, onClick = onClose)
                    Spacer(Modifier.weight(1f))
                    val q = page.removePrefix("q:").takeIf { page.startsWith("q:") }?.let { id -> Profile.questions.first { it.id == id } }
                    val canGo = when {
                        q != null -> answers[q.id].orEmpty().isNotEmpty()
                        page == "name" -> name.isNotBlank()
                        else -> true
                    }
                    if (page == "result") {
                        SysButton(if (editing) "SAVE" else "ARISE", selected = true, color = C.Gold, onClick = { finish() })
                    } else {
                        if (editing) SysButton("Save now", color = C.Gold, small = true, onClick = { finish() })
                        SysButton("Next ›", selected = true, enabled = canGo, onClick = { next() })
                    }
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

private fun pickTime(ctx: android.content.Context, t: LocalTime, set: (LocalTime) -> Unit) {
    TimePickerDialog(ctx, { _, h, m -> set(LocalTime.of(h, m)) }, t.hour, t.minute, false).show()
}

@Composable
private fun TimeRow(label: String, t: LocalTime, set: (LocalTime) -> Unit) {
    val ctx = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, modifier = Modifier.width(150.dp), fontSize = 14.sp)
        SysButton(fmt(t), onClick = { pickTime(ctx, t, set) })
    }
}

/** Which days, what hours, how long the commute. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SchedulePage(
    student: Boolean,
    office: Set<DayOfWeek>,
    wfh: Set<DayOfWeek>,
    start: LocalTime,
    end: LocalTime,
    commute: Int,
    onWeek: (Set<DayOfWeek>, Set<DayOfWeek>) -> Unit,
    onStart: (LocalTime) -> Unit,
    onEnd: (LocalTime) -> Unit,
    onCommute: (Int) -> Unit,
) {
    WorkWeekPicker(office, wfh, onWeek)
    if (student) Text("Office = college / school days, WFH = online classes.", color = C.Muted, fontSize = 12.sp)
    Spacer(Modifier.height(14.dp))
    Text(if (student) "Class hours" else "Work hours", fontSize = 18.sp, fontWeight = FontWeight.Bold)
    TimeRow("Starts", start, onStart)
    TimeRow("Ends", end, onEnd)
    if (office.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        Text("Commute, one way", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("You leave at ${fmt(start.minusMinutes(commute.toLong()))} and get home around ${fmt(end.plusMinutes(commute.toLong()))}.", color = C.Text2, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0, 15, 30, 45, 60, 90, 120).forEach { m ->
                SysButton(if (m == 0) "None" else if (m < 60) "$m min" else "${m / 60}h${if (m % 60 > 0) " ${m % 60}" else ""}", small = true, selected = commute == m, onClick = { onCommute(m) })
            }
        }
    }
}

@Composable
private fun MealsPage(fixed: Boolean, meals: Meals, onChange: (Meals) -> Unit) {
    Text("Meal times", fontSize = 20.sp, fontWeight = FontWeight.Bold)
    Text(
        if (fixed) "When does each meal start? If breakfast closes, set when it closes: the System plans your morning around it."
        else "Roughly when you usually eat. Walks are timed after meals, workouts away from them.",
        color = C.Text2, fontSize = 13.sp,
    )
    Spacer(Modifier.height(8.dp))
    TimeRow(if (fixed) "Breakfast opens" else "Breakfast", meals.breakfast) { onChange(meals.copy(breakfast = it)) }
    if (fixed) TimeRow("Breakfast closes", meals.breakfastEnd ?: meals.breakfast.plusMinutes(30)) { onChange(meals.copy(breakfastEnd = it)) }
    TimeRow("Lunch", meals.lunch) { onChange(meals.copy(lunch = it)) }
    TimeRow("Dinner", meals.dinner) { onChange(meals.copy(dinner = it)) }
}

/** Daily medicines or supplements, each tied to a meal or bedtime. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MedsPage(meds: List<Med>, onChange: (List<Med>) -> Unit) {
    Text("Daily medicines or supplements", fontSize = 20.sp, fontWeight = FontWeight.Bold)
    Text("Anything you take every day at a fixed time. Each one becomes a small quest. Skip if none.", color = C.Text2, fontSize = 13.sp)
    Spacer(Modifier.height(8.dp))
    meds.forEachIndexed { i, m ->
        Column(Modifier.fillMaxWidth().background(C.Bg.copy(alpha = 0.6f), RoundedCornerShape(10.dp)).padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    Field("Name", m.name) { v -> onChange(meds.mapIndexed { j, x -> if (j == i) x.copy(name = v) else x }) }
                }
                Spacer(Modifier.width(8.dp))
                SysButton("✕", small = true, color = C.Danger, onClick = { onChange(meds.filterIndexed { j, _ -> j != i }) })
            }
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MedTime.entries.forEach { t ->
                    SysButton(t.label, small = true, selected = m.at == t, onClick = { onChange(meds.mapIndexed { j, x -> if (j == i) x.copy(at = t) else x }) })
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
    SysButton("+ Add one", small = true, onClick = { onChange(meds + Med("", MedTime.LUNCH)) })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguagePage(language: String?, app: String, onLanguage: (String?) -> Unit, onApp: (String) -> Unit) {
    var other by remember { mutableStateOf(language?.takeIf { it !in languages } ?: "") }
    Text("Learning a language?", fontSize = 20.sp, fontWeight = FontWeight.Bold)
    Text("It becomes a daily 10-minute quest, plus lessons in the rotation.", color = C.Text2, fontSize = 13.sp)
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SysButton("No", small = true, selected = language == null, color = C.Muted, onClick = { onLanguage(null) })
        languages.forEach { l -> SysButton(l, small = true, selected = language == l, onClick = { onLanguage(l) }) }
    }
    Spacer(Modifier.height(8.dp))
    Field("Another language", other) {
        other = it
        onLanguage(it.ifBlank { null })
    }
    if (language != null) {
        Spacer(Modifier.height(8.dp))
        Field("App you use (Duolingo, Falou…)", app) { onApp(it) }
    }
}

@Composable
private fun LengthPage(today: LocalDate, length: Int, onChange: (Int) -> Unit) {
    val yearEnd = daysLeftInYear(today)
    var custom by remember { mutableStateOf(if (length !in listOf(0, 21, 30, 66, 90, yearEnd)) length.toString() else "") }
    Text("How long is your challenge?", fontSize = 20.sp, fontWeight = FontWeight.Bold)
    Text("Day 1 to the finish line. At the end you see how far you came, then choose to continue or stop.", color = C.Text2, fontSize = 13.sp)
    Spacer(Modifier.height(8.dp))
    listOf(
        21 to "21 days · a quick reset",
        30 to "30 days · one month",
        66 to "66 days · how long a habit really takes",
        90 to "90 days · a full transformation",
        yearEnd to "$yearEnd days · until the end of ${today.year}",
        0 to "No end · just keep going",
    ).forEach { (d, label) ->
        SysButton(label, selected = length == d && custom.isEmpty(), modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), onClick = {
            custom = ""
            onChange(d)
        })
    }
    Spacer(Modifier.height(6.dp))
    Field("Or a custom number of days", custom, KeyboardType.Number) {
        custom = it.filter(Char::isDigit).take(3)
        custom.toIntOrNull()?.takeIf { n -> n > 0 }?.let(onChange)
    }
}

@Composable
private fun Intro(
    again: Boolean,
    refused: Boolean,
    onAccept: () -> Unit,
    onRefuse: () -> Unit,
    canUndo: Boolean,
    onUndo: () -> Unit,
) {
    LaunchedEffect(Unit) {
        Voice.say(if (again) "Welcome back. This time, it's for real." else "You have acquired the qualifications to be a player.")
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.height(330.dp))
        Text("ARISE", fontSize = 54.sp, fontWeight = FontWeight.Bold, fontFamily = Mono, color = C.Text, letterSpacing = 10.sp)
        Text(
            if (again) "In your life. For real this time." else "In your life.",
            fontSize = 18.sp, color = C.Glow, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        SystemWindow {
            Text("[SYSTEM]", color = C.Glow, fontFamily = Mono, fontSize = 12.sp, letterSpacing = 4.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                if (again) "The previous attempt has been erased. Nothing you did before counts against you now. Everything from here does."
                else "You have acquired the qualifications to be a Player.",
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "A short assessment shows where you are. Then the System builds daily quests around your real life: " +
                    "your work, your meals, your mornings. Disappear for a while. Come back unrecognisable.",
                color = C.Text2, fontSize = 13.sp,
            )
            Spacer(Modifier.height(10.dp))
            Text("Will you accept?", color = C.Gold, fontFamily = Mono, fontWeight = FontWeight.Bold)
            if (refused) {
                Spacer(Modifier.height(6.dp))
                Text("[SYSTEM] Refusal is not an option.", color = C.Danger, fontFamily = Mono, fontSize = 13.sp)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SysButton("ACCEPT", onClick = onAccept, selected = true, modifier = Modifier.weight(1f))
                SysButton("Decline", onClick = onRefuse, color = C.Muted)
            }
        }
        if (canUndo) {
            SysButton("Undo restart: bring my old progress back", onClick = onUndo, color = C.Muted, small = true)
        }
    }
}

/** One question: big option rows. Single choice moves on by itself; multi choice has a limit. */
@Composable
private fun Ask(q: Question, picked: List<String>, onPick: (List<String>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(q.title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        if (q.subtitle.isNotEmpty()) Text(q.subtitle, color = C.Text2, fontSize = 13.sp)
        if (q.type == QType.MULTI && q.max < 99) Text("${picked.size} / ${q.max} picked", color = C.Muted, fontSize = 12.sp, fontFamily = Mono)
        Spacer(Modifier.height(4.dp))
        q.options.forEach { o ->
            val on = o.id in picked
            val shape = RoundedCornerShape(12.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(if (on) C.Glow.copy(alpha = 0.16f) else C.Panel.copy(alpha = 0.85f), shape)
                    .border(1.dp, if (on) C.Glow else C.Border, shape)
                    .clickable {
                        val nextPick = when (q.type) {
                            QType.SINGLE -> listOf(o.id)
                            QType.MULTI -> when {
                                on -> picked - o.id
                                // "Nothing" style options clear the rest, and the rest clear them.
                                o.id == "none" || o.id == "nothing" -> listOf(o.id)
                                picked.size >= q.max -> picked
                                else -> picked.filter { it != "none" && it != "nothing" } + o.id
                            }
                        }
                        onPick(nextPick)
                    }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (o.icon.isNotEmpty()) Text(o.icon, fontSize = 20.sp, modifier = Modifier.width(36.dp))
                Text(o.label, fontSize = 15.sp, modifier = Modifier.weight(1f), color = if (on) C.Text else C.Text2)
                if (q.type == QType.MULTI) Text(if (on) "☑" else "☐", color = if (on) C.Glow else C.Muted, fontSize = 20.sp)
            }
        }
    }
}

@Composable
private fun SupportCard(lowest: Boolean, onCall: () -> Unit) {
    SystemWindow(accent = C.Gold) {
        Text("Thank you for being honest.", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "You don't have to carry this alone. Talking to someone really does help, and it isn't a weakness.\n\n" +
                "Tele-MANAS is free, confidential and open 24×7 in India: call 14416 or 1-800-891-4416. " +
                "Or talk to a friend, family member or doctor you trust.",
            color = C.Text2, fontSize = 14.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (lowest) "The System will keep things small and steady for now. Small wins count. Choose \"Steady\" when it asks how hard to push."
            else "The System will start you with small, winnable quests.",
            color = C.Gold, fontSize = 13.sp,
        )
        Spacer(Modifier.height(12.dp))
        SysButton("📞 Call 14416", onClick = onCall, color = C.Gold)
    }
}

/** Life Reset-style ratings: where you are now, and where the plan can take you by the end of the year. */
@Composable
private fun Result(answers: Map<String, List<String>>, length: Int, name: String, language: String?) {
    var showPotential by remember { mutableStateOf(false) }
    val ratings = Profile.ratings(answers, if (length > 0) length else 66)
    val areas = answers[Profile.AREAS].orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("[SYSTEM] Assessment complete", color = C.Gold, fontFamily = Mono, fontSize = 13.sp, letterSpacing = 2.sp)
        Text("$name's rating", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("This reflects your life right now. Every cleared day raises it.", color = C.Text2, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SysButton("Day 1 rating", selected = !showPotential, small = true, onClick = { showPotential = false })
            SysButton(if (length > 0) "Potential by Day $length" else "Potential in 66 days", selected = showPotential, small = true, color = C.Gold, onClick = { showPotential = true })
        }
        ratings.chunked(2).forEachIndexed { row, pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEachIndexed { i, r ->
                    RatingTile(
                        r.label, r.icon,
                        if (showPotential) r.potential else r.now,
                        if (showPotential) r.potential - r.now else null,
                        highlight = row == 0 && i == 0,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Panel {
            SysLabel("Your daily quests", C.Gold)
            Spacer(Modifier.height(6.dp))
            val lines = buildList {
                add("☀ Rise on time, then your own morning routine")
                add("💧 Water and 👣 steps" + (language?.let { ", plus 🗣 $it" } ?: "") + ", all day")
                add("⚔ Strength twice a week, a new combination every time, with a voice coach")
                add("🏃 Run plan from zero to 20 minutes non-stop")
                add("🚶 Walks after lunch and dinner (bonus)")
                add("📚 A lesson a day, then answer out loud")
                add("⚡ Surprise emergency quests")
                if (Profile.CAREER in areas) add("🎯 Deep work: 25 focused minutes on work days")
                if (Profile.SOCIAL in areas) add("🤝 Reach out to someone, 3 times a week")
                if (Profile.MIND in areas || Profile.SPIRIT in areas || Profile.CRISIS in areas) add("📓 A 3-line journal every night")
                add("🌙 Night routine, then lights off")
            }
            lines.forEach { Text(it, color = C.Text2, fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp)) }
        }
        Text(
            "Miss a day and a penalty quest appears. Never miss twice.",
            color = C.Danger, fontSize = 12.sp, fontFamily = Mono,
        )
    }
}
