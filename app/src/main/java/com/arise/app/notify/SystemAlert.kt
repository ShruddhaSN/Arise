package com.arise.app.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.arise.app.MainActivity
import com.arise.app.R
import com.arise.app.data.Done
import com.arise.app.data.Store
import com.arise.app.game.Feats
import com.arise.app.game.Game
import com.arise.app.game.Kind
import com.arise.app.ui.C
import com.arise.app.ui.Cues
import com.arise.app.ui.Mono
import com.arise.app.ui.SysButton
import com.arise.app.ui.SystemWindow
import com.arise.app.ui.Voice
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime

/**
 * The System interrupting whatever you're doing. With "display over other apps" allowed, the pop-up
 * opens directly on top of any app. Without it, a full-screen notification shows it on the lock
 * screen (and as a heads-up banner while the phone is in use).
 */
object SystemAlert {
    const val CHANNEL = "arise_alert"
    private const val ALERT_ID = 5252
    const val WAKE = "wake"
    const val EMERGENCY = "emergency"
    const val REPORT = "report"

    fun createChannel(ctx: Context) {
        val ch = NotificationChannel(CHANNEL, "System pop-ups", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Wake-up, emergency quests and the night report"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 60, 80, 60, 80, 300)
        }
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun intent(ctx: Context, kind: String, title: String, text: String, questId: String?, date: LocalDate): Intent =
        Intent(ctx, SystemAlertActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("kind", kind)
            .putExtra("title", title)
            .putExtra("text", text)
            .putExtra("quest", questId)
            .putExtra("date", date.toString())

    @SuppressLint("MissingPermission")
    /** True when pop-ups can open over other apps (then no extra notification is needed). */
    fun canPop(ctx: Context): Boolean = android.provider.Settings.canDrawOverlays(ctx)

    /**
     * The pop-up over other apps. Without that permission it falls back to a full-screen notification,
     * unless [fallback] is false (the caller already posted its own notification, so we don't double up).
     */
    fun show(ctx: Context, kind: String, title: String, text: String, questId: String? = null, date: LocalDate = LocalDate.now(), fallback: Boolean = true) {
        val i = intent(ctx, kind, title, text, questId, date)
        if (canPop(ctx)) {
            val opened = runCatching { ctx.startActivity(i) }.isSuccess
            if (opened) return
        }
        if (!fallback) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val pi = PendingIntent.getActivity(ctx, 77, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_arise)
            .setColor(0xFF4DA3FF.toInt())
            .setContentTitle("[SYSTEM] $title")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setFullScreenIntent(pi, true)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setTimeoutAfter(30 * 60 * 1000L)
            .build()
        NotificationManagerCompat.from(ctx).notify(ALERT_ID, n)
    }

    fun dismiss(ctx: Context) = NotificationManagerCompat.from(ctx).cancel(ALERT_ID)
}

/** Quest actions that work from a notification or pop-up, without opening the app. */
object QuestActions {
    /** Records "I'm up" now (if not already) and returns what the System says about it. */
    fun wake(ctx: Context): List<String> {
        val today = LocalDate.now()
        val data = Store.load(ctx)
        val rec = data.day(today)
        val next = if (rec.wakeTime == null) {
            val t = LocalTime.now()
            data.with(rec.copy(wakeTime = "%02d:%02d".format(t.hour, t.minute)))
        } else {
            data
        }
        Store.save(ctx, next)
        if (rec.wakeTime == null) Blocker.onWake(ctx, next.settings)
        Reminders.clearWake(ctx)
        Reminders.scheduleNext(ctx, java.time.LocalDateTime.now())
        return Feats.wakeLines(next, today)
    }

    /** Ticks a quest as fully done. */
    fun done(ctx: Context, id: String, date: LocalDate) {
        val data = Store.load(ctx)
        val q = Game.plan(data, date).quests.firstOrNull { it.id == id } ?: return
        val rec = data.day(date)
        val next = when (q.kind) {
            Kind.AMOUNT -> rec.copy(amounts = rec.amounts + (id to q.target), skipped = rec.skipped - id)
            Kind.ROUTINE -> rec.copy(done = rec.done + (id to Done.FULL), skipped = rec.skipped - id)
            else -> rec.copy(done = rec.done + (id to Done.FULL), skipped = rec.skipped - id)
        }
        Store.save(ctx, data.with(next))
        Reminders.cancelSettled(ctx, data.with(next))
        Reminders.scheduleNext(ctx, java.time.LocalDateTime.now())
    }
}

class QuestActionReceiver : BroadcastReceiver() {
    companion object {
        private const val ACTION_WAKE = "com.arise.app.WAKE"
        private const val ACTION_DONE = "com.arise.app.DONE"

        fun wake(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
            ctx, 9001, Intent(ctx, QuestActionReceiver::class.java).setAction(ACTION_WAKE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        fun done(ctx: Context, id: String, date: LocalDate, notifId: Int): PendingIntent = PendingIntent.getBroadcast(
            ctx, ("$id$date").hashCode(),
            Intent(ctx, QuestActionReceiver::class.java).setAction(ACTION_DONE)
                .putExtra("quest", id).putExtra("date", date.toString()).putExtra("notif", notifId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    @SuppressLint("MissingPermission")
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            ACTION_WAKE -> {
                // Records the time. No second "you're awake" notification: one is enough.
                QuestActions.wake(ctx)
            }
            ACTION_DONE -> {
                val id = intent.getStringExtra("quest") ?: return
                val date = intent.getStringExtra("date")?.let(LocalDate::parse) ?: LocalDate.now()
                QuestActions.done(ctx, id, date)
                NotificationManagerCompat.from(ctx).cancel(intent.getIntExtra("notif", 0))
            }
        }
        val pending = goAsync()
        Reminders.updateWidget(ctx) { pending.finish() }
    }
}

/** The pop-up: dimmed screen, glowing System window, the message typed out and spoken. */
class SystemAlertActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        SystemAlert.dismiss(this)
        Voice.init(this)
        Voice.enabled = Store.load(this).settings.voice
        val kind = intent.getStringExtra("kind") ?: SystemAlert.REPORT
        val title = intent.getStringExtra("title") ?: "SYSTEM"
        val text = intent.getStringExtra("text") ?: ""
        val quest = intent.getStringExtra("quest")
        val date = intent.getStringExtra("date")?.let(LocalDate::parse) ?: LocalDate.now()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = C.Glow, background = Color.Transparent, surface = C.Panel)) {
                CompositionLocalProvider(LocalContentColor provides C.Text) {
                    AlertBody(kind, title, text, quest, date)
                }
            }
        }
    }

