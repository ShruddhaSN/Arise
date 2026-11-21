package com.arise.app.notify

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arise.app.MainActivity
import com.arise.app.data.AppData
import com.arise.app.data.Store
import com.arise.app.game.Program
import com.arise.app.game.fmt
import com.arise.app.ui.C
import com.arise.app.ui.Cues
import com.arise.app.ui.Mono
import com.arise.app.ui.SysButton
import com.arise.app.ui.SystemBackdrop
import com.arise.app.ui.SystemWindow
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Screen blocker state, kept in its own tiny preferences file so the accessibility service can check
 * it on every app switch without reading the whole save.
 */
object Blocker {
    private const val PREFS = "arise_blocker"

    /** Apps people usually lose their mornings to. Pre-ticked if installed. */
    val suggested = setOf(
        "com.instagram.android", "com.google.android.youtube", "com.facebook.katana", "com.snapchat.android",
        "com.twitter.android", "com.reddit.frontpage", "com.zhiliaoapp.musically", "com.netflix.mediaclient",
        "in.mohalla.sharechat", "com.pinterest", "com.facebook.orca",
    )

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Copies the blocked apps and the night window from the settings. Call after any data change. */
    fun sync(ctx: Context, data: AppData) {
        val s = data.settings
        val today = LocalDate.now()
        val bed = s.bedGoal
        val wake = minOf(s.wakeOffice, s.wakeWfh, s.wakeOff)
        prefs(ctx).edit()
            .putStringSet("apps", s.blocked)
            .putBoolean("night", s.blockNight && s.onboarded)
            .putInt("nightFrom", bed.toSecondOfDay() / 60)
            .putInt("nightTo", wake.toSecondOfDay() / 60)
            .apply()
    }

    /** Seal the blocked apps for [minutes] from now. */
    fun seal(ctx: Context, minutes: Int, reason: String) {
        val until = System.currentTimeMillis() + minutes * 60_000L
        if (until <= prefs(ctx).getLong("sealUntil", 0L)) return
        prefs(ctx).edit().putLong("sealUntil", until).putString("reason", reason).apply()
    }

    /** Phone-free first 30 minutes after "I'm up", if that's switched on. */
    fun onWake(ctx: Context, s: com.arise.app.data.Settings) {
        if (s.blockMorning && s.blocked.isNotEmpty()) seal(ctx, 30, "Phone-free first 30 minutes. Morning routine first.")
    }

    fun unseal(ctx: Context) {
        prefs(ctx).edit().putLong("sealUntil", 0L).apply()
    }

    /** Lets blocked apps through for a few minutes. */
    fun unlock(ctx: Context, minutes: Int) {
        prefs(ctx).edit().putLong("unlockUntil", System.currentTimeMillis() + minutes * 60_000L).apply()
    }

    fun sealedUntil(ctx: Context): Long = prefs(ctx).getLong("sealUntil", 0L)

    private fun nightActive(ctx: Context, now: LocalTime): Boolean {
        val p = prefs(ctx)
        if (!p.getBoolean("night", false)) return false
        val m = now.toSecondOfDay() / 60
        val from = p.getInt("nightFrom", 23 * 60)
        val to = p.getInt("nightTo", 7 * 60)
        return if (from > to) m >= from || m < to else m in from until to
    }

    /** Why [pkg] is blocked right now, or null if it isn't. */
    fun blockReason(ctx: Context, pkg: String): String? {
        val p = prefs(ctx)
        val apps = p.getStringSet("apps", emptySet()) ?: emptySet()
        if (pkg !in apps) return null
        val now = System.currentTimeMillis()
        if (now < p.getLong("unlockUntil", 0L)) return null
        if (now < p.getLong("sealUntil", 0L)) return p.getString("reason", "Sealed by the System")
        if (nightActive(ctx, LocalTime.now())) return "Night seal: lights-off until your wake-up time"
        return null
    }

    /** When the current block ends, for the "sealed until" line. */
    fun blockEnds(ctx: Context): LocalTime? {
        val p = prefs(ctx)
        val until = p.getLong("sealUntil", 0L)
        if (until > System.currentTimeMillis()) return Instant.ofEpochMilli(until).atZone(ZoneId.systemDefault()).toLocalTime()
        if (nightActive(ctx, LocalTime.now())) return LocalTime.ofSecondOfDay(p.getInt("nightTo", 7 * 60) * 60L)
        return null
    }

