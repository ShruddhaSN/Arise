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
        root.put("version", 1)
        val s = data.settings
        root.put("settings", JSONObject().apply {
            put("notifications", s.notifications)
            put("runWeekShift", s.runWeekShift)
            put("playerName", s.playerName)
            s.startDate?.let { put("startDate", it.toString()) }
            s.backfillFrom?.let { put("backfillFrom", it.toString()) }
            put("officeDays", JSONArray(s.officeDays.map { it.name }))
            put("wfhDays", JSONArray(s.wfhDays.map { it.name }))
            put("wakeWork", s.wakeWork.toString())
            put("wakeFree", s.wakeFree.toString())
            s.goalWeight?.let { put("goalWeight", it) }
        })
        val days = JSONArray()
        data.records.values.sortedBy { it.date }.forEach { r ->
            days.put(JSONObject().apply {
                put("date", r.date.toString())
                put("done", JSONObject().apply { r.done.forEach { (k, v) -> put(k, v.name) } })
                r.dayType?.let { put("dayType", it.name) }
                r.bedTime?.let { put("bedTime", it) }
                r.wakeTime?.let { put("wakeTime", it) }
                r.weight?.let { put("weight", it) }
                r.waist?.let { put("waist", it) }
                r.steps?.let { put("steps", it) }
                r.energy?.let { put("energy", it) }
                r.longestRunSec?.let { put("longestRunSec", it) }
                r.runMinutes?.let { put("runMinutes", it) }
            })
        }
        root.put("days", days)
        return root.toString()
    }

    /** Throws on invalid input, so imports can report a bad backup. */
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
            wakeWork = sj.str("wakeWork")?.let(LocalTime::parse) ?: d.wakeWork,
            wakeFree = sj.str("wakeFree")?.let(LocalTime::parse) ?: d.wakeFree,
            goalWeight = sj.dbl("goalWeight"),
        )
        val records = mutableMapOf<LocalDate, DayRecord>()
        val days = root.getJSONArray("days")
        for (i in 0 until days.length()) {
            val o = days.getJSONObject(i)
            val date = LocalDate.parse(o.getString("date"))
            val dj = o.optJSONObject("done") ?: JSONObject()
            val done = dj.keys().asSequence().associateWith { Done.valueOf(dj.getString(it)) }
            records[date] = DayRecord(
                date = date,
                done = done,
                dayType = o.str("dayType")?.let { DayType.valueOf(it) },
                bedTime = o.str("bedTime"),
                wakeTime = o.str("wakeTime"),
                weight = o.dbl("weight"),
                waist = o.dbl("waist"),
                steps = o.int("steps"),
                energy = o.int("energy"),
                longestRunSec = o.int("longestRunSec"),
                runMinutes = o.int("runMinutes"),
            )
        }
        return AppData(records, settings)
    }

    private fun JSONObject.str(k: String): String? = if (has(k) && !isNull(k)) getString(k) else null
    private fun JSONObject.dbl(k: String): Double? = if (has(k) && !isNull(k)) getDouble(k) else null
    private fun JSONObject.days(k: String): Set<DayOfWeek>? {
        val a = optJSONArray(k) ?: return null
        return (0 until a.length()).map { DayOfWeek.valueOf(a.getString(it)) }.toSet()
    }
    private fun JSONObject.int(k: String): Int? = if (has(k) && !isNull(k)) getInt(k) else null
}
