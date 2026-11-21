package com.arise.app.game

import com.arise.app.data.AppData
import com.arise.app.data.ChallengeKind
import com.arise.app.data.DayType
import com.arise.app.data.Settings
import com.arise.app.data.Stat
import com.arise.app.data.Taken
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** A 7-day rule: keep it every single day. */
data class WeeklyChallenge(val id: String, val title: String, val rule: String, val icon: String, val tip: String)

/** Where to learn a skill: an app (opens its Play Store search) or a website. */
data class Resource(val name: String, val url: String, val isApp: Boolean)

/** A month to learn a skill: resources, a free video path, and a week-by-week plan. */
data class SkillChallenge(
    val id: String,
    val name: String,
    val icon: String,
    val resources: List<Resource>,
    val video: String,
    /** Week 1..4 goals. */
    val path: List<String>,
    val minutes: Int = 20,
)

/** An optional body program with a follow-along video and a guided session each day. */
data class BodyChallenge(
    val id: String,
    val title: String,
    val icon: String,
    val days: Int,
    val about: String,
    val video: String,
    val honest: String? = null,
    /** Today's moves for day 1..[days]. */
    val plan: (day: Int, s: Settings) -> List<Prescribed>,
)

/** What happened on each day of a challenge so far. */
enum class Mark { KEPT, SLIPPED, MISSED, TODAY, LATER }

object Challenges {
    private fun yt(q: String) = "https://www.youtube.com/results?search_query=" + URLEncoder.encode(q, "UTF-8")
    private fun play(q: String) = Resource(q, "https://play.google.com/store/search?q=" + URLEncoder.encode(q, "UTF-8") + "&c=apps", true)
    private fun web(name: String, url: String) = Resource(name, url, false)

    const val WEEKLY_XP = 150
    const val SKILL_XP = 300
    const val BODY_XP = 200

    // ---- Weekly ----

    val weekly = listOf(
        WeeklyChallenge("nosugar", "No added sugar", "No sweets, desserts, sugary drinks or sugar in your chai or coffee.", "🍬", "Fruit is fine. Sugar hides in biscuits, sauces and 'healthy' bars, so read labels."),
        WeeklyChallenge("nojunk", "No junk or fried food", "No chips, fries, pakoras, fast food or packaged snacks.", "🍟", "Keep a backup snack ready: chana, makhana, nuts, fruit."),
        WeeklyChallenge("nosoda", "Only water, tea or coffee", "No soft drinks, packaged juices or energy drinks.", "🥤", "Sparkling water with lemon scratches the same itch."),
        WeeklyChallenge("phonebed", "No phone in bed", "Phone charges out of reach before lights-off. Every night.", "📵", "Use a cheap alarm clock, or put the phone across the room."),
        WeeklyChallenge("nosocialam", "No social media before noon", "No Instagram, YouTube or reels until 12 PM.", "🌅", "Seal those apps in Tools → Screen blocker."),
        WeeklyChallenge("steps10k", "10,000 steps a day", "Hit 10,000 steps every day this week.", "👣", "Two 15-minute walks plus normal moving gets most people there."),
        WeeklyChallenge("water3", "3 litres a day", "Drink 3 litres of water every day.", "💧", "A full bottle before lunch, another before dinner."),
        WeeklyChallenge("cold", "Cold shower finish", "End every shower with 30 seconds of cold water.", "🧊", "Breathe out slowly. It gets easier from day 3."),
        WeeklyChallenge("nocaffeine", "No caffeine after 2 PM", "No tea, coffee or cola after 2 PM.", "☕", "Sleep improves within days. Swap to herbal tea."),
        WeeklyChallenge("nolate", "Kitchen closes after dinner", "Nothing to eat after dinner. Water and herbal tea only.", "🌙", "Brush your teeth right after dinner. It works."),
        WeeklyChallenge("read10", "Read 10 pages a day", "10 pages of a real book, every day.", "📖", "Same time, same place: before bed works for most people."),
        WeeklyChallenge("gratitude", "3 gratitudes a day", "Write 3 specific things you're grateful for, every day.", "🙏", "Specific beats general: 'the chai Amma made', not 'family'."),
        WeeklyChallenge("protein", "Protein in every meal", "Every meal has a protein: eggs, dal, paneer, curd, chana, chicken, fish.", "🥚", "Eat the protein part first. You'll crave less later."),
        WeeklyChallenge("veggies", "Vegetables twice a day", "Vegetables in at least two meals every day.", "🥦", "A cucumber or carrot counts. Start small."),
        WeeklyChallenge("noshopping", "No online shopping", "Don't buy anything that isn't essential online.", "🛒", "Put it in the cart and wait 7 days. Most of it you won't want."),
        WeeklyChallenge("bed1045", "In bed by 10:45 PM", "Lights off, in bed by 10:45, every night.", "😴", "Start the night routine 40 minutes earlier."),
    )

