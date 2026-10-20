package com.arise.app.ui

import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.arise.app.game.Deck
import com.arise.app.game.DayLesson
import com.arise.app.game.Game
import com.arise.app.game.Kind
import com.arise.app.game.Knowledge
import com.arise.app.game.Program
import com.arise.app.game.Progress
import com.arise.app.game.Quest
import com.arise.app.game.Slots
import com.arise.app.game.Topic
import com.arise.app.game.fmt
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

private val dayFmt = DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.ENGLISH)

/** Train tab targets. */
object TrainTarget {
    const val RUN = 0
    const val STRENGTH = 1
    const val MOBILITY = 2
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
)

fun fmtAmount(v: Double, unit: String): String = when (unit) {
    "L" -> if (v % 1.0 == 0.0) v.toInt().toString() else "%.2f".format(Locale.ENGLISH, v).trimEnd('0').trimEnd('.')
    "steps" -> "%,d".format(Locale.ENGLISH, v.toInt())
    else -> v.toInt().toString()
}

private fun coachTarget(q: Quest): Int? = when (q.id) {
    "run" -> TrainTarget.RUN
    "strength" -> TrainTarget.STRENGTH
    "mobility", "stretch" -> TrainTarget.MOBILITY
    else -> null
}

@Composable
fun TodayScreen(
    data: AppData,
    today: LocalDate,
    now: LocalTime,
    progress: Progress,
    onUpdate: (AppData) -> Unit,
    openTrain: (target: Int, autoStartFlow: String?) -> Unit,
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

    var showFull by rememberSaveable { mutableStateOf(false) }
    var amountFor by remember { mutableStateOf<Quest?>(null) }
    var skipFor by remember { mutableStateOf<Quest?>(null) }
    var laterFor by remember { mutableStateOf<Quest?>(null) }
    var pop by remember { mutableStateOf<Pair<String, Long>?>(null) }

    fun save(r: DayRecord) = onUpdate(data.with(r))
    fun saveWithPop(q: Quest, r: DayRecord) {
        val gained = Game.xpFor(q, r) - Game.xpFor(q, rec)
        if (gained > 0) {
            pop = "+$gained ${q.stat.label} XP" to System.currentTimeMillis()
            Cues.tap(ctx)
        }
        save(r)
    }

    val act = QuestActions(
        done = { q, d ->
            saveWithPop(q, rec.copy(done = if (d == null) rec.done - q.id else rec.done + (q.id to d), skipped = rec.skipped - q.id))
        },
        amount = { q, v ->
            saveWithPop(q, rec.copy(amounts = if (v <= 0.0) rec.amounts - q.id else rec.amounts + (q.id to v), skipped = rec.skipped - q.id))
        },
        askAmount = { q -> amountFor = q },
        wake = { t ->
            val q = plan.quests.first { it.kind == Kind.WAKE }
            saveWithPop(q, rec.copy(wakeTime = "%02d:%02d".format(t.hour, t.minute)))
        },
        pickWake = {
            val q = plan.quests.first { it.kind == Kind.WAKE }
            val init = rec.wakeTime?.let(LocalTime::parse) ?: now
            TimePickerDialog(ctx, { _, h, m ->
                saveWithPop(q, rec.copy(wakeTime = "%02d:%02d".format(h, m)))
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
                ),
            )
        },
        notes = { on -> save(rec.copy(done = if (on) rec.done + ("notes" to Done.FULL) else rec.done - "notes")) },
        coach = { q ->
            val t = coachTarget(q)
            if (t != null) openTrain(t, if (t == TrainTarget.MOBILITY) q.id else null)
        },
    )

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(Modifier.tutorialTarget("header")) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SysButton("‹", onClick = { date = date.minusDays(1) }, enabled = date > s.earliest)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            SysLabel(if (locked) "[System] Upcoming · locked" else "[System] Daily quests")
                            Text(date.format(dayFmt), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "Week ${Program.week(s, date)} · ${plan.type.icon} ${plan.type.label} day" + when {
                                    date < s.start -> " · fill-in day"
                                    !isToday && !locked -> " · editing a past day"
                                    else -> ""
                                },
                                color = C.Muted, fontSize = 13.sp,
                            )
                        }
                        SysButton("›", onClick = { date = date.plusDays(1) }, enabled = date < today.plusDays(7))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Lv ${progress.level}", fontFamily = Mono, fontWeight = FontWeight.Bold, color = C.Glow)
                        Text(" · ${progress.rank}-Rank", fontFamily = Mono, color = C.Text2, fontSize = 13.sp)
                        Spacer(Modifier.width(10.dp))
                        Box(Modifier.weight(1f)) { XpBar(progress.xpInLevel.toFloat() / progress.xpLevelSpan, height = 6.dp) }
                        Spacer(Modifier.width(10.dp))
                        Text("🔥 ${progress.streak}", fontFamily = Mono, color = C.Gold)
                    }
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

            if (!locked) {
                item {
                    val need = ceil(Game.CLEAR_RATIO * status.total).toInt()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${status.doneCount}/${status.total}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            when {
                                status.sick -> "  · sick day: rest, it won't break your streak"
                                status.cleared -> "  · day cleared ✓"
                                else -> "  · clear $need to keep the streak"
                            },
                            color = if (status.cleared) C.Good else C.Text2, fontSize = 13.sp,
                        )
                        Spacer(Modifier.weight(1f))
                        Text("+${status.xp} XP", color = C.Glow, fontFamily = Mono, fontWeight = FontWeight.Bold)
                    }
                    Text("Min passes left this week: $passesLeft/${Program.MIN_PASSES_PER_WEEK}", color = C.Muted, fontSize = 12.sp)
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

            item {
                AllDayBox(plan.allDay, rec, locked, act, Modifier.tutorialTarget("allday"))
            }

            if (deckMode && !showFull) {
                val cards = Deck.cards(data, date, now)
                val snoozed = Deck.snoozed(data, date, now)
                item {
                    Box(Modifier.tutorialTarget("deck")) {
                        DeckSection(cards, rec, lesson, passesLeft, act)
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
                val settledQs = plan.blocks.mapNotNull { it.quest }.filter { Game.settled(it, rec) }
                val doneQs = settledQs.filter { !rec.skipped.containsKey(it.id) }
                val skippedQs = settledQs.filter { rec.skipped.containsKey(it.id) }
                if (doneQs.isNotEmpty()) {
                    item { SettledList("✓ Done today", doneQs, rec, act, C.Good) }
                }
                if (skippedQs.isNotEmpty()) {
                    item { SettledList("✗ Not today", skippedQs, rec, act, C.Muted) }
                }
                item {
                    SysButton("See full day", onClick = { showFull = true }, small = true, modifier = Modifier.tutorialTarget("fullday"))
                }
            } else {
                if (deckMode) {
                    item {
                        SysButton("‹ Back to cards", onClick = { showFull = false }, small = true, modifier = Modifier.tutorialTarget("fullday"))
                    }
                }
                if (locked) {
                    item {
                        Text(
                            "🔒 Unlocks on ${date.format(dayFmt)}. You can look, but not tick.",
                            color = C.Muted, fontSize = 13.sp,
                        )
                    }
                }
                val nextIdx = if (isToday) plan.blocks.indexOfFirst { it.time > now } else -1
                items(plan.blocks, key = { it.time.toString() + it.title }) { b ->
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
                    Text("Why? It helps the System spot patterns.", color = C.Text2, fontSize = 13.sp)
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

@Composable
private fun DeckSection(cards: List<Block>, rec: DayRecord, lesson: DayLesson?, passesLeft: Int, act: QuestActions) {
    if (cards.isEmpty()) {
        Panel(accent = C.Good) {
            SysLabel("Now", C.Good)
            Spacer(Modifier.height(4.dp))
            Text("Nothing due right now ✓", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text("New cards appear when their time comes.", color = C.Muted, fontSize = 13.sp)
        }
        return
    }
    Column {
        AnimatedContent(
            targetState = cards.first(),
            transitionSpec = {
                (slideInHorizontally { it / 2 } + fadeIn(tween(250))) togetherWith (slideOutHorizontally { -it } + fadeOut(tween(200)))
            },
            contentKey = { it.quest?.id },
            label = "deck",
        ) { b ->
            DeckCard(b, rec, lesson, passesLeft, act)
        }
        if (cards.size > 1) {
            // The cards waiting behind, drawn as thin edges.
            repeat(minOf(cards.size - 1, 3)) { i ->
                Box(
                    Modifier
                        .padding(horizontal = (14 + i * 10).dp)
                        .fillMaxWidth()
                        .height(5.dp)
                        .background(C.Border.copy(alpha = 0.8f - i * 0.2f), RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp)),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${cards.size - 1} more in the deck: " + cards.drop(1).joinToString(" · ") { it.quest!!.title },
                color = C.Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun DeckCard(b: Block, rec: DayRecord, lesson: DayLesson?, passesLeft: Int, act: QuestActions) {
    val q = b.quest ?: return
    SystemWindow {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SysLabel(if (rec.deferred.containsKey(q.id)) "Now · moved from ${fmt(b.time)}" else "Now · ${fmt(b.time)}")
            Spacer(Modifier.weight(1f))
            Text("+${q.xp} ${q.stat.label}", color = C.Glow, fontFamily = Mono, fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Top) {
            Text(q.icon, fontSize = 30.sp, modifier = Modifier.width(46.dp))
            Column(Modifier.weight(1f)) {
                Text(q.title, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text(q.full, color = C.Text2, fontSize = 14.sp)
            }
        }
        if (q.id == "learn") {
            Spacer(Modifier.height(10.dp))
            LessonInfo(lesson, rec, false, act)
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            PrimaryActions(q, rec, passesLeft, false, act)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            if (q.id !in Slots.FIXED) SysButton("Later", small = true, color = C.Muted, onClick = { act.later(q) })
            SysButton("Not today", small = true, color = C.Danger, onClick = { act.skip(q) })
        }
        if (q.min != null) {
            Spacer(Modifier.height(6.dp))
            Text("Min: ${q.min} (20% XP, uses 1 of your weekly passes)", color = C.Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun PrimaryActions(q: Quest, rec: DayRecord, passesLeft: Int, locked: Boolean, act: QuestActions) {
    val en = !locked
    when (q.kind) {
        Kind.WAKE -> {
            SysButton("Set time", small = true, enabled = en, color = C.Muted, onClick = act.pickWake)
            SysButton(rec.wakeTime?.let { "✓ Up at ${fmt(LocalTime.parse(it))}" } ?: "I'm up", enabled = en,
                selected = rec.wakeTime != null, color = C.Good, onClick = { act.wake(LocalTime.now()) })
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
                if (state == Done.FULL) "✓ Done" else "Done",
                enabled = en, selected = state == Done.FULL, color = C.Good,
                onClick = { act.done(q, if (state == Done.FULL) null else Done.FULL) },
            )
        }
        Kind.AMOUNT -> {
            if (coachTarget(q) != null) SysButton(if (q.id == "run") "Coach ▶" else "Timer ▶", enabled = en, onClick = { act.coach(q) })
            SysButton("Log ${q.unit}", enabled = en, color = C.Gold, onClick = { act.askAmount(q) })
            val full = (rec.amounts[q.id] ?: 0.0) >= q.target
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
    }
}

@Composable
private fun SettledList(title: String, quests: List<Quest>, rec: DayRecord, act: QuestActions, color: Color) {
    var open by remember { mutableStateOf(false) }
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
                q.title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = if (r >= 1f || skipped != null) C.Text2 else C.Text,
                textDecoration = if (r >= 1f) TextDecoration.LineThrough else TextDecoration.None,
                modifier = Modifier.weight(1f),
            )
            val chip = when {
                locked -> "🔒"
                skipped != null -> "${skipped.icon} skipped"
                q.kind == Kind.WAKE -> rec.wakeTime?.let { fmt(LocalTime.parse(it)) } ?: "—"
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

@Composable
private fun KnowledgeCard(card: com.arise.app.game.Card) {
    var open by remember { mutableStateOf(false) }
    Panel {
        Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SysLabel("Knowledge card · ${card.field}", C.Gold)
                Text(card.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(if (open) "▴" else "▾", color = C.Muted)
        }
        if (open) {
            Spacer(Modifier.height(6.dp))
            Text(card.hook, color = C.Text2, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Text("Go deeper: ${card.dig}", color = C.Muted, fontSize = 12.sp)
        }
    }
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
            val back = java.time.Instant.ofEpochMilli(until).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
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
