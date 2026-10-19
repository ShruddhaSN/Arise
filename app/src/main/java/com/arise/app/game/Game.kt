package com.arise.app.game

import com.arise.app.data.AppData
import com.arise.app.data.DayType
import com.arise.app.data.Done
import com.arise.app.data.Stat
import java.time.LocalDate
import kotlin.math.roundToInt

data class DayStatus(val plan: DayPlan, val doneCount: Int, val total: Int, val xp: Int) {
    val ratio: Float get() = if (total == 0) 0f else doneCount.toFloat() / total
    val cleared: Boolean get() = ratio >= Game.CLEAR_RATIO
}

data class Boss(
    val id: String,
    val name: String,
    val goal: String,
    val xp: Int,
    val check: (AppData, LocalDate) -> Boolean,
)

data class Progress(
    val totalXp: Int,
    val level: Int,
    val rank: String,
    val title: String,
    val xpInLevel: Int,
    val xpLevelSpan: Int,
    val stats: Map<Stat, Int>,
    val discipline: Int,
    val streak: Int,
    val bestStreak: Int,
    val clearedDays: Int,
    val bosses: List<Pair<Boss, Boolean>>,
)

/** All progress is derived from the day records, so nothing can drift out of sync. */
object Game {
    const val CLEAR_RATIO = 0.7f

    val penaltyQuest = Quest(
        "penalty", "PENALTY: Make it right",
        "20 squats and a 10-min walk. Then you're back in the game.",
        null, Stat.STR, 30,
    )

    fun typeFor(data: AppData, date: LocalDate): DayType =
        data.records[date]?.dayType ?: Program.defaultType(data.settings, date)

    fun plan(data: AppData, date: LocalDate): DayPlan =
        Program.plan(data.settings, date, typeFor(data, date))

    fun xpFor(q: Quest, d: Done): Int = if (d == Done.FULL) q.xp else (q.xp * 0.2f).roundToInt()

    fun status(data: AppData, date: LocalDate): DayStatus {
        val p = plan(data, date)
        val done = data.records[date]?.done ?: emptyMap()
        val qs = p.quests
        var xp = qs.sumOf { q -> done[q.id]?.let { xpFor(q, it) } ?: 0 }
        if (done.containsKey(penaltyQuest.id)) xp += penaltyQuest.xp
        return DayStatus(p, qs.count { done.containsKey(it.id) }, qs.size, xp)
    }

    /** Never miss twice: if yesterday wasn't cleared, today starts with a penalty quest. */
    fun penaltyActive(data: AppData, date: LocalDate): Boolean {
        val y = date.minusDays(1)
        // Filled-in days before Day 1, and Day 1 itself, never trigger a penalty.
        return data.settings.onboarded && y > data.settings.start && !status(data, y).cleared
    }

    fun passesUsed(data: AppData, date: LocalDate): Int {
        val start = Program.weekStart(date)
        return (0L..6L).sumOf { d ->
            data.records[start.plusDays(d)]?.done?.values?.count { it == Done.MIN } ?: 0
        }
    }

    fun passesLeft(data: AppData, date: LocalDate): Int =
        (Program.MIN_PASSES_PER_WEEK - passesUsed(data, date)).coerceAtLeast(0)

    fun streak(data: AppData, today: LocalDate): Int {
        var d = if (status(data, today).cleared) today else today.minusDays(1)
        var n = 0
        while (d >= data.settings.earliest && status(data, d).cleared) {
            n++
            d = d.minusDays(1)
        }
        return n
    }

    private fun longestRunOf(data: AppData, today: LocalDate, test: (LocalDate) -> Boolean): Int {
        var best = 0
        var cur = 0
        var d = data.settings.earliest
        while (d <= today) {
            if (test(d)) {
                cur++
                best = maxOf(best, cur)
            } else {
                cur = 0
            }
            d = d.plusDays(1)
        }
        return best
    }

    fun bestStreak(data: AppData, today: LocalDate): Int =
        longestRunOf(data, today) { status(data, it).cleared }

    private fun countDone(data: AppData, id: String, fullOnly: Boolean = false): Int =
        data.records.values.count { r ->
            val d = r.done[id]
            d != null && (!fullOnly || d == Done.FULL)
        }

    fun longestRunSec(data: AppData): Int = data.records.values.maxOfOrNull { it.longestRunSec ?: 0 } ?: 0

    private fun weightDrop(data: AppData): Double {
        val ws = data.records.values.filter { it.weight != null }.sortedBy { it.date }
        if (ws.size < 2) return 0.0
        return ws.first().weight!! - ws.last().weight!!
    }