    fun weeklyById(id: String) = weekly.firstOrNull { it.id == id }

    /** Your weeks: day 1-7, 8-14, 15-21… counted from Day 1 of the current season. */
    fun weekNo(s: Settings, date: LocalDate): Int =
        (ChronoUnit.DAYS.between(s.currentSeasonStart, date) / 7).toInt().coerceAtLeast(0)

    fun weekFirstDay(s: Settings, date: LocalDate): LocalDate = s.currentSeasonStart.plusWeeks(weekNo(s, date).toLong())

    /**
     * The System's random pick. No repeats: every challenge comes up once before any comes back,
     * and never the same one twice in a row.
     */
    fun pick(s: Settings, seed: Long): WeeklyChallenge {
        val past = s.challenges.filter { it.kind == ChallengeKind.WEEKLY }.map { it.id }
        val thisRound = past.takeLast(past.size % weekly.size).toSet() + listOfNotNull(past.lastOrNull())
        val pool = weekly.filter { it.id !in thisRound }.ifEmpty { weekly }
        return pool.shuffled(java.util.Random(seed * 97L + 11L)).first()
    }

    /**
     * This week's offer: a random challenge you can accept or skip. Null once you've accepted or skipped
     * one this week. It's a side quest: no penalty, ever.
     */
    fun weeklyOffer(data: AppData, today: LocalDate): WeeklyChallenge? {
        val s = data.settings
        if (today.isBefore(s.start)) return null
        val first = weekFirstDay(s, today)
        val last = first.plusDays(6)
        if (s.weeklyDeclined == first) return null
        val weeklies = s.challenges.filter { it.kind == ChallengeKind.WEEKLY }
        if (weeklies.any { running(it, today) }) return null
        if (weeklies.any { !it.start.isBefore(first) && !it.start.isAfter(last) }) return null
        return pick(s, first.toEpochDay())
    }

    /** Accepting mid-week: it starts today and still ends on the week's last day. */
    fun acceptWeekly(data: AppData, today: LocalDate, w: WeeklyChallenge): AppData {
        val s = data.settings
        val t = Taken(w.id, ChallengeKind.WEEKLY, today, end = weekFirstDay(s, today).plusDays(6))
        return data.copy(settings = s.copy(challenges = s.challenges + t))
    }

    fun declineWeekly(data: AppData, today: LocalDate): AppData =
        data.copy(settings = data.settings.copy(weeklyDeclined = weekFirstDay(data.settings, today)))

    // ---- Monthly skills ----

