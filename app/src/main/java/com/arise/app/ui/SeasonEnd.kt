package com.arise.app.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arise.app.data.AppData
import com.arise.app.game.Feats
import com.arise.app.game.Profile
import com.arise.app.game.Progress
import java.time.LocalDate

/**
 * The finish line: Day 1 against now, then the choice. Continue (a new season, same length),
 * pick a new length, or stop the challenge and keep using Arise as a tracker.
 */
@Composable
fun SeasonEndOverlay(data: AppData, today: LocalDate, p: Progress, onUpdate: (AppData) -> Unit) {
    val s = data.settings
    var choosing by remember { mutableStateOf(false) }
    val now = remember(data, p) { Feats.ratingsNow(data, p) }
    val day1 = remember(data) { Profile.ratings(s.profile, 66) }
    val ctx = LocalContext.current
    LaunchedEffect(Unit) {
        Cues.levelUp(ctx)
        Voice.say("Challenge complete. ${s.challengeDays} days.")
    }

    fun newSeason(days: Int) {
        onUpdate(
            data.copy(
                settings = s.copy(
                    challengeDays = days,
                    seasonStart = today,
                    season = s.season + 1,
                    seasonEndSeen = false,
                ),
            ),
        )
    }

    Box(Modifier.fillMaxSize().background(C.Bg)) {
        SystemBackdrop(accent = C.Gold, gate = true)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(330.dp))
            Text("[SYSTEM]", color = C.Gold, fontFamily = Mono, letterSpacing = 4.sp)
            Text("CHALLENGE COMPLETE", fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = Mono, textAlign = TextAlign.Center)
            Text("${s.challengeDays} days · season ${s.season} · ${p.clearedDays} days cleared · best streak ${p.bestStreak}", color = C.Text2, fontSize = 13.sp, textAlign = TextAlign.Center)
            Panel(accent = C.Gold) {
                Row {
                    Text("", modifier = Modifier.weight(1f))
                    Text("DAY 1", color = C.Muted, fontSize = 10.sp, fontFamily = Mono, modifier = Modifier.width(56.dp))
                    Text("NOW", color = C.Gold, fontSize = 10.sp, fontFamily = Mono, modifier = Modifier.width(70.dp))
                }
                now.zip(day1).forEach { (n, d) ->
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text("${n.icon} ${n.label}", modifier = Modifier.weight(1f))
                        Text("${d.now}", color = C.Text2, fontFamily = Mono, modifier = Modifier.width(56.dp))
                        Text("${n.now}  +${(n.now - d.now).coerceAtLeast(0)}", color = C.Gold, fontFamily = Mono, fontWeight = FontWeight.Bold, modifier = Modifier.width(70.dp))
                    }
                }
            }
            Text("You said you would. You did. What now?", color = C.Text, fontWeight = FontWeight.SemiBold)
            if (!choosing) {
                SysButton("CONTINUE: another ${s.challengeDays} days", onClick = { newSeason(s.challengeDays) }, selected = true, color = C.Gold, modifier = Modifier.fillMaxWidth())
                SysButton("Choose a new length", onClick = { choosing = true }, modifier = Modifier.fillMaxWidth())
                SysButton("Stop the challenge (keep tracking)", color = C.Muted, small = true, modifier = Modifier.fillMaxWidth(), onClick = {
                    onUpdate(data.copy(settings = s.copy(challengeDays = 0, seasonEndSeen = true)))
                })
                Text("Your level, titles and history stay either way.", color = C.Muted, fontSize = 11.sp)
            } else {
                listOf(21, 30, 66, 90).forEach { d ->
                    SysButton("$d days", onClick = { newSeason(d) }, modifier = Modifier.fillMaxWidth())
                }
                SysButton("‹ Back", onClick = { choosing = false }, color = C.Muted, small = true)
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}
