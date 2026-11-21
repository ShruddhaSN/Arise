package com.arise.app.notify

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.arise.app.data.AppData
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Sleep and steps without ticking anything.
 *
 * Sleep comes from Health Connect (Samsung Health, a watch) when it has a session, otherwise from the
 * screen: the longest stretch overnight with the screen off, where short check-ins (under 15 minutes)
 * don't break it. Anything you correct by hand ("manual") is never overwritten.
 */
object Health {
    data class Sleep(val start: LocalTime, val end: LocalTime, val source: String)

    private val zone: ZoneId get() = ZoneId.systemDefault()

    // ---- Screen-based sleep (Usage access) ----

    fun hasUsageAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun usageAccessIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun ms(t: LocalDateTime): Long = t.atZone(zone).toInstant().toEpochMilli()
    private fun time(ms: Long): LocalTime = Instant.ofEpochMilli(ms).atZone(zone).toLocalTime().withSecond(0).withNano(0)

    /** Last night's sleep from screen on/off, for the morning of [date]. Needs Android 9+. */
    fun screenSleep(ctx: Context, date: LocalDate): Sleep? {
        if (Build.VERSION.SDK_INT < 28 || !hasUsageAccess(ctx)) return null
        val usm = ctx.getSystemService(UsageStatsManager::class.java) ?: return null
        val from = ms(date.minusDays(1).atTime(19, 0))
        val to = minOf(ms(date.atTime(14, 0)), System.currentTimeMillis())
        if (to <= from) return null
        val events = runCatching { usm.queryEvents(from, to) }.getOrNull() ?: return null
        val offs = mutableListOf<LongArray>() // [off, on]
        var offAt: Long? = null
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> if (offAt == null) offAt = e.timeStamp
                UsageEvents.Event.SCREEN_INTERACTIVE -> offAt?.let {
                    offs += longArrayOf(it, e.timeStamp)
                    offAt = null
                }
            }
        }
        if (offs.isEmpty()) return null
        // Merge stretches separated by a quick check of the phone.
        val merged = mutableListOf<LongArray>()
        for (o in offs) {
            val last = merged.lastOrNull()
            if (last != null && o[0] - last[1] < 15 * 60_000L) last[1] = o[1] else merged += o.copyOf()
        }
        val best = merged.maxByOrNull { it[1] - it[0] } ?: return null
        val length = best[1] - best[0]
        if (length < 3 * 3600_000L) return null
        // Must start in the evening/night and end in the morning of [date].
        val endDay = Instant.ofEpochMilli(best[1]).atZone(zone).toLocalDate()
        if (endDay != date) return null
        return Sleep(time(best[0]), time(best[1]), "screen")
    }

    // ---- Health Connect ----

    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
    )

    fun available(ctx: Context): Boolean =
        runCatching { HealthConnectClient.getSdkStatus(ctx) == HealthConnectClient.SDK_AVAILABLE }.getOrDefault(false)

    private fun client(ctx: Context): HealthConnectClient? =
        if (available(ctx)) runCatching { HealthConnectClient.getOrCreate(ctx) }.getOrNull() else null

    suspend fun granted(ctx: Context): Set<String> {
        val c = client(ctx) ?: return emptySet()
        return runCatching { c.permissionController.getGrantedPermissions() }.getOrDefault(emptySet())
    }

    suspend fun steps(ctx: Context, date: LocalDate): Long? {
        val c = client(ctx) ?: return null
        if (HealthPermission.getReadPermission(StepsRecord::class) !in granted(ctx)) return null
        return runCatching {
            val start = date.atStartOfDay(zone).toInstant()
            val end = minOf(date.plusDays(1).atStartOfDay(zone).toInstant(), Instant.now())
            val r = c.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(start, end)))
            r[StepsRecord.COUNT_TOTAL]
        }.getOrNull()
    }

    suspend fun healthSleep(ctx: Context, date: LocalDate): Sleep? {
        val c = client(ctx) ?: return null
        if (HealthPermission.getReadPermission(SleepSessionRecord::class) !in granted(ctx)) return null
        return runCatching {
            val start = date.minusDays(1).atTime(18, 0).atZone(zone).toInstant()
            val end = date.atTime(15, 0).atZone(zone).toInstant()
            val res = c.readRecords(ReadRecordsRequest(SleepSessionRecord::class, TimeRangeFilter.between(start, end)))
            val s = res.records.maxByOrNull { it.endTime.toEpochMilli() - it.startTime.toEpochMilli() } ?: return@runCatching null
            Sleep(time(s.startTime.toEpochMilli()), time(s.endTime.toEpochMilli()), "health")
        }.getOrNull()
    }

    /**
     * Fills in today's sleep and steps. Returns new data, or null if nothing changed.
     * Steps only go up (treadmill steps you added by hand are kept).
     */
    suspend fun sync(ctx: Context, data: AppData, today: LocalDate = LocalDate.now()): AppData? {
        val s = data.settings
        if (!s.onboarded) return null
        var next = data
        val rec = data.day(today)
        if (rec.sleepSource != "manual") {
            val sleep = (if (s.healthSync) healthSleep(ctx, today) else null) ?: if (s.sleepDetect) screenSleep(ctx, today) else null
            if (sleep != null) {
                val start = "%02d:%02d".format(sleep.start.hour, sleep.start.minute)
                val end = "%02d:%02d".format(sleep.end.hour, sleep.end.minute)
                if (rec.bedTime != start || rec.sleepEnd != end || rec.sleepSource != sleep.source) {
                    next = next.with(next.day(today).copy(bedTime = start, sleepEnd = end, sleepSource = sleep.source))
                }
            }
        }
        if (s.healthSync) {
            steps(ctx, today)?.let { n ->
                val cur = next.day(today).amounts["steps"] ?: 0.0
                if (n.toDouble() > cur) {
                    val r = next.day(today)
                    next = next.with(r.copy(amounts = r.amounts + ("steps" to n.toDouble()) + ("steps_hc" to n.toDouble())))
                }
            }
        }
        return if (next === data) null else next
    }
}