    val skills = listOf(
        SkillChallenge(
            "piano", "Piano", "🎹",
            listOf(play("Simply Piano"), play("Flowkey")), yt("piano for absolute beginners lesson 1"),
            listOf(
                "Posture, hand position, the 5-finger C position. Find middle C without looking.",
                "Chords C, F and G. A simple melody, each hand on its own.",
                "Hands together, slowly. Learn one easy song.",
                "Play one full song start to finish. Record yourself.",
            ),
        ),
        SkillChallenge(
            "guitar", "Guitar", "🎸",
            listOf(play("JustinGuitar"), play("Yousician")), yt("JustinGuitar beginner course lesson 1"),
            listOf(
                "Holding the guitar, tuning, chords A, D and E.",
                "One-minute chord changes. Basic down strumming.",
                "Chords G and C. Start one simple song.",
                "Play that song all the way through. Record it.",
            ),
        ),
        SkillChallenge(
            "abacus", "Abacus & mental maths", "🧮",
            listOf(play("abacus learning")), yt("abacus for beginners lesson 1"),
            listOf(
                "Bead values. Set and read numbers 1 to 99.",
                "Addition with the 'small friends' (5) rule.",
                "'Big friends' (10) rule, subtraction.",
                "2-digit speed drills. Try picturing the abacus in your head.",
            ),
        ),
        SkillChallenge(
            "chess", "Chess", "♟",
            listOf(play("Lichess"), play("Chess.com")), yt("how to play chess for beginners"),
            listOf(
                "How every piece moves, check and checkmate. Lichess → Learn.",
                "Opening principles: centre, develop, castle early.",
                "Tactics: forks, pins, skewers. 10 puzzles a day.",
                "Play 10 rapid games and review each one.",
            ),
        ),
        SkillChallenge(
            "typing", "Touch typing", "⌨",
            listOf(web("keybr.com", "https://www.keybr.com"), web("Monkeytype", "https://monkeytype.com")), yt("touch typing for beginners home row"),
            listOf(
                "Home row only. Don't look at the keyboard, even if it's slow.",
                "Top and bottom rows. Accuracy over speed.",
                "Full keyboard, 20 WPM without looking.",
                "Push to 35–40 WPM at 95% accuracy.",
            ),
        ),
        SkillChallenge(
            "drawing", "Drawing", "✏",
            listOf(web("Drawabox (free)", "https://drawabox.com")), yt("drawing for absolute beginners basics"),
            listOf(
                "Confident lines and ellipses, page after page.",
                "Boxes in perspective.",
                "Simple objects from life: a mug, a bottle, a chair.",
                "One finished drawing of something in your room.",
            ),
        ),
        SkillChallenge(
            "coding", "Coding basics", "💻",
            listOf(web("freeCodeCamp", "https://www.freecodecamp.org/learn"), play("Sololearn")), yt("HTML CSS for absolute beginners"),
            listOf(
                "HTML: headings, paragraphs, links, images.",
                "CSS: colours, spacing, layout.",
                "Build a one-page profile site about yourself.",
                "Put it online for free (GitHub Pages) and share the link.",
            ),
        ),
        SkillChallenge(
            "rubik", "Rubik's cube", "🧊",
            listOf(play("Rubik's cube solver tutorial")), yt("J Perm beginner method Rubik's cube"),
            listOf(
                "The white cross and white corners.",
                "The middle layer.",
                "The yellow cross and yellow face.",
                "Solve it start to finish without notes. Time yourself.",
            ),
        ),
        SkillChallenge(
            "asl", "Sign language", "🤟",
            listOf(play("Lingvano")), yt("Bill Vicars ASL lesson 1"),
            listOf(
                "Fingerspelling A–Z and your name.",
                "100 everyday signs.",
                "Simple questions and answers.",
                "Introduce yourself fully, no notes.",
            ),
        ),
        SkillChallenge(
            "singing", "Singing", "🎤",
            listOf(play("vocal warm up singing lessons")), yt("singing lessons for beginners breathing warm up"),
            listOf(
                "Breathing from the belly, gentle warm-ups.",
                "Matching pitch, simple scales.",
                "One song, a section at a time.",
                "Sing the whole song and record it.",
            ),
        ),
        SkillChallenge(
            "juggling", "Juggling", "🤹",
            listOf(play("juggling tutorial")), yt("how to juggle 3 balls for beginners"),
            listOf(
                "One ball, a clean arc at eye level.",
                "Two balls, exchanges.",
                "Three balls: the first full cycle.",
                "20 catches in a row.",
            ),
        ),
        SkillChallenge(
            "calligraphy", "Calligraphy & handwriting", "🖋",
            listOf(play("calligraphy practice")), yt("brush pen calligraphy for beginners"),
            listOf(
                "Basic strokes: thin up, thick down.",
                "Lowercase letters.",
                "Joining letters into words.",
                "Write a full quote you love.",
            ),
        ),
    )

