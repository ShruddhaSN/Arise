package com.arise.app.ui

import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arise.app.data.AppData
import com.arise.app.data.DayRecord
import com.arise.app.data.Done
import com.arise.app.data.Skip
import com.arise.app.game.Block
import com.arise.app.game.Challenges
import com.arise.app.game.Curriculum
import com.arise.app.game.DayLesson
import com.arise.app.game.DayPlan
import com.arise.app.game.DayStatus
import com.arise.app.game.Deck
import com.arise.app.game.Feats
import com.arise.app.game.Game
import com.arise.app.game.Kind
import com.arise.app.game.Knowledge
import com.arise.app.game.Program
import com.arise.app.game.Progress
import com.arise.app.game.Quest
import com.arise.app.game.Season
import com.arise.app.game.Slots
import com.arise.app.game.Topic
import com.arise.app.game.fmt
import com.arise.app.notify.Blocker
import com.arise.app.notify.Reminders
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.ceil

private val dayFmt = DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.ENGLISH)

/** Train tab targets. */
object TrainTarget {
    const val RUN = 0
    const val STRENGTH = 1
    const val MOBILITY = 2
    const val CHALLENGE = 3
}

/** Everything a quest card can do. Locked (future) cards get none of it. */
private class QuestActions(
    val done: (Quest, Done?) -> Unit,
    val amount: (Quest, Double) -> Unit,
    val askAmount: (Quest) -> Unit,
    val wake: (LocalTime) -> Unit,
    val pickWake: () -> Unit,
    val later: (Quest) -> Unit,
    val laterAt: (Quest, LocalTime) -> Unit,
    val unsnooze: (Quest) -> Unit,
    val skip: (Quest) -> Unit,
    val undo: (Quest) -> Unit,
    val notes: (Boolean) -> Unit,
    val coach: (Quest) -> Unit,
    val routine: (Quest) -> Unit,
    val recall: () -> Unit,
)

fun fmtAmount(v: Double, unit: String): String = when (unit) {
    "L" -> if (v % 1.0 == 0.0) v.toInt().toString() else "%.2f".format(Locale.ENGLISH, v).trimEnd('0').trimEnd('.')
    "steps" -> "%,d".format(Locale.ENGLISH, v.toInt())
    else -> v.toInt().toString()
}

private fun coachTarget(q: Quest): Int? = when {
    q.id == "run" -> TrainTarget.RUN
    q.id == "strength" -> TrainTarget.STRENGTH
    q.id == "mobility" || q.id == "stretch" -> TrainTarget.MOBILITY
    q.id.startsWith("bc_") -> TrainTarget.CHALLENGE
    else -> null
}

/** Days left in this year, today included. */
fun daysLeftInYear(today: LocalDate): Int =
    (ChronoUnit.DAYS.between(today, LocalDate.of(today.year, 12, 31)) + 1).toInt()

fun intensityName(i: Int): String = when (i) {
    1 -> "STEADY"
    2 -> "HARD MODE"
    else -> "DISAPPEAR MODE"
}

