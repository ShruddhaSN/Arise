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
        planMemo.getOrPut(date) {
            val rec = data.records[date]
            Program.plan(
                data.settings, date, typeFor(data, date), runLevel(data, date),
                PlanCtx(wake = rec?.wake, event = rec?.event, tomorrow = typeFor(data, date.plusDays(1))),
            )
        }
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
            // A Done on a quest that later became measurable still counts in full.
            Kind.AMOUNT -> if (rec.done[q.id] == Done.FULL) 1f else ((rec.amounts[q.id] ?: 0.0) / q.target).toFloat().coerceIn(0f, 1f)
            Kind.WAKE -> wakeRatio(q.target, rec.wakeTime)
            Kind.ROUTINE -> {
                val need = q.items.filter { !it.optional }
                if (need.isEmpty()) 0f else need.count { it.id in rec.routine }.toFloat() / need.size
            }
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
            Kind.AMOUNT -> (rec.amounts[q.id] ?: 0.0) > 0.0 || rec.done.containsKey(q.id)
            Kind.WAKE -> rec.wakeTime != null
            Kind.ROUTINE -> ratio(q, rec) >= 1f
        }
    }

    fun xpFor(q: Quest, rec: DayRecord?): Int = (q.xp * ratio(q, rec)).roundToInt()

    fun status(data: AppData, date: LocalDate): DayStatus {
        val p = plan(data, date)
        val rec = data.records[date]
        val qs = p.quests
        val required = qs.filter { !it.optional }
        var xp = qs.sumOf { xpFor(it, rec) }
        if (rec != null) {
            if (rec.done.containsKey(penaltyQuest.id)) xp += penaltyQuest.xp
            if (rec.done.containsKey("notes") && qs.any { it.id == "learn" }) xp += NOTES_XP
            if (rec.done.containsKey("recall") && qs.any { it.id == "learn" }) xp += NOTES_XP
            if (rec.done.containsKey(Challenges.weeklyPenaltyQuest.id)) xp += Challenges.weeklyPenaltyQuest.xp
        }
        return DayStatus(p, required.count { counts(it, rec) }, required.size, xp)
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
        val topics = Profile.topics(data.settings)
        val lang = data.settings.language
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
                it != Topic.WILDCARD && it != prev && it in topics && Curriculum.lesson(it, index[it] ?: 0, lang) != null
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
            val lessons = (0 until count).mapNotNull { Curriculum.lesson(topic, i0 + it, lang) }
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

    /** Days where quest [id] was completed in full. */
    private fun countFullQuest(data: AppData, id: String): Int = data.records.values.count { r ->
        val q = plan(data, r.date).quests.firstOrNull { it.id == id }
        q != null && ratio(q, r) >= 1f
    }

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
                plan(d, day).quests.filter { !it.optional }.all { ratio(it, rec) >= 1f }
            } >= 1
        }),
        Boss("week", "Iron Week", "Reach a 7-day streak", 250, check = { d, t -> bestStreak(d, t) >= 7 }),
        Boss("sleep7", "Sleep Sovereign", "Lights off on time (Done) 7 nights in a row", 250, check = { d, t ->
            longestRunOf(d, t) { d.records[it]?.done?.get("sleep") == Done.FULL } >= 7
        }),
        Boss("run5", "The 5-Minute Wall", "Run 5 minutes non-stop", 300, check = { d, _ -> longestRunSec(d) >= 300 }),
        Boss("scholar", "Scholar", "Complete 20 lessons", 300, check = { d, _ -> countLearn(d) >= 20 }),
        Boss("run10", "Ten Unbroken", "Run 10 minutes non-stop", 400, check = { d, _ -> longestRunSec(d) >= 600 }),
        Boss("iron", "Iron Body", "Complete 12 strength sessions at full", 300, check = { d, _ -> countFullQuest(d, "strength") >= 12 }),
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
                if (rec.done.containsKey("recall") && s.plan.quests.any { it.id == "learn" }) {
                    statXp[Stat.INT] = statXp.getValue(Stat.INT) + NOTES_XP
                }
            }
            total += s.xp
            if (s.cleared) cleared++
            d = d.plusDays(1)
        }
        val claimed = data.settings.claimedBosses
        val korean = data.settings.language.equals("Korean", ignoreCase = true)
        val bossList = bosses.filter { korean || !it.id.startsWith("kr_") }
            .map { b -> b to (if (b.claim) b.id in claimed else b.check(data, today)) }
        total += bossList.filter { it.second }.sumOf { it.first.xp }
        total += Challenges.bonusXp(data, today)

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

