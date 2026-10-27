package com.arise.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.arise.app.data.AppData
import com.arise.app.data.Settings
import com.arise.app.data.Store
import com.arise.app.game.Feats
import com.arise.app.game.Game
import com.arise.app.game.Report
import com.arise.app.game.Season
import com.arise.app.notify.Reminders
import com.arise.app.ui.AchievementOverlay
import com.arise.app.ui.AriseTheme
import com.arise.app.ui.AssessmentScreen
import com.arise.app.ui.C
import com.arise.app.ui.CalendarScreen
import com.arise.app.ui.ChallengesScreen
import com.arise.app.ui.LevelUpOverlay
import com.arise.app.ui.LocalTutorialTargets
import com.arise.app.ui.LogScreen
import com.arise.app.ui.Focus
import com.arise.app.ui.PlayerScreen
import com.arise.app.ui.SeasonEndOverlay
import com.arise.app.ui.ToolsScreen
import com.arise.app.ui.SysMsg
import com.arise.app.ui.SystemBus
import com.arise.app.ui.SystemPopup
import com.arise.app.ui.TodayScreen
import com.arise.app.ui.TrainScreen
import com.arise.app.ui.TrainTarget
import com.arise.app.ui.TutorialOverlay
import com.arise.app.ui.TutorialTargets
import com.arise.app.ui.tutorialSteps
import com.arise.app.ui.tutorialTarget
import com.arise.app.ui.Voice
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime

/** The night report as a System window. */
private fun reportMessage(r: Report): SysMsg {
    val need = (kotlin.math.ceil(Game.CLEAR_RATIO * r.total).toInt() - r.done).coerceAtLeast(0)
    return SysMsg(
        if (r.cleared) "DAILY REPORT · CLEARED" else "DAILY REPORT",
        listOfNotNull(
            "Quests: ${r.done}/${r.total} · +${r.xp} XP",
            r.wakeLine,
            if (r.bonusTotal > 0) "Bonus quests: ${r.bonusDone}/${r.bonusTotal}" else null,
            if (r.skipped.isNotEmpty()) "Skipped: ${r.skipped.joinToString(", ")}" else null,
            if (r.exSkips > 0) "Skips inside workouts: ${r.exSkips}" else null,
            if (r.focusMin > 0) "Focus: ${r.focusMin} min" else null,
            if (r.unlocks > 0) "Emergency unlocks of sealed apps: ${r.unlocks}" else null,
            if (r.cleared) "Status: CLEARED. The streak lives." else "Status: NOT CLEARED. $need more before midnight, or a penalty awaits.",
            "Tomorrow begins with: ${r.tomorrow}",
        ),
        accent = if (r.cleared) C.Gold else C.Danger,
        button = if (r.cleared) "REST" else "ON IT",
        speak = if (r.cleared) "Daily report. All clear." else "Daily report. $need quests remain.",
    )
}

class MainActivity : ComponentActivity() {
    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { Reminders.refresh(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Reminders.createChannel(this)
        com.arise.app.notify.CoachCues.createChannel(this)
        Voice.init(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        Reminders.refresh(this)
        setContent { AriseTheme { AriseApp() } }
    }
}

private data class Tab(val icon: String, val label: String)

private val tabs = listOf(
    Tab("⚔", "Today"),
    Tab("🏃", "Train"),
    Tab("⏱", "Tools"),
    Tab("📈", "Log"),
    Tab("👤", "Player"),
)

@Composable
fun AriseApp() {
    val ctx = LocalContext.current
    var data by remember { mutableStateOf(Store.load(ctx)) }
    var today by remember { mutableStateOf(LocalDate.now()) }
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = LocalTime.now()
            today = LocalDate.now()
        }
    }
    val update: (AppData) -> Unit = { d ->
        data = d
        Store.save(ctx, d)
        Reminders.refresh(ctx)
    }
    Voice.enabled = data.settings.voice
    // Notifications and pop-ups (like "I'm up" from the lock screen) change data outside the app.
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                var fresh = Store.load(ctx)
                Focus.settle(ctx, fresh)?.let {
                    fresh = it
                    Store.save(ctx, it)
                }
                if (Store.toJson(fresh) != Store.toJson(data)) data = fresh
                now = LocalTime.now()
                today = LocalDate.now()
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var trainTarget by rememberSaveable { mutableIntStateOf(TrainTarget.RUN) }
    var autoFlow by remember { mutableStateOf<String?>(null) }
    var editingSetup by remember { mutableStateOf(false) }
    var calendar by remember { mutableStateOf(false) }
    var challengesOpen by remember { mutableStateOf(false) }
    var tutorial by remember { mutableStateOf(false) }

    val progress = remember(data, today) { Game.progress(data, today) }
    // Stats at the last level-up (or app start), so the animation can show what grew.
    var baseline by remember { mutableStateOf(progress) }

    // System pop-ups sit above everything, including the assessment.
    @Composable
    fun Popups() {
        SystemBus.queue.firstOrNull()?.let { m -> SystemPopup(m) { if (SystemBus.queue.isNotEmpty()) SystemBus.queue.removeAt(0) } }
    }

