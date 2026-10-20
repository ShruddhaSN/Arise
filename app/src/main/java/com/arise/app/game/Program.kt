package com.arise.app.game

import com.arise.app.data.DayType
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
    /** Enter how much: litres, steps, minutes. XP scales with the amount. */
    AMOUNT,
    /** "I'm up": scored by how close the actual wake time is to the goal. */
    WAKE,
}

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
)

enum class Special { NONE, CHECK_IN, RESCUE }

data class Block(
    val time: LocalTime,
    val title: String,
    val detail: String = "",
    val quest: Quest? = null,
    val notify: Boolean = true,
    val special: Special = Special.NONE,
)

data class DayPlan(
    val date: LocalDate,
    val type: DayType,
    val blocks: List<Block>,
    /** Quests that live in the all-day box, not the card deck. */
    val allDay: List<Quest>,
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

    /** Runs Tue/Fri/Sun, strength Wed/Sat. Mon/Thu are recovery days (steps only). */
    fun workout(s: Settings, date: LocalDate, type: DayType): Workout = when {
        type == DayType.OFF || type == DayType.SICK -> Workout.NONE
        date == s.earliest -> Workout.FIRST_WALK
        else -> when (date.dayOfWeek) {
            DayOfWeek.TUESDAY, DayOfWeek.FRIDAY, DayOfWeek.SUNDAY -> Workout.RUN
            DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY -> Workout.STRENGTH
            else -> Workout.NONE
        }
    }

    fun wakeGoal(s: Settings, type: DayType): LocalTime = when (type) {
        DayType.OFFICE -> s.wakeOffice
        DayType.WFH -> s.wakeWfh
        else -> s.wakeOff
    }

    // ---- Weekly progression ----

    fun learnMinutes(week: Int): Int = when {
        week <= 1 -> 15
        week <= 3 -> 20
        else -> 30
    }

    fun bedtime(week: Int): LocalTime = if (week <= 1) LocalTime.of(23, 30) else LocalTime.of(23, 0)

    private fun waterLitres(week: Int): Double = if (week <= 2) 2.5 else 3.0

    private fun stepGoal(week: Int): Double = when {
        week <= 2 -> 6000.0
        week <= 4 -> 7000.0
        else -> 8000.0
    }

    fun isWeekend(type: DayType) = type == DayType.WEEKEND || type == DayType.OFF

    // ---- Quests ----

    private fun rise(goal: LocalTime) = Quest(
        "wake", "Rise", "Goal ${fmt(goal)}. Tap \"I'm up\" when you're out of bed, then ghee water and 2 min of sunlight.",
        null, Stat.VIT, 30, "🌅", Kind.WAKE, target = (goal.toSecondOfDay() / 60).toDouble(),
    )

    private val phoneFree = Quest(
        "phonefree", "Phone-free first 30 min", "No Instagram or feeds for 30 min after waking. Do your first quest first.",
        null, Stat.INT, 20, "📵",
    )

    private fun sleep(week: Int): Quest {
        val b = bedtime(week)
        return Quest(
            "sleep", "Lights off by ${fmt(b)}", "Phone charging away from the bed, lights off by ${fmt(b)}",
            "Lights off by ${fmt(b.plusMinutes(30))}", Stat.VIT, 40, "😴",
        )
    }

    fun learnTarget(week: Int, type: DayType): Int = learnMinutes(week) + if (isWeekend(type)) 15 else 0

    private fun learn(week: Int, type: DayType) = Quest(
        "learn", "Today's lesson", "Open today's lesson, make notes in your notebook.",
        null, Stat.INT, 40, "📚", Kind.AMOUNT, target = learnTarget(week, type).toDouble(), unit = "min", step = 5.0,
    )

    private val normoz = Quest("normoz", "Normoz DS", "Take Normoz DS after lunch", null, Stat.VIT, 10, "💊")

    private fun water(week: Int) = Quest(
        "water", "Water", "Drink across the day, not all at once", null, Stat.VIT, 20, "💧",
        Kind.AMOUNT, target = waterLitres(week), unit = "L", step = 0.25,
    )

    private fun steps(week: Int) = Quest(
        "steps", "Steps", "From Samsung Health. Walks after meals count here.", null, Stat.AGI, 40, "👣",
        Kind.AMOUNT, target = stepGoal(week), unit = "steps", step = 1000.0,
    )

    private val korean = Quest(
        "korean", "Korean", "Duolingo Korean or Falou. One Duolingo lesson keeps your streak AND counts here.",
        null, Stat.INT, 30, "🇰🇷", Kind.AMOUNT, target = 10.0, unit = "min", step = 5.0,
    )

    private val mobility = Quest(
        "mobility", "Morning mobility", "8-min guided flow. Tap Timer to start.",
        null, Stat.FLX, 25, "🧘", Kind.AMOUNT, target = 8.0, unit = "min", step = 1.0,
    )

    private val stretch = Quest(
        "stretch", "Evening stretch", "10-min guided stretch. Tap Timer to start.",
        null, Stat.FLX, 25, "🧘", Kind.AMOUNT, target = 10.0, unit = "min", step = 1.0,
    )

    private val review = Quest(
        "review", "Weekly review",
        "Player tab: stats, streak, bosses. Log tab: charts. Name one win and one fix. Save a backup.",
        null, Stat.INT, 30, "📝",
    )

    private val weighIn = Quest(
        "weigh", "Weigh-in",
        "After the bathroom, before food or water. Then waist at the belly button. Enter both in the Log tab.",
        null, Stat.VIT, 20, "⚖",
    )

    /** First weigh-in on Day 1, then every Sunday. */
    fun isWeighDay(s: Settings, date: LocalDate): Boolean =
        date == s.start || (date.dayOfWeek == DayOfWeek.SUNDAY && date > s.start)

    private fun workoutQuest(w: Workout, week: Int, runWeek: Int): Quest? = when (w) {
        Workout.RUN -> {
            val p = runPlan(runWeek)
            Quest(
                "run", "Run: ${p.title}", p.summary, "10-min brisk walk on the treadmill", Stat.AGI, 70, "🏃",
            )
        }
        Workout.STRENGTH -> Quest(
            "strength", "Strength training", "${strengthPlan(week).size} exercises. Open the coach for the list.",
            "1 round of each exercise", Stat.STR, 60, "⚔",
        )
        Workout.FIRST_WALK -> Quest(
            "run", "Day 1: Walk 20 min", "20 minutes at a comfortable pace. The first gate is the easiest one.",
            "10 min", Stat.AGI, 50, "🚶",
        )
        Workout.NONE -> null
    }

    private fun t(h: Int, m: Int) = LocalTime.of(h, m)

    fun plan(s: Settings, date: LocalDate, type: DayType): DayPlan {
        val w = week(s, date)
        val runWeek = (w + s.runWeekShift).coerceAtLeast(1)
        val wq = workoutQuest(workout(s, date, type), w, runWeek)
        val up = wakeGoal(s, type)
        val b = mutableListOf<Block>()

        fun walkReminder(at: LocalTime, meal: String) =
            Block(at, "Walk 10 min after $meal", "Within 30 min of eating, it blunts the sugar spike. It counts toward your steps.")

        if (type != DayType.SICK) {
            b += Block(up, "Rise", quest = rise(up))
            b += Block(up, "Phone-free", quest = phoneFree, notify = false)
        }
        when (type) {
            DayType.OFFICE -> {
                b += Block(up.plusMinutes(10), "Mobility", quest = mobility)
                b += Block(t(7, 30), "Leave for office", "Sleep or music in the car. Recovery counts.", notify = false)
                b += Block(t(8, 45), "Office breakfast", "Protein and fibre first, then carbs", notify = false)
                b += Block(t(11, 0), "Rescue check", special = Special.RESCUE)
                b += walkReminder(t(13, 15), "lunch")
                b += Block(t(13, 40), "Normoz DS", quest = normoz)
                if (wq != null) b += Block(t(18, 15), "Training", quest = wq)
                b += walkReminder(t(20, 45), "dinner")
                b += Block(t(21, 0), "Lesson", quest = learn(w, type))
            }
            DayType.WFH -> {
                if (wq != null) b += Block(up.plusMinutes(15), "Training", quest = wq)
                b += Block(t(8, 30), "PG breakfast", "Breakfast closes at 9. Eat the protein part first.")
                b += Block(t(11, 0), "Rescue check", special = Special.RESCUE)
                b += Block(t(11, 30), "Move break", "Stand up, walk for 2 min, drink water")
                b += walkReminder(t(13, 15), "lunch")
                b += Block(t(13, 40), "Normoz DS", quest = normoz)
                b += Block(t(16, 30), "Lesson", quest = learn(w, type))
                b += Block(t(17, 0), "Snack swap", "Chana, makhana or curd instead of fried PG snacks")
                b += Block(t(18, 30), "Stretch", quest = stretch)
                b += walkReminder(t(20, 45), "dinner")
            }
            DayType.WEEKEND -> {
                if (wq != null) b += Block(up.plusMinutes(30), "Training", quest = wq)
                b += Block(up.plusMinutes(75), "Breakfast", "Eat the protein part first.")
                b += Block(t(11, 30), "Lesson · deep dive", quest = learn(w, type))
                b += walkReminder(t(13, 15), "lunch")
                b += Block(t(13, 40), "Normoz DS", quest = normoz)
                b += Block(t(18, 0), "Stretch", quest = stretch)
                b += walkReminder(t(20, 45), "dinner")
            }
            DayType.OFF -> {
                b += Block(t(11, 30), "Lesson · deep dive", quest = learn(w, type))
                b += Block(t(13, 40), "Normoz DS", quest = normoz)
                b += Block(t(18, 0), "Stretch", quest = stretch)
            }
            DayType.SICK -> {
                b += Block(t(13, 40), "Normoz DS", quest = normoz)
            }
        }
        if (type != DayType.SICK && isWeighDay(s, date)) {
            b += Block(up.plusMinutes(5), "Weigh-in", quest = weighIn)
        }
        if (type != DayType.SICK && date.dayOfWeek == DayOfWeek.SUNDAY) {
            b += Block(t(19, 0), "Weekly review", quest = review)
        }
        b += Block(t(21, 45), "Quest check", special = Special.CHECK_IN)
        b += Block(t(22, 30), "Wind-down", "Dim the lights, phone on charge away from the bed, no new episodes")
        b += Block(bedtime(w), "Sleep", quest = sleep(w))

        val allDay = if (type == DayType.SICK) listOf(water(w)) else listOf(water(w), steps(w), korean)
        return DayPlan(date, type, b.sortedBy { it.time }, allDay)
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
        "Jog slowly enough that you could still talk. Treadmill guide: walk 5–5.5 km/h, " +
            "jog 6.5–7.5 km/h, incline 1%. Wait 1.5–2 h after a meal. If a week feels too hard, tap Easier."

    // ---- Strength: bodyweight → dumbbells ----

    fun strengthPlan(week: Int): List<Exercise> {
        val sets = if (week <= 2) 2 else 3
        val plank = if (week <= 2) 20 else 30
        return when {
            week <= 4 -> listOf(
                Exercise("Chair squat", "$sets × 10", "Sit back until you touch the chair, then stand tall. Knees follow your toes."),
                Exercise("Glute bridge", "$sets × 12", "Squeeze your glutes at the top for 1 second."),
                Exercise("Incline push-up", "$sets × 8", "Hands on a bed or wall, body in one straight line."),
                Exercise("Dumbbell row", "$sets × 10 / arm", "One hand on the bed, pull the dumbbell to your hip."),
                Exercise("Dead bug", "$sets × 8 / side", "Lower back pressed into the mat. Move slowly."),
                Exercise("Plank", "$sets × ${plank}s", "Elbows under shoulders, hips level.", sec = plank),
            )
            week <= 8 -> listOf(
                Exercise("Goblet squat", "3 × 10", "Hold one dumbbell at your chest, sit down between your heels."),
                Exercise("Dumbbell Romanian deadlift", "3 × 10", "Soft knees, push hips back, dumbbells slide down your thighs, flat back."),
                Exercise("Incline push-up", "3 × 10", "Lower the incline when it gets easy."),
                Exercise("One-arm row", "3 × 12 / arm", "Pause for a second at the top."),
                Exercise("Reverse lunge", "3 × 8 / leg", "Step back, both knees about 90°, push through the front heel."),
                Exercise("Plank", "3 × 40s", "Breathe slowly through it.", sec = 40),
            )
            else -> listOf(
                Exercise("Goblet squat", "3 × 12", "Heavier dumbbell if 12 feels easy."),
                Exercise("Dumbbell Romanian deadlift", "3 × 12", "Feel the stretch in your hamstrings."),
                Exercise("Push-up", "3 × 8", "Knees down if needed. Chest down to fist height."),
                Exercise("One-arm row", "3 × 12 / arm", "Control the way down."),
                Exercise("Split squat", "3 × 10 / leg", "Back knee lowers toward the floor."),
                Exercise("Side plank", "3 × 25s / side", "Hips high, body in a straight line.", sec = 25, perSide = true),
            )
        }
    }

    const val STRENGTH_NOTE =
        "Rest 60–90 s between sets. If the last 2 reps of a set feel easy, go heavier next time. " +
            "Strength training is the best exercise for insulin resistance."

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