@Composable
fun TodayScreen(
    data: AppData,
    today: LocalDate,
    now: LocalTime,
    progress: Progress,
    onUpdate: (AppData) -> Unit,
    openTrain: (target: Int, autoStartFlow: String?) -> Unit,
    openChallenges: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val s = data.settings
    // Before Day 1, open on Day 1 itself as a locked preview.
    var date by remember { mutableStateOf(if (today < s.start) s.start else today) }
    val isToday = date == today
    val locked = date > today
    val deckMode = isToday && today >= s.start
    val status = Game.status(data, date)
    val plan = status.plan
    val rec = data.day(date)
    val passesLeft = Game.passesLeft(data, date)
    val penalty = isToday && Game.penaltyActive(data, date)
    val lesson = remember(data, date) { Game.lessonFor(data, date) }

    // 0 = to-do (the deck), 1 = done, 2 = skipped, 3 = full day.
    var view by rememberSaveable { mutableStateOf(0) }
    var amountFor by remember { mutableStateOf<Quest?>(null) }
    var skipFor by remember { mutableStateOf<Quest?>(null) }
    var laterFor by remember { mutableStateOf<Quest?>(null) }
    var routineFor by remember { mutableStateOf<String?>(null) }
    var recallOpen by remember { mutableStateOf(false) }
    var pop by remember { mutableStateOf<Pair<String, Long>?>(null) }

    fun save(r: DayRecord) = onUpdate(data.with(r))
    fun saveWithPop(q: Quest, r: DayRecord) {
        val gained = Game.xpFor(q, r) - Game.xpFor(q, rec)
        if (gained > 0) {
            pop = "+$gained ${q.stat.label} XP" to System.currentTimeMillis()
            Cues.tap(ctx)
        }
        save(r)
        // Clearing the day is a System moment.
        val before = Game.status(data, date)
        val after = Game.status(data.with(r), date)
        if (isToday && !before.cleared && after.cleared) {
            SystemBus.post(
                SysMsg(
                    "DAILY QUEST CLEARED",
                    listOf(
                        "${after.doneCount}/${after.total} quests complete. +${after.xp} XP today.",
                        "Your streak is safe. No penalty tomorrow.",
                        "Keep going: everything else today is bonus XP.",
                    ),
                    accent = C.Gold, speak = "Daily quest cleared.",
                ),
            )
        }
    }

    fun announceWake(next: DayRecord) {
        val lines = Feats.wakeLines(data.with(next), date)
        if (lines.isNotEmpty()) {
            SystemBus.post(SysMsg("PLAYER HAS AWAKENED", lines, speak = "Player has awakened. The day has been recalculated."))
        }
    }

    val act = QuestActions(
        done = { q, d ->
            saveWithPop(q, rec.copy(done = if (d == null) rec.done - q.id else rec.done + (q.id to d), skipped = rec.skipped - q.id))
        },
        amount = { q, v ->
            val next = rec.copy(amounts = if (v <= 0.0) rec.amounts - q.id else rec.amounts + (q.id to v), skipped = rec.skipped - q.id)
            saveWithPop(q, next)
            // Finishing a lesson opens the recall questions, once.
            if (q.id == "learn" && Game.ratio(q, next) >= 0.5f && !rec.done.containsKey("recall")) recallOpen = true
        },
        askAmount = { q -> amountFor = q },
        wake = { t ->
            val q = plan.quests.first { it.kind == Kind.WAKE }
            val next = rec.copy(wakeTime = "%02d:%02d".format(t.hour, t.minute))
            saveWithPop(q, next)
            Reminders.clearWake(ctx)
            Blocker.onWake(ctx, s)
            announceWake(next)
        },
        pickWake = {
            val q = plan.quests.first { it.kind == Kind.WAKE }
            val init = rec.wake ?: LocalTime.now()
            TimePickerDialog(ctx, { _, h, m ->
                val next = rec.copy(wakeTime = "%02d:%02d".format(h, m))
                saveWithPop(q, next)
                Reminders.clearWake(ctx)
                announceWake(next)
            }, init.hour, init.minute, false).show()
        },
        later = { q -> laterFor = q },
        laterAt = { q, t -> save(rec.copy(deferred = rec.deferred + (q.id to Deck.millisAt(date, t)))) },
        unsnooze = { q -> save(rec.copy(deferred = rec.deferred - q.id)) },
        skip = { q -> skipFor = q },
        undo = { q ->
            save(
                rec.copy(
                    done = rec.done - q.id,
                    amounts = rec.amounts - q.id,
                    skipped = rec.skipped - q.id,
                    wakeTime = if (q.kind == Kind.WAKE) null else rec.wakeTime,
                    routine = if (q.kind == Kind.ROUTINE) rec.routine - q.items.map { it.id }.toSet() else rec.routine,
                ),
            )
        },
        notes = { on -> save(rec.copy(done = if (on) rec.done + ("notes" to Done.FULL) else rec.done - "notes")) },
        coach = { q ->
            val t = coachTarget(q)
            if (t != null) openTrain(t, if (t == TrainTarget.MOBILITY) q.id else null)
        },
        routine = { q -> routineFor = q.id },
        recall = { recallOpen = true },
    )

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Box(Modifier.tutorialTarget("header")) {
                    DailyQuestHeader(
                        data, date, today, plan, status, rec, progress, locked, penalty,
                        onPrev = { date = date.minusDays(1) },
                        onNext = { date = date.plusDays(1) },
                    )
                }
            }

            if (today < s.start && date == s.start) {
                item {
                    SystemWindow(accent = C.Gold) {
                        SysLabel("[System] Day 1 begins tomorrow", C.Gold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "This is a preview of Day 1. Tonight's only real quest: phone away by 10:30, lights off by 11:30. Lay out your workout clothes.",
                            color = C.Text2, fontSize = 13.sp,
                        )
                    }
                }
            }

            if (isToday && plan.notes.isNotEmpty()) {
                item { NotesPanel(plan.notes) }
            }
            if (deckMode && s.challenges.none { it.kind == com.arise.app.data.ChallengeKind.WEEKLY && Challenges.running(it, today) }) {
                item {
                    val picks = Challenges.offers(today)
                    Panel(accent = C.Gold, modifier = Modifier.clickable(onClick = openChallenges)) {
                        SysLabel("[System] Weekly challenge available", C.Gold)
                        Spacer(Modifier.height(4.dp))
                        Text(picks.joinToString("  ·  ") { "${it.icon} ${it.title}" }, fontSize = 13.sp, color = C.Text2)
                        Text("+${Challenges.WEEKLY_DAY_XP} XP per day kept, ${Challenges.WEEKLY_XP} for a perfect week. Tap to choose.", color = C.Muted, fontSize = 11.sp)
                    }
                }
            }

            if (penalty) {
                item {
                    val pq = Game.penaltyQuest
                    val done = rec.done.containsKey(pq.id)
                    Panel(accent = if (done) C.Good else C.Danger, glow = !done) {
                        SysLabel("[System] Penalty quest", C.Danger)
                        Spacer(Modifier.height(4.dp))
                        Text("Yesterday wasn't cleared. Never miss twice.", color = C.Text2, fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(pq.title, fontWeight = FontWeight.SemiBold)
                                Text(pq.full, color = C.Text2, fontSize = 13.sp)
                            }
                            SysButton(
                                if (done) "✓ Done" else "Done",
                                onClick = { act.done(pq, if (done) null else Done.FULL) },
                                selected = done, color = C.Good,
                            )
                        }
                    }
                }
            }

            val weekPen = if (isToday) Challenges.weeklyPenalty(data, date) else null
            if (weekPen != null) {
                item {
                    val pq = Challenges.weeklyPenaltyQuest
                    val done = rec.done.containsKey(pq.id)
                    Panel(accent = if (done) C.Good else C.Danger, glow = !done) {
                        SysLabel("[System] Weekly penalty", C.Danger)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${Challenges.title(weekPen)}: kept ${Challenges.kept(data, weekPen, date)} of 7 days.",
                            color = C.Text2, fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(pq.title, fontWeight = FontWeight.SemiBold)
                                Text(pq.full, color = C.Text2, fontSize = 13.sp)
                            }
                            SysButton(if (done) "✓ Done" else "Done", onClick = { act.done(pq, if (done) null else Done.FULL) }, selected = done, color = C.Good)
                        }
                    }
                }
            }

            item {
                AllDayBox(plan.allDay, rec, locked, act, Modifier.tutorialTarget("allday"))
            }

            val settledQs = plan.blocks.mapNotNull { it.quest }.filter { Game.settled(it, rec) }
            val doneQs = settledQs.filter { !rec.skipped.containsKey(it.id) }
            val skippedQs = settledQs.filter { rec.skipped.containsKey(it.id) }
            if (deckMode) {
                val cards = Deck.cards(data, date, now)
                item {
                    Row(Modifier.tutorialTarget("fullday"), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("To-do ${cards.size}", "Done ${doneQs.size}", "Skipped ${skippedQs.size}", "Full day").forEachIndexed { i, label ->
                            SysButton(label, small = true, selected = view == i, onClick = { view = i },
                                color = when (i) {
                                    1 -> C.Good
                                    2 -> C.Muted
                                    else -> C.Glow
                                })
                        }
                    }
                }
            }
            if (deckMode && view == 0) {
                val cards = Deck.cards(data, date, now)
                val snoozed = Deck.snoozed(data, date, now)
                item {
                    Box(Modifier.tutorialTarget("deck")) {
                        DeckSection(cards, date, rec, lesson, passesLeft, now, act)
                    }
                }
                if (snoozed.isNotEmpty()) {
                    item { LaterPile(snoozed, act) }
                }
                item {
                    val next = Deck.upNext(data, date, now)
                    Row(Modifier.tutorialTarget("upnext").padding(horizontal = 4.dp)) {
                        Text("UP NEXT  ", color = C.Muted, fontFamily = Mono, fontSize = 11.sp)
                        Text(
                            next?.let { "${fmt(it.time)} · ${it.quest?.title ?: it.title}" } ?: "Nothing else today. Rest well.",
                            color = C.Text2, fontSize = 13.sp,
                        )
                    }
                }
            } else if (deckMode && view == 1) {
                item {
                    if (doneQs.isEmpty()) Text("Nothing done yet. The first one is the hardest.", color = C.Muted, fontSize = 13.sp)
                    else SettledList("✓ Done today", doneQs, rec, act, C.Good, startOpen = true)
                }
            } else if (deckMode && view == 2) {
                item {
                    if (skippedQs.isEmpty()) Text("No skips today. Keep it that way.", color = C.Muted, fontSize = 13.sp)
                    else SettledList("✗ Skipped", skippedQs, rec, act, C.Muted, startOpen = true)
                }
            } else {
                if (locked) {
                    item {
                        Text(
                            "🔒 Unlocks on ${date.format(dayFmt)}. You can look, but not tick.",
                            color = C.Muted, fontSize = 13.sp,
                        )
                    }
                }
                val nextIdx = if (isToday) plan.blocks.indexOfFirst { it.time > now } else -1
                items(plan.blocks, key = { it.time.toString() + it.title + (it.quest?.id ?: "") }) { b ->
                    if (b.special == com.arise.app.game.Special.NONE) {
                        FullDayRow(b, rec, locked, plan.blocks.indexOf(b) == nextIdx, lesson, passesLeft, act)
                    }
                }
            }

            item {
                KnowledgeCard(Knowledge.forDay(s.earliest, date))
            }
            item { Spacer(Modifier.height(24.dp)) }
        }

        pop?.let { p -> key(p.second) { XpPop(p.first, Modifier.align(Alignment.TopCenter).padding(top = 140.dp)) } }
    }

    amountFor?.let { q ->
        AmountDialog(q, rec.amounts[q.id] ?: 0.0, onSave = { v -> act.amount(q, v); amountFor = null }, onDismiss = { amountFor = null })
    }
    routineFor?.let { id ->
        val q = plan.quests.firstOrNull { it.id == id }
        if (q == null) {
            routineFor = null
        } else {
            RoutineDialog(
                q, rec,
                onToggle = { itemId ->
                    val ticked = if (itemId in rec.routine) rec.routine - itemId else rec.routine + itemId
                    val next = rec.copy(routine = ticked, skipped = rec.skipped - q.id)
                    val wasDone = Game.ratio(q, rec) >= 1f
                    saveWithPop(q, next)
                    if (!wasDone && Game.ratio(q, next) >= 1f) {
                        routineFor = null
                        val upNext = Deck.upNext(data.with(next), date, LocalTime.now())
                        SystemBus.post(
                            SysMsg(
                                "${q.title.uppercase()} COMPLETE",
                                listOfNotNull(
                                    "+${q.xp} ${q.stat.label} XP",
                                    if (q.id == "night") "Phone away. Lights off. Tomorrow starts now." else "The body is ready. Begin the day.",
                                    upNext?.let { "Next: ${fmt(it.time)} · ${it.quest?.title ?: it.title}" },
                                ),
                                accent = C.Good, speak = "${q.title} complete.",
                            ),
                        )
                    }
                },
                onClose = { routineFor = null },
            )
        }
    }
    if (recallOpen) {
        val first = lesson?.lessons?.firstOrNull()
        RecallDialog(
            Curriculum.recall(first, date), rec.answers,
            onSave = { answers ->
                recallOpen = false
                if (answers.isEmpty()) return@RecallDialog
                save(rec.copy(answers = rec.answers + answers, done = rec.done + ("recall" to Done.FULL)))
                val words = answers.values.sumOf { a -> a.split(Regex("\\s+")).count { it.isNotBlank() } }
                val keyPoints = Curriculum.recall(first, date).filter { it.first in answers.keys }.mapNotNull { it.second }
                SystemBus.post(
                    SysMsg(
                        "RECALL RECORDED",
                        buildList {
                            add(
                                when {
                                    words < 15 -> "$words words. Short. Next time, try to talk for 30 seconds without stopping."
                                    words < 60 -> "$words words. Good recall. Saying it out loud locks it in."
                                    else -> "$words words. Excellent recall. You could teach this."
                                },
                            )
                            add("+${Game.NOTES_XP} INT XP")
                            if (keyPoints.isNotEmpty()) {
                                add("Check yourself against the key points:")
                                keyPoints.forEach { add("• $it") }
                            }
                        },
                        accent = C.Glow,
                    ),
                )
            },
            onDismiss = { recallOpen = false },
        )
    }
    laterFor?.let { q ->
        val choice = Slots.choices(data, date, q, LocalTime.now())
        AlertDialog(
            onDismissRequest = { laterFor = null },
            containerColor = C.Panel,
            title = { Text("Later: ${q.title}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    choice.options.forEach { o ->
                        Column(Modifier.fillMaxWidth()) {
                            SysButton(
                                if (o.best) o.label else "${o.label} · ${o.detail}",
                                modifier = Modifier.fillMaxWidth(),
                                selected = o.best,
                                color = if (o.best) C.Gold else C.Glow,
                                onClick = {
                                    act.laterAt(q, o.at)
                                    laterFor = null
                                },
                            )
                            if (o.best) Text(o.detail, color = C.Text2, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
                        }
                    }
                    SysButton("Pick a time…", modifier = Modifier.fillMaxWidth(), color = C.Muted, onClick = {
                        val init = LocalTime.now().plusHours(1)
                        TimePickerDialog(ctx, { _, h, m ->
                            act.laterAt(q, LocalTime.of(h, m))
                            laterFor = null
                        }, init.hour, init.minute, false).show()
                    })
                    choice.note?.let { Text(it, color = C.Gold, fontSize = 12.sp) }
                }
            },
            confirmButton = { TextButton(onClick = { laterFor = null }) { Text("Cancel", color = C.Muted) } },
        )
    }
    skipFor?.let { q ->
        AlertDialog(
            onDismissRequest = { skipFor = null },
            containerColor = C.Panel,
            title = { Text("Not today: ${q.title}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Why? The System counts every skip and looks for patterns.", color = C.Text2, fontSize = 13.sp)
                    Skip.entries.forEach { reason ->
                        SysButton("${reason.icon}  ${reason.label}", modifier = Modifier.fillMaxWidth(), onClick = {
                            save(rec.copy(skipped = rec.skipped + (q.id to reason)))
                            skipFor = null
                        })
                    }
                }
            },
            confirmButton = { TextButton(onClick = { skipFor = null }) { Text("Cancel", color = C.Muted) } },
        )
    }
}

