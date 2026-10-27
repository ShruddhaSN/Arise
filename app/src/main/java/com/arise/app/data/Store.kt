package com.arise.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Everything lives in one JSON blob in SharedPreferences. Small, simple, easy to back up. */
object Store {
    private const val PREFS = "arise"
    private const val KEY = "data_v1"
    private const val SNAPSHOT = "data_before_restart"

    fun load(ctx: Context): AppData {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return AppData()
        return runCatching { fromJson(raw) }.getOrDefault(AppData())
    }

    fun save(ctx: Context, data: AppData) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, toJson(data))
            .apply()
    }

    fun toJson(data: AppData): String {
        val root = JSONObject()
        root.put("version", 3)
        val s = data.settings
        root.put("settings", JSONObject().apply {
            put("notifications", s.notifications)
            put("runWeekShift", s.runWeekShift)
            put("playerName", s.playerName)
            s.startDate?.let { put("startDate", it.toString()) }
            s.backfillFrom?.let { put("backfillFrom", it.toString()) }
            put("officeDays", JSONArray(s.officeDays.map { it.name }))
            put("wfhDays", JSONArray(s.wfhDays.map { it.name }))
            put("wakeOffice", s.wakeOffice.toString())
            put("wakeWfh", s.wakeWfh.toString())
            put("wakeOff", s.wakeOff.toString())
            s.goalWeight?.let { put("goalWeight", it) }
            put("day1Changed", s.day1Changed)
            put("tutorialSeen", s.tutorialSeen)
            put("seenLevel", s.seenLevel)
            put("claimedBosses", JSONArray(s.claimedBosses.toList()))
            s.walkSpeed?.let { put("walkSpeed", it) }
            s.runSpeed?.let { put("runSpeed", it) }
            s.incline?.let { put("incline", it) }
            put("profile", JSONObject().apply { s.profile.forEach { (k, v) -> put(k, JSONArray(v)) } })
            put("intensity", s.intensity)
            put("restarts", s.restarts)
            put("voice", s.voice)
            put("easier", JSONArray(s.easier.toList()))
            put("avoid", JSONArray(s.avoid.toList()))
            put("seenAchievements", JSONArray(s.seenAchievements.toList()))
            s.reportSeen?.let { put("reportSeen", it.toString()) }
            put("workStart", s.workStart.toString())
            put("workEnd", s.workEnd.toString())
            put("commuteMin", s.commuteMin)
            put("meals", JSONObject().apply {
                put("breakfast", s.meals.breakfast.toString())
                s.meals.breakfastEnd?.let { put("breakfastEnd", it.toString()) }
                put("lunch", s.meals.lunch.toString())
                put("dinner", s.meals.dinner.toString())
                put("fixed", s.meals.fixed)
            })
            put("meds", JSONArray(s.meds.map { JSONObject().put("name", it.name).put("at", it.at.name) }))
            s.language?.let { put("language", it) }
            put("languageApp", s.languageApp)
            put("morning", routineJson(s.morning))
            put("night", routineJson(s.night))
            put("challengeDays", s.challengeDays)
            s.seasonStart?.let { put("seasonStart", it.toString()) }
            put("season", s.season)
            put("seasonEndSeen", s.seasonEndSeen)
            put("blocked", JSONArray(s.blocked.toList()))
            put("blockMorning", s.blockMorning)
            put("blockNight", s.blockNight)
            put("blockFocus", s.blockFocus)
            put("focusMin", s.focusMin)
            put("challenges", JSONArray(s.challenges.map { c ->
                JSONObject().apply {
                    put("id", c.id)
                    put("kind", c.kind.name)
                    put("start", c.start.toString())
                    c.time?.let { put("time", it.toString()) }
                    put("keep", c.keep)
                    c.quit?.let { put("quit", it.toString()) }
                }
            }))
        })
        val days = JSONArray()
        data.records.values.sortedBy { it.date }.forEach { r ->
            days.put(JSONObject().apply {
                put("date", r.date.toString())
                put("done", JSONObject().apply { r.done.forEach { (k, v) -> put(k, v.name) } })
                put("amounts", JSONObject().apply { r.amounts.forEach { (k, v) -> put(k, v) } })
                put("skipped", JSONObject().apply { r.skipped.forEach { (k, v) -> put(k, v.name) } })
                put("deferred", JSONObject().apply { r.deferred.forEach { (k, v) -> put(k, v) } })
                r.dayType?.let { put("dayType", it.name) }
                r.bedTime?.let { put("bedTime", it) }
                r.wakeTime?.let { put("wakeTime", it) }
                r.weight?.let { put("weight", it) }
                r.waist?.let { put("waist", it) }
                r.energy?.let { put("energy", it) }
                r.protein?.let { put("protein", it) }
                r.longestRunSec?.let { put("longestRunSec", it) }
                r.runMinutes?.let { put("runMinutes", it) }
                if (r.routine.isNotEmpty()) put("routine", JSONArray(r.routine.toList()))
                r.event?.let { e ->
                    put("event", JSONObject().apply {
                        put("title", e.title)
                        put("start", e.start.toString())
                        put("end", e.end.toString())
                        put("readyMin", e.readyMin)
                    })
                }
                if (r.exSkips.isNotEmpty()) {
                    put("exSkips", JSONArray(r.exSkips.map { JSONObject().put("exercise", it.exercise).put("reason", it.reason.name) }))
                }
                if (r.answers.isNotEmpty()) put("answers", JSONObject().apply { r.answers.forEach { (k, v) -> put(k, v) } })
            })
        }
        root.put("days", days)
        return root.toString()
    }

    /** Throws on invalid input, so imports can report a bad backup. Reads v1 data too. */
    fun fromJson(raw: String): AppData {
        val root = JSONObject(raw)
        val sj = root.optJSONObject("settings") ?: JSONObject()
        val d = Settings()
        val settings = Settings(
            notifications = sj.optBoolean("notifications", true),
            runWeekShift = sj.optInt("runWeekShift", 0),
            playerName = sj.optString("playerName", ""),
            startDate = sj.str("startDate")?.let(LocalDate::parse),
            backfillFrom = sj.str("backfillFrom")?.let(LocalDate::parse),
            officeDays = sj.days("officeDays") ?: d.officeDays,
            wfhDays = sj.days("wfhDays") ?: d.wfhDays,
            // v1 stored one "wakeWork" time for office and WFH.
            wakeOffice = (sj.str("wakeOffice") ?: sj.str("wakeWork"))?.let(LocalTime::parse) ?: d.wakeOffice,
            wakeWfh = sj.str("wakeWfh")?.let(LocalTime::parse) ?: d.wakeWfh,
            wakeOff = sj.str("wakeOff")?.let(LocalTime::parse) ?: d.wakeOff,
            goalWeight = sj.dbl("goalWeight"),
            day1Changed = sj.optBoolean("day1Changed", false),
            tutorialSeen = sj.optBoolean("tutorialSeen", false),
            seenLevel = sj.optInt("seenLevel", 1),
            claimedBosses = sj.optJSONArray("claimedBosses")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
                ?: emptySet(),
            walkSpeed = sj.dbl("walkSpeed"),
            runSpeed = sj.dbl("runSpeed"),
            incline = sj.dbl("incline"),
            profile = sj.optJSONObject("profile")?.let { p ->
                p.keys().asSequence().associateWith { k -> p.optJSONArray(k)?.strings().orEmpty() }
            } ?: emptyMap(),
            intensity = sj.optInt("intensity", d.intensity),
            restarts = sj.optInt("restarts", 0),
            voice = sj.optBoolean("voice", true),
            easier = sj.optJSONArray("easier")?.strings()?.toSet() ?: emptySet(),
            avoid = sj.optJSONArray("avoid")?.strings()?.toSet() ?: emptySet(),
            seenAchievements = sj.optJSONArray("seenAchievements")?.strings()?.toSet() ?: emptySet(),
            reportSeen = sj.str("reportSeen")?.let(LocalDate::parse),
            workStart = sj.str("workStart")?.let(LocalTime::parse) ?: d.workStart,
            workEnd = sj.str("workEnd")?.let(LocalTime::parse) ?: d.workEnd,
            commuteMin = sj.optInt("commuteMin", d.commuteMin),
            meals = sj.optJSONObject("meals")?.let { m ->
                Meals(
                    breakfast = m.str("breakfast")?.let(LocalTime::parse) ?: d.meals.breakfast,
                    breakfastEnd = m.str("breakfastEnd")?.let(LocalTime::parse),
                    lunch = m.str("lunch")?.let(LocalTime::parse) ?: d.meals.lunch,
                    dinner = m.str("dinner")?.let(LocalTime::parse) ?: d.meals.dinner,
                    fixed = m.optBoolean("fixed", false),
                )
            } ?: d.meals,
            meds = sj.optJSONArray("meds")?.let { a ->
                (0 until a.length()).mapNotNull { i ->
                    val j = a.getJSONObject(i)
                    runCatching { Med(j.getString("name"), MedTime.valueOf(j.getString("at"))) }.getOrNull()
                }
            } ?: emptyList(),
            language = sj.str("language"),
            languageApp = sj.optString("languageApp", ""),
            morning = sj.optJSONArray("morning")?.let(::routineFrom) ?: d.morning,
            night = sj.optJSONArray("night")?.let(::routineFrom) ?: d.night,
            challengeDays = sj.optInt("challengeDays", d.challengeDays),
            seasonStart = sj.str("seasonStart")?.let(LocalDate::parse),
            season = sj.optInt("season", 1),
            seasonEndSeen = sj.optBoolean("seasonEndSeen", false),
            blocked = sj.optJSONArray("blocked")?.strings()?.toSet() ?: emptySet(),
            blockMorning = sj.optBoolean("blockMorning", true),
            blockNight = sj.optBoolean("blockNight", false),
            blockFocus = sj.optBoolean("blockFocus", true),
            focusMin = sj.optInt("focusMin", 25),
            challenges = sj.optJSONArray("challenges")?.let { a ->
                (0 until a.length()).mapNotNull { i ->
                    val c = a.getJSONObject(i)
                    runCatching {
                        Taken(
                            id = c.getString("id"),
                            kind = ChallengeKind.valueOf(c.getString("kind")),
                            start = LocalDate.parse(c.getString("start")),
                            time = c.str("time")?.let(LocalTime::parse),
                            keep = c.optBoolean("keep", true),
                            quit = c.str("quit")?.let(LocalDate::parse),
                        )
                    }.getOrNull()
                }
            } ?: emptyList(),
        )
        val records = mutableMapOf<LocalDate, DayRecord>()
        val days = root.getJSONArray("days")
        for (i in 0 until days.length()) {
            val o = days.getJSONObject(i)
            val date = LocalDate.parse(o.getString("date"))
            val amounts = o.map("amounts") { k, j -> j.getDouble(k) }.toMutableMap()
            // v1 kept steps in its own field.
            o.int("steps")?.let { amounts.putIfAbsent("steps", it.toDouble()) }
            records[date] = DayRecord(
                date = date,
                done = o.map("done") { k, j -> Done.valueOf(j.getString(k)) },
                amounts = amounts,
                skipped = o.map("skipped") { k, j -> Skip.valueOf(j.getString(k)) },
                deferred = o.map("deferred") { k, j -> j.getLong(k) },
                dayType = o.str("dayType")?.let { DayType.valueOf(it) },
                bedTime = o.str("bedTime"),
                wakeTime = o.str("wakeTime"),
                weight = o.dbl("weight"),
                waist = o.dbl("waist"),
                energy = o.int("energy"),
                protein = o.int("protein"),
                longestRunSec = o.int("longestRunSec"),
                runMinutes = o.int("runMinutes"),
                routine = o.optJSONArray("routine")?.strings()?.toSet() ?: emptySet(),
                event = o.optJSONObject("event")?.let { e ->
                    Event(
                        title = e.optString("title", "Event"),
                        start = LocalTime.parse(e.getString("start")),
                        end = LocalTime.parse(e.getString("end")),
                        readyMin = e.optInt("readyMin", 45),
                    )
                },
                exSkips = o.optJSONArray("exSkips")?.let { a ->
                    (0 until a.length()).mapNotNull { i ->
                        val j = a.getJSONObject(i)
                        runCatching { ExSkipLog(j.getString("exercise"), ExSkip.valueOf(j.getString("reason"))) }.getOrNull()
                    }
                } ?: emptyList(),
                answers = o.map("answers") { k, j -> j.getString(k) },
            )
        }
        return AppData(records, settings)
    }

    private fun <T> JSONObject.map(k: String, read: (String, JSONObject) -> T): Map<String, T> {
        val j = optJSONObject(k) ?: return emptyMap()
        return j.keys().asSequence().associateWith { read(it, j) }
    }

    private fun routineJson(list: List<RoutineDef>) = JSONArray(
        list.map { r ->
            JSONObject().apply {
                put("id", r.id)
                put("label", r.label)
                put("icon", r.icon)
                put("when", r.`when`.name)
                r.day?.let { put("day", it.name) }
                put("optional", r.optional)
                put("alternate", r.alternate)
            }
        },
    )

    private fun routineFrom(a: JSONArray): List<RoutineDef> = (0 until a.length()).mapNotNull { i ->
        val j = a.getJSONObject(i)
        runCatching {
            RoutineDef(
                id = j.getString("id"),
                label = j.getString("label"),
                icon = j.optString("icon", "•"),
                `when` = RWhen.valueOf(j.optString("when", "DAILY")),
                day = j.str("day")?.let { DayOfWeek.valueOf(it) },
                optional = j.optBoolean("optional", false),
                alternate = j.optBoolean("alternate", false),
            )
        }.getOrNull()
    }

    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }

    /** Keeps a copy of everything before a restart, so it can be undone. */
    fun saveSnapshot(ctx: Context, data: AppData) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(SNAPSHOT, toJson(data)).apply()
    }

    fun loadSnapshot(ctx: Context): AppData? {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(SNAPSHOT, null) ?: return null
        return runCatching { fromJson(raw) }.getOrNull()
    }

    fun clearSnapshot(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(SNAPSHOT).apply()
    }

    private fun JSONObject.str(k: String): String? = if (has(k) && !isNull(k)) getString(k) else null
    private fun JSONObject.dbl(k: String): Double? = if (has(k) && !isNull(k)) getDouble(k) else null
    private fun JSONObject.int(k: String): Int? = if (has(k) && !isNull(k)) getInt(k) else null
    private fun JSONObject.days(k: String): Set<DayOfWeek>? {
        val a = optJSONArray(k) ?: return null
        return (0 until a.length()).map { DayOfWeek.valueOf(a.getString(it)) }.toSet()
    }
}