    private fun openApp() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }

    @Composable
    private fun AlertBody(kind: String, title: String, text: String, quest: String?, date: LocalDate) {
        val appear = remember { Animatable(0f) }
        var typed by remember { mutableIntStateOf(0) }
        var after by remember { mutableStateOf<List<String>?>(null) }
        LaunchedEffect(Unit) {
            Cues.ping(this@SystemAlertActivity)
            Voice.say(
                when (kind) {
                    SystemAlert.WAKE -> "Player. The daily quest has arrived. Wake up."
                    SystemAlert.EMERGENCY -> "Emergency quest. $title"
                    else -> title
                },
            )
            appear.animateTo(1f, tween(300))
            while (typed < title.length) {
                typed++
                delay(25)
            }
        }
        val accent = when (kind) {
            SystemAlert.EMERGENCY -> C.Gold
            SystemAlert.WAKE -> C.Glow
            else -> C.Glow
        }
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.78f * appear.value)),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.padding(22.dp).alpha(appear.value).scale(0.9f + 0.1f * appear.value)) {
                SystemWindow(accent = accent) {
                    Text(
                        when (kind) {
                            SystemAlert.EMERGENCY -> "[SYSTEM] ⚡ EMERGENCY QUEST"
                            SystemAlert.WAKE -> "[SYSTEM] DAILY QUEST"
                            else -> "[SYSTEM]"
                        },
                        color = accent, fontFamily = Mono, fontSize = 12.sp, letterSpacing = 3.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(title.take(typed), fontSize = 21.sp, fontWeight = FontWeight.Bold, fontFamily = Mono, color = C.Text)
                    Spacer(Modifier.height(10.dp))
                    (after ?: text.lines()).forEach { Text(it, color = C.Text2, fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp)) }
                    Spacer(Modifier.height(16.dp))
                    when {
                        after != null -> SysButton("CONTINUE", onClick = { openApp() }, selected = true, modifier = Modifier.fillMaxWidth())
                        kind == SystemAlert.WAKE -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SysButton("I'M UP", onClick = {
                                after = QuestActions.wake(this@SystemAlertActivity)
                                Voice.say("Player has awakened.")
                                Reminders.updateWidget(this@SystemAlertActivity)
                            }, selected = true, color = C.Good, modifier = Modifier.fillMaxWidth())
                            SysButton("Open Arise", onClick = { openApp() }, color = C.Muted, small = true, modifier = Modifier.fillMaxWidth())
                        }
                        kind == SystemAlert.EMERGENCY && quest != null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SysButton("ACCEPT", onClick = { finish() }, selected = true, color = C.Gold, modifier = Modifier.weight(1f))
                                SysButton("✓ Done", onClick = {
                                    QuestActions.done(this@SystemAlertActivity, quest, date)
                                    Reminders.updateWidget(this@SystemAlertActivity)
                                    Voice.say("Emergency quest cleared.")
                                    finish()
                                }, color = C.Good, modifier = Modifier.weight(1f))
                            }
                            SysButton("Decline (no penalty)", onClick = { finish() }, color = C.Muted, small = true, modifier = Modifier.fillMaxWidth())
                        }
                        else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SysButton("OPEN ARISE", onClick = { openApp() }, selected = true, modifier = Modifier.weight(1f))
                            SysButton("OK", onClick = { finish() }, color = C.Muted)
                        }
                    }
                }
            }
        }
    }
}
