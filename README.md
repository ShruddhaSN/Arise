# ARISE ⚔️

A Solo Leveling–style life system for Android: daily quests tied to your schedule, XP and ranks, a run coach, strength sessions, a body log with charts, boss fights, notifications and a home-screen widget.

## Getting the app onto your phone (nothing to install)

GitHub builds the APK in the cloud, so you don't need Android Studio.

1. **Create a GitHub account** at github.com, if you don't have one.
2. **Create a new repository** (top right **+** → *New repository*):
   - Name: `arise`
   - **Public** (simplest, so your phone can download without logging in) or Private
   - Don't tick "Add a README"
3. **Upload this folder**. On the new repo page, click **"uploading an existing file"**, then drag in **everything inside the `Arise` folder**, including the hidden `.github` folder.
   - On Windows, turn on *View → Show → Hidden items* in File Explorer so you can see `.github`.
   - Click **Commit changes**.
4. **Wait for the build** (about 5 min). Open the **Actions** tab. A green ✓ means it's done.
5. **Download on your phone.** Open this link in your phone's browser:
   `https://github.com/<your-username>/arise/releases/download/latest/Arise.apk`
   You can also go to the repo → **Releases** → **Arise (latest build)** → `Arise.apk`.
6. **Install it.** Tap the downloaded file. Android will ask you to allow installs from your browser. Allow it, then tap **Install**.

### First launch on a Redmi (important)
MIUI aggressively kills background apps. Long-press the Arise icon → **App info**, then set:
- **Autostart** → ON
- **Battery saver** → No restrictions
- **Notifications** → allow all, including lock screen and floating

Then add the widget: long-press the home screen → **Widgets** → **Arise**.

### Updating
When the code changes, upload the changed files to the repo again. A new APK is built automatically. Install it over the old one and your data stays, because every build is signed with the same key (`app/arise.keystore`).

### Backups
Every Sunday, go to **Player → Save backup file** and save it to Google Drive. That one file holds everything. On a new phone, install Arise and tap **Restore**.

## How the System works

| Thing | Rule |
|---|---|
| Day 1 | The day you finish the first-time setup (name, work days, wake times, "already started on paper?", goal). Weeks count from here. |
| Fill-in days | If you started on paper, those days can be ticked off for XP and streaks. They never cause penalties. Neither does Day 1. |
| Day types | Office, WFH and days off come from your setup (default Mon/Wed/Thu office, Tue/Fri WFH). Tap to switch a day, or pick **Rest / Off** when you're sick or travelling. |
| Training | Run Tue/Fri/Sun · Strength Wed/Sat · Easy walk Mon/Thu |
| Full vs Min | Full = 100% XP. Min = 20% XP and uses 1 of your **2 passes per week**. |
| Cleared day | 70% of quests done (Full or Min). Cleared days build your streak. |
| Penalty | If yesterday wasn't cleared, today has a penalty quest. *Never miss twice.* |
| Progression | Each week, learning time goes up, bedtime moves to 11 PM, water goes to 3 L, and the run and strength plans step up. |
| Ranks | E (Lv 1–4) → D → C → B → A → S (Lv 25+) |

## Project layout
```
app/src/main/java/com/arise/app/
  MainActivity.kt        app shell, tabs, level-up dialog
  data/                  models + JSON storage
  game/Program.kt        schedule, quests, run & strength plans  ← tweak your routine here
  game/Game.kt           XP, levels, streaks, bosses
  game/Knowledge.kt      daily knowledge cards
  notify/Reminders.kt    schedule notifications
  widget/AriseWidget.kt  home-screen widget
  ui/                    screens and charts
```

## Roadmap (v2)
- Health Connect sync: steps and sleep from Samsung Health or your watch
- Quest editor inside the app
- Cycle-phase notes alongside Flo
