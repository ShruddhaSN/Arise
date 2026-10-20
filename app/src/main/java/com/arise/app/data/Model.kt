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

data class DayRecord(
    val date: LocalDate,
    /** Yes/no quests. */
    val done: Map<String, Done> = emptyMap(),
    /** Measurable quests: litres, steps, minutes. */
    val amounts: Map<String, Double> = emptyMap(),
    /** "Not today", with a reason. */
    val skipped: Map<String, Skip> = emptyMap(),
    /** "Later": when a card was sent to the back of the deck (epoch millis). */
    val deferred: Map<String, Long> = emptyMap(),
    /** Set only from the calendar. Null = follow the normal week. */
    val dayType: DayType? = null,
    /** Last night's lights-off time, "HH:mm". */
    val bedTime: String? = null,
    /** This morning's wake time, "HH:mm". Also scores the Rise quest. */
    val wakeTime: String? = null,
    val weight: Double? = null,
    val waist: Double? = null,
    /** 1 (drained) to 5 (great). */
    val energy: Int? = null,
    /** 1 = low, 2 = okay, 3 = good. */
    val protein: Int? = null,
    val longestRunSec: Int? = null,
    val runMinutes: Int? = null,
) {
    val steps: Int? get() = amounts["steps"]?.toInt()
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
) {
    val onboarded: Boolean get() = startDate != null
    val start: LocalDate get() = startDate ?: LocalDate.now()
    val earliest: LocalDate get() = backfillFrom?.let { minOf(it, start) } ?: start
    val name: String get() = playerName.ifBlank { "Player" }
}

data class AppData(
    val records: Map<LocalDate, DayRecord> = emptyMap(),
    val settings: Settings = Settings(),
) {
    fun day(date: LocalDate): DayRecord = records[date] ?: DayRecord(date)
    fun with(rec: DayRecord): AppData = copy(records = records + (rec.date to rec))
}
