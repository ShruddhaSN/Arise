package com.arise.app.game

import com.arise.app.data.DayType
import com.arise.app.data.Event
import com.arise.app.data.Med
import com.arise.app.data.MedTime
import com.arise.app.data.RWhen
import com.arise.app.data.RoutineDef
import com.arise.app.data.Settings
import com.arise.app.data.Stat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

enum class Kind {
    /** Done / Min. */
    CHECK,
    /** Enter how much: litres, steps, minutes, sets. XP scales with the amount. */
    AMOUNT,
    /** "I'm up": scored by how close the actual wake time is to the goal. */
    WAKE,
    /** A checklist (morning / night routine). Optional; you tap "Done for today" when you're finished. */
    ROUTINE,
    /** Last night's sleep, detected (screen / Health Connect) or corrected by hand. [Quest.target] = goal minutes. */
    SLEEP,
}

data class RoutineItem(val id: String, val label: String, val icon: String, val optional: Boolean = false)

data class Quest(
    val id: String,
    val title: String,
    val full: String,
    /** Null means there is no minimum version. Only CHECK quests have one. */
    val min: String?,
    val stat: Stat,
    val xp: Int,
    val icon: String,
    val kind: Kind = Kind.CHECK,
    /** AMOUNT: the full target. WAKE: goal time as minutes after midnight. */
    val target: Double = 1.0,
    val unit: String = "",
    /** Quick-add step for AMOUNT quests. */
    val step: Double = 1.0,
    /** Bonus quest: earns XP but never counts against clearing the day. */
    val optional: Boolean = false,
    /** ROUTINE: the checklist. */
    val items: List<RoutineItem> = emptyList(),
    /** Soft time limit shown on the card, in minutes after the card's time. Null = none. */
    val limitMin: Int? = 60,
    /** Disappears when the time limit runs out (emergency quests). */
    val expires: Boolean = false,
)

enum class Special { NONE, CHECK_IN, RESCUE }

data class Block(
    val time: LocalTime,
    val title: String,
    val detail: String = "",
    val quest: Quest? = null,
    val notify: Boolean = true,
    val special: Special = Special.NONE,
    /** A reminder for this quest id: skipped once that quest is done. */
    val remindFor: String? = null,
)

/** What the plan needs beyond the settings: what actually happened today. */
data class PlanCtx(
    /** Actual wake time, once "I'm up" is tapped. The morning moves with it. */
    val wake: LocalTime? = null,
    val events: List<Event> = emptyList(),
    /** Tomorrow's day type, for "soak chia tonight". */
    val tomorrow: DayType = DayType.WEEKEND,
    /** Today's run / strength slot, from the weekly count (missed sessions move to the next free day). */
    val workout: Workout? = null,
    /** The day after a rest day: lighter targets. */
    val gentle: Boolean = false,
    /** To-do sessions and other blocks worked out from the rest of your data. */
    val extra: List<Block> = emptyList(),
)

data class DayPlan(
    val date: LocalDate,
    val type: DayType,
    val blocks: List<Block>,
    /** Quests that live in the all-day box, not the card deck. */
    val allDay: List<Quest>,
    /** What the System changed today and why ("Training moved to 5:30 PM"). */
    val notes: List<String> = emptyList(),
) {
    val quests: List<Quest> get() = allDay + blocks.mapNotNull { it.quest }
}

enum class Workout { RUN, STRENGTH, FIRST_WALK, NONE }

enum class SegKind(val label: String) {
    WARMUP("WARM-UP WALK"),
    RUN("RUN"),
    WALK("WALK"),
    COOLDOWN("COOL-DOWN WALK"),
}

data class Segment(val kind: SegKind, val sec: Int)

data class RunPlan(val week: Int, val runs: List<Int>, val walkSec: Int) {
    val segments: List<Segment>
        get() = buildList {
            add(Segment(SegKind.WARMUP, 300))
            runs.forEachIndexed { i, r ->
                add(Segment(SegKind.RUN, r))
                if (i < runs.lastIndex && walkSec > 0) add(Segment(SegKind.WALK, walkSec))
            }
            add(Segment(SegKind.COOLDOWN, 300))
        }
    val totalSec: Int get() = segments.sumOf { it.sec }
    val runSec: Int get() = runs.sum()
    val longestRun: Int get() = runs.max()
    val title: String
        get() = when {
            runs.size == 1 -> "${dur(runs[0])} non-stop"
            runs.distinct().size == 1 -> "${runs.size} × ${dur(runs[0])}"
            else -> runs.joinToString(" + ") { dur(it) }
        }
    val summary: String
        get() = if (runs.size == 1) "5 min walk, run ${dur(runs[0])}, 5 min walk"
        else "5 min walk, $title of running with ${dur(walkSec)} walks between, 5 min walk"
}

/**
 * [sec] > 0 means the move is timed (guided timer). [perSide] doubles it, left then right.
 * Strength moves with [sec] > 0 are holds (plank) and get a hold timer.
 */
