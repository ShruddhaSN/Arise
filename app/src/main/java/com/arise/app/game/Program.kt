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

data class Quest(
    val id: String,
    val title: String,
    val full: String,
    /** Null means there is no minimum version. */
    val min: String?,
    val stat: Stat,
    val xp: Int,
)

data class Block(
    val time: LocalTime,
    val title: String,
    val detail: String = "",
    val quest: Quest? = null,
    val notify: Boolean = true,
    /** Evening reminder that lists what's left. */
    val checkIn: Boolean = false,
)

data class DayPlan(
    val date: LocalDate,
    val type: DayType,
    val blocks: List<Block>,
    val anytime: List<Quest>,
) {
    val quests: List<Quest> get() = anytime + blocks.mapNotNull { it.quest }
}

enum class Workout { RUN, STRENGTH, EASY_WALK, FIRST_WALK, NONE }

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

data class Exercise(val name: String, val dose: String, val cue: String)

fun dur(sec: Int): String = when {
    sec < 60 -> "${sec}s"
    sec % 60 == 0 -> "${sec / 60} min"
    else -> "${sec / 60}m ${sec % 60}s"
}

private val clock = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
fun fmt(t: LocalTime): String = t.format(clock)

object Program {
    const val MIN_PASSES_PER_WEEK = 2

    /** Week 1 starts on Day 1 (setup day). Filled-in days before Day 1 also count as week 1. */
    fun week(s: Settings, date: LocalDate): Int =
        (ChronoUnit.DAYS.between(s.start, date).coerceAtLeast(0) / 7).toInt() + 1