// ---- Achievements: titles with a story, shown once with a full-screen card ----

data class Achievement(
    val id: String,
    val name: String,
    val quote: String,
    val goal: String,
    val check: (AppData, LocalDate) -> Boolean,
)

/** The System's end-of-day report. */
data class Report(
    val date: LocalDate,
    val done: Int,
    val total: Int,
    val xp: Int,
    val cleared: Boolean,
    val skipped: List<String>,
    val exSkips: Int,
    val bonusDone: Int,
    val bonusTotal: Int,
    val wakeLine: String?,
    val tomorrow: String,
    val unlocks: Int = 0,
    val focusMin: Int = 0,
)

object Feats {
    private fun days(data: AppData, today: LocalDate): Sequence<LocalDate> =
        generateSequence(data.settings.earliest) { it.plusDays(1) }.takeWhile { it <= today }

    private fun countDays(data: AppData, today: LocalDate, test: (LocalDate) -> Boolean): Int = days(data, today).count(test)

    private fun quest(data: AppData, d: LocalDate, id: String): Quest? = Game.plan(data, d).quests.firstOrNull { it.id == id }

    private fun full(data: AppData, d: LocalDate, id: String): Boolean {
        val q = quest(data, d, id) ?: return false
        return Game.ratio(q, data.records[d]) >= 1f
    }

    private fun longest(data: AppData, today: LocalDate, test: (LocalDate) -> Boolean): Int {
        var best = 0
        var cur = 0
        for (d in days(data, today)) {
            if (test(d)) {
                cur++
                best = maxOf(best, cur)
            } else {
                cur = 0
            }
        }
        return best
    }