    fun skillById(id: String) = skills.firstOrNull { it.id == id }

    // ---- Body programs ----

    private fun moves(s: Settings, ids: List<String>): List<Move> =
        ids.mapNotNull { Exercises.byId(it) }.filter { Exercises.allowed(s, it) }

    /** Sets go 2 → 3 halfway; reps and holds creep up every other day. */
    private fun build(ms: List<Move>, day: Int, days: Int): List<Prescribed> {
        val sets = if (day <= days / 2) 2 else 3
        val bump = (day - 1) / 2
        return ms.map { m ->
            if (m.hold > 0) Prescribed(m, sets, 0, m.hold + bump * 5) else Prescribed(m, sets, m.reps + bump, 0)
        }
    }

    val body = listOf(
        BodyChallenge(
            "chest7", "Chest firming & lift · 7 days", "🎯", 7,
            "Push moves for the chest muscle under the breast tissue, back and posture work so the chest sits higher, and a stretch for rounded shoulders.",
            yt("7 day chest lift workout for women at home"),
            honest = "Honest truth: no exercise can shrink breast size in 7 days, because breasts are mostly fat and gland tissue. " +
                "Size drops only as overall body fat drops. What 7 days can do: firmer chest muscles and better posture, which make the chest look lifted and tighter.",
            plan = { d, s -> build(moves(s, listOf("incline_pushup", "db_floor_press", "prone_y", "door_row", "plank", "chest_opener")), d, 7) },
        ),
        BodyChallenge(
            "posture7", "Posture fix · 7 days", "🧍", 7,
            "Undo desk and phone hunch: upper back, shoulder blades and chest opening.",
            yt("7 day posture correction exercises at home"),
            plan = { d, s -> build(moves(s, listOf("prone_y", "superman", "door_row", "bird_dog", "chest_opener", "cat_cow")), d, 7) },
        ),
        BodyChallenge(
            "core14", "Core & belly · 14 days", "🔥", 14,
            "A stronger, flatter-looking midsection: deep core, obliques and a short fat-burning finisher.",
            yt("14 day flat belly core workout at home beginner"),
            honest = "Belly fat can't be burned from one spot. This builds the muscle under it; your steps, sleep and food remove the fat on top.",
            plan = { d, s -> build(moves(s, listOf("dead_bug", "heel_taps", "bicycle", "slow_climber", "plank", "fast_march")), d, 14) },
        ),
        BodyChallenge(
            "arms14", "Toned arms · 14 days", "💪", 14,
            "Biceps, triceps and shoulders for firmer arms.",
            yt("14 day toned arms workout dumbbells at home"),
            honest = "Arm 'flab' is mostly fat; toning makes the muscle underneath firmer and it shows more as overall fat drops.",
            plan = { d, s -> build(moves(s, listOf("bicep_curl", "chair_dip", "lateral_raise", "db_row", "wall_pushup")), d, 14) },
        ),
        BodyChallenge(
            "glutes21", "Glutes & legs · 21 days", "🍑", 21,
            "Rounder, stronger glutes and toned legs.",
            yt("21 day glute challenge at home"),
            plan = { d, s -> build(moves(s, listOf("glute_bridge", "donkey_kick", "bw_squat", "single_bridge", "reverse_lunge", "wall_sit")), d, 21) },
        ),
        BodyChallenge(
            "flex14", "Touch your toes · 14 days", "🙆", 14,
            "Hamstrings and lower back, a little more every day until your fingers reach your toes.",
            yt("14 day flexibility challenge touch your toes"),
            plan = { d, s -> build(moves(s, listOf("cat_cow", "lying_hamstring", "standing_fold", "seated_fold")), d, 14) },
        ),
        BodyChallenge(
            "pushup30", "Your first real push-ups · 30 days", "⚔", 30,
            "Wall → incline → knee → full push-ups, one level a week.",
            yt("push up progression for beginners women"),
            plan = { d, s ->
                val id = when {
                    d <= 7 -> "wall_pushup"
                    d <= 14 -> "incline_pushup"
                    d <= 22 -> "knee_pushup"
                    else -> "pushup"
                }
                val m = Exercises.byId(id)!!
                listOf(Prescribed(m, 3, 6 + ((d - 1) % 8), 0)) + build(moves(s, listOf("plank")), d, 30)
            },
        ),
        BodyChallenge(
            "plank30", "Plank challenge · 30 days", "⏱", 30,
            "From 20 seconds to over 2 minutes, a few seconds more every day.",
            yt("30 day plank challenge"),
            plan = { d, _ ->
                val m = Exercises.byId("plank")!!
                listOf(Prescribed(m, 1, 0, (20 + (d - 1) * 4).coerceAtMost(140)))
            },
        ),
    )

