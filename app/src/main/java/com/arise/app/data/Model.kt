package com.arise.app.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

enum class DayType(val label: String, val icon: String) {
    OFFICE("Office", "🏢"),
    WFH("WFH", "🏠"),
    WEEKEND("Day off", "🌤"),
    OFF("Leave", "🌴"),
    SICK("Sick", "🤒"),
}

enum class Done { FULL, MIN }

enum class Stat(val label: String, val full: String, val icon: String) {
    STR("STR", "Strength", "⚔"),
    AGI("AGI", "Agility · stamina", "🏃"),
    VIT("VIT", "Vitality · health & sleep", "❤"),
    FLX("FLX", "Flexibility", "🧘"),
    INT("INT", "Intelligence", "🧠"),
}

enum class Skip(val label: String, val icon: String) {
    TIRED("Tired", "😴"),
    NO_TIME("No time", "⏰"),
    SICK("Sick", "🤒"),
    NOPE("Didn't feel like it", "🙅"),
}

/** Why a single exercise (or run interval) was skipped mid-session. */
enum class ExSkip(val label: String, val icon: String) {
    CONFUSED("Didn't understand how", "❓"),
    TOO_HARD("Too hard", "🥵"),
    NOPE("Didn't feel like it", "🙅"),
    PAIN("Pain or discomfort", "🩹"),
    NO_GEAR("Missing equipment", "🧰"),
}

data class ExSkipLog(val exercise: String, val reason: ExSkip)

/**
 * A dinner, wedding, graduation... Quests inside [readyStart]–[end] move around it.
 * An [end] at or before [start] means "until midnight".
 */
data class Event(
    val title: String,
    val start: LocalTime,
    val end: LocalTime,
    val readyMin: Int = 45,
) {
    val readyStart: LocalTime get() = if (start.toSecondOfDay() / 60 > readyMin) start.minusMinutes(readyMin.toLong()) else LocalTime.MIN
    val realEnd: LocalTime get() = if (end <= start) LocalTime.of(23, 59) else end
    val minutes: Int get() = (realEnd.toSecondOfDay() - start.toSecondOfDay()) / 60
    /** Six hours or more: the day is mostly gone, so the System goes easy. */
    val wholeDay: Boolean get() = minutes >= 6 * 60

    fun covers(t: LocalTime): Boolean = !t.isBefore(readyStart) && t.isBefore(realEnd)
}

/** When a routine item applies. */
enum class RWhen(val label: String) {
    DAILY("Every day"),
    WORK("Work / college days"),
    HOME("WFH & days off"),
    BEFORE_HOME("Night before a WFH day or day off"),
    WEEKDAY("One day a week"),
}

/**
 * One step of the morning or night routine, set up by the user.
 * [alternate]: "Serum / Moisturizer" picks one option each day.
 */
data class RoutineDef(
    val id: String,
    val label: String,
    val icon: String = "•",
    val `when`: RWhen = RWhen.DAILY,
    val day: DayOfWeek? = null,
    val optional: Boolean = false,
    val alternate: Boolean = false,
)

/** Meal times. [fixed] = served at set times (PG, hostel, mess); [breakfastEnd] = when breakfast closes. */
data class Meals(
    val breakfast: LocalTime = LocalTime.of(8, 30),
    val breakfastEnd: LocalTime? = null,
    val lunch: LocalTime = LocalTime.of(13, 0),
    val dinner: LocalTime = LocalTime.of(20, 30),
    val fixed: Boolean = false,
)

enum class MedTime(val label: String) {
    WAKE("On waking"),
    BREAKFAST("After breakfast"),
    LUNCH("After lunch"),
    DINNER("After dinner"),
    BED("At bedtime"),
}

data class Med(val name: String, val at: MedTime)

enum class ChallengeKind { WEEKLY, SKILL, BODY }

/**
 * A challenge you took on. [time] = when its daily quest goes in the schedule (skill and body).
 * [keep]: after a skill month ends, it stays as an "active skill" quest. [quit] = given up on that day.
 */
data class Taken(
    val id: String,
    val kind: ChallengeKind,
    val start: LocalDate,
    val time: LocalTime? = null,
    val keep: Boolean = true,
    val quit: LocalDate? = null,
)

data class DayRecord(
    val date: LocalDate,
    /** Yes/no quests. */
    val done: Map<String, Done> = emptyMap(),
    /** Measurable quests: litres, steps, minutes, sets. */
    val amounts: Map<String, Double> = emptyMap(),
    /** "Not today", with a reason. */
    val skipped: Map<String, Skip> = emptyMap(),
    /** "Later": when a card was sent to the back of the deck (epoch millis). */
    val deferred: Map<String, Long> = emptyMap(),
    /** Set only from the calendar. Null = follow the normal week. */
    val dayType: DayType? = null,
    /** Last night's lights-off time, "HH:mm". */
    val bedTime: String? = null,
    /** This morning's wake time, "HH:mm". Scores the Rise quest and shifts the morning. */
    val wakeTime: String? = null,
    val weight: Double? = null,
    val waist: Double? = null,
    /** 1 (drained) to 5 (great). */
    val energy: Int? = null,
    /** 1 = low, 2 = okay, 3 = good. */
    val protein: Int? = null,
    val longestRunSec: Int? = null,
    val runMinutes: Int? = null,
    /** Ticked routine checklist items (morning and night). */
    val routine: Set<String> = emptySet(),
    val event: Event? = null,
    /** Every skip inside a workout, with the reason. */
    val exSkips: List<ExSkipLog> = emptyList(),
    /** Lesson recall: question → what you said. */
    val answers: Map<String, String> = emptyMap(),
) {
    val steps: Int? get() = amounts["steps"]?.toInt()
    val wake: LocalTime? get() = wakeTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
}