/** The Solo Leveling daily quest window: countdown to midnight, progress, the warning. */
@Composable
private fun DailyQuestHeader(
    data: AppData,
    date: LocalDate,
    today: LocalDate,
    plan: DayPlan,
    status: DayStatus,
    rec: DayRecord,
    progress: Progress,
    locked: Boolean,
    penalty: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    val s = data.settings
    val isToday = date == today
    var clock by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(isToday) {
        while (isToday) {
            clock = LocalTime.now()
            delay(1000)
        }
    }
    SystemWindow(accent = if (penalty) C.Danger else C.Glow) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SysButton("‹", onClick = onPrev, enabled = date > s.earliest, small = true)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                SysLabel(if (locked) "[System] Upcoming · locked" else "[System] Daily quest", if (penalty) C.Danger else C.Glow)
                Text(date.format(dayFmt), fontSize = 19.sp, fontWeight = FontWeight.Bold)
                val dayNo = Season.day(s, date)
                Text(
                    (if (dayNo >= 1) Season.label(s, date) + " · " else "") + "Week ${Program.week(s, date)} · ${plan.type.icon} ${Program.dayLabel(s, plan.type)}" + when {
                        date < s.start -> " · fill-in day"
                        !isToday && !locked -> " · past day"
                        else -> ""
                    },
                    color = C.Muted, fontSize = 12.sp,
                )
            }
            SysButton("›", onClick = onNext, enabled = date < today.plusDays(7), small = true)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Lv ${progress.level}", fontFamily = Mono, fontWeight = FontWeight.Bold, color = C.Glow)
            Text(" · ${progress.rank}-Rank", fontFamily = Mono, color = C.Text2, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            Text("🔥 ${progress.streak}", fontFamily = Mono, color = C.Gold)
            Spacer(Modifier.width(10.dp))
            Text("+${status.xp} XP", color = C.Glow, fontFamily = Mono, fontWeight = FontWeight.Bold)
        }

        // Wake line, pinned all day.
        if (!locked && plan.quests.any { it.kind == Kind.WAKE }) {
            val goal = plan.quests.first { it.kind == Kind.WAKE }.target.toInt()
            val goalT = LocalTime.ofSecondOfDay(goal * 60L)
            Spacer(Modifier.height(6.dp))
            val w = rec.wake
            if (w != null) {
                val late = w.toSecondOfDay() / 60 - goal
                Text(
                    "☀ Up at ${fmt(w)} · goal ${fmt(goalT)}" + if (late > 0) " (${Feats.lateText(late)})" else " ✓",
                    color = if (late > 30) C.Gold else C.Good, fontSize = 13.sp, fontFamily = Mono,
                )
            } else {
                Text("☀ Not up yet · goal ${fmt(goalT)}", color = C.Muted, fontSize = 13.sp, fontFamily = Mono)
            }
        }
        rec.event?.let { e ->
            Text("🎉 ${e.title} · ${fmt(e.start)}–${fmt(e.realEnd)}", color = C.Gold, fontSize = 13.sp, fontFamily = Mono)
        }

        if (!locked) {
            Spacer(Modifier.height(10.dp))
            val need = ceil(Game.CLEAR_RATIO * status.total).toInt()
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${status.doneCount}/${status.total}", fontWeight = FontWeight.Bold, fontSize = 22.sp, fontFamily = Mono)
                Text(
                    when {
                        status.sick -> "  sick day: rest, no penalty"
                        status.cleared -> "  cleared ✓"
                        else -> "  clear $need to keep the streak"
                    },
                    color = if (status.cleared) C.Good else C.Text2, fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
                Spacer(Modifier.weight(1f))
                if (isToday) {
                    val left = 24 * 3600 - clock.toSecondOfDay()
                    Column(horizontalAlignment = Alignment.End) {
                        Text("REMAINING", color = C.Muted, fontSize = 9.sp, fontFamily = Mono, letterSpacing = 2.sp)
                        Text(
                            "%02d:%02d:%02d".format(left / 3600, (left / 60) % 60, left % 60),
                            color = if (left < 3 * 3600 && !status.cleared) C.Danger else C.Text, fontFamily = Mono, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            XpBar(if (status.total == 0) 0f else status.doneCount.toFloat() / need.coerceAtLeast(1), if (status.cleared) C.Good else C.Glow)
            Spacer(Modifier.height(4.dp))
            Text("Min passes left this week: ${Game.passesLeft(data, date)}/${Program.MIN_PASSES_PER_WEEK}", color = C.Muted, fontSize = 11.sp)
            if (isToday && !status.cleared && !status.sick) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "⚠ WARNING: Failing to clear the daily quest brings a penalty tomorrow.",
                    color = C.Danger, fontSize = 11.sp, fontFamily = Mono,
                )
            }
        }
        if (isToday) {
            Spacer(Modifier.height(6.dp))
            val left = s.challengeDays - Season.day(s, today) + 1
            Text(
                (if (s.challengeDays > 0 && left > 0) "⏳ $left day${if (left == 1) "" else "s"} left in your challenge · " else "") + intensityName(s.intensity),
                color = C.Gold, fontSize = 11.sp, fontFamily = Mono, letterSpacing = 1.sp,
            )
        }
    }
}