    if (!data.settings.onboarded || editingSetup) {
        Box(Modifier.fillMaxSize()) {
            AssessmentScreen(data, today, update, editing = editingSetup, onClose = { editingSetup = false })
            Popups()
        }
        return
    }
    if (Season.over(data.settings, today) && !data.settings.seasonEndSeen) {
        SeasonEndOverlay(data, today, progress, update)
        return
    }
    if (calendar) {
        CalendarScreen(data, today, update, onClose = { calendar = false })
        return
    }
    if (challengesOpen) {
        Box(Modifier.fillMaxSize()) {
            ChallengesScreen(data, today, update, onTrain = {
                challengesOpen = false
                trainTarget = TrainTarget.CHALLENGE
                tab = 1
            }, onClose = { challengesOpen = false })
            Popups()
        }
        return
    }

    // The night report: once a day, the first time the app is open after the 9:45 PM quest check.
    LaunchedEffect(now, today) {
        val s = data.settings
        if (today >= s.start && !now.isBefore(LocalTime.of(21, 45)) && s.reportSeen != today) {
            SystemBus.post(reportMessage(Feats.report(data, today)))
            update(data.copy(settings = s.copy(reportSeen = today)))
        }
    }
    val newTitles = remember(data, today) {
        Feats.unlocked(data, today).filter { it.id !in data.settings.seenAchievements }
    }

    val targets = remember { TutorialTargets() }
    LaunchedEffect(data.settings.tutorialSeen) {
        if (!data.settings.tutorialSeen) {
            tab = 0
            delay(600) // let the Today screen lay out so the spotlight knows where things are
            tutorial = true
        }
    }

    CompositionLocalProvider(LocalTutorialTargets provides targets) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = C.Bg,
                bottomBar = {
                    NavigationBar(containerColor = C.Panel, modifier = Modifier.tutorialTarget("tabs")) {
                        tabs.forEachIndexed { i, t ->
                            NavigationBarItem(
                                selected = tab == i,
                                onClick = { tab = i },
                                icon = { Text(t.icon, fontSize = 18.sp) },
                                label = { Text(t.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedTextColor = C.Glow,
                                    unselectedTextColor = C.Muted,
                                    indicatorColor = C.PanelHi,
                                ),
                            )
                        }
                    }
                },
            ) { pad ->
                Box(Modifier.padding(pad).fillMaxSize()) {
                    when (tab) {
                        0 -> TodayScreen(data, today, now, progress, update, openTrain = { target, flow ->
                            trainTarget = target
                            autoFlow = flow
                            tab = 1
                        }, openChallenges = { challengesOpen = true })
                        1 -> TrainScreen(
                            data, today, update, trainTarget, autoFlow,
                            onTargetChange = { trainTarget = it },
                            onAutoFlowConsumed = { autoFlow = null },
                        )
                        2 -> ToolsScreen(data, today, update, onChallenges = { challengesOpen = true })
                        3 -> LogScreen(data, today, update)
                        else -> PlayerScreen(
                            data, today, progress, update,
                            onEditSetup = { editingSetup = true },
                            onCalendar = { calendar = true },
                            onTutorial = {
                                tab = 0
                                tutorial = true
                            },
                            onRestart = {
                                val s = data.settings
                                Store.saveSnapshot(ctx, data)
                                tab = 0
                                update(
                                    AppData(
                                        settings = Settings(
                                            notifications = s.notifications,
                                            voice = s.voice,
                                            restarts = s.restarts + 1,
                                            playerName = s.playerName,
                                            officeDays = s.officeDays,
                                            wfhDays = s.wfhDays,
                                            wakeOffice = s.wakeOffice,
                                            wakeWfh = s.wakeWfh,
                                            wakeOff = s.wakeOff,
                                            goalWeight = s.goalWeight,
                                            workStart = s.workStart,
                                            workEnd = s.workEnd,
                                            commuteMin = s.commuteMin,
                                            meals = s.meals,
                                            meds = s.meds,
                                            language = s.language,
                                            languageApp = s.languageApp,
                                            morning = s.morning,
                                            night = s.night,
                                            challengeDays = s.challengeDays,
                                            blocked = s.blocked,
                                        ),
                                    ),
                                )
                            },
                        )
                    }
                }
            }

            if (tutorial) {
                TutorialOverlay(tutorialSteps(data.settings.name), onDone = {
                    tutorial = false
                    if (!data.settings.tutorialSeen) update(data.copy(settings = data.settings.copy(tutorialSeen = true)))
                })
            } else if (progress.level > data.settings.seenLevel) {
                LevelUpOverlay(
                    name = data.settings.name,
                    fromLevel = data.settings.seenLevel,
                    toLevel = progress.level,
                    statsBefore = if (baseline.level < progress.level) baseline.stats else null,
                    statsAfter = progress.stats,
                    onDone = {
                        baseline = progress
                        update(data.copy(settings = data.settings.copy(seenLevel = progress.level)))
                    },
                )
            } else if (newTitles.isNotEmpty()) {
                val a = newTitles.first()
                AchievementOverlay(a.name, a.quote, a.goal, onDone = {
                    update(data.copy(settings = data.settings.copy(seenAchievements = data.settings.seenAchievements + a.id)))
                })
            }
            Popups()
        }
    }
}
