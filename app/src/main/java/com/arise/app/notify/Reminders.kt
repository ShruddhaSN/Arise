package com.arise.app.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.glance.appwidget.updateAll
import com.arise.app.MainActivity
import com.arise.app.R
import com.arise.app.data.AppData
import com.arise.app.data.Store
import com.arise.app.game.Block
import com.arise.app.game.Feats
import com.arise.app.game.Game
import com.arise.app.game.Kind
import com.arise.app.game.Quotes
import com.arise.app.game.Special
import com.arise.app.game.fmt
import com.arise.app.widget.CountdownWidget
import com.arise.app.widget.AriseWidget
import com.arise.app.widget.QuestListWidget
import com.arise.app.widget.StatusWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Only one alarm is ever pending: the next thing on the schedule (a block, a card back from Later,
 * or a single follow-up nudge). When it fires, we show it and schedule the one after.
 */
object Reminders {
    const val CHANNEL = "arise_system"
    private const val REQ = 4242
    const val WAKE_ID = 5151
    /** A quest still open this long after its time gets one reminder. Never more. */
    private const val NUDGE_MIN = 20L

    fun createChannel(ctx: Context) {
        val ch = NotificationChannel(CHANNEL, "System quests", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Daily quest reminders from the System"
        }
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        SystemAlert.createChannel(ctx)
    }

    /** Reschedule the next reminder and redraw the widgets. Call after any data change. */
    fun refresh(ctx: Context) {
        val data = Store.load(ctx)
        Blocker.sync(ctx, data)
        cancelSettled(ctx, data)
        scheduleNext(ctx, LocalDateTime.now())
        updateWidget(ctx)
    }

    /** Removes notifications for quests you've already done or skipped, wherever you ticked them. */
    fun cancelSettled(ctx: Context, data: AppData) {
        val today = LocalDate.now()
        val rec = data.records[today] ?: return
        val nm = NotificationManagerCompat.from(ctx)
        for (b in Game.plan(data, today).blocks) {
            val id = b.quest?.id ?: b.remindFor ?: continue
            val q = Game.plan(data, today).quests.firstOrNull { it.id == id } ?: continue
            if (!Game.settled(q, rec)) continue
            val n = b.time.toSecondOfDay()
            nm.cancel(n)
            nm.cancel(n + 1)
            if (q.kind == Kind.WAKE) clearWake(ctx)
        }
        rec.deferred.forEach { (id, ms) ->
            val q = Game.plan(data, today).quests.firstOrNull { it.id == id } ?: return@forEach
            if (Game.settled(q, rec)) {
                val t = java.time.Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalTime()
                nm.cancel(t.toSecondOfDay() + 1)
            }
        }
    }

    /** Side quests stay quiet, except the ones you chose or that matter: medicines, to-dos, challenges, reminders you set. */
    private fun notifies(b: Block): Boolean {
        if (!b.notify) return false
        val q = b.quest ?: return true
        if (!q.optional) return true
        return q.id.startsWith("med_") || q.id.startsWith("todo_") || q.id.startsWith("sk_") || q.id.startsWith("bc_") || q.id.startsWith("wc_") || q.expires
    }