    fun serviceOn(ctx: Context): Boolean {
        val enabled = android.provider.Settings.Secure.getString(ctx.contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.split(':').any { it.startsWith(ctx.packageName + "/") }
    }

    /** Installed apps with a launcher icon: (package, name). Suggested ones first. */
    fun launchable(ctx: Context): List<Pair<String, String>> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val list = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            pm.queryIntentActivities(intent, 0)
        }
        return list.map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != ctx.packageName }
            .distinctBy { it.first }
            .sortedWith(compareBy({ it.first !in suggested }, { it.second.lowercase() }))
    }

    fun appName(ctx: Context, pkg: String): String = runCatching {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)
}

/** Watches which app comes to the front. A sealed app gets covered by the System's block screen. */
class BlockerService : AccessibilityService() {
    private var lastPkg: String? = null
    private var lastAt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        val reason = Blocker.blockReason(this, pkg) ?: return
        // Don't re-open the block screen in a tight loop for the same app.
        val now = System.currentTimeMillis()
        if (pkg == lastPkg && now - lastAt < 1500) return
        lastPkg = pkg
        lastAt = now
        startActivity(
            Intent(this, BlockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("pkg", pkg)
                .putExtra("reason", reason),
        )
    }

    override fun onInterrupt() = Unit
}

/** "ACCESS DENIED": the System's block screen. */
class BlockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra("pkg") ?: ""
        val reason = intent.getStringExtra("reason") ?: "Sealed by the System"
        val name = Blocker.appName(this, pkg)
        Cues.ping(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = C.Glow, background = C.Bg, surface = C.Panel)) {
                CompositionLocalProvider(LocalContentColor provides C.Text) {
                    BackHandler { goHome() }
                    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                    LaunchedEffect(Unit) {
                        while (true) {
                            delay(1000)
                            now = System.currentTimeMillis()
                        }
                    }
                    val ends = remember { Blocker.blockEnds(this@BlockActivity) }
                    val unlocksToday = remember { Store.load(this@BlockActivity).day(LocalDate.now()).amounts["unlocks"]?.toInt() ?: 0 }
                    val clock = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalTime()
                    Box(Modifier.fillMaxSize().background(C.Bg), contentAlignment = Alignment.Center) {
                        SystemBackdrop(accent = C.Danger)
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SystemWindow(accent = C.Danger) {
                                Text("[SYSTEM]", color = C.Danger, fontFamily = Mono, fontSize = 12.sp, letterSpacing = 4.sp)
                                Spacer(Modifier.height(6.dp))
                                Text("ACCESS DENIED", fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
                                Spacer(Modifier.height(8.dp))
                                Text("$name is sealed.", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                                Text(reason, color = C.Text2, fontSize = 14.sp)
                                ends?.let {
                                    val left = java.time.Duration.between(clock, it).seconds.let { sec -> if (sec < 0) sec + 86400 else sec }
                                    Text(
                                        "Opens at ${fmt(it)} · %d:%02d left".format(left / 60, left % 60),
                                        color = C.Gold, fontFamily = Mono, fontSize = 13.sp,
                                    )
                                }
                                Spacer(Modifier.height(14.dp))
                                SysButton("BACK TO MY QUESTS", onClick = {
                                    startActivity(Intent(this@BlockActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    finish()
                                }, selected = true, modifier = Modifier.fillMaxWidth())
                                Spacer(Modifier.height(8.dp))
                                SysButton("Go to home screen", onClick = { goHome() }, color = C.Muted, modifier = Modifier.fillMaxWidth())
                                Spacer(Modifier.height(8.dp))
                                SysButton(
                                    "Emergency unlock · 5 min" + if (unlocksToday > 0) " (used $unlocksToday today)" else "",
                                    small = true, color = C.Danger, modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        Blocker.unlock(this@BlockActivity, 5)
                                        val data = Store.load(this@BlockActivity)
                                        val rec = data.day(LocalDate.now())
                                        Store.save(this@BlockActivity, data.with(rec.copy(amounts = rec.amounts + ("unlocks" to (rec.amounts["unlocks"] ?: 0.0) + 1))))
                                        finish()
                                    },
                                )
                                Text("Unlocks are counted in your night report.", color = C.Muted, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}