    val all: List<Achievement> = listOf(
        Achievement("first_light", "First Light", "The first gate opens at dawn.", "Wake up on time once") { d, t ->
            countDays(d, t) { full(d, it, "wake") } >= 1
        },
        Achievement("early_riser", "Early Riser", "I wake up as the sun awakes; I join the world in its wakefulness.", "Wake up on time 7 days in a row") { d, t ->
            longest(d, t) { full(d, it, "wake") } >= 7
        },
        Achievement("ritualist", "Ritualist", "Small rituals, done daily, become who you are.", "Finish the morning routine 7 times") { d, t ->
            countDays(d, t) { full(d, it, "morning") } >= 7
        },
        Achievement("moonlit", "Moonlit Discipline", "The night is where tomorrow is won.", "Finish the night routine 7 times") { d, t ->
            countDays(d, t) { full(d, it, "night") } >= 7
        },
        Achievement("wanderer", "Wanderer", "Every walk after a meal is a quiet victory over the spike.", "10 after-meal walks") { d, _ ->
            d.records.values.sumOf { r -> listOf("walk_lunch", "walk_dinner").count { r.done.containsKey(it) } } >= 10
        },
        Achievement("responder", "First Responder", "When the System calls, you answer.", "Clear 5 emergency quests") { d, _ ->
            d.records.values.sumOf { r -> r.done.keys.count { it.startsWith("emg") } } >= 5
        },
        Achievement("redemption", "Redemption", "Falling is allowed. Staying down isn't.", "Complete a penalty quest") { d, _ ->
            d.records.values.any { it.done.containsKey(Game.penaltyQuest.id) }
        },
        Achievement("voice", "The Voice", "What you can say out loud, you truly know.", "Answer 10 recall questions out loud") { d, _ ->
            d.records.values.sumOf { it.answers.size } >= 10
        },
        Achievement("no_retreat", "No Retreat", "Not one rep skipped.", "Finish a full strength session with zero skips") { d, t ->
            countDays(d, t) { full(d, it, "strength") && d.records[it]?.exSkips.isNullOrEmpty() } >= 1
        },
        Achievement("survivor", "Event Survivor", "Even on the big days, the basics held.", "Clear a day that had an event") { d, t ->
            countDays(d, t) { d.records[it]?.event != null && Game.status(d, it).cleared } >= 1
        },
        Achievement("vanished", "Vanished", "They'll notice when you come back.", "Clear 30 days") { d, t ->
            countDays(d, t) { Game.status(d, it).cleared } >= 30
        },
        Achievement("seven", "Seven Days Strong", "One week. The hardest one.", "Reach a 7-day streak") { d, t ->
            Game.bestStreak(d, t) >= 7
        },
        Achievement("focused", "Focused", "Twenty-five minutes of nothing but the task.", "Finish a Pomodoro focus session") { d, _ ->
            d.records.values.any { (it.amounts["focus_n"] ?: 0.0) >= 1 }
        },
        Achievement("deep_diver", "Deep Diver", "Depth is the new superpower.", "Finish 10 focus sessions") { d, _ ->
            d.records.values.sumOf { it.amounts["focus_n"] ?: 0.0 } >= 10
        },
        Achievement("hydrated", "Like Water", "Be water, my friend.", "Hit your water goal 7 days") { d, t ->
            countDays(d, t) { full(d, it, "water") } >= 7
        },
        Achievement("walker", "Ten Thousand Steps of Will", "Every step is a vote for who you're becoming.", "Hit your step goal 7 days") { d, t ->
            countDays(d, t) { full(d, it, "steps") } >= 7
        },
        Achievement("sealed", "Unshakeable", "The scroll called. You didn't answer.", "7 days with no emergency unlocks of blocked apps") { d, t ->
            d.settings.blocked.isNotEmpty() && countDays(d, t) { (d.records[it]?.amounts?.get("unlocks") ?: 0.0) == 0.0 && d.records[it] != null } >= 7
        },
        Achievement("iron_week", "Iron Will", "Seven days. Not one exception.", "Clear a weekly challenge") { d, t ->
            d.settings.challenges.any { it.kind == com.arise.app.data.ChallengeKind.WEEKLY && Challenges.cleared(d, it, t) }
        },
        Achievement("apprentice", "Apprentice", "A month ago you couldn't. Now you can.", "Clear a monthly skill challenge") { d, t ->
            d.settings.challenges.any { it.kind == com.arise.app.data.ChallengeKind.SKILL && Challenges.cleared(d, it, t) }
        },
        Achievement("reforged", "Reforged", "You chose the hard thing, and finished it.", "Clear a body challenge") { d, t ->
            d.settings.challenges.any { it.kind == com.arise.app.data.ChallengeKind.BODY && Challenges.cleared(d, it, t) }
        },
        Achievement("season_clear", "Season Clear", "You said you would. You did.", "Reach the last day of a challenge") { d, t ->
            d.settings.season > 1 || Season.over(d.settings, t)
        },
    )

