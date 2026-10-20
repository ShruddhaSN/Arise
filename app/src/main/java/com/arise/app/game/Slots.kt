package com.arise.app.game

import com.arise.app.data.AppData
import com.arise.app.data.DayType
import java.time.LocalDate
import java.time.LocalTime

/** Where a "Later" card can go. */
data class SlotOption(val at: LocalTime, val label: String, val detail: String, val best: Boolean = false)

data class LaterChoice(val options: List<SlotOption>, val note: String?)

object Slots {
    /** These only make sense at their own time, so they can't be moved. */
    val FIXED = setOf("wake", "phonefree", "sleep", "weigh")

    private val LATEST_ANY = LocalTime.of(22, 0)
    private val LATEST_WORKOUT = LocalTime.of(20, 0)
    private const val MEAL_GAP_MIN = 90L

    private fun meals(s: com.arise.app.data.Settings, type: DayType): List<Pair<String, LocalTime>> {
        val breakfast = when (type) {
            DayType.OFFICE -> LocalTime.of(8, 45)
            DayType.WFH -> LocalTime.of(8, 30)
            else -> Program.wakeGoal(s, type).plusMinutes(75)
        }
        return listOf("breakfast" to breakfast, "lunch" to LocalTime.of(13, 0), "dinner" to LocalTime.of(20, 30))
    }

    /** Rounds up to the next :00 or :30. */
    private fun roundUp(t: LocalTime): LocalTime {
        val m = t.minute
        val base = t.withSecond(0).withNano(0)
        return when {
            m == 0 || m == 30 -> base
            m < 30 -> base.withMinute(30)
            else -> base.withMinute(0).plusHours(1)
        }
    }

    private fun bestWorkoutSlot(data: AppData, date: LocalDate, now: LocalTime): SlotOption? {
        val type = Game.typeFor(data, date)
        val mealList = meals(data.settings, type)
        // Office days: out from 7:30 until about 6 PM.
        val earliest = if (type == DayType.OFFICE) maxOf(now, LocalTime.of(18, 0)) else now.plusMinutes(30)
        var t = roundUp(earliest)
        while (!t.isAfter(LATEST_WORKOUT) && t.isAfter(now)) {
            val lastMeal = mealList.filter { !it.second.isAfter(t) }.maxByOrNull { it.second }
            val sinceMeal = lastMeal?.let { java.time.Duration.between(it.second, t).toMinutes() }
            val okGap = sinceMeal == null || sinceMeal >= MEAL_GAP_MIN
            // Don't start right before a meal either: leave 30 min to finish.
            val nextMeal = mealList.filter { it.second.isAfter(t) }.minByOrNull { it.second }
            val okBefore = nextMeal == null || java.time.Duration.between(t, nextMeal.second).toMinutes() >= 30
            if (okGap && okBefore) {
                val why = when {
                    lastMeal == null -> "before breakfast, empty stomach is fine at this pace"
                    else -> "${sinceMeal!! / 60}h${if (sinceMeal % 60 >= 30) "+" else ""} after ${lastMeal.first}"
                }
                return SlotOption(t, "⭐ ${fmt(t)}", "Best slot: $why", best = true)
            }
            t = t.plusMinutes(30)
        }
        return null
    }

    fun choices(data: AppData, date: LocalDate, q: Quest, now: LocalTime): LaterChoice {
        val opts = mutableListOf<SlotOption>()
        var note: String? = null
        val workout = q.id == "run" || q.id == "strength"
        if (workout) {
            val best = bestWorkoutSlot(data, date, now)
            if (best != null) opts += best
            else note = "No good workout slot is left today (you need 1.5 h after a meal and to finish before 8 PM). Consider Min or Not today."
        }
        val in45 = now.plusMinutes(45)
        val in2h = now.plusHours(2)
        if (in45.isAfter(now) && in45.isBefore(LATEST_ANY) && opts.none { it.at == roundUp(in45) }) {
            opts += SlotOption(in45, "In 45 min", fmt(in45))
        }
        if (!workout && in2h.isAfter(now) && in2h.isBefore(LATEST_ANY)) {
            opts += SlotOption(in2h, "In 2 hours", fmt(in2h))
        }
        val evening = LocalTime.of(19, 0)
        if (!workout && now.isBefore(LocalTime.of(17, 30))) {
            opts += SlotOption(evening, "This evening", fmt(evening))
        }
        if (opts.isEmpty() && note == null) note = "It's late. Better to choose Not today and rest."
        return LaterChoice(opts, note)
    }
}