@Composable
private fun NotesPanel(notes: List<String>) {
    var open by remember { mutableStateOf(true) }
    Panel(accent = C.Gold) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            SysLabel("[System] Schedule recalculated", C.Gold)
            Spacer(Modifier.weight(1f))
            Text(if (open) "▴" else "▾", color = C.Muted)
        }
        if (open) {
            Spacer(Modifier.height(4.dp))
            notes.forEach { Text("• $it", color = C.Text2, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun XpPop(text: String, modifier: Modifier) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, tween(1300)) }
    if (a.value < 1f) {
        Text(
            text,
            color = C.Gold, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 22.sp,
            modifier = modifier.offset(y = (-60 * a.value).dp).alpha(1f - a.value * a.value),
        )
    }
}

@Composable
private fun AllDayBox(quests: List<Quest>, rec: DayRecord, locked: Boolean, act: QuestActions, modifier: Modifier) {
    Panel(modifier) {
        SysLabel("All day")
        quests.forEach { q ->
            val v = rec.amounts[q.id] ?: 0.0
            val r = Game.ratio(q, rec)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(q.icon, fontSize = 18.sp, modifier = Modifier.width(30.dp))
                Column(Modifier.weight(1f).clickable(enabled = !locked) { act.askAmount(q) }) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(q.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            "  ${fmtAmount(v, q.unit)} / ${fmtAmount(q.target, q.unit)} ${q.unit}",
                            color = if (r >= 1f) C.Good else C.Text2, fontFamily = Mono, fontSize = 12.sp,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    XpBar(r, if (r >= 1f) C.Good else C.Glow, height = 4.dp)
                }
                Spacer(Modifier.width(8.dp))
                SysButton("−", small = true, enabled = !locked && v > 0, color = C.Muted, onClick = { act.amount(q, (v - q.step).coerceAtLeast(0.0)) })
                Spacer(Modifier.width(6.dp))
                SysButton("+${fmtAmount(q.step, q.unit)}", small = true, enabled = !locked, onClick = { act.amount(q, v + q.step) })
            }
        }
    }
}

