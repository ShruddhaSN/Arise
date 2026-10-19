package com.arise.app.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

enum class DayType(val label: String) {
    OFFICE("Office"),
    WFH("WFH"),
    WEEKEND("Weekend"),
    OFF("Rest / Off"),
}

enum class Done { FULL, MIN }

enum class Stat(val label: String, val full: String) {
    STR("STR", "Strength"),
    AGI("AGI", "Agility · stamina"),
    VIT("VIT", "Vitality · health & sleep"),
    FLX("FLX", "Flexibility"),
    INT("INT", "Intelligence"),
}

data class DayRecord(
    val date: LocalDate,
    val done: Map<String, Done> = emptyMap(),
    val dayType: DayType? = null,
    /** Last night's lights-off time, "HH:mm". */
    val bedTime: String? = null,
    /** This morning's wake time, "HH:mm". */
    val wakeTime: String? = null,
    val weight: Double? = null,
    val waist: Double? = null,
    val steps: Int? = null,
    /** 1 (drained) to 5 (great). */
    val energy: Int? = null,
    val longestRunSec: Int? = null,
    val runMinutes: Int? = null,
)

data class Settings(
    val notifications: Boolean = true,
    /** Shifts the run plan easier (-) or harder (+) than the calendar week. */
    val runWeekShift: Int = 0,
    val playerName: String = "",
    /** Day 1, the day setup was finished. Weeks and penalties count from here. Null = not set up yet. */
    val startDate: LocalDate? = null,
    /** Earliest day that can be filled in, if the user started on paper before installing. */
    val backfillFrom: LocalDate? = null,
    val officeDays: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY),
    val wfhDays: Set<DayOfWeek> = setOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY),
    val wakeWork: LocalTime = LocalTime.of(6, 30),
    val wakeFree: LocalTime = LocalTime.of(7, 30),
    val goalWeight: Double? = null,
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