    /** Your ratings now: the Day 1 assessment plus everything you've done since. */
    fun ratingsNow(data: AppData, p: Progress): List<Profile.Rating> {
        val day1 = Profile.ratings(data.settings.profile, data.settings.challengeDays.takeIf { it > 0 } ?: 66)
        fun gain(label: String): Int = when (label) {
            "Strength" -> (p.stats.getValue(Stat.STR) - 10) * 2 + (p.stats.getValue(Stat.AGI) - 10)
            "Vitality" -> (p.stats.getValue(Stat.VIT) - 10) * 2 + (p.stats.getValue(Stat.FLX) - 10)
            "Discipline" -> (p.discipline - 10) * 2
            "Focus" -> (p.stats.getValue(Stat.INT) - 10) + p.streak / 2
            "Wisdom" -> (p.stats.getValue(Stat.INT) - 10) * 2
            else -> 0
        }
        val parts = day1.drop(1).map { it.copy(now = (it.now + gain(it.label)).coerceAtMost(99)) }
        return listOf(day1.first().copy(now = parts.sumOf { it.now } / parts.size)) + parts
    }

    /** One before/after line: first week vs the last 7 days. */
    data class Growth(val label: String, val icon: String, val before: String, val after: String, val better: Boolean?)

    /**
     * Before vs after. Compares your first 7 tracked days with the last 7. Needs 10+ days, otherwise
     * the two windows overlap and say nothing.
     */
    fun growth(data: AppData, today: LocalDate): List<Growth> {
        val start = data.settings.earliest
        val days = generateSequence(start) { it.plusDays(1) }.takeWhile { it <= today }.toList()
        if (days.size < 10) return emptyList()
        val first = days.take(7)
        val last = days.takeLast(7)
        fun avg(ds: List<LocalDate>, f: (DayRecord?) -> Double?): Double? = ds.mapNotNull { f(data.records[it]) }.takeIf { it.isNotEmpty() }?.average()
        fun minutesOf(t: String?): Double? = t?.let { runCatching { LocalTime.parse(it) }.getOrNull() }?.let { (it.toSecondOfDay() / 60).toDouble() }
        fun bedScale(t: String?): Double? = minutesOf(t)?.let { if (it < 12 * 60) it + 24 * 60 else it }
        fun clock(m: Double): String = fmt(LocalTime.of(((m.toInt() / 60) % 24), m.toInt() % 60))
        val out = mutableListOf<Growth>()
        fun add(label: String, icon: String, f: (DayRecord?) -> Double?, show: (Double) -> String, lowerIsBetter: Boolean) {
            val a = avg(first, f) ?: return
            val b = avg(last, f) ?: return
            out += Growth(label, icon, show(a), show(b), if (a == b) null else (b < a) == lowerIsBetter)
        }
        add("Wake-up time", "☀", { minutesOf(it?.wakeTime) }, ::clock, true)
        add("Lights off", "🌙", { bedScale(it?.bedTime) }, ::clock, true)
        add("Water a day", "💧", { it?.amounts?.get("water") }, { "%.1f L".format(it) }, false)
        add("Steps a day", "👣", { it?.amounts?.get("steps") }, { "%,d".format(it.toInt()) }, false)
        add("Focus a day", "🎯", { it?.amounts?.get("focus") ?: 0.0 }, { "${it.toInt()} min" }, false)
        val qa = first.map { Game.status(data, it).ratio }.average()
        val qb = last.map { Game.status(data, it).ratio }.average()
        out += Growth("Quests done", "⚔", "${(qa * 100).toInt()}%", "${(qb * 100).toInt()}%", if (qa == qb) null else qb > qa)
        val ws = data.records.values.filter { it.weight != null }.sortedBy { it.date }
        if (ws.size >= 2) {
            val w0 = ws.first().weight!!
            val w1 = ws.last().weight!!
            out += Growth("Weight", "⚖", "%.1f kg".format(w0), "%.1f kg".format(w1), if (w0 == w1) null else w1 < w0)
        }
        return out
    }

    /** Lifetime totals for the insights screen. */
    fun totals(data: AppData, today: LocalDate): Map<String, Int> {
        val days = generateSequence(data.settings.earliest) { it.plusDays(1) }.takeWhile { it <= today }
        return mapOf(
            "Quests done" to days.sumOf { Game.status(data, it).doneCount },
            "Focus minutes" to data.records.values.sumOf { (it.amounts["focus"] ?: 0.0).toInt() },
            "Recall answers" to data.records.values.sumOf { it.answers.size },
            "Skips" to data.records.values.sumOf { it.skipped.size + it.exSkips.size },
        )
    }