    fun updateWidget(ctx: Context, onDone: () -> Unit = {}) {
        val app = ctx.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try {
                runCatching { AriseWidget().updateAll(app) }
                runCatching { StatusWidget().updateAll(app) }
                runCatching { QuestListWidget().updateAll(app) }
                runCatching { CountdownWidget().updateAll(app) }
            } finally {
                onDone()
            }
        }
    }

    /** The wake alert stays on the lock screen until you're up. */
    fun clearWake(ctx: Context) {
        NotificationManagerCompat.from(ctx).cancel(WAKE_ID)
    }

    private data class Next(val at: LocalDateTime, val blockTime: LocalTime?, val deferredId: String?, val nudge: Boolean)

    private fun nudges(b: Block): Boolean {
        val q = b.quest ?: return false
        return b.notify && !q.optional && !q.expires && q.kind != Kind.WAKE && q.limitMin != null
    }

    private fun nextEvent(data: AppData, from: LocalDateTime): Next? {
        val zone = ZoneId.systemDefault()
        val today = from.toLocalDate()
        val returns = data.records[today]?.deferred.orEmpty().mapNotNull { (id, ms) ->
            val at = java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDateTime()
            if (at.isAfter(from)) Next(at, null, id, false) else null
        }
        val candidates = mutableListOf<Next>()
        candidates += returns
        // One nudge per open quest, today only.
        val rec = data.records[today]
        Game.plan(data, today).blocks.filter { nudges(it) && !Game.settled(it.quest!!, rec) && rec?.deferred?.containsKey(it.quest.id) != true }
            .map { LocalDateTime.of(today, it.time).plusMinutes(NUDGE_MIN) to it.time }
            .filter { it.first.isAfter(from) && it.first.toLocalDate() == today }
            .minByOrNull { it.first }
            ?.let { candidates += Next(it.first, it.second, null, true) }
        loop@ for (d in 0L..7L) {
            val date = today.plusDays(d)
            for (b in Game.plan(data, date).blocks) {
                if (!notifies(b)) continue
                val at = LocalDateTime.of(date, b.time)
                if (at.isAfter(from)) {
                    candidates += Next(at, b.time, null, false)
                    break@loop
                }
            }
        }
        return candidates.minByOrNull { it.at }
    }

    fun scheduleNext(ctx: Context, from: LocalDateTime) {
        val data = Store.load(ctx)
        val am = ctx.getSystemService(AlarmManager::class.java)
        val base = Intent(ctx, ReminderReceiver::class.java)
        am.cancel(PendingIntent.getBroadcast(ctx, REQ, base, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        if (!data.settings.notifications || !data.settings.onboarded) return
        val n = nextEvent(data, from) ?: return
        val intent = Intent(ctx, ReminderReceiver::class.java)
            .putExtra("date", n.at.toLocalDate().toString())
            .putExtra("time", (n.blockTime ?: n.at.toLocalTime()).toString())
            .putExtra("at", n.at.toString())
            .putExtra("deferred", n.deferredId)
            .putExtra("nudge", n.nudge)
        val pi = PendingIntent.getBroadcast(ctx, REQ, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val millis = n.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }
    }

    private fun allowed(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(ctx: Context, date: LocalDate, time: LocalTime, deferredId: String? = null, nudge: Boolean = false) {
        if (!allowed(ctx)) return
        val data = Store.load(ctx)
        val plan = Game.plan(data, date)
        val rec = data.records[date]
        val name = data.settings.name
        if (deferredId != null) {
            val dq = plan.quests.firstOrNull { it.id == deferredId } ?: return
            if (Game.settled(dq, rec)) return
            post(ctx, "[SYSTEM] Rescheduled quest: ${dq.title}", "It's time. ${dq.full}", time.toSecondOfDay() + 1, doneId = dq.id.takeIf { dq.kind == Kind.CHECK }, date = date)
            return
        }
        val b = plan.blocks.firstOrNull { it.time == time && notifies(it) } ?: return
        val q = b.quest
        if (q != null && Game.settled(q, rec)) return // already handled, no nagging
        b.remindFor?.let { id -> plan.quests.firstOrNull { it.id == id }?.let { rq -> if (Game.settled(rq, rec)) return } }

        if (nudge && q != null) {
            // Same id as the first notification: it updates that one instead of adding another.
            post(
                ctx, "[SYSTEM] ${q.title} is still waiting",
                "From ${fmt(time)}. Whenever you're ready, or tap Later / Not today in the app. No pressure.",
                time.toSecondOfDay(), doneId = q.id.takeIf { q.kind == Kind.CHECK }, date = date,
            )
            return
        }

        when {
            b.special == Special.CHECK_IN -> {
                val r = Feats.report(data, date)
                val title = if (r.cleared) "[SYSTEM] Daily report · on track" else "[SYSTEM] Daily report"
                val open = plan.quests.count { !it.optional && !Game.counts(it, rec) && rec?.skipped?.containsKey(it.id) != true }
                val text = "Core ${r.done}/${r.total} · +${r.xp} XP" +
                    (if (r.cleared) ". Well done, $name." else if (open > 0) ". $open core quest${if (open == 1) "" else "s"} still open, if you have the energy. If not, rest is fine." else ".")
                if (SystemAlert.canPop(ctx)) SystemAlert.show(ctx, SystemAlert.REPORT, title.removePrefix("[SYSTEM] ").uppercase(), text, date = date)
                else post(ctx, title, text, time.toSecondOfDay())
            }
            b.special == Special.RESCUE -> {
                // Only speak up if the morning slipped: 2+ morning quests still open.
                val open = plan.blocks.filter { it.time < time && it.quest != null && !it.quest.optional && !Game.settled(it.quest, rec) }
                if (open.size < 2) return
                post(ctx, "[SYSTEM] The day can still be cleared", "Next quest: ${open.first().quest!!.title}. One card at a time, $name.", time.toSecondOfDay())
            }
            q != null && q.kind == Kind.WAKE -> {
                postWake(ctx, q.title, "Good morning, $name. Tap I'M UP the moment you're out of bed. The Daily Quest has arrived.")
                // The lock-screen notification above is the one notification; the pop-up only opens over apps.
                SystemAlert.show(
                    ctx, SystemAlert.WAKE, "THE DAILY QUEST HAS ARRIVED",
                    "Goal ${fmt(time)}. Get out of bed, then tap I'M UP. ${Quotes.forDay(data.settings, date).second.line}",
                    date = date, fallback = false,
                )
            }
            q != null && q.expires -> {
                if (SystemAlert.canPop(ctx)) SystemAlert.show(ctx, SystemAlert.EMERGENCY, q.title, "${q.full}\nReward: +${q.xp} ${q.stat.label} XP", questId = q.id, date = date)
                else post(ctx, "[SYSTEM] ⚡ ${q.title}", "${q.full}\nReward: +${q.xp} ${q.stat.label} XP", time.toSecondOfDay(), doneId = q.id, date = date)
            }
            q != null -> {
                post(
                    ctx, "[SYSTEM] Quest: ${q.title}", "${q.full}\nReward: +${q.xp} ${q.stat.label} XP", time.toSecondOfDay(),
                    doneId = q.id.takeIf { q.kind == Kind.CHECK }, date = date,
                )
            }
            else -> post(ctx, "[SYSTEM] ${b.title}", b.detail, time.toSecondOfDay())
        }
    }

    private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** A notification. [doneId] adds a "✓ Done" button that ticks the quest without opening the app. */
    @SuppressLint("MissingPermission")
    private fun post(ctx: Context, title: String, text: String, id: Int, doneId: String? = null, date: LocalDate? = null) {
        if (!allowed(ctx)) return
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_arise)
            .setColor(0xFF4DA3FF.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp(ctx))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        if (doneId != null && date != null) {
            n.addAction(0, "✓ Done", QuestActionReceiver.done(ctx, doneId, date, id))
        }
        NotificationManagerCompat.from(ctx).notify(id, n.build())
    }

    /** The wake alert: stays on the lock screen with an I'M UP button until you're up. */
    @SuppressLint("MissingPermission")
    private fun postWake(ctx: Context, title: String, text: String) {
        if (!allowed(ctx)) return
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_arise)
            .setColor(0xFF4DA3FF.toInt())
            .setContentTitle("[SYSTEM] $title")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp(ctx))
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setTimeoutAfter(5 * 60 * 60 * 1000L)
            .addAction(0, "I'M UP", QuestActionReceiver.wake(ctx))
            .build()
        NotificationManagerCompat.from(ctx).notify(WAKE_ID, n)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val date = intent.getStringExtra("date")?.let(LocalDate::parse)
        val time = intent.getStringExtra("time")?.let(LocalTime::parse)
        val at = intent.getStringExtra("at")?.let(LocalDateTime::parse)
        var from = LocalDateTime.now()
        if (date != null && time != null) {
            Reminders.show(ctx, date, time, intent.getStringExtra("deferred"), intent.getBooleanExtra("nudge", false))
            val due = at ?: LocalDateTime.of(date, time)
            if (due.isAfter(from)) from = due
        }
        Reminders.scheduleNext(ctx, from)
        val pending = goAsync()
        Reminders.updateWidget(ctx) { pending.finish() }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Reminders.scheduleNext(ctx, LocalDateTime.now())
        val pending = goAsync()
        Reminders.updateWidget(ctx) { pending.finish() }
    }
}