data class Exercise(
    val name: String,
    val dose: String,
    val cue: String,
    val sec: Int = 0,
    val perSide: Boolean = false,
)

fun dur(sec: Int): String = when {
    sec < 60 -> "${sec}s"
    sec % 60 == 0 -> "${sec / 60} min"
    else -> "${sec / 60}m ${sec % 60}s"
}

private val clock = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
fun fmt(t: LocalTime): String = t.format(clock)

object Program {
    const val MIN_PASSES_PER_WEEK = 2

    /** Week 1 starts on Day 1. Filled-in days before Day 1 also count as week 1. */
    fun week(s: Settings, date: LocalDate): Int =
        (ChronoUnit.DAYS.between(s.start, date).coerceAtLeast(0) / 7).toInt() + 1

    /** Sunday-to-Saturday week, for minimum passes. */
    fun weekStart(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value % 7).toLong())

    fun defaultType(s: Settings, date: LocalDate): DayType = when (date.dayOfWeek) {
        in s.officeDays -> DayType.OFFICE
        in s.wfhDays -> DayType.WFH
        else -> DayType.WEEKEND
    }

    /**
     * Where each of the week's sessions would fall, spread out (runs and strength alternate). Index = day of the
     * program week (0..6). Game moves a missed one to the next free day.
     */
    fun weekSlots(s: Settings): List<Pair<Int, Workout>> {
        val runs = s.runsPerWeek.coerceIn(0, 7)
        val str = s.strengthPerWeek.coerceIn(0, 7 - runs)
        val n = runs + str
        if (n == 0) return emptyList()
        val order = mutableListOf<Workout>()
        var r = runs
        var k = str
        while (r + k > 0) {
            if (r > 0 && (r >= k || order.lastOrNull() == Workout.STRENGTH)) { order += Workout.RUN; r-- }
            else if (k > 0) { order += Workout.STRENGTH; k-- }
            else { order += Workout.RUN; r-- }
        }
        return order.mapIndexed { i, w -> (1 + i * 7 / n).coerceAtMost(6) to w }
    }

    /** Core quests: the only ones that count for being on track. Everything else is a side quest. */
    val CORE = setOf("wake", "sleep", "steps", "run", "strength")

    fun wakeGoal(s: Settings, type: DayType): LocalTime = when (type) {
        DayType.OFFICE -> s.wakeOffice
        DayType.WFH -> s.wakeWfh
        else -> s.wakeOff
    }

    // ---- Weekly progression ----

    fun learnMinutes(s: Settings, week: Int): Int {
        val base = Profile.learnBase(s)
        if (base != null) return base + 5 * (week - 1).coerceIn(0, 2)
        return when {
            week <= 1 -> 15
            week <= 3 -> 20
            else -> 30
        }
    }

    private fun waterLitres(week: Int): Double = if (week <= 2) 2.5 else 3.0

    /** Steps go up with the weeks, and with how hard you asked the System to push. */
    private fun stepGoal(s: Settings, week: Int): Double = when {
        week <= 2 -> 6000.0
        week <= 4 -> 7000.0
        else -> 8000.0
    } + 1000.0 * (s.intensity - 1).coerceIn(0, 2)

    fun isWeekend(type: DayType) = type == DayType.WEEKEND || type == DayType.OFF

    /** "Office" reads "College" for students, "WFH" reads "Online", and a day off is a "Home day" for anyone without work days. */
    fun dayLabel(s: Settings, type: DayType): String = when {
        type == DayType.OFFICE && s.student -> "College"
        type == DayType.WFH && s.student -> "Online class"
        type == DayType.WEEKEND && !s.works -> "Home day"
        else -> type.label
    }

    /** When you leave for and get back from the office or college. */
    fun leaveTime(s: Settings): LocalTime = minus(s.workStart, s.commuteMin.toLong())

    fun homeTime(s: Settings): LocalTime = capPlus(s.workEnd, s.commuteMin.toLong())

    // ---- Quests ----

    private fun rise(goal: LocalTime) = Quest(
        "wake", "Rise", "Goal ${fmt(goal)}. Tap \"I'm up\" the moment you're out of bed. Your morning routine unlocks and the day re-plans around you.",
        null, Stat.VIT, 30, "🌅", Kind.WAKE, target = (goal.toSecondOfDay() / 60).toDouble(), limitMin = null,
    )

    private val phoneFree = Quest(
        "phonefree", "Phone-free first 30 min", "No Instagram or feeds for 30 min after waking. Morning routine first.",
        null, Stat.INT, 20, "📵", limitMin = 30,
    )

    private fun applies(r: RoutineDef, date: LocalDate, type: DayType, tomorrow: DayType): Boolean = when (r.`when`) {
        RWhen.DAILY -> true
        RWhen.WORK -> type == DayType.OFFICE
        RWhen.HOME -> type == DayType.WFH || isWeekend(type)
        RWhen.BEFORE_HOME -> tomorrow == DayType.WFH || isWeekend(tomorrow)
        RWhen.WEEKDAY -> r.day == date.dayOfWeek
    }

    /** "Serum / Moisturizer" becomes one of the two, picked fresh each day. */
    private fun itemLabel(r: RoutineDef, date: LocalDate): String {
        if (!r.alternate) return r.label
        val options = r.label.split("/").map { it.trim() }.filter { it.isNotEmpty() }
        if (options.size < 2) return r.label
        val pick = options[java.util.Random(date.toEpochDay() * 17L + r.id.hashCode()).nextInt(options.size)]
        return "$pick (today's pick)"
    }

    private fun routine(id: String, title: String, icon: String, defs: List<RoutineDef>, date: LocalDate, type: DayType, tomorrow: DayType, limit: Int): Quest? {
        val items = defs.filter { applies(it, date, type, tomorrow) }
            .map { RoutineItem(it.id, itemLabel(it, date), it.icon, it.optional) }
        if (items.isEmpty()) return null
        return Quest(
            id, title, "A checklist of what you did. Nothing here is compulsory. Tap \"Done for today\" whenever you're finished.",
            null, Stat.VIT, 20, icon, Kind.ROUTINE, items = items, limitMin = null, optional = true,
        )
    }

    private fun sleep(s: Settings) = Quest(
        "sleep", "Sleep", "Detected from your phone overnight, or from Samsung Health. Goal ${s.sleepGoalMin / 60}h${if (s.sleepGoalMin % 60 > 0) " ${s.sleepGoalMin % 60}m" else ""}, asleep by ${fmt(s.bedGoal)}. Tap to correct it.",
        null, Stat.VIT, 40, "😴", Kind.SLEEP, target = s.sleepGoalMin.toDouble(), unit = "min", limitMin = null,
    )

    /** One learning side quest, open all day. Counts once you've answered its recall question. */
    private val learn = Quest(
        "learn", "Learn something", "Today's pick: a short video or a few minutes in a free app. Then answer one question out loud. Side quest.",
        null, Stat.INT, 40, "📚", limitMin = null, optional = true,
    )

    private fun med(i: Int, m: Med) = Quest(
        "med_$i", m.name, "${m.at.label}. Every day, same time.", null, Stat.VIT, 10, "💊", limitMin = 60,
    )

    private fun water(week: Int) = Quest(
        "water", "Water", "Drink across the day, not all at once", null, Stat.VIT, 20, "💧",
        Kind.AMOUNT, target = waterLitres(week), unit = "L", step = 0.25, limitMin = null,
    )

    private fun steps(s: Settings, week: Int, gentle: Boolean) = Quest(
        "steps", "Steps", (if (s.healthSync) "Synced from Samsung Health." else "Type your step count from your phone's health app.") +
            (if (gentle) " Gentle day: lower goal." else "") + " Walks after meals count here.", null, Stat.AGI, 40, "👣",
        Kind.AMOUNT, target = (stepGoal(s, week) * if (gentle) 0.75 else 1.0).let { Math.round(it / 500.0) * 500.0 }, unit = "steps", step = 1000.0, limitMin = null,
    )

    private val flags = mapOf(
        "korean" to "🇰🇷", "japanese" to "🇯🇵", "chinese" to "🇨🇳", "mandarin" to "🇨🇳", "spanish" to "🇪🇸", "french" to "🇫🇷",
        "german" to "🇩🇪", "hindi" to "🇮🇳", "english" to "🇬🇧", "italian" to "🇮🇹", "arabic" to "🇸🇦", "russian" to "🇷🇺",
    )

    private fun language(s: Settings): Quest? {
        val lang = s.language ?: return null
        val app = s.languageApp.ifBlank { "your app" }
        return Quest(
            "korean", lang, "$app, a video or speaking practice. One lesson a day keeps the streak alive AND counts here.",
            null, Stat.INT, 30, flags[lang.lowercase()] ?: "🗣", Kind.AMOUNT, target = 10.0, unit = "min", step = 5.0, limitMin = null,
        )
    }

    private val mobility = Quest(
        "mobility", "Morning mobility", "8-min guided flow with a voice coach. Tap Timer to start.",
        null, Stat.FLX, 25, "🧘", Kind.AMOUNT, target = 8.0, unit = "min", step = 1.0,
    )

    private val stretch = Quest(
        "stretch", "Evening stretch", "10-min guided stretch with a voice coach. Tap Timer to start.",
        null, Stat.FLX, 25, "🧘", Kind.AMOUNT, target = 10.0, unit = "min", step = 1.0,
    )

    private val review = Quest(
        "review", "Weekly review",
        "Player and Log tabs: stats, streak, growth. Name one win and one fix. Save a backup.",
        null, Stat.INT, 30, "📝", limitMin = 120,
    )

    private val weighIn = Quest(
        "weigh", "Weigh-in",
        "After the bathroom, before food or water. Then waist at the belly button. Enter both in the Log tab.",
        null, Stat.VIT, 20, "⚖", limitMin = 45,
    )

    private fun walkAfter(s: Settings, meal: String, id: String) = Quest(
        id, "Walk 10 min after $meal",
        "Within 30 min of eating. It blunts the blood-sugar spike" + (if (Profile.pcos(s)) ", which matters a lot with PCOS" else "") +
            ". It counts toward your steps. Bonus quest.",
        null, Stat.AGI, 20, "🚶", optional = true, limitMin = 45,
    )

    private val deepWork = Quest(
        "deepwork", "Deep work · 25 min", "One task, phone sealed. Tools → Pomodoro does it for you and ticks this off.",
        "10 minutes", Stat.INT, 30, "🎯", limitMin = 120,
    )

    private val reachOut = Quest(
        "reach", "Reach out to someone", "Message or call one person you care about. A real message, not a forwarded reel.",
        null, Stat.VIT, 20, "🤝", limitMin = 120,
    )

    private val journal = Quest(
        "journal", "3-line journal", "1 win today, 1 thing you learned, 1 thing you're grateful for.",
        null, Stat.INT, 20, "📓", limitMin = 60,
    )

    private data class Emergency(val title: String, val text: String, val stat: Stat, val officeOk: Boolean)

    private val emergencies = listOf(
        Emergency("Drink a full glass of water", "Right now. All of it.", Stat.VIT, true),
        Emergency("15 squats", "Slow and deep. Stand up from your chair if you need to.", Stat.STR, false),
        Emergency("Walk for 5 minutes", "Leave the room. Any direction.", Stat.AGI, true),
        Emergency("Take the stairs: 2 floors", "Up and down. No lift.", Stat.AGI, true),
        Emergency("30-second plank", "Elbows under shoulders. Breathe.", Stat.STR, false),
        Emergency("Posture reset + 10 slow breaths", "Sit tall, shoulders down, breathe in for 4 and out for 6.", Stat.VIT, true),
        Emergency("Write 1 thing you learned today", "One line in your notebook or notes app.", Stat.INT, true),
        Emergency("Phone face down for 20 minutes", "Flip it now. Do one real task instead.", Stat.INT, true),
        Emergency("10 wall push-ups", "Hands on the wall, body straight.", Stat.STR, false),
        Emergency("2-minute stretch", "Neck rolls, shoulder rolls, reach for the ceiling, touch your toes.", Stat.FLX, true),
        Emergency("20 calf raises", "Up on your toes, slow down.", Stat.STR, true),
        Emergency("Fast march for 1 minute", "Knees high, arms pumping. Get your heart rate up.", Stat.AGI, false),
        Emergency("Message someone you haven't talked to in a while", "One line is enough.", Stat.VIT, true),
    )

    private fun languageEmergency(s: Settings): Emergency? =
        s.language?.let { Emergency("Say 5 $it words out loud", "From memory. Then one simple sentence.", Stat.INT, true) }

    /** 1–3 surprise quests a day (by intensity), at random times between 10 AM and 7:30 PM, avoiding meals and the commute. */
    private fun emergencyBlocks(s: Settings, date: LocalDate, type: DayType): List<Block> {
        val rnd = java.util.Random(date.toEpochDay() * 131L + 3L)
        val n = s.intensity.coerceIn(1, 3)
        val lunch = s.meals.lunch
        val slots = (0 until 20).map { LocalTime.of(10, 0).plusMinutes(30L * it) }
            .filter { it.isBefore(lunch) || !it.isBefore(lunch.plusMinutes(45)) }
            .filter { type != DayType.OFFICE || it.isBefore(minus(s.workEnd, 15)) || !it.isBefore(homeTime(s)) }
            .shuffled(rnd).take(n).sorted()
        val pool = (emergencies + listOfNotNull(languageEmergency(s))).filter { type != DayType.OFFICE || it.officeOk }.shuffled(rnd)
        return slots.mapIndexed { i, t ->
            val e = pool[i % pool.size]
            Block(
                t, "Emergency quest",
                quest = Quest(
                    "emg${i + 1}", "EMERGENCY: ${e.title}", "${e.text} You have 30 minutes. Bonus XP, no penalty.",
                    null, e.stat, 25, "⚡", optional = true, limitMin = 30, expires = true,
                ),
            )
        }
    }

    /** First weigh-in on Day 1, then every Sunday. */
    fun isWeighDay(s: Settings, date: LocalDate): Boolean =
        date == s.start || (date.dayOfWeek == DayOfWeek.SUNDAY && date > s.start)

    private fun workoutQuest(s: Settings, date: LocalDate, w: Workout, week: Int, runWeek: Int): Quest? = when (w) {
        Workout.RUN -> {
            val p = runPlan(runWeek)
            Quest(
                "run", "Run: ${p.title}", "${p.summary}. The coach records how many runs you finish.",
                null, Stat.AGI, 70, "🏃", Kind.AMOUNT, target = p.runs.size.toDouble(), unit = "runs", step = 1.0, limitMin = 120,
            )
        }
        Workout.STRENGTH -> {
            val list = Exercises.session(s, date, week)
            Quest(
                "strength", "Strength training",
                "${list.size} moves today: ${list.joinToString(", ") { it.move.name }}. Open the coach: it shows each move and counts with you.",
                null, Stat.STR, 70, "⚔", Kind.AMOUNT, target = Exercises.totalSets(list).toDouble(), unit = "sets", step = 1.0, limitMin = 120,
            )
        }
        Workout.FIRST_WALK -> Quest(
            "run", "Day 1: Walk 20 min", "20 minutes at a comfortable pace. The first gate is the easiest one.",
            "10 min", Stat.AGI, 50, "🚶", limitMin = 120,
        )
        Workout.NONE -> null
    }

    private fun t(h: Int, m: Int) = LocalTime.of(h, m)

    /** Adds minutes without wrapping past midnight. */
    fun capPlus(t: LocalTime, minutes: Long): LocalTime {
        val r = t.plusMinutes(minutes)
        return if (minutes >= 0 && r.isBefore(t)) LocalTime.of(23, 59) else r
    }

    /** Subtracts minutes without wrapping before midnight. */
    fun minus(t: LocalTime, minutes: Long): LocalTime {
        val r = t.minusMinutes(minutes)
        return if (r.isAfter(t)) LocalTime.MIN else r
    }

    private fun later(a: LocalTime, b: LocalTime) = if (a.isAfter(b)) a else b

    /** Quests that can be moved around an event. */
    private val movable = setOf("run", "strength", "learn", "stretch", "mobility", "review", "deepwork", "reach")
    private fun movableId(id: String) = id in movable || id.startsWith("sk_") || id.startsWith("bc_") || id.startsWith("todo_")

    /**
     * The day's schedule, built from the user's own setup: work or college hours and commute, meal times,
     * medicines, routines and language. The morning follows the actual wake time once "I'm up" is tapped
     * ([PlanCtx.wake]); clock-fixed things (leaving for work, a mess that closes) stay. An event moves or
     * excuses the quests it overlaps. [runLevel] is the run week earned so far.
     */
    fun plan(s: Settings, date: LocalDate, type: DayType, runLevel: Int = week(s, date), ctx: PlanCtx = PlanCtx()): DayPlan {
        val w = week(s, date)
        val runWeek = (runLevel + s.runWeekShift).coerceAtLeast(1)
        val evs = if (type == DayType.SICK || type == DayType.OFFICE) emptyList() else ctx.events
        val wq = workoutQuest(s, date, ctx.workout ?: Workout.NONE, w, runWeek)
        val goal = wakeGoal(s, type)
        val woke = ctx.wake != null
        val up = ctx.wake ?: goal
        val b = mutableListOf<Block>()
        val notes = mutableListOf<String>()
        val areas = Profile.areas(s)
        val m = s.meals
        val bed = s.bedGoal
        val place = if (s.student) "college" else "work"
        var breakfastAt = m.breakfast

        if (type != DayType.SICK) {
            b += Block(goal, "Rise", quest = rise(goal))
            b += Block(up, "Phone-free", quest = phoneFree, notify = false)
            routine("morning", "Morning routine", "☀", s.morning, date, type, ctx.tomorrow, 60)?.let {
                b += Block(up, "Morning routine", quest = it, notify = false)
            }
        }

        /** Breakfast on a day at home: a mess window that can close, or whenever you like. */
        fun homeBreakfast(defaultAt: LocalTime) {
            val end = m.breakfastEnd
            if (m.fixed && end != null) {
                when {
                    up.isBefore(minus(end, 40)) -> {
                        breakfastAt = later(defaultAt, up.plusMinutes(15))
                        b += Block(breakfastAt, "Breakfast", "Closes at ${fmt(end)}. Eat the protein part first.")
                    }
                    up.isBefore(minus(end, 10)) -> {
                        breakfastAt = up.plusMinutes(5)
                        b += Block(breakfastAt, "Breakfast", "It closes at ${fmt(end)}: go now. The rest of the routine can wait 15 minutes.")
                        notes += "Breakfast first: it closes at ${fmt(end)}."
                    }
                    else -> {
                        breakfastAt = up.plusMinutes(30)
                        b += Block(breakfastAt, "Protein breakfast", "Breakfast has closed. ${Profile.breakfastFallback(s)}")
                        notes += "Breakfast has closed. ${Profile.breakfastFallback(s)}"
                    }
                }
            } else {
                breakfastAt = later(defaultAt, up.plusMinutes(30))
                b += Block(breakfastAt, "Breakfast", "Protein first, then carbs.")
            }
        }

        when (type) {
            DayType.OFFICE -> {
                val leave = leaveTime(s)
                val home = homeTime(s)
                val mob = up.plusMinutes(10)
                val mobilityAtHome = mob.plusMinutes(8).isAfter(minus(leave, 5))
                if (!mobilityAtHome) b += Block(mob, "Mobility", quest = mobility)
                if (woke && !up.isBefore(minus(leave, 20))) notes += "Running late. Routine first, then go. Eat breakfast at $place."
                // At home if there's time and (for a mess) it's already open; otherwise at work.
                val homeBf = minus(leave, 20)
                val canEatHome = !homeBf.isBefore(up.plusMinutes(15)) && (!m.fixed || !m.breakfast.isAfter(homeBf))
                breakfastAt = if (canEatHome) homeBf else s.workStart.plusMinutes(15)
                b += Block(breakfastAt, "Breakfast", if (breakfastAt.isBefore(leave)) "Before you leave. Protein first." else "At $place. Protein first, then carbs.", notify = false)
                b += Block(leave, "Leave for $place", "Music, a podcast or a nap on the way. Recovery counts.", notify = false)
                if (Profile.CAREER in areas) b += Block(s.workStart.plusMinutes(60), "Deep work", quest = deepWork)
                s.rescue?.let { b += Block(it, "Rescue check", special = Special.RESCUE) }
                b += Block(m.lunch.plusMinutes(15), "Walk", quest = walkAfter(s, "lunch", "walk_lunch"))
                // Train once you're home, at least 1.5 h after lunch and finished before dinner.
                if (wq != null) {
                    val evening = later(home.plusMinutes(15), m.lunch.plusMinutes(90))
                    if (!evening.isAfter(t(20, 0))) {
                        b += Block(evening, "Training", quest = wq)
                    } else {
                        b += Block(up.plusMinutes(15), "Training", quest = wq)
                        notes += "You get home late, so training is in the morning."
                    }
                }
                if (mobilityAtHome) {
                    val at = capPlus(home, 75)
                    b += Block(at, "Mobility", quest = mobility)
                    if (woke) notes += "Mobility moved to ${fmt(at)}: no time before leaving."
                }
                b += Block(m.dinner.plusMinutes(15), "Walk", quest = walkAfter(s, "dinner", "walk_dinner"))

            }
            DayType.WFH -> {
                var trainedAfterWork = false
                var trainEnd: LocalTime? = null
                if (wq != null) {
                    // Before work if it fits before work starts (and before a mess closes), else after work.
                    val end = up.plusMinutes(55)
                    val mess = m.breakfastEnd
                    val deadline = if (m.fixed && mess != null) minOf(minus(mess, 15), minus(s.workStart, 15)) else minus(s.workStart, 15)
                    if (!end.isAfter(deadline)) {
                        b += Block(up.plusMinutes(15), "Training", quest = wq)
                        trainEnd = end
                    } else {
                        val at = later(s.workEnd.plusMinutes(30), m.lunch.plusMinutes(90))
                        b += Block(at, "Training", quest = wq)
                        trainedAfterWork = true
                        if (woke) notes += "Training moved to ${fmt(at)}: after ${if (s.student) "classes" else "work"}."
                    }
                }
                homeBreakfast(later(m.breakfast, trainEnd ?: up))
                if (Profile.CAREER in areas) b += Block(s.workStart.plusMinutes(15), "Deep work", quest = deepWork)
                s.rescue?.let { b += Block(it, "Rescue check", special = Special.RESCUE) }
                b += Block(s.workStart.plusMinutes(90), "Move break", "Stand up, walk for 2 min, drink water")
                b += Block(m.lunch.plusMinutes(15), "Walk", quest = walkAfter(s, "lunch", "walk_lunch"))

                b += Block(capPlus(s.workEnd, 60), "Snack swap", "Chana, makhana, fruit or curd instead of fried snacks")
                b += Block(later(t(18, 30), capPlus(s.workEnd, 150)), "Stretch", quest = stretch)
                b += Block(m.dinner.plusMinutes(15), "Walk", quest = walkAfter(s, "dinner", "walk_dinner"))
            }
            DayType.WEEKEND, DayType.OFF -> {
                if (wq != null) {
                    val tr = up.plusMinutes(30)
                    if (!tr.isAfter(t(10, 30))) {
                        b += Block(tr, "Training", quest = wq)
                    } else {
                        b += Block(t(17, 30), "Training", quest = wq)
                        if (woke) notes += "Training moved to ${fmt(t(17, 30))}."
                    }
                }
                homeBreakfast(later(m.breakfast, up.plusMinutes(75)))

                b += Block(m.lunch.plusMinutes(15), "Walk", quest = walkAfter(s, "lunch", "walk_lunch"))
                b += Block(t(18, 0), "Stretch", quest = stretch)
                b += Block(m.dinner.plusMinutes(15), "Walk", quest = walkAfter(s, "dinner", "walk_dinner"))
            }
            DayType.SICK -> Unit
        }

        // Medicines and supplements, tied to meals.
        s.meds.forEachIndexed { i, med ->
            val at = when (med.at) {
                MedTime.WAKE -> up.plusMinutes(5)
                MedTime.BREAKFAST -> breakfastAt.plusMinutes(20)
                MedTime.LUNCH -> m.lunch.plusMinutes(30)
                MedTime.DINNER -> m.dinner.plusMinutes(30)
                MedTime.BED -> minus(bed, 30)
            }
            b += Block(at, med.name, quest = med(i, med))
        }

        if (type != DayType.SICK && isWeighDay(s, date)) {
            b += Block(up.plusMinutes(5), "Weigh-in", quest = weighIn)
        }
        if (type != DayType.SICK && date.dayOfWeek == DayOfWeek.SUNDAY) {
            b += Block(t(19, 0), "Weekly review", quest = review)
        }
        if (type != DayType.SICK && Profile.SOCIAL in areas &&
            date.dayOfWeek in setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY)
        ) {
            b += Block(t(19, 30), "Reach out", quest = reachOut)
        }
        if (type != DayType.SICK && (Profile.MIND in areas || Profile.SPIRIT in areas || Profile.CRISIS in areas)) {
            b += Block(t(21, 50), "Journal", quest = journal)
        }
        // The lesson is open from the morning; an optional reminder at the time you chose.
        if (type != DayType.SICK && evs.none { it.wholeDay }) {
            b += Block(up, "Learn something", quest = learn, notify = false)
            s.lessonAt?.let { b += Block(it, "Learn something", "Today's pick is waiting. A few minutes is enough.", remindFor = "learn") }
        }
        if (type != DayType.SICK && evs.none { it.wholeDay }) b += emergencyBlocks(s, date, type)
        b += Challenges.blocks(s, date, type)
        b += ctx.extra
        s.checkIn?.let { b += Block(it, "Quest check", special = Special.CHECK_IN) }
        routine("night", "Night routine", "🌙", s.night, date, type, ctx.tomorrow, 40)?.let {
            b += Block(minus(bed, 40), "Night routine", quest = it, notify = false)
        }
        s.windDown?.let { b += Block(it, "Wind-down", "Dim the lights, phone on charge away from the bed, no new episodes.") }
        b += Block(bed, "Bedtime goal", "Asleep by ${fmt(bed)} is the goal. Phone down now; the System will see how you slept.")

        for (ev in evs.sortedBy { it.start }) applyEvent(ev, up, b, notes, bed)

        if (ctx.gentle) notes += "Gentle day after a rest day: lighter step goal. One small win is enough."
        // Only the core counts. Everything else becomes a side quest: bonus XP, never against you.
        val core = b.map { blk -> blk.quest?.let { q -> if (q.id !in CORE && !q.optional) blk.copy(quest = q.copy(optional = true)) else null } ?: blk }
        val allDay = listOfNotNull(
            sleep(s).takeIf { type != DayType.SICK },
            steps(s, w, ctx.gentle).takeIf { type != DayType.SICK },
            water(w).copy(optional = true),
            language(s)?.takeIf { type != DayType.SICK }?.copy(optional = true),
        )
        return DayPlan(date, type, core.sortedBy { it.time }, allDay, notes)
    }

    /** Moves, excuses or drops whatever the event overlaps, and adds "get ready". */
    private fun applyEvent(ev: Event, up: LocalTime, b: MutableList<Block>, notes: MutableList<String>, bed: LocalTime) {
        val after = capPlus(ev.realEnd, 20)
        val before = minus(ev.readyStart, 60)
        val excused = mutableListOf<String>()
        val iter = b.listIterator()
        while (iter.hasNext()) {
            val blk = iter.next()
            if (!ev.covers(blk.time)) continue
            val q = blk.quest
            when {
                q == null && blk.special == Special.NONE -> iter.remove()
                q == null -> Unit
                q.expires || q.id.startsWith("walk_") -> iter.remove()
                !movableId(q.id) -> Unit
                ev.wholeDay -> {
                    iter.remove()
                    excused += q.title
                }
                (q.id == "run" || q.id == "strength" || q.id.startsWith("bc_")) && !after.isAfter(t(20, 0)) -> {
                    iter.set(blk.copy(time = after))
                    notes += "${q.title} moved to ${fmt(after)}, after ${ev.title}."
                }
                q.id != "run" && q.id != "strength" && !q.id.startsWith("bc_") && !after.isAfter(t(21, 30)) -> {
                    iter.set(blk.copy(time = after))
                    notes += "${q.title} moved to ${fmt(after)}, after ${ev.title}."
                }
                !before.isBefore(up.plusMinutes(20)) -> {
                    iter.set(blk.copy(time = before))
                    notes += "${q.title} moved to ${fmt(before)}, before you get ready."
                }
                else -> {
                    iter.remove()
                    excused += q.title
                }
            }
        }
        if (excused.isNotEmpty()) notes += "Excused for ${ev.title}: ${excused.joinToString(", ")}. No penalty."
        if (ev.wholeDay) notes += "Event day: the System only asks for the basics. Dancing and walking count as steps."

        // A late night moves the night routine and relaxes lights-off.
        val nightAt = capPlus(ev.realEnd, 15)
        val nightIdx = b.indexOfFirst { it.quest?.id == "night" }
        if (nightIdx >= 0 && ev.realEnd.isAfter(b[nightIdx].time)) {
            b[nightIdx] = b[nightIdx].copy(time = nightAt)
            notes += "Night routine moved to ${fmt(nightAt)}."
        }
        if (ev.realEnd.isAfter(minus(bed, 30))) {
            val lightsOff = capPlus(ev.realEnd, 45)
            val i = b.indexOfFirst { it.title == "Bedtime goal" }
            if (i >= 0) b[i] = Block(lightsOff, "Bedtime goal", "Big day. Home, water, night routine, then sleep.")
            b.removeAll { it.title == "Wind-down" && ev.covers(it.time) }
            notes += "Bedtime goal moved to ${fmt(lightsOff)} for ${ev.title}. Enjoy it."
        }
        if (ev.readyStart != ev.start) {
            b += Block(ev.readyStart, "Get ready: ${ev.title}", "Shower, outfit, bag. Drink a glass of water before you go.")
        }
        b += Block(ev.start, "🎉 ${ev.title}", "Enjoy it. Water between drinks, protein if there's a choice. Dancing counts.", notify = false)
        if (ev.realEnd != LocalTime.of(23, 59)) b += Block(ev.realEnd, "${ev.title} ends", "Home, water, night routine.", notify = false)
    }

    // ---- Run plan: zero → 20 minutes non-stop ----

    fun runPlan(week: Int): RunPlan = when (week) {
        1 -> RunPlan(1, List(8) { 30 }, 90)
        2 -> RunPlan(2, List(8) { 45 }, 90)
        3 -> RunPlan(3, List(6) { 60 }, 90)
        4 -> RunPlan(4, List(5) { 90 }, 90)
        5 -> RunPlan(5, List(3) { 180 }, 90)
        6 -> RunPlan(6, List(2) { 300 }, 120)
        7 -> RunPlan(7, List(2) { 480 }, 120)
        8 -> RunPlan(8, listOf(600, 300), 120)
        9 -> RunPlan(9, listOf(900), 0)
        10 -> RunPlan(10, listOf(1080), 0)
        else -> RunPlan(week, listOf((1200 + (week - 11) * 120).coerceAtMost(1800)), 0)
    }

    const val RUN_TIPS =
        "Jog slowly enough that you could still talk. Wait 1.5–2 h after a meal. " +
            "The plan only moves up when you finish 2 full runs in a week, otherwise the week repeats."

    /** Recommended treadmill settings per segment, "or whatever feels comfortable". */
    fun treadmillGuide(kind: SegKind): String = when (kind) {
        SegKind.WARMUP -> "4.5–5.0 km/h · incline 1%"
        SegKind.RUN -> "6.0–6.5 km/h · incline 1%"
        SegKind.WALK -> "4.5–5.0 km/h · incline 1%"
        SegKind.COOLDOWN -> "4.0 km/h · incline 0–1%"
    }

    val mobilityFlow = listOf(
        Exercise("Cat–cow", "1 min", "Slow, move with your breath.", sec = 60),
        Exercise("World's greatest stretch", "1 min / side", "Lunge, hand down, rotate the other arm to the sky.", sec = 60, perSide = true),
        Exercise("Hip flexor stretch", "1 min / side", "Half-kneel, squeeze the back glute, lean gently forward.", sec = 60, perSide = true),
        Exercise("Child's pose", "1 min", "Knees wide, arms long, breathe into your back.", sec = 60),
        Exercise("Hamstring stretch", "1 min / side", "On your back, one leg up, hold behind the thigh.", sec = 60, perSide = true),
    )

    val stretchFlow = listOf(
        Exercise("Butterfly", "1 min", "Soles together, let the knees fall open.", sec = 60),
        Exercise("Seated forward fold", "1 min", "Long spine first, then fold.", sec = 60),
        Exercise("Figure-4 stretch", "1 min / side", "On your back, ankle over the opposite knee.", sec = 60, perSide = true),
        Exercise("Cobra", "1 min", "Hips down, chest lifts gently.", sec = 60),
        Exercise("Thread the needle", "1 min / side", "On all fours, slide one arm under, shoulder to the mat.", sec = 60, perSide = true),
        Exercise("Legs up the wall", "3 min", "Lie back and breathe. Great before sleep.", sec = 180),
    )
}