    fun unlocked(data: AppData, today: LocalDate): List<Achievement> = all.filter { it.check(data, today) }

    fun lateText(minutes: Int): String = when {
        minutes <= 0 -> "on time"
        minutes < 60 -> "$minutes min late"
        else -> "${minutes / 60}h ${minutes % 60}m late"
    }

    /** What the System says when you tap "I'm up": score, what moved, what unlocked. */
    fun wakeLines(data: AppData, date: LocalDate): List<String> {
        val rec = data.records[date] ?: return emptyList()
        val w = rec.wake ?: return emptyList()
        val plan = Game.plan(data, date)
        val q = plan.quests.firstOrNull { it.kind == Kind.WAKE } ?: return emptyList()
        val goal = java.time.LocalTime.ofSecondOfDay(q.target.toLong() * 60)
        val late = w.toSecondOfDay() / 60 - q.target.toInt()
        return buildList {
            add("Awake at ${fmt(w)}. Goal ${fmt(goal)}: ${lateText(late)}.")
            add("Rise: +${Game.xpFor(q, rec)} VIT XP")
            addAll(plan.notes)
            plan.quests.firstOrNull { it.id == "morning" }?.let { add("Morning routine unlocked: ${it.items.count { i -> !i.optional }} steps.") }
            if (Game.penaltyActive(data, date)) add("A penalty quest is waiting. Clear it today.")
        }
    }

    fun report(data: AppData, date: LocalDate): Report {
        val st = Game.status(data, date)
        val rec = data.records[date]
        val plan = st.plan
        val bonus = plan.quests.filter { it.optional }
        val skipped = plan.quests.mapNotNull { q -> rec?.skipped?.get(q.id)?.let { "${q.title} (${it.label.lowercase()})" } }
        val wakeQ = plan.quests.firstOrNull { it.kind == Kind.WAKE }
        val wakeLine = if (wakeQ != null && rec?.wake != null) {
            val late = rec.wake!!.toSecondOfDay() / 60 - wakeQ.target.toInt()
            "Woke at ${fmt(rec.wake!!)}" + when {
                late <= 0 -> " · on time"
                else -> " · ${late / 60}h ${late % 60}m late"
            }
        } else null
        val tPlan = Game.plan(data, date.plusDays(1))
        val first = tPlan.blocks.firstOrNull { it.quest != null }
        return Report(
            date = date,
            done = st.doneCount,
            total = st.total,
            xp = st.xp,
            cleared = st.cleared,
            skipped = skipped,
            exSkips = rec?.exSkips?.size ?: 0,
            bonusDone = bonus.count { Game.counts(it, rec) },
            bonusTotal = bonus.size,
            wakeLine = wakeLine,
            tomorrow = first?.let { "${fmt(it.time)} · ${it.quest?.title}" } ?: "Rest",
            unlocks = (rec?.amounts?.get("unlocks") ?: 0.0).toInt(),
            focusMin = (rec?.amounts?.get("focus") ?: 0.0).toInt(),
        )
    }
}

/** The challenge: Day X of N, and what happens at the end. */
object Season {
    fun day(s: com.arise.app.data.Settings, date: LocalDate): Int =
        (java.time.temporal.ChronoUnit.DAYS.between(s.currentSeasonStart, date) + 1).toInt()

    /** Past the last day of a challenge with an end. */
    fun over(s: com.arise.app.data.Settings, today: LocalDate): Boolean = s.challengeDays > 0 && day(s, today) > s.challengeDays

    fun label(s: com.arise.app.data.Settings, date: LocalDate): String {
        val d = day(s, date)
        return if (s.challengeDays > 0) "Day $d / ${s.challengeDays}" else "Day $d"
    }
}