    fun bodyById(id: String) = body.firstOrNull { it.id == id }

    // ---- Shared ----

    fun length(t: Taken): Int = when (t.kind) {
        ChallengeKind.WEEKLY -> t.end?.let { (ChronoUnit.DAYS.between(t.start, it) + 1).toInt() } ?: 7
        ChallengeKind.SKILL -> 30
        ChallengeKind.BODY -> bodyById(t.id)?.days ?: 7
    }

    fun day(t: Taken, date: LocalDate): Int = (ChronoUnit.DAYS.between(t.start, date) + 1).toInt()

    fun endDate(t: Taken): LocalDate = t.start.plusDays(length(t).toLong() - 1)

    /** In its main run on [date] (not quit, not finished). */
    fun running(t: Taken, date: LocalDate): Boolean =
        !date.isBefore(t.start) && !date.isAfter(endDate(t)) && (t.quit == null || date.isBefore(t.quit))

    /** Spaced recall after a skill month: these many days after it ends, then every 30 days. */
    private val recallDays = listOf(1L, 3L, 7L, 14L, 30L, 60L)

    /** Which recall this is (0, 1, 2…) on [date], or null if [date] isn't a recall day. */
    fun recallIndex(t: Taken, date: LocalDate): Int? {
        if (t.kind != ChallengeKind.SKILL || !t.keep || t.quit != null || !date.isAfter(endDate(t))) return null
        val gap = ChronoUnit.DAYS.between(endDate(t), date)
        val i = recallDays.indexOf(gap)
        if (i >= 0) return i
        return if (gap > 60 && (gap - 60) % 30 == 0L) recallDays.size + ((gap - 60) / 30).toInt() - 1 else null
    }

    /** A finished skill month you chose to keep: recall quests on spaced days. */
    fun maintaining(t: Taken, date: LocalDate): Boolean = recallIndex(t, date) != null

    /** The next recall day after [today], for the challenge card. */
    fun nextRecall(t: Taken, today: LocalDate): LocalDate? =
        (1L..400L).asSequence().map { today.plusDays(it) }.firstOrNull { recallIndex(t, it) != null }