/** Every card that's due, side by side. Swipe to see the stack; quick ones can be done from any card. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeckSection(
    cards: List<Block>,
    date: LocalDate,
    rec: DayRecord,
    lesson: DayLesson?,
    passesLeft: Int,
    now: LocalTime,
    act: QuestActions,
) {
    if (cards.isEmpty()) {
        Panel(accent = C.Good) {
            SysLabel("Now", C.Good)
            Spacer(Modifier.height(4.dp))
            Text("Nothing due right now ✓", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text("New cards appear when their time comes.", color = C.Muted, fontSize = 13.sp)
        }
        return
    }
    val pager = rememberPagerState(pageCount = { cards.size })
    Column {
        HorizontalPager(
            state = pager,
            pageSpacing = 12.dp,
            verticalAlignment = Alignment.Top,
            key = { i -> cards.getOrNull(i)?.quest?.id ?: i },
        ) { page ->
            cards.getOrNull(page)?.let { DeckCard(it, date, rec, lesson, passesLeft, now, act) }
        }
        if (cards.size > 1) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                cards.indices.forEach { i ->
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (i == pager.currentPage) 9.dp else 6.dp)
                            .background(if (i == pager.currentPage) C.Glow else C.Border, CircleShape),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Swipe ‹ › · ${pager.currentPage + 1} of ${cards.size} due now",
                color = C.Muted, fontSize = 11.sp, fontFamily = Mono,
                modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

/** "⏳ 23 min left" or "Overdue 12 min", from the card's time (or when it came back from Later). */
private fun timeLeft(b: Block, rec: DayRecord, now: LocalTime): Pair<String, Color>? {
    val q = b.quest ?: return null
    val limit = q.limitMin ?: return null
    val base = rec.deferred[q.id]?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime() } ?: b.time
    val deadline = base.plusMinutes(limit.toLong())
    if (deadline.isBefore(base)) return null
    val left = Duration.between(now, deadline).toMinutes()
    return when {
        left >= 0 -> "⏳ $left min left" to (if (left <= 10) C.Danger else C.Muted)
        else -> "Overdue ${-left} min" to C.Danger
    }
}