data class Settings(
    val notifications: Boolean = true,
    /** Shifts the run plan easier (-) or harder (+) than the calendar week. */
    val runWeekShift: Int = 0,
    val playerName: String = "",
    /** Day 1. Weeks and penalties count from here. Null = not set up yet. */
    val startDate: LocalDate? = null,
    /** Earliest day that can be filled in, if the user started on paper before installing. */
    val backfillFrom: LocalDate? = null,
    val officeDays: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY),
    val wfhDays: Set<DayOfWeek> = setOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY),
    val wakeOffice: LocalTime = LocalTime.of(6, 30),
    val wakeWfh: LocalTime = LocalTime.of(7, 30),
    val wakeOff: LocalTime = LocalTime.of(8, 0),
    val goalWeight: Double? = null,
    /** Day 1 can be moved once. */
    val day1Changed: Boolean = false,
    val tutorialSeen: Boolean = false,
    /** Highest level whose level-up animation has been shown. */
    val seenLevel: Int = 1,
    /** Self-claimed bosses (e.g. Korean milestones). */
    val claimedBosses: Set<String> = emptySet(),
    /** Treadmill settings used last time, km/h and %. */
    val walkSpeed: Double? = null,
    val runSpeed: Double? = null,
    val incline: Double? = null,
    /** Assessment answers: question id → chosen option ids (or a typed value). */
    val profile: Map<String, List<String>> = emptyMap(),
    /** 1 = steady, 2 = hard, 3 = disappear mode. */
    val intensity: Int = 2,
    /** How many times the System has been restarted. */
    val restarts: Int = 0,
    /** The System speaks: coach counts, form tips, pop-ups. */
    val voice: Boolean = true,
    /** Exercises marked "too hard": the next session picks an easier version. */
    val easier: Set<String> = emptySet(),
    /** Exercises that caused pain: never picked again. */
    val avoid: Set<String> = emptySet(),
    /** Achievements whose unlock screen has been shown. */
    val seenAchievements: Set<String> = emptySet(),
    /** Last day the night report was shown. */
    val reportSeen: LocalDate? = null,
    /** Work or college hours, and the one-way commute on office/college days. */
    val workStart: LocalTime = LocalTime.of(9, 0),
    val workEnd: LocalTime = LocalTime.of(17, 0),
    val commuteMin: Int = 45,
    val meals: Meals = Meals(),
    val meds: List<Med> = emptyList(),
    /** Language being learned (e.g. "Korean"); null = none. And the app used for it. */
    val language: String? = null,
    val languageApp: String = "",
    val morning: List<RoutineDef> = DEFAULT_MORNING,
    val night: List<RoutineDef> = DEFAULT_NIGHT,
    /** Challenge length in days. 0 = no end (just keep going). */
    val challengeDays: Int = 66,
    /** Day 1 of the current season (a new one starts each time you continue). Null = [startDate]. */
    val seasonStart: LocalDate? = null,
    val season: Int = 1,
    /** The end-of-challenge screen for this season has been shown. */
    val seasonEndSeen: Boolean = false,
    /** Screen blocker: apps to seal (package names), and when. */
    val blocked: Set<String> = emptySet(),
    val blockMorning: Boolean = true,
    val blockNight: Boolean = false,
    val blockFocus: Boolean = true,
    /** Pomodoro focus length in minutes. */
    val focusMin: Int = 25,
    /** Every weekly, skill and body challenge ever taken, newest last. */
    val challenges: List<Taken> = emptyList(),
) {
    val currentSeasonStart: LocalDate get() = seasonStart ?: start
    /** Office/college days exist. */
    val works: Boolean get() = officeDays.isNotEmpty() || wfhDays.isNotEmpty()
    val student: Boolean get() = "student" in answer("occupation")
    val onboarded: Boolean get() = startDate != null
    val start: LocalDate get() = startDate ?: LocalDate.now()
    val earliest: LocalDate get() = backfillFrom?.let { minOf(it, start) } ?: start
    val name: String get() = playerName.ifBlank { "Player" }

    fun answer(q: String): List<String> = profile[q].orEmpty()
    fun picked(q: String, option: String): Boolean = option in answer(q)
}

data class AppData(
    val records: Map<LocalDate, DayRecord> = emptyMap(),
    val settings: Settings = Settings(),
) {
    fun day(date: LocalDate): DayRecord = records[date] ?: DayRecord(date)
    fun with(rec: DayRecord): AppData = copy(records = records + (rec.date to rec))
}

val DEFAULT_MORNING = listOf(
    RoutineDef("m_brush", "Brush teeth", "🪥"),
    RoutineDef("m_face", "Wash face", "🫧"),
    RoutineDef("m_water", "A glass of water", "💧"),
)

val DEFAULT_NIGHT = listOf(
    RoutineDef("n_skin", "Skincare", "🧴"),
    RoutineDef("n_brush", "Brush teeth", "🪥"),
    RoutineDef("n_phone", "Phone on charge, away from the bed", "🔌"),
)