    val bosses: List<Boss> = listOf(
        Boss("gate", "The First Gate", "Clear your first day (70% of quests)", 100) { d, t ->
            longestRunOf(d, t) { status(d, it).cleared } >= 1
        },
        Boss("perfect", "Perfect Day", "Every quest done at FULL in a single day", 150) { d, t ->
            longestRunOf(d, t) { day ->
                val s = status(d, day)
                val done = d.records[day]?.done ?: emptyMap()
                s.plan.quests.all { done[it.id] == Done.FULL }
            } >= 1
        },
        Boss("week", "Iron Week", "Reach a 7-day streak", 250) { d, t -> bestStreak(d, t) >= 7 },
        Boss("sleep7", "Sleep Sovereign", "Sleep quest at FULL for 7 nights in a row", 250) { d, t ->
            longestRunOf(d, t) { d.records[it]?.done?.get("sleep") == Done.FULL } >= 7
        },
        Boss("run5", "The 5-Minute Wall", "Run 5 minutes non-stop", 300) { d, _ -> longestRunSec(d) >= 300 },
        Boss("scholar", "Scholar", "Complete 20 learning sessions", 300) { d, _ -> countDone(d, "learn") >= 20 },
        Boss("run10", "Ten Unbroken", "Run 10 minutes non-stop", 400) { d, _ -> longestRunSec(d) >= 600 },
        Boss("iron", "Iron Body", "Complete 12 strength sessions at FULL", 300) { d, _ ->
            countDone(d, "strength", fullOnly = true) >= 12
        },
        Boss("scale2", "Scale Shift", "Weight down 2 kg from your first log", 300) { d, _ -> weightDrop(d) >= 2.0 },
        Boss("streak21", "21 Days of Steel", "Reach a 21-day streak", 500) { d, t -> bestStreak(d, t) >= 21 },
        Boss("scale5", "Metamorphosis", "Weight down 5 kg from your first log", 600) { d, _ -> weightDrop(d) >= 5.0 },
        Boss("run20", "FINAL BOSS: The 20-Minute Run", "Run 20 minutes non-stop", 1000) { d, _ ->
            longestRunSec(d) >= 1200
        },
    )

    /** Level L starts at 50·L·(L−1) XP, so each level needs 100 more XP than the last. */
    private fun levelStart(l: Int): Int = 50 * l * (l - 1)

    fun rankFor(level: Int): String = when {
        level < 5 -> "E"
        level < 10 -> "D"
        level < 15 -> "C"
        level < 20 -> "B"
        level < 25 -> "A"
        else -> "S"
    }

    private fun titleFor(rank: String): String = when (rank) {
        "E" -> "Awakened Hunter"
        "D" -> "Rising Hunter"
        "C" -> "Seasoned Hunter"
        "B" -> "Elite Hunter"
        "A" -> "National-level Hunter"
        else -> "Shadow Monarch"
    }

    fun progress(data: AppData, today: LocalDate): Progress {
        val statXp = Stat.entries.associateWith { 0 }.toMutableMap()
        var total = 0
        var cleared = 0
        var d = data.settings.earliest
        while (d <= today) {
            val s = status(data, d)
            val done = data.records[d]?.done ?: emptyMap()
            s.plan.quests.forEach { q ->
                done[q.id]?.let { statXp[q.stat] = statXp.getValue(q.stat) + xpFor(q, it) }
            }
            if (done.containsKey(penaltyQuest.id)) {
                statXp[penaltyQuest.stat] = statXp.getValue(penaltyQuest.stat) + penaltyQuest.xp
            }
            total += s.xp
            if (s.cleared) cleared++
            d = d.plusDays(1)
        }
        val bossList = bosses.map { it to it.check(data, today) }
        total += bossList.filter { it.second }.sumOf { it.first.xp }

        var level = 1
        while (levelStart(level + 1) <= total) level++
        val rank = rankFor(level)
        val best = bestStreak(data, today)
        return Progress(
            totalXp = total,
            level = level,
            rank = rank,
            title = titleFor(rank),
            xpInLevel = total - levelStart(level),
            xpLevelSpan = levelStart(level + 1) - levelStart(level),
            stats = statXp.mapValues { 10 + it.value / 100 },
            discipline = 10 + cleared + best / 2,
            streak = streak(data, today),
            bestStreak = best,
            clearedDays = cleared,
            bosses = bossList,
        )
    }
}