    /** Things to do from memory, no app, no video. Rotates; falls back to a general prompt. */
    private val recallPrompts = mapOf(
        "piano" to listOf("Play your week-4 song from memory, no app.", "Play chords C, F and G, then a melody you know, eyes off your hands.", "Teach someone (or your phone camera) where middle C is and one song."),
        "guitar" to listOf("Play your song from memory, start to finish.", "Switch between A, D, E, G and C for one minute without stopping.", "Strum a pattern you learned and sing along."),
        "abacus" to listOf("Do 10 two-digit sums on the abacus without notes.", "Picture the abacus and do 5 sums in your head.", "Explain the 'small friends' and 'big friends' rules out loud."),
        "chess" to listOf("Solve 10 puzzles without hints.", "Play one game and write down which opening principle you used.", "Explain a fork, a pin and a skewer out loud."),
        "typing" to listOf("Do a 1-minute typing test without looking. Beat 35 WPM?", "Type one paragraph of anything, eyes on the screen only.", "Type the alphabet three times, home row only for your hands' start position."),
        "drawing" to listOf("Draw 3 boxes in perspective from memory.", "Draw one object in your room in 10 minutes.", "Fill a page with confident lines and ellipses, then one small sketch."),
        "coding" to listOf("Rebuild a small HTML page from memory: heading, paragraph, link, image.", "Explain what CSS margin and padding do, out loud.", "Add one new section to your profile page."),
        "rubik" to listOf("Scramble and solve the cube without notes. Time it.", "Solve just the first two layers from memory.", "Explain the beginner method steps out loud in order."),
        "asl" to listOf("Fingerspell your full name and 5 words.", "Introduce yourself fully in sign, no notes.", "Sign 20 everyday words from memory."),
        "singing" to listOf("Warm up from memory, then sing your song once.", "Match 5 notes on a pitch app.", "Record your song and compare with the week-4 recording."),
        "juggling" to listOf("Get 20 catches in a row with 3 balls.", "Do the 3-ball cascade for 30 seconds.", "Two balls in one hand for 10 throws."),
        "calligraphy" to listOf("Write a full quote from memory.", "Write the lowercase alphabet in one go.", "Join 10 words with clean thin and thick strokes."),
    )

    fun recallPrompt(id: String, index: Int): String {
        val list = recallPrompts[id] ?: listOf("Do the hardest thing you learned, from memory. No app, no video.")
        return list[index % list.size]
    }

    fun questId(t: Taken): String = when (t.kind) {
        ChallengeKind.WEEKLY -> "wc_${t.id}"
        ChallengeKind.SKILL -> "sk_${t.id}"
        ChallengeKind.BODY -> "bc_${t.id}"
    }

    fun title(t: Taken): String = when (t.kind) {
        ChallengeKind.WEEKLY -> weeklyById(t.id)?.title
        ChallengeKind.SKILL -> skillById(t.id)?.name
        ChallengeKind.BODY -> bodyById(t.id)?.title
    } ?: t.id

    fun bodyToday(s: Settings, t: Taken, date: LocalDate): List<Prescribed> =
        bodyById(t.id)?.plan?.invoke(day(t, date).coerceIn(1, length(t)), s) ?: emptyList()

    /** Today's quests from challenges, with where they go in the day. */
    fun blocks(s: Settings, date: LocalDate, type: DayType): List<Block> {
        if (type == DayType.SICK) return emptyList()
        val out = mutableListOf<Block>()
        for (t in s.challenges) {
            val run = running(t, date)
            when (t.kind) {
                ChallengeKind.WEEKLY -> if (run) {
                    val w = weeklyById(t.id) ?: continue
                    out += Block(
                        LocalTime.of(21, 30), "Weekly challenge",
                        quest = Quest(
                            questId(t), "${w.title} · day ${day(t, date)}/${length(t)}", "${w.rule} Did you keep it today? Honest answers only; a slip is just data.",
                            null, Stat.VIT, 25, w.icon, limitMin = 120, optional = true,
                        ),
                    )
                }
                ChallengeKind.SKILL -> {
                    val k = skillById(t.id) ?: continue
                    val at = t.time ?: if (type == DayType.OFFICE || type == DayType.WFH) LocalTime.of(19, 30) else LocalTime.of(16, 0)
                    val recall = recallIndex(t, date)
                    if (run) {
                        val week = ((day(t, date) - 1) / 7).coerceIn(0, k.path.lastIndex)
                        out += Block(
                            at, "Skill",
                            quest = Quest(
                                questId(t), "${k.name} · day ${day(t, date)}/30", "This week: ${k.path[week]}",
                                null, Stat.INT, 35, k.icon, Kind.AMOUNT,
                                target = k.minutes.toDouble(), unit = "min", step = 5.0, limitMin = 120,
                            ),
                        )
                    } else if (recall != null) {
                        out += Block(
                            at, "Skill recall",
                            quest = Quest(
                                questId(t), "Recall: ${k.name} (#${recall + 1})",
                                "${recallPrompt(t.id, recall)} Recalling from memory is what makes it stick. Next recall gets further apart.",
                                null, Stat.INT, 25, "🧠", limitMin = 180,
                            ),
                        )
                    }
                }
                ChallengeKind.BODY -> if (run) {
                    val bc = bodyById(t.id) ?: continue
                    val list = bodyToday(s, t, date)
                    if (list.isEmpty()) continue
                    out += Block(
                        t.time ?: LocalTime.of(19, 0), "Body challenge",
                        quest = Quest(
                            questId(t), "${bc.title.substringBefore(" ·")} · day ${day(t, date)}/${bc.days}",
                            "${list.size} moves: ${list.joinToString(", ") { it.move.name }}. Open the coach or follow the video.",
                            null, Stat.STR, 40, bc.icon, Kind.AMOUNT,
                            target = Exercises.totalSets(list).toDouble(), unit = "sets", step = 1.0, limitMin = 120,
                        ),
                    )
                }
            }
        }
        return out
    }