@Composable
private fun DeckCard(b: Block, date: LocalDate, rec: DayRecord, lesson: DayLesson?, passesLeft: Int, now: LocalTime, act: QuestActions) {
    val q = b.quest ?: return
    val accent = when {
        q.expires -> C.Gold
        q.optional -> C.Good
        else -> C.Glow
    }
    SystemWindow(accent = accent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SysLabel(
                when {
                    q.expires -> "⚡ Emergency quest"
                    rec.deferred.containsKey(q.id) -> "Now · moved from ${fmt(b.time)}"
                    else -> "Now · ${fmt(b.time)}"
                },
                accent,
            )
            Spacer(Modifier.weight(1f))
            Text("+${q.xp} ${q.stat.label}", color = C.Glow, fontFamily = Mono, fontSize = 12.sp)
        }
        Row {
            if (q.optional) Text("BONUS · doesn't count against you  ", color = C.Good, fontSize = 10.sp, fontFamily = Mono)
            Spacer(Modifier.weight(1f))
            timeLeft(b, rec, now)?.let { (t, c) -> Text(t, color = c, fontSize = 11.sp, fontFamily = Mono) }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Top) {
            Text(q.icon, fontSize = 30.sp, modifier = Modifier.width(46.dp))
            Column(Modifier.weight(1f)) {
                Text(q.title, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text(q.full, color = C.Text2, fontSize = 14.sp)
            }
        }
        if (q.kind == Kind.ROUTINE) {
            Spacer(Modifier.height(10.dp))
            RoutinePreview(q, rec)
        }
        if (q.id == "learn") {
            Spacer(Modifier.height(10.dp))
            LessonInfo(lesson, rec, false, act)
        }
        if (q.id.startsWith("sk_") || q.id.startsWith("bc_")) {
            Spacer(Modifier.height(10.dp))
            ChallengeLinks(q.id)
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            PrimaryActions(q, rec, passesLeft, false, act)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            if (!Slots.fixed(q.id)) SysButton("Later", small = true, color = C.Muted, onClick = { act.later(q) })
            SysButton(
                when {
                    q.id.startsWith("wc_") -> "I slipped"
                    q.optional -> "Pass"
                    else -> "Not today"
                },
                small = true, color = C.Danger, onClick = { act.skip(q) },
            )
        }
        if (q.min != null) {
            Spacer(Modifier.height(6.dp))
            Text("Min: ${q.min} (20% XP, uses 1 of your weekly passes)", color = C.Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun RoutinePreview(q: Quest, rec: DayRecord) {
    val need = q.items.filter { !it.optional }
    val done = need.count { it.id in rec.routine }
    Column(
        Modifier.fillMaxWidth().background(C.Bg.copy(alpha = 0.6f), RoundedCornerShape(10.dp)).padding(10.dp),
    ) {
        Text("$done / ${need.size} done", color = if (done == need.size) C.Good else C.Gold, fontFamily = Mono, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        XpBar(if (need.isEmpty()) 0f else done.toFloat() / need.size, C.Good, height = 4.dp)
        Spacer(Modifier.height(6.dp))
        Text(q.items.joinToString("  ") { (if (it.id in rec.routine) "✓" else it.icon) }, fontSize = 16.sp)
    }
}

@Composable
private fun PrimaryActions(q: Quest, rec: DayRecord, passesLeft: Int, locked: Boolean, act: QuestActions) {
    val en = !locked
    when (q.kind) {
        Kind.WAKE -> {
            SysButton("Set time", small = true, enabled = en, color = C.Muted, onClick = act.pickWake)
            SysButton(rec.wake?.let { "✓ Up at ${fmt(it)}" } ?: "I'M UP", enabled = en,
                selected = rec.wakeTime != null, color = C.Good, onClick = { if (rec.wakeTime == null) act.wake(LocalTime.now()) else act.pickWake() })
        }
        Kind.ROUTINE -> {
            val full = Game.ratio(q, rec) >= 1f
            SysButton(if (full) "✓ Complete" else "Open checklist ▶", enabled = en, selected = full, color = C.Good, onClick = { act.routine(q) })
        }
        Kind.CHECK -> {
            if (coachTarget(q) != null) SysButton("Coach ▶", enabled = en, onClick = { act.coach(q) })
            val state = rec.done[q.id]
            if (q.min != null) {
                SysButton(
                    if (state == Done.MIN) "✓ Min" else "Min",
                    enabled = en && (state == Done.MIN || passesLeft > 0), selected = state == Done.MIN, color = C.Gold,
                    onClick = { act.done(q, if (state == Done.MIN) null else Done.MIN) },
                )
            }
            SysButton(
                if (q.id.startsWith("wc_")) (if (state == Done.FULL) "✓ Kept it" else "Kept it today") else if (state == Done.FULL) "✓ Done" else "Done",
                enabled = en, selected = state == Done.FULL, color = C.Good,
                onClick = { act.done(q, if (state == Done.FULL) null else Done.FULL) },
            )
        }
        Kind.AMOUNT -> {
            if (coachTarget(q) != null) SysButton(if (q.id == "mobility" || q.id == "stretch") "Timer ▶" else "Coach ▶", enabled = en, onClick = { act.coach(q) })
            SysButton("Log ${q.unit}", enabled = en, color = C.Gold, onClick = { act.askAmount(q) })
            val full = Game.ratio(q, rec) >= 1f
            SysButton(if (full) "✓ Done" else "Done", enabled = en, selected = full, color = C.Good,
                onClick = { act.amount(q, if (full) 0.0 else q.target) })
        }
    }
}

@Composable
private fun LessonInfo(lesson: DayLesson?, rec: DayRecord, locked: Boolean, act: QuestActions) {
    val uri = LocalUriHandler.current
    if (lesson == null || lesson.lessons.isEmpty()) {
        Text("Pick anything you're curious about today.", color = C.Muted, fontSize = 13.sp)
        return
    }
    val label = if (lesson.topic == Topic.WILDCARD) "⚡ Wildcard lesson" else "${lesson.topic.icon} ${lesson.topic.label}"
    Column(
        Modifier
            .fillMaxWidth()
            .background(C.Bg.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SysLabel(label, C.Gold)
        lesson.lessons.forEachIndexed { i, l ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text((if (lesson.lessons.size > 1) "${i + 1}. " else "") + l.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text("${l.source} · ~${l.minutes} min", color = C.Muted, fontSize = 12.sp)
                }
                SysButton("▶ Open", small = true, onClick = { runCatching { uri.openUri(l.url) } })
            }
            Text("Do: ${l.task}", color = C.Text2, fontSize = 12.sp)
        }
        val notes = rec.done.containsKey("notes")
        Row(
            Modifier.fillMaxWidth().clickable(enabled = !locked) { act.notes(!notes) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (notes) "☑" else "☐", color = if (notes) C.Good else C.Muted, fontSize = 18.sp, modifier = Modifier.width(28.dp))
            Text("Notes written in my notebook  (+${Game.NOTES_XP} INT)", color = if (notes) C.Good else C.Text2, fontSize = 13.sp)
        }
        val recalled = rec.done.containsKey("recall")
        SysButton(
            if (recalled) "✓ Recall answered · redo" else "🎤 Recall: answer out loud (+${Game.NOTES_XP} INT)",
            onClick = act.recall, enabled = !locked, small = true, color = if (recalled) C.Good else C.Glow,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SettledList(title: String, quests: List<Quest>, rec: DayRecord, act: QuestActions, color: Color, startOpen: Boolean = false) {
    var open by remember { mutableStateOf(startOpen) }
    Panel(accent = C.Border) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            Text("$title (${quests.size})", color = color, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(if (open) "▴" else "▾", color = C.Muted)
        }
        if (open) {
            quests.forEach { q ->
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(q.icon, modifier = Modifier.width(28.dp))
                    Column(Modifier.weight(1f)) {
                        Text(q.title, fontSize = 14.sp, color = C.Text2)
                        val detail = rec.skipped[q.id]?.let { "${it.icon} ${it.label}" } ?: "+${Game.xpFor(q, rec)} XP"
                        Text(detail, color = C.Muted, fontSize = 12.sp)
                    }
                    if (q.kind == Kind.ROUTINE) SysButton("Open", small = true, color = C.Muted, onClick = { act.routine(q) })
                    Spacer(Modifier.width(6.dp))
                    SysButton("Undo", small = true, color = C.Muted, onClick = { act.undo(q) })
                }
            }
        }
    }
}

@Composable
private fun FullDayRow(
    b: Block,
    rec: DayRecord,
    locked: Boolean,
    isNext: Boolean,
    lesson: DayLesson?,
    passesLeft: Int,
    act: QuestActions,
) {
    val q = b.quest
    if (q == null) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp)) {
            Text(fmt(b.time), color = C.Muted, fontFamily = Mono, fontSize = 12.sp, modifier = Modifier.width(72.dp))
            Column {
                Text(b.title, color = C.Text2, fontSize = 13.sp)
                if (b.detail.isNotEmpty()) Text(b.detail, color = C.Muted, fontSize = 11.sp)
            }
        }
        return
    }
    var open by remember(q.id) { mutableStateOf(false) }
    val r = Game.ratio(q, rec)
    val skipped = rec.skipped[q.id]
    val accent = when {
        skipped != null -> C.Muted.copy(alpha = 0.4f)
        r >= 1f -> C.Good
        r > 0f -> C.Gold
        isNext -> C.Glow
        else -> C.Border
    }
    Panel(accent = accent) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            Text(fmt(b.time), color = C.Muted, fontFamily = Mono, fontSize = 12.sp, modifier = Modifier.width(72.dp))
            Text(q.icon, modifier = Modifier.width(28.dp))
            Text(
                q.title + if (q.optional) " ·bonus" else "",
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = if (r >= 1f || skipped != null) C.Text2 else C.Text,
                textDecoration = if (r >= 1f) TextDecoration.LineThrough else TextDecoration.None,
                modifier = Modifier.weight(1f),
            )
            val chip = when {
                locked -> "🔒"
                skipped != null -> "${skipped.icon} skipped"
                q.kind == Kind.WAKE -> rec.wake?.let { fmt(it) } ?: "—"
                q.kind == Kind.ROUTINE -> {
                    val need = q.items.filter { !it.optional }
                    "${need.count { it.id in rec.routine }}/${need.size}"
                }
                q.kind == Kind.AMOUNT -> "${fmtAmount(rec.amounts[q.id] ?: 0.0, q.unit)}/${fmtAmount(q.target, q.unit)}"
                rec.done[q.id] == Done.FULL -> "✓"
                rec.done[q.id] == Done.MIN -> "Min"
                else -> ""
            }
            Text(chip, color = accent, fontFamily = Mono, fontSize = 12.sp)
            Text(if (open) "  ▴" else "  ▾", color = C.Muted)
        }
        if (open) {
            Spacer(Modifier.height(6.dp))
            Text(q.full, color = C.Text2, fontSize = 13.sp)
            if (q.kind == Kind.ROUTINE) {
                Spacer(Modifier.height(8.dp))
                RoutinePreview(q, rec)
            }
            if (q.id == "learn") {
                Spacer(Modifier.height(8.dp))
                LessonInfo(lesson, rec, locked, act)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                if (skipped != null && !locked) SysButton("Undo skip", small = true, color = C.Muted, onClick = { act.undo(q) })
                PrimaryActions(q, rec, passesLeft, locked, act)
            }
        }
    }
}

/** Daily knowledge as a quiz: guess first, then reveal. */
@Composable
private fun KnowledgeCard(card: com.arise.app.game.Card) {
    var revealed by remember(card) { mutableStateOf(false) }
    Panel(accent = C.Gold) {
        SysLabel("Daily knowledge · ${card.field}", C.Gold)
        Spacer(Modifier.height(6.dp))
        Text(card.question, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        if (!revealed) {
            Text("Guess first, out loud. Then reveal.", color = C.Muted, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            SysButton("Reveal the answer ›", onClick = { revealed = true }, color = C.Gold, small = true)
        } else {
            Text(card.answer, color = C.Gold, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(card.story, color = C.Text2, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Text("Tell someone this today. Explaining it is how it sticks.", color = C.Muted, fontSize = 11.sp)
        }
    }
}

/** The routine checklist: big rows, tap to tick. Closes itself when everything required is done. */
@Composable
private fun RoutineDialog(q: Quest, rec: DayRecord, onToggle: (String) -> Unit, onClose: () -> Unit) {
    val need = q.items.filter { !it.optional }
    val done = need.count { it.id in rec.routine }
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = C.Panel,
        title = {
            Column {
                SysLabel("[System] ${q.title}", if (q.id == "night") C.Glow else C.Gold)
                Spacer(Modifier.height(4.dp))
                Text("$done / ${need.size}", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 26.sp)
                Spacer(Modifier.height(6.dp))
                XpBar(if (need.isEmpty()) 0f else done.toFloat() / need.size, C.Good)
            }
        },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                q.items.forEach { item ->
                    val on = item.id in rec.routine
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(if (on) C.Good.copy(alpha = 0.12f) else C.Bg.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .clickable { onToggle(item.id) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(item.icon, fontSize = 22.sp, modifier = Modifier.width(36.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.label, fontSize = 15.sp,
                                color = if (on) C.Text2 else C.Text,
                                textDecoration = if (on) TextDecoration.LineThrough else TextDecoration.None,
                            )
                            if (item.optional) Text("optional", color = C.Muted, fontSize = 11.sp)
                        }
                        Text(if (on) "☑" else "☐", color = if (on) C.Good else C.Muted, fontSize = 22.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close", color = C.Muted) } },
    )
}

/**
 * After a lesson: answer 1–2 questions out loud. The phone's speech recognition writes down what you
 * say (tap 🎤 again to add more), or you can type.
 */
@Composable
private fun RecallDialog(
    questions: List<Pair<String, String?>>,
    existing: Map<String, String>,
    onSave: (Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val answers = remember { mutableStateMapOf<String, String>().apply { questions.forEach { (q, _) -> existing[q]?.let { put(q, it) } } } }
    var listeningFor by remember { mutableStateOf<String?>(null) }
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val heard = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        val q = listeningFor
        if (q != null && !heard.isNullOrBlank()) answers[q] = ((answers[q] ?: "") + " " + heard).trim()
        listeningFor = null
    }
    fun listen(q: String) {
        listeningFor = q
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, q)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
        try {
            speech.launch(intent)
        } catch (e: ActivityNotFoundException) {
            listeningFor = null
            Toast.makeText(ctx, "Speech input isn't available on this phone. Type your answer instead.", Toast.LENGTH_LONG).show()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Panel,
        title = {
            Column {
                SysLabel("[System] Recall check")
                Spacer(Modifier.height(4.dp))
                Text("Answer out loud, like you're teaching it.", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                questions.forEachIndexed { i, (q, _) ->
                    Column {
                        Text("${i + 1}. $q", color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        SysButton(
                            if (answers[q].isNullOrBlank()) "🎤 Speak" else "🎤 Add more",
                            onClick = { listen(q) }, small = true, selected = true,
                        )
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = answers[q] ?: "",
                            onValueChange = { answers[q] = it },
                            placeholder = { Text("…or type here", color = C.Muted, fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = C.Glow, unfocusedBorderColor = C.Border, cursorColor = C.Glow,
                                focusedTextColor = C.Text, unfocusedTextColor = C.Text,
                            ),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(answers.filterValues { it.isNotBlank() }.toMap()) }) { Text("Save", color = C.Glow) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Later", color = C.Muted) } },
    )
}

@Composable
private fun AmountDialog(q: Quest, current: Double, onSave: (Double) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(if (current > 0) fmtAmount(current, q.unit).replace(",", "") else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Panel,
        title = { Text("${q.icon} ${q.title}") },
        text = {
            Column {
                Text("Target: ${fmtAmount(q.target, q.unit)} ${q.unit}. Partial amounts earn partial XP.", color = C.Text2, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text("How much (${q.unit})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.replace(",", "").replace(" ", "").toDoubleOrNull() ?: 0.0) }) {
                Text("Save", color = C.Glow)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = C.Muted) } },
    )
}

@Composable
private fun LaterPile(snoozed: List<Pair<Block, Long>>, act: QuestActions) {
    val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    Panel(accent = C.Border) {
        Text("⏸ Later (${snoozed.size})", color = C.Text2, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        snoozed.forEach { (b, until) ->
            val q = b.quest ?: return@forEach
            val back = Instant.ofEpochMilli(until).atZone(ZoneId.systemDefault()).toLocalTime()
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(q.icon, modifier = Modifier.width(28.dp))
                Column(Modifier.weight(1f)) {
                    Text(q.title, fontSize = 14.sp)
                    Text("Back at ${back.format(timeFmt)}", color = C.Muted, fontSize = 12.sp)
                }
                SysButton("Bring back", small = true, onClick = { act.unsnooze(q) })
            }
        }
    }
}

/** Skill quests get their apps, video and this week's goal; body quests their follow-along video. */
@Composable
private fun ChallengeLinks(questId: String) {
    val uri = LocalUriHandler.current
    val id = questId.drop(3)
    Column(
        Modifier.fillMaxWidth().background(C.Bg.copy(alpha = 0.6f), RoundedCornerShape(10.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (questId.startsWith("sk_")) {
            val k = Challenges.skillById(id) ?: return@Column
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                k.resources.take(2).forEach { r ->
                    SysButton((if (r.isApp) "📲 " else "🌐 ") + r.name, small = true, onClick = { runCatching { uri.openUri(r.url) } })
                }
            }
            SysButton("▶ Video lessons", small = true, color = C.Muted, onClick = { runCatching { uri.openUri(k.video) } })
        } else {
            val b = Challenges.bodyById(id) ?: return@Column
            SysButton("▶ Follow-along video", small = true, color = C.Muted, onClick = { runCatching { uri.openUri(b.video) } })
            b.honest?.let { Text(it, color = C.Gold, fontSize = 11.sp) }
        }
    }
}
