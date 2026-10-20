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
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.arise.app.MainActivity
import com.arise.app.R

/**
 * While a timed session runs, every segment change gets its own exact alarm. If the app is in the
 * background (e.g. you switched to Instagram), the alarm posts a heads-up notification with sound and
 * vibration. When the app is on screen, the in-app cue handles it and the alarm stays quiet.
 */
object CoachCues {
    const val CHANNEL = "arise_coach"
    private const val NOTIF_ID = 7001
    private const val REQ_BASE = 6000
    private const val MAX = 80

    /** Set by the session screen: true while it's visible. */
    @Volatile
    var onScreen: Boolean = false

    fun createChannel(ctx: Context) {
        val ch = NotificationChannel(CHANNEL, "Coach cues", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Run / walk / next move alerts during a session"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 400, 150, 400)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).build(),
            )
        }
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    data class Cue(val atElapsedMs: Long, val title: String, val text: String)

    private fun pi(ctx: Context, i: Int, cue: Cue?): PendingIntent {
        val intent = Intent(ctx, CoachCueReceiver::class.java)
        if (cue != null) intent.putExtra("title", cue.title).putExtra("text", cue.text)
        return PendingIntent.getBroadcast(ctx, REQ_BASE + i, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** [cues] use SystemClock.elapsedRealtime() times. Replaces anything scheduled before. */
    fun schedule(ctx: Context, cues: List<Cue>) {
        cancel(ctx)
        val am = ctx.getSystemService(AlarmManager::class.java)
        val now = SystemClock.elapsedRealtime()
        cues.filter { it.atElapsedMs > now }.take(MAX).forEachIndexed { i, c ->
            val p = pi(ctx, i, c)
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, c.atElapsedMs, p)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, c.atElapsedMs, p)
            }
        }
    }

    fun cancel(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        for (i in 0 until MAX) am.cancel(pi(ctx, i, null))
        NotificationManagerCompat.from(ctx).cancel(NOTIF_ID)
    }

    @SuppressLint("MissingPermission")
    fun post(ctx: Context, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            ctx, 1, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_arise)
            .setColor(0xFFFF8A3D.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setTimeoutAfter(20_000)
            .build()
        NotificationManagerCompat.from(ctx).notify(NOTIF_ID, n)
    }
}

class CoachCueReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (CoachCues.onScreen) return
        val title = intent.getStringExtra("title") ?: return
        CoachCues.post(ctx, title, intent.getStringExtra("text") ?: "")
    }
}