    fun weekStart(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value % 7).toLong())

    fun defaultType(s: Settings, date: LocalDate): DayType = when (date.dayOfWeek) {
        in s.officeDays -> DayType.OFFICE
        in s.wfhDays -> DayType.WFH
        else -> DayType.WEEKEND
    }

    /** Runs Tue/Fri/Sun, strength Wed/Sat, easy walks Mon/Thu — whatever the day type. */
    fun workout(s: Settings, date: LocalDate, type: DayType): Workout = when {
        type == DayType.OFF -> Workout.NONE
        date == s.earliest -> Workout.FIRST_WALK
        else -> when (date.dayOfWeek) {
            DayOfWeek.TUESDAY, DayOfWeek.FRIDAY, DayOfWeek.SUNDAY -> Workout.RUN
            DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY -> Workout.STRENGTH
            else -> Workout.EASY_WALK
        }
    }

    // ---- Weekly progression ----

    fun learnMinutes(week: Int): Int = when {
        week <= 1 -> 15
        week <= 3 -> 20
        else -> 30
    }

    fun bedtime(week: Int): LocalTime = if (week <= 1) LocalTime.of(23, 30) else LocalTime.of(23, 0)

    private fun waterLitres(week: Int): String = if (week <= 2) "2.5" else "3"

    // ---- Quests ----

    private fun isFree(type: DayType) = type == DayType.WEEKEND || type == DayType.OFF

    private fun wakeAt(s: Settings, type: DayType): LocalTime = if (isFree(type)) s.wakeFree else s.wakeWork

    private fun wake(s: Settings, type: DayType): Quest {
        val w = wakeAt(s, type)
        val t = fmt(w)
        val m = fmt(w.plusMinutes(30))
        return Quest(
            "wake", "Rise by $t",
            "Out of bed by $t, ghee water, then 2 min of sunlight or an open window",
            "Out of bed by $m", Stat.VIT, 30,
        )
    }

    private fun sleep(week: Int): Quest {
        val b = bedtime(week)
        return Quest(
            "sleep", "Lights off by ${fmt(b)}",
            "Phone charging away from the bed, lights off by ${fmt(b)}",
            "Lights off by ${fmt(b.plusMinutes(30))}", Stat.VIT, 40,
        )
    }

    private fun learn(week: Int, long: Boolean): Quest {
        val m = learnMinutes(week) + if (long) 15 else 0
        return Quest(
            "learn", "Learn for $m min",
            "$m min on today's knowledge card or any topic: video, article, book or podcast",
            "5 min", Stat.INT, 40,
        )
    }

    private val normoz = Quest("normoz", "Normoz DS", "Take Normoz DS after lunch", null, Stat.VIT, 10)

    private fun walkAfter(meal: String) = Quest(
        "walk_$meal", "Walk after $meal",
        "10-min walk within 30 min of $meal (blunts the blood-sugar spike)",
        "5-min walk", Stat.VIT, 20,
    )

    private val breakfast = Quest(
        "breakfast", "Protein breakfast",
        "Breakfast by 9:00, protein first: 2–3 boiled eggs, curd + roasted chana, or overnight oats",
        "Anything with protein before 10:00", Stat.VIT, 20,
    )

    private fun water(week: Int) = Quest(
        "water", "Water ${waterLitres(week)} L", "Drink ${waterLitres(week)} L across the day",
        "2 L", Stat.VIT, 20,
    )

    private val protein = Quest(
        "protein", "Protein at every meal",
        "Protein at every meal, eaten first: eggs, dal, paneer, curd, chana, chicken",
        "Protein at 1 meal", Stat.STR, 30,
    )

    private val mobility = Quest(
        "mobility", "Mobility flow", "8-min morning mobility flow (Train → Mobility)",
        "3-min stretch", Stat.FLX, 25,
    )

    private val stretch = Quest(
        "stretch", "Evening stretch", "10-min stretch routine (Train → Mobility)",
        "3 min", Stat.FLX, 25,
    )

    private val review = Quest(
        "review", "Weekly review",
        "Player tab: check your stats, streak and bosses. Name one win and one fix for next week.",
        null, Stat.INT, 30,
    )

    /** First weigh-in on Day 1, then every Sunday. */
    fun isWeighDay(s: Settings, date: LocalDate): Boolean =
        date == s.start || (date.dayOfWeek == DayOfWeek.SUNDAY && date > s.start)

    private val weighIn = Quest(
        "weigh", "Weekly weigh-in",
        "Weight after the bathroom, before food or water, then waist at the belly button. Enter them in the Log tab.",
        null, Stat.VIT, 20,
    )

    private fun workoutQuest(w: Workout, week: Int, runWeek: Int): Quest? = when (w) {
        Workout.RUN -> {
            val p = runPlan(runWeek)
            Quest(
                "run", "Run: ${p.title}", "Run coach session (Train → Run): ${p.summary}",
                "10-min brisk walk on the treadmill", Stat.AGI, 70,
            )
        }
        Workout.STRENGTH -> Quest(
            "strength", "Strength training",
            "Strength session (Train → Strength): ${strengthPlan(week).size} exercises",
            "1 round of each exercise", Stat.STR, 60,
        )
        Workout.EASY_WALK -> Quest(
            "walk_evening", "Easy walk 15 min", "15-min easy walk, outside or on the treadmill",
            "5 min", Stat.AGI, 30,
        )
        Workout.FIRST_WALK -> Quest(
            "run", "Day 1: Walk 20 min",
            "20-min walk at a comfortable pace. The first gate is the easiest one.",
            "10 min", Stat.AGI, 50,
        )
        Workout.NONE -> null
    }

    private fun t(h: Int, m: Int) = LocalTime.of(h, m)

    fun plan(s: Settings, date: LocalDate, type: DayType): DayPlan {
        val w = week(s, date)
        val runWeek = (w + s.runWeekShift).coerceAtLeast(1)
        val wq = workoutQuest(workout(s, date, type), w, runWeek)
        val up = wakeAt(s, type)
        val b = mutableListOf<Block>()
        when (type) {
            DayType.OFFICE -> {
                b += Block(up, "Rise", quest = wake(s, type))
                b += Block(up.plusMinutes(10), "Mobility", quest = mobility)
                b += Block(t(7, 30), "Leave for office", "Sleep or music in the car. Recovery counts.", notify = false)
                b += Block(t(8, 45), "Office breakfast", "Protein and fibre first, then carbs", notify = false)
                b += Block(t(13, 0), "Lunch, then walk", quest = walkAfter("lunch"))
                b += Block(t(13, 40), "Normoz DS", quest = normoz)
                if (wq != null) b += Block(t(18, 15), "Training", quest = wq)
                b += Block(t(20, 30), "Dinner, then walk", quest = walkAfter("dinner"))
                b += Block(t(21, 0), "Learn", quest = learn(w, false))
            }
            DayType.WFH -> {
                b += Block(up, "Rise", quest = wake(s, type))
                if (wq != null) b += Block(up.plusMinutes(15), "Training (treadmill is usually free now)", quest = wq)
                b += Block(maxOf(t(8, 30), up.plusMinutes(90)), "Breakfast", quest = breakfast)
                b += Block(t(11, 0), "Move break", "Stand up, walk for 2 min, drink water")
                b += Block(t(13, 0), "Lunch, then walk", quest = walkAfter("lunch"))
                b += Block(t(13, 40), "Normoz DS", quest = normoz)
                b += Block(t(16, 30), "Learn", quest = learn(w, false))
                b += Block(t(17, 0), "Snack swap", "Chana, makhana or curd instead of fried PG snacks")
                b += Block(t(18, 30), "Stretch", quest = stretch)
                b += Block(t(20, 30), "Dinner, then walk", quest = walkAfter("dinner"))
            }
            DayType.WEEKEND -> {
                b += Block(up, "Rise", quest = wake(s, type))
                if (wq != null) b += Block(up.plusMinutes(30), "Training", quest = wq)
                b += Block(up.plusMinutes(90), "Breakfast", quest = breakfast)
                b += Block(t(11, 0), "Learn", quest = learn(w, true))
                b += Block(t(13, 0), "Lunch, then walk", quest = walkAfter("lunch"))
                b += Block(t(13, 40), "Normoz DS", quest = normoz)
                b += Block(t(18, 0), "Stretch", quest = stretch)
                b += Block(t(20, 30), "Dinner, then walk", quest = walkAfter("dinner"))
            }
            DayType.OFF -> {
                b += Block(up, "Rise", quest = wake(s, type))
                b += Block(t(13, 0), "Lunch, then walk", quest = walkAfter("lunch"))
                b += Block(t(13, 40), "Normoz DS", quest = normoz)
                b += Block(t(20, 30), "Dinner, then walk", quest = walkAfter("dinner"))
            }
        }
        if (isWeighDay(s, date)) {
            b += Block(up.plusMinutes(5), "Weigh-in", quest = weighIn)
        }
        if (date.dayOfWeek == DayOfWeek.SUNDAY) b += Block(t(19, 0), "Weekly review", quest = review)
        b += Block(t(21, 45), "Quest check", "See what's left today", checkIn = true)
        b += Block(t(22, 30), "Wind-down", "Dim the lights, phone on charge away from the bed, no new episodes")
        b += Block(bedtime(w), "Sleep", quest = sleep(w))
        return DayPlan(date, type, b.sortedBy { it.time }, listOf(water(w), protein))
    }

    // ---- Run plan: zero → 20 minutes non-stop by the end of December ----

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
            "jog 6.5–7.5 km/h, incline 1%. If a week feels too hard, tap Easier. Repeating a week is normal."

    // ---- Strength: bodyweight → dumbbells ----

    fun strengthPlan(week: Int): List<Exercise> {
        val sets = if (week <= 2) 2 else 3
        return when {
            week <= 4 -> listOf(
                Exercise("Chair squat", "$sets × 10", "Sit back until you touch the chair, then stand tall. Knees follow your toes."),
                Exercise("Glute bridge", "$sets × 12", "Squeeze your glutes at the top for 1 second."),
                Exercise("Incline push-up", "$sets × 8", "Hands on a bed or wall, body in one straight line."),
                Exercise("Dumbbell row", "$sets × 10 / arm", "One hand on the bed, pull the dumbbell to your hip."),
                Exercise("Dead bug", "$sets × 8 / side", "Lower back pressed into the mat. Move slowly."),
                Exercise("Plank", "$sets × ${if (week <= 2) 20 else 30}s", "Elbows under shoulders, hips level."),
            )
            week <= 8 -> listOf(
                Exercise("Goblet squat", "3 × 10", "Hold one dumbbell at your chest, sit down between your heels."),
                Exercise("Dumbbell Romanian deadlift", "3 × 10", "Soft knees, push hips back, dumbbells slide down your thighs, flat back."),
                Exercise("Incline push-up", "3 × 10", "Lower the incline when it gets easy."),
                Exercise("One-arm row", "3 × 12 / arm", "Pause for a second at the top."),
                Exercise("Reverse lunge", "3 × 8 / leg", "Step back, both knees about 90°, push through the front heel."),
                Exercise("Plank", "3 × 40s", "Breathe slowly through it."),
            )
            else -> listOf(
                Exercise("Goblet squat", "3 × 12", "Heavier dumbbell if 12 feels easy."),
                Exercise("Dumbbell Romanian deadlift", "3 × 12", "Feel the stretch in your hamstrings."),
                Exercise("Push-up", "3 × 8", "Knees down if needed. Chest down to fist height."),
                Exercise("One-arm row", "3 × 12 / arm", "Control the way down."),
                Exercise("Split squat", "3 × 10 / leg", "Back knee lowers toward the floor."),
                Exercise("Side plank", "3 × 25s / side", "Hips high, body in a straight line."),
            )
        }
    }

    const val STRENGTH_NOTE =
        "Rest 60–90 s between sets. If the last 2 reps of a set feel easy, go heavier next time. " +
            "Strength training is the best exercise for insulin resistance."

    val mobilityFlow = listOf(
        Exercise("Cat–cow", "1 min", "Slow, move with your breath."),
        Exercise("World's greatest stretch", "1 min / side", "Lunge, hand down, rotate the other arm to the sky."),
        Exercise("Hip flexor stretch", "1 min / side", "Half-kneel, squeeze the back glute, lean gently forward."),
        Exercise("Child's pose", "1 min", "Knees wide, arms long, breathe into your back."),
        Exercise("Hamstring stretch", "1 min / side", "On your back, one leg up, hold behind the thigh."),
    )

    val stretchFlow = listOf(
        Exercise("Butterfly", "1 min", "Soles together, let the knees fall open."),
        Exercise("Seated forward fold", "1 min", "Long spine first, then fold."),
        Exercise("Figure-4 stretch", "1 min / side", "On your back, ankle over the opposite knee."),
        Exercise("Cobra", "1 min", "Hips down, chest lifts gently."),
        Exercise("Thread the needle", "1 min / side", "On all fours, slide one arm under, shoulder to the mat."),
        Exercise("Legs up the wall", "3 min", "Lie back and breathe. Great before sleep."),
    )
}
