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
        root.put("version", 2)
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
            )
        }
        return AppData(records, settings)
    }

    private fun <T> JSONObject.map(k: String, read: (String, JSONObject) -> T): Map<String, T> {
        val j = optJSONObject(k) ?: return emptyMap()
        return j.keys().asSequence().associateWith { read(it, j) }
    }

    private fun JSONObject.str(k: String): String? = if (has(k) && !isNull(k)) getString(k) else null
    private fun JSONObject.dbl(k: String): Double? = if (has(k) && !isNull(k)) getDouble(k) else null
    private fun JSONObject.int(k: String): Int? = if (has(k) && !isNull(k)) getInt(k) else null
    private fun JSONObject.days(k: String): Set<DayOfWeek>? {
        val a = optJSONArray(k) ?: return null
        return (0 until a.length()).map { DayOfWeek.valueOf(a.getString(it)) }.toSet()
    }
}
