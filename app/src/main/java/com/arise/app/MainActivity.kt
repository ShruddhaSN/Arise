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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.arise.app.data.AppData
import com.arise.app.data.Store
import com.arise.app.game.Game
import com.arise.app.notify.Reminders
import com.arise.app.ui.AriseTheme
import com.arise.app.ui.C
import com.arise.app.ui.LogScreen
import com.arise.app.ui.Mono
import com.arise.app.ui.OnboardingScreen
import com.arise.app.ui.PlayerScreen
import com.arise.app.ui.TodayScreen
import com.arise.app.ui.TrainScreen
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime

class MainActivity : ComponentActivity() {
    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { Reminders.refresh(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Reminders.createChannel(this)
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

    var tab by rememberSaveable { mutableIntStateOf(0) }
    val progress = remember(data, today) { Game.progress(data, today) }
    var seenLevel by remember { mutableIntStateOf(progress.level) }
    var levelUp by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(progress.level) {
        if (progress.level > seenLevel) levelUp = progress.level
        seenLevel = progress.level
    }

    var editingSetup by remember { mutableStateOf(false) }
    if (!data.settings.onboarded || editingSetup) {
        OnboardingScreen(data, today, update, editing = editingSetup, onClose = { editingSetup = false })
        return
    }

    Scaffold(
        containerColor = C.Bg,
        bottomBar = {
            NavigationBar(containerColor = C.Panel) {
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
                0 -> TodayScreen(data, today, now, update, openTrain = { tab = 1 })
                1 -> TrainScreen(data, today, update)
                2 -> LogScreen(data, today, update)
                else -> PlayerScreen(data, today, progress, update, onEditSetup = { editingSetup = true })
            }
        }
    }

    levelUp?.let { lvl ->
        AlertDialog(
            onDismissRequest = { levelUp = null },
            containerColor = C.Panel,
            title = { Text("[SYSTEM] LEVEL UP", fontFamily = Mono, color = C.Gold, fontWeight = FontWeight.Bold) },
            text = {
                Text("You reached Level $lvl · ${Game.rankFor(lvl)}-Rank.\nYour body is changing. Keep going, Player.")
            },
            confirmButton = { TextButton(onClick = { levelUp = null }) { Text("ARISE", color = C.Glow, fontFamily = Mono) } },
        )
    }
}