    /** Day-by-day marks for a challenge, for the progress dots. */
    fun marks(data: AppData, t: Taken, today: LocalDate): List<Mark> {
        val id = questId(t)
        return (0 until length(t)).map { i ->
            val d = t.start.plusDays(i.toLong())
            val rec = data.records[d]
            val q = Game.plan(data, d).quests.firstOrNull { it.id == id }
            when {
                t.quit != null && !d.isBefore(t.quit) -> Mark.MISSED
                d.isAfter(today) -> Mark.LATER
                rec?.skipped?.containsKey(id) == true -> Mark.SLIPPED
                q != null && Game.ratio(q, rec) >= (if (t.kind == ChallengeKind.WEEKLY) 1f else 0.5f) -> Mark.KEPT
                d == today -> Mark.TODAY
                else -> Mark.MISSED
            }
        }
    }

    /** Cleared: weekly = every day kept; skill = 20 of 30 days; body = 80% of days. Only once it's over. */
    fun cleared(data: AppData, t: Taken, today: LocalDate): Boolean {
        if (today.isBefore(endDate(t))) return false
        val m = marks(data, t, today)
        val kept = m.count { it == Mark.KEPT }
        return when (t.kind) {
            ChallengeKind.WEEKLY -> kept == m.size
            ChallengeKind.SKILL -> kept >= 20
            ChallengeKind.BODY -> kept * 5 >= m.size * 4
        }
    }

    /** XP per kept day of a weekly challenge; a perfect week rounds up to [WEEKLY_XP]. */
    const val WEEKLY_DAY_XP = 20

    /** No longer used: weekly challenges never bring a penalty. */
    const val WEEKLY_PENALTY_MISSED = 4

    fun kept(data: AppData, t: Taken, today: LocalDate): Int = marks(data, t, today).count { it == Mark.KEPT }

    fun weeklyXp(kept: Int): Int = if (kept >= 7) WEEKLY_XP else kept * WEEKLY_DAY_XP

    /** The weekly challenge that ended yesterday with too few days kept, if any. */
    fun active(s: Settings, today: LocalDate): List<Taken> = s.challenges.filter { running(it, today) || maintaining(it, today) || (it.kind == ChallengeKind.SKILL && it.keep && it.quit == null && today.isAfter(endDate(it))) }

    /** Weekly XP scales with days kept (paid once the week is over); skill and body pay when cleared. */
    fun bonusXp(data: AppData, today: LocalDate): Int = data.settings.challenges.sumOf { t ->
        when {
            t.kind == ChallengeKind.WEEKLY -> if (t.quit == null && !today.isBefore(endDate(t))) weeklyXp(kept(data, t, today)) else 0
            !cleared(data, t, today) -> 0
            t.kind == ChallengeKind.SKILL -> SKILL_XP
            else -> BODY_XP
        }
    }
}
