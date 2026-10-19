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
import com.arise.app.game.Game
import com.arise.app.widget.AriseWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Only one alarm is ever pending: the next schedule block. When it fires, we show the
 * notification and schedule the one after it.
 */
object Reminders {
    const val CHANNEL = "arise_system"
    private const val REQ = 4242

    fun createChannel(ctx: Context) {
        val ch = NotificationChannel(CHANNEL, "System quests", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Daily quest reminders from the System"
        }
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    /** Reschedule the next reminder and redraw the widget. Call after any data change. */
    fun refresh(ctx: Context) {
        scheduleNext(ctx, LocalDateTime.now())
        updateWidget(ctx)
    }

    fun updateWidget(ctx: Context, onDone: () -> Unit = {}) {
        val app = ctx.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try {
                AriseWidget().updateAll(app)
            } finally {
                onDone()
            }
        }
    }

    private fun nextBlock(data: AppData, from: LocalDateTime): Pair<LocalDateTime, Block>? {
        for (d in 0L..7L) {
            val date = from.toLocalDate().plusDays(d)
            for (b in Game.plan(data, date).blocks) {
                if (!b.notify) continue
                val at = LocalDateTime.of(date, b.time)
                if (at.isAfter(from)) return at to b
            }
        }
        return null
    }

    fun scheduleNext(ctx: Context, from: LocalDateTime) {
        val data = Store.load(ctx)
        val am = ctx.getSystemService(AlarmManager::class.java)
        val base = Intent(ctx, ReminderReceiver::class.java)
        am.cancel(PendingIntent.getBroadcast(ctx, REQ, base, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        if (!data.settings.notifications || !data.settings.onboarded) return
        val (at, _) = nextBlock(data, from) ?: return
        val intent = Intent(ctx, ReminderReceiver::class.java)
            .putExtra("date", at.toLocalDate().toString())
            .putExtra("time", at.toLocalTime().toString())
        val pi = PendingIntent.getBroadcast(ctx, REQ, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }
    }

    @SuppressLint("MissingPermission")
    fun show(ctx: Context, date: LocalDate, time: LocalTime) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val data = Store.load(ctx)
        val plan = Game.plan(data, date)
        val b = plan.blocks.firstOrNull { it.time == time && it.notify } ?: return
        val done = data.day(date).done
        val q = b.quest
        if (q != null && done.containsKey(q.id)) return // already done, no nagging

        val title: String
        val text: String
        when {
            b.checkIn -> {
                val left = plan.quests.filter { !done.containsKey(it.id) }
                if (left.isEmpty()) {
                    title = "[SYSTEM] All quests cleared"
                    text = "Flawless. Rest well, ${data.settings.name}."
                } else {
                    title = "[SYSTEM] ${left.size} quest${if (left.size == 1) "" else "s"} remain"
                    text = left.joinToString(" · ") { it.title }
                }
            }
            q != null -> {
                title = "[SYSTEM] Quest: ${q.title}"
                text = "${q.full}\nReward: +${q.xp} ${q.stat.label} XP"
            }
            else -> {
                title = "[SYSTEM] ${b.title}"
                text = b.detail
            }
        }

        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_arise)
            .setColor(0xFF4DA3FF.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(ctx).notify(time.toSecondOfDay(), n)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val date = intent.getStringExtra("date")?.let(LocalDate::parse)
        val time = intent.getStringExtra("time")?.let(LocalTime::parse)
        var from = LocalDateTime.now()
        if (date != null && time != null) {
            Reminders.show(ctx, date, time)
            val due = LocalDateTime.of(date, time)
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
