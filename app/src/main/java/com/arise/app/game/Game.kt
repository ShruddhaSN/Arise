package com.arise.app.game

import com.arise.app.data.AppData
import com.arise.app.data.DayRecord
import com.arise.app.data.DayType
import com.arise.app.data.Done
import com.arise.app.data.Stat
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

data class DayStatus(
    val plan: DayPlan,
    val doneCount: Int,
    val total: Int,
    val xp: Int,
) {
    val ratio: Float get() = if (total == 0) 0f else doneCount.toFloat() / total
    /** Sick days are neutral: they never clear, never break a streak, never cause a penalty. */
    val sick: Boolean get() = plan.type == DayType.SICK
    val cleared: Boolean get() = !sick && ratio >= Game.CLEAR_RATIO
}

data class Boss(
    val id: String,
    val name: String,
    val goal: String,
    val xp: Int,
    /** Claimed by the player ("I did it") instead of detected. */
    val claim: Boolean = false,
    val check: (AppData, LocalDate) -> Boolean = { _, _ -> false },
)

data class DayLesson(val topic: Topic, val lessons: List<Lesson>)

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
    /** XP of a solid day. Level targets are "N good days" of this. */
    const val GOOD_DAY_XP = 250
    const val NOTES_XP = 10

    val penaltyQuest = Quest(
        "penalty", "PENALTY: Make it right",
        "20 squats and a 10-min walk. Then you're back in the game.",
        null, Stat.STR, 30, "⚠",
    )

    fun typeFor(data: AppData, date: LocalDate): DayType =
        data.records[date]?.dayType ?: Program.defaultType(data.settings, date)

    // Plans are computed a lot (charts, streaks, bosses), so cache them for the current data snapshot.
    private val lock = Any()
    private var memoData: AppData? = null
    private val planMemo = HashMap<LocalDate, DayPlan>()
    private val levelMemo = HashMap<Int, Int>()

    private fun <T> memo(data: AppData, block: () -> T): T = synchronized(lock) {
        if (memoData !== data) {
            memoData = data
            planMemo.clear()
            levelMemo.clear()
        }
        block()
    }

    fun plan(data: AppData, date: LocalDate): DayPlan = memo(data) {
        planMemo.getOrPut(date) { Program.plan(data.settings, date, typeFor(data, date), runLevel(data, date)) }
    }

    /** Full runs needed in a week to move the run plan up. */
    const val FULL_RUNS_TO_LEVEL = 2

    private fun runTarget(data: AppData, level: Int): Int =
        Program.runPlan((level + data.settings.runWeekShift).coerceAtLeast(1)).runs.size

    /** Runs completed in full during program week [week], judged against run level [level]. */
    fun fullRunsInWeek(data: AppData, week: Int, level: Int): Int {
        val s = data.settings
        val start = s.start.plusDays((week - 1) * 7L)
        val target = runTarget(data, level)
        return (0L..6L).count { d ->
            val day = start.plusDays(d)
            val rec = data.records[day]
            day != s.earliest && rec != null &&
                ((rec.amounts["run"] ?: 0.0) >= target || rec.done["run"] == Done.FULL)
        }
    }

    /**
     * The run plan week you've earned: it only goes up after a week with [FULL_RUNS_TO_LEVEL] full runs,
     * otherwise the week repeats.
     */
    fun runLevel(data: AppData, date: LocalDate): Int = memo(data) {
        val target = Program.week(data.settings, date)
        levelMemo.getOrPut(target) {
            var level = 1
            for (w in 1 until target) {
                if (fullRunsInWeek(data, w, level) >= FULL_RUNS_TO_LEVEL) level++
            }
            level
        }
    }

    // ---- Scoring a single quest ----

    private fun wakeRatio(goalMinutes: Double, wake: String?): Float {
        val t = wake?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: return 0f
        val late = t.toSecondOfDay() / 60 - goalMinutes
        return when {
            late <= 0 -> 1f
            late <= 30 -> 0.75f
            late <= 60 -> 0.5f
            else -> 0.2f
        }
    }

    /** 0..1: how much of the quest's XP was earned. */
    fun ratio(q: Quest, rec: DayRecord?): Float {
        if (rec == null) return 0f
        return when (q.kind) {
            Kind.CHECK -> when (rec.done[q.id]) {
                Done.FULL -> 1f
                Done.MIN -> 0.2f
                null -> 0f
            }
            Kind.AMOUNT -> ((rec.amounts[q.id] ?: 0.0) / q.target).toFloat().coerceIn(0f, 1f)
            Kind.WAKE -> wakeRatio(q.target, rec.wakeTime)
        }
    }

    /** Counts toward clearing the day: any Done/Min, or at least half of a measurable target. */
    fun counts(q: Quest, rec: DayRecord?): Boolean {
        if (rec == null) return false
        return when (q.kind) {
            Kind.CHECK -> rec.done.containsKey(q.id)
            else -> ratio(q, rec) >= 0.5f
        }
    }

    /** Finished with for today, so the card leaves the deck. */
    fun settled(q: Quest, rec: DayRecord?): Boolean {
        if (rec == null) return false
        if (rec.skipped.containsKey(q.id)) return true
        return when (q.kind) {
            Kind.CHECK -> rec.done.containsKey(q.id)
            Kind.AMOUNT -> (rec.amounts[q.id] ?: 0.0) > 0.0
            Kind.WAKE -> rec.wakeTime != null
        }
    }

    fun xpFor(q: Quest, rec: DayRecord?): Int = (q.xp * ratio(q, rec)).roundToInt()

    fun status(data: AppData, date: LocalDate): DayStatus {
        val p = plan(data, date)
        val rec = data.records[date]
        val qs = p.quests
        var xp = qs.sumOf { xpFor(it, rec) }
        if (rec != null) {
            if (rec.done.containsKey(penaltyQuest.id)) xp += penaltyQuest.xp
            if (rec.done.containsKey("notes") && qs.any { it.id == "learn" }) xp += NOTES_XP
        }
        return DayStatus(p, qs.count { counts(it, rec) }, qs.size, xp)
    }

    /** Never miss twice. Day 1, fill-in days and sick days never cause a penalty. */
    fun penaltyActive(data: AppData, date: LocalDate): Boolean {
        val y = date.minusDays(1)
        if (!data.settings.onboarded || y <= data.settings.start) return false
        val s = status(data, y)
        return !s.sick && !s.cleared
    }

    /** Only Min on yes/no quests uses a pass (an old Min on a quest that is now measurable is refunded). */
    fun passesUsed(data: AppData, date: LocalDate): Int {
        val start = Program.weekStart(date)
        return (0L..6L).sumOf { d ->
            val day = start.plusDays(d)
            val rec = data.records[day] ?: return@sumOf 0
            val checkIds = plan(data, day).quests.filter { it.kind == Kind.CHECK }.map { it.id }.toSet()
            rec.done.count { it.value == Done.MIN && it.key in checkIds }
        }
    }

    fun passesLeft(data: AppData, date: LocalDate): Int =
        (Program.MIN_PASSES_PER_WEEK - passesUsed(data, date)).coerceAtLeast(0)

    fun streak(data: AppData, today: LocalDate): Int {
        var d = today
        val first = data.settings.earliest
        val todayStatus = status(data, today)
        if (!todayStatus.cleared) d = today.minusDays(1)
        var n = 0
        while (d >= first) {
            val s = status(data, d)
            when {
                s.sick -> Unit
                s.cleared -> n++
                else -> return n
            }
            d = d.minusDays(1)
        }
        return n
    }

    /** Longest run of days passing [test]; sick days are skipped over. */
    private fun longestRunOf(data: AppData, today: LocalDate, test: (LocalDate) -> Boolean): Int {
        var best = 0
        var cur = 0
        var d = data.settings.earliest
        while (d <= today) {
            if (typeFor(data, d) != DayType.SICK) {
                if (test(d)) {
                    cur++
                    best = maxOf(best, cur)
                } else {
                    cur = 0
                }
            }
            d = d.plusDays(1)
        }
        return best
    }

    fun bestStreak(data: AppData, today: LocalDate): Int =
        longestRunOf(data, today) { status(data, it).cleared }

    // ---- Lessons: a smart shuffle that is the same every time you open the app ----

    private fun <T> weighted(items: List<T>, rnd: java.util.Random, weight: (T) -> Double): T {
        val total = items.sumOf(weight)
        var r = rnd.nextDouble() * total
        for (x in items) {
            r -= weight(x)
            if (r <= 0) return x
        }
        return items.last()
    }

    /**
     * Replays every day from the start so the pick is stable. Rules: never the same topic twice in a row,
     * topics not seen for a while are more likely, about 1 weekday in 7 is a wildcard, weekends get two lessons.
     * A topic only moves on to its next lesson once the lesson quest was at least half done.
     */
    fun lessonFor(data: AppData, date: LocalDate): DayLesson? {
        val start = data.settings.earliest
        if (date < start) return null
        val index = mutableMapOf<Topic, Int>()
        val lastSeen = mutableMapOf<Topic, Int>()
        var prev: Topic? = null
        var n = 0
        var d = start
        while (d <= date) {
            val type = typeFor(data, d)
            val weekend = Program.isWeekend(type)
            val rnd = java.util.Random(d.toEpochDay() * 7919L + 17L)
            val pool = Topic.entries.filter {
                it != Topic.WILDCARD && it != prev && Curriculum.lesson(it, index[it] ?: 0) != null
            }
            val wild = !weekend && rnd.nextDouble() < 0.14
            val topic = when {
                n == 0 && Topic.LEARN in pool -> Topic.LEARN
                wild || pool.isEmpty() -> Topic.WILDCARD
                else -> weighted(pool, rnd) { t ->
                    val gap = (n - (lastSeen[t] ?: -4)).coerceAtLeast(1).toDouble()
                    t.weight * gap * gap
                }
            }
            val i0 = index[topic] ?: 0
            val count = if (weekend && topic != Topic.WILDCARD) 2 else 1
            val lessons = (0 until count).mapNotNull { Curriculum.lesson(topic, i0 + it) }
            if (d == date) return DayLesson(topic, lessons)

            val learnQ = plan(data, d).quests.firstOrNull { it.id == "learn" }
            if (learnQ != null && ratio(learnQ, data.records[d]) >= 0.5f) index[topic] = i0 + lessons.size
            lastSeen[topic] = n
            prev = topic
            n++
            d = d.plusDays(1)
        }
        return null
    }

    // ---- Bosses ----

    private fun countFull(data: AppData, id: String): Int =
        data.records.values.count { it.done[id] == Done.FULL }

    private fun countLearn(data: AppData): Int = data.records.values.count { r ->
        val q = plan(data, r.date).quests.firstOrNull { it.id == "learn" }
        q != null && counts(q, r)
    }

    fun longestRunSec(data: AppData): Int = data.records.values.maxOfOrNull { it.longestRunSec ?: 0 } ?: 0

    private fun weightDrop(data: AppData): Double {
        val ws = data.records.values.filter { it.weight != null }.sortedBy { it.date }
        if (ws.size < 2) return 0.0
        return ws.first().weight!! - ws.last().weight!!
    }

    val bosses: List<Boss> = listOf(
        Boss("gate", "The First Gate", "Clear your first day (70% of quests)", 100, check = { d, t ->
            longestRunOf(d, t) { status(d, it).cleared } >= 1
        }),
        Boss("perfect", "Perfect Day", "Every quest at 100% in a single day", 150, check = { d, t ->
            longestRunOf(d, t) { day ->
                val rec = d.records[day]
                plan(d, day).quests.all { ratio(it, rec) >= 1f }
            } >= 1
        }),
        Boss("week", "Iron Week", "Reach a 7-day streak", 250, check = { d, t -> bestStreak(d, t) >= 7 }),
        Boss("sleep7", "Sleep Sovereign", "Lights off on time (Done) 7 nights in a row", 250, check = { d, t ->
            longestRunOf(d, t) { d.records[it]?.done?.get("sleep") == Done.FULL } >= 7
        }),
        Boss("run5", "The 5-Minute Wall", "Run 5 minutes non-stop", 300, check = { d, _ -> longestRunSec(d) >= 300 }),
        Boss("scholar", "Scholar", "Complete 20 lessons", 300, check = { d, _ -> countLearn(d) >= 20 }),
        Boss("run10", "Ten Unbroken", "Run 10 minutes non-stop", 400, check = { d, _ -> longestRunSec(d) >= 600 }),
        Boss("iron", "Iron Body", "Complete 12 strength sessions at full", 300, check = { d, _ -> countFull(d, "strength") >= 12 }),
        Boss("scale2", "Scale Shift", "Weight down 2 kg from your first weigh-in", 300, check = { d, _ -> weightDrop(d) >= 2.0 }),
        Boss("streak21", "21 Days of Steel", "Reach a 21-day streak", 500, check = { d, t -> bestStreak(d, t) >= 21 }),
        Boss("scale5", "Metamorphosis", "Weight down 5 kg from your first weigh-in", 600, check = { d, _ -> weightDrop(d) >= 5.0 }),
        Boss("run20", "FINAL BOSS: The 20-Minute Run", "Run 20 minutes non-stop", 1000, check = { d, _ -> longestRunSec(d) >= 1200 }),
        // Korean milestones: claimed by you when you've honestly done them.
        Boss("kr_hangul", "🇰🇷 Hangul Master", "Read 20 random Hangul words out loud without stopping", 200, claim = true),
        Boss("kr_intro", "🇰🇷 Self-introduction", "Introduce yourself in Korean for 1 minute, no notes", 250, claim = true),
        Boss("kr_ttmik1", "🇰🇷 TTMIK Level 1 cleared", "Finish all 25 Level 1 lessons", 400, claim = true),
        Boss("kr_easy", "🇰🇷 Easy Korean, no English", "Watch a full Easy Korean video with only Korean subtitles", 400, claim = true),
        Boss("kr_topik", "👑 TOPIK I practice test", "Score level 1 on a TOPIK I practice test. Opens a hidden gate.", 800, claim = true),
    )

    // ---- Levels: small, big, small, big. Never more than ~3.5 weeks between levels. ----

    private val levelDays = listOf(
        1, 2, 2, 3, // → D at Lv 5
        3, 5, 4, 6, 5, // → C at Lv 10
        7, 6, 9, 8, 10, // → B at Lv 15
        9, 12, 11, 14, 13, // → A at Lv 20
        16, 15, 19, 18, 24, // → S at Lv 25
    )

    /** Good days needed to go from [level] to level + 1. */
    fun daysFor(level: Int): Int = levelDays.getOrElse(level - 1) { if ((level - 25) % 2 == 0) 21 else 24 }

    private fun levelStart(level: Int): Int = GOOD_DAY_XP * (1 until level).sumOf { daysFor(it) }

    fun rankFor(level: Int): String = when {
        level < 5 -> "E"
        level < 10 -> "D"
        level < 15 -> "C"
        level < 20 -> "B"
        level < 25 -> "A"
        else -> "S"
    }

    fun titleFor(rank: String): String = when (rank) {
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
            val rec = data.records[d]
            s.plan.quests.forEach { q -> statXp[q.stat] = statXp.getValue(q.stat) + xpFor(q, rec) }
            if (rec != null) {
                if (rec.done.containsKey(penaltyQuest.id)) {
                    statXp[penaltyQuest.stat] = statXp.getValue(penaltyQuest.stat) + penaltyQuest.xp
                }
                if (rec.done.containsKey("notes") && s.plan.quests.any { it.id == "learn" }) {
                    statXp[Stat.INT] = statXp.getValue(Stat.INT) + NOTES_XP
                }
            }
            total += s.xp
            if (s.cleared) cleared++
            d = d.plusDays(1)
        }
        val claimed = data.settings.claimedBosses
        val bossList = bosses.map { b -> b to (if (b.claim) b.id in claimed else b.check(data, today)) }
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
