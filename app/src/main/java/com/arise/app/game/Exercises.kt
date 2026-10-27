package com.arise.app.game

import com.arise.app.data.Settings
import java.net.URLEncoder
import java.time.LocalDate

enum class Group(val label: String) {
    LEGS("Legs"),
    HIPS("Glutes & hips"),
    PUSH("Push · chest & arms"),
    PULL("Pull · back & posture"),
    CORE("Core"),
    FINISHER("Finisher · fat burn"),
    STRETCH("Flexibility"),
    ARMS("Arms"),
}

/**
 * One strength move. [level] 1 = easiest. [reps] per set (per side if [perSide]);
 * [hold] > 0 makes it a timed hold instead. [hard] lists joints it's tough on.
 */
data class Move(
    val id: String,
    val name: String,
    val group: Group,
    val level: Int,
    val reps: Int,
    val cue: String,
    val how: String,
    val dumbbell: Boolean = false,
    val hold: Int = 0,
    val perSide: Boolean = false,
    /** Seconds per rep, for the spoken count. */
    val tempo: Int = 3,
    val hard: Set<String> = emptySet(),
) {
    val demoUrl: String
        get() = "https://www.youtube.com/results?search_query=" + URLEncoder.encode("$name exercise proper form for beginners", "UTF-8")
}

/** A move as prescribed today. */
data class Prescribed(val move: Move, val sets: Int, val reps: Int, val hold: Int) {
    val dose: String
        get() = if (hold > 0) "$sets × ${hold}s" + (if (move.perSide) " / side" else "")
        else "$sets × $reps" + (if (move.perSide) " / side" else "")
}

object Exercises {
    private const val KNEES = "knees"
    private const val BACK = "back"
    private const val SHOULDERS = "shoulders"
    private const val WRISTS = "wrists"

    val all: List<Move> = listOf(
        // Legs
        Move("chair_squat", "Chair squat", Group.LEGS, 1, 10, "Sit back until you touch the chair, then stand tall.",
            "Stand in front of a chair, feet shoulder-width. Push your hips back and sit down slowly until you touch the chair. Drive through your heels to stand. Knees follow your toes."),
        Move("wall_sit", "Wall sit", Group.LEGS, 1, 0, "Back flat on the wall, thighs as low as feels okay.",
            "Lean your back on a wall and slide down until your knees are bent, like sitting on an invisible chair. Hold. Breathe.", hold = 30, hard = setOf(KNEES)),
        Move("bw_squat", "Bodyweight squat", Group.LEGS, 2, 12, "Sit between your heels, chest up.",
            "Feet shoulder-width, toes slightly out. Push hips back and down, chest up, until thighs are near parallel. Stand up by pushing the floor away."),
        Move("step_up", "Step-up", Group.LEGS, 2, 10, "Whole foot on the step, push through the heel.",
            "Use a sturdy step or the bottom stair. Step up with one foot, stand tall, step down slowly. All reps on one leg, then switch.", perSide = true, hard = setOf(KNEES)),
        Move("reverse_lunge", "Reverse lunge", Group.LEGS, 2, 8, "Step back, both knees about 90°.",
            "Stand tall. Step one foot back and lower until both knees bend to about 90°. Push through the front heel to come back up.", perSide = true, hard = setOf(KNEES)),
        Move("goblet_squat", "Goblet squat", Group.LEGS, 3, 10, "Dumbbell at your chest, sit down between your heels.",
            "Hold one dumbbell vertically at your chest with both hands. Squat down between your heels, elbows inside your knees. Stand up tall.", dumbbell = true),
        Move("split_squat", "Split squat", Group.LEGS, 3, 8, "Back knee lowers toward the floor.",
            "Take a long step forward and stay there. Lower your back knee toward the floor, then push back up. All reps on one side, then switch.", perSide = true, hard = setOf(KNEES)),

        // Glutes & hips
        Move("glute_bridge", "Glute bridge", Group.HIPS, 1, 12, "Squeeze your glutes at the top for 1 second.",
            "Lie on your back, knees bent, feet flat. Push through your heels and lift your hips until your body is a straight line from knees to shoulders. Squeeze, then lower slowly."),
        Move("donkey_kick", "Donkey kick", Group.HIPS, 1, 12, "Kick the sole of your foot toward the ceiling.",
            "On all fours. Keep the knee bent and lift one foot toward the ceiling, squeezing your glute. Don't arch your back. All reps on one side, then switch.", perSide = true, hard = setOf(WRISTS)),
        Move("single_bridge", "Single-leg bridge", Group.HIPS, 2, 8, "Hips stay level as you lift.",
            "Like a glute bridge, but with one leg straight in the air. Push through the planted heel. All reps on one side, then switch.", perSide = true),
        Move("bw_good_morning", "Good morning", Group.HIPS, 2, 12, "Hinge at the hips, flat back.",
            "Hands behind your head, soft knees. Push your hips back and tip your chest forward with a flat back until you feel your hamstrings stretch. Stand back up.", hard = setOf(BACK)),
        Move("db_rdl", "Dumbbell Romanian deadlift", Group.HIPS, 3, 10, "Soft knees, hips back, flat back.",
            "Hold dumbbells in front of your thighs. Push your hips back and slide the weights down your legs with a flat back. Stop when you feel a hamstring stretch, then stand up by squeezing your glutes.", dumbbell = true, hard = setOf(BACK)),
        Move("db_hip_thrust", "Dumbbell hip thrust", Group.HIPS, 3, 12, "Upper back on the bed edge, chin tucked.",
            "Sit on the floor with your upper back against the edge of the bed, dumbbell on your hips. Drive your hips up until your body is flat like a table. Squeeze and lower.", dumbbell = true),

        // Push
        Move("wall_pushup", "Wall push-up", Group.PUSH, 1, 10, "Body in one straight line.",
            "Hands on the wall at shoulder height, step back. Bend your elbows to bring your chest to the wall, then push away. Keep your body straight like a plank."),
        Move("incline_pushup", "Incline push-up", Group.PUSH, 2, 10, "Hands on the bed, body straight.",
            "Hands on the edge of a bed or sturdy table. Lower your chest to the edge, elbows at about 45°, and push back up.", hard = setOf(WRISTS)),
        Move("db_floor_press", "Dumbbell floor press", Group.PUSH, 2, 12, "Elbows touch the floor softly, then press.",
            "Lie on your back, knees bent, a dumbbell in each hand above your chest. Lower until your upper arms touch the floor, then press up. Great for the chest.", dumbbell = true),
        Move("knee_pushup", "Knee push-up", Group.PUSH, 2, 8, "Straight line from knees to head.",
            "On your knees and hands, hands a bit wider than your shoulders. Lower your chest toward the floor, then push up.", hard = setOf(WRISTS, SHOULDERS)),
        Move("db_shoulder_press", "Dumbbell shoulder press", Group.PUSH, 3, 10, "Ribs down, press straight up.",
            "Sit or stand with dumbbells at shoulder height. Press them overhead without arching your back, then lower slowly.", dumbbell = true, hard = setOf(SHOULDERS)),
        Move("pushup", "Push-up", Group.PUSH, 3, 8, "Chest down to fist height.",
            "Plank position, hands under shoulders. Lower your whole body as one, chest to fist height, and push up. Drop to your knees when form breaks.", hard = setOf(WRISTS, SHOULDERS)),

        // Pull
        Move("prone_y", "Prone Y-raise", Group.PULL, 1, 10, "Thumbs up, lift your arms in a Y.",
            "Lie face down, arms overhead in a Y shape, thumbs up. Lift your arms off the floor by squeezing your shoulder blades, hold 1 second, lower."),
        Move("superman", "Superman", Group.PULL, 1, 10, "Lift arms and legs together, gently.",
            "Lie face down, arms forward. Lift your arms, chest and legs a little off the floor, hold for 2 seconds, lower slowly.", hard = setOf(BACK)),
        Move("door_row", "Doorframe row", Group.PULL, 2, 10, "Pull your chest to the frame.",
            "Hold both sides of a door frame, feet close to the door, and lean back with straight arms. Pull your chest toward the frame by squeezing your shoulder blades.", hard = setOf(SHOULDERS)),
        Move("db_row", "One-arm dumbbell row", Group.PULL, 2, 10, "Pull the dumbbell to your hip.",
            "One hand and knee on the bed, back flat. Pull the dumbbell up toward your hip, elbow close to your body, then lower slowly. All reps on one side, then switch.", dumbbell = true, perSide = true),
        Move("db_reverse_fly", "Dumbbell reverse fly", Group.PULL, 3, 12, "Lead with your elbows, squeeze your back.",
            "Hinge forward with a flat back, light dumbbells hanging. Raise your arms out to the sides like wings, squeeze your shoulder blades, lower slowly.", dumbbell = true, hard = setOf(BACK)),

        // Core
        Move("dead_bug", "Dead bug", Group.CORE, 1, 8, "Lower back pressed into the mat.",
            "On your back, arms up, knees bent at 90°. Slowly lower the opposite arm and leg toward the floor, keeping your lower back flat. Switch sides.", perSide = true),
        Move("bird_dog", "Bird dog", Group.CORE, 1, 8, "Reach long, hips level.",
            "On all fours. Reach one arm forward and the opposite leg back until both are straight. Hold for 2 seconds, return, switch sides.", perSide = true, hard = setOf(WRISTS)),
        Move("heel_taps", "Heel taps", Group.CORE, 1, 12, "Shoulders slightly up, tap side to side.",
            "Lie on your back, knees bent. Lift your shoulders a little and reach your hand to tap the same-side heel, then the other side.", perSide = true),
        Move("plank", "Plank", Group.CORE, 2, 0, "Elbows under shoulders, hips level.",
            "Forearms on the floor, elbows under shoulders, body straight from head to heels. Squeeze your glutes and breathe.", hold = 30, hard = setOf(SHOULDERS)),
        Move("slow_climber", "Slow mountain climber", Group.CORE, 2, 10, "Knee to chest, slow and controlled.",
            "High plank on your hands. Bring one knee toward your chest, then the other, slowly. Keep your hips low.", perSide = true, hard = setOf(WRISTS)),
        Move("side_plank", "Side plank", Group.CORE, 3, 0, "Hips high, body in a straight line.",
            "On your side, elbow under your shoulder. Lift your hips so your body is straight. Bend the bottom knee for the easier version.", hold = 20, perSide = true, hard = setOf(SHOULDERS)),
        Move("bicycle", "Bicycle crunch", Group.CORE, 3, 10, "Elbow toward the opposite knee, slowly.",
            "On your back, hands behind your head. Bring one knee in while turning the opposite elbow toward it, then switch. Slow beats fast.", perSide = true),

        // Arms (used by body challenges).
        Move("bicep_curl", "Dumbbell biceps curl", Group.ARMS, 1, 12, "Elbows pinned to your sides.",
            "Stand tall, dumbbells by your sides, palms forward. Curl them up to your shoulders without swinging, lower slowly.", dumbbell = true),
        Move("lateral_raise", "Lateral raise", Group.ARMS, 2, 10, "Lift to shoulder height, no higher.",
            "Light dumbbells by your sides. Raise your arms out to the sides to shoulder height with soft elbows, lower slowly.", dumbbell = true, hard = setOf(SHOULDERS)),
        Move("chair_dip", "Chair dip", Group.ARMS, 2, 8, "Elbows point straight back.",
            "Hands on the edge of a sturdy chair behind you, feet on the floor. Bend your elbows to lower your hips, then push back up.", hard = setOf(SHOULDERS, WRISTS)),

        // Flexibility (used by body challenges).
        Move("cat_cow", "Cat–cow", Group.STRETCH, 1, 10, "Move with your breath.",
            "On all fours. Breathe in and drop your belly, look up. Breathe out and round your back, chin to chest."),
        Move("standing_fold", "Standing forward fold", Group.STRETCH, 1, 0, "Soft knees, let your head hang.",
            "Feet hip-width. Hinge at the hips and let your upper body hang toward the floor. Bend your knees as much as you need.", hold = 30),
        Move("lying_hamstring", "Lying hamstring stretch", Group.STRETCH, 1, 0, "Hold behind the thigh, not the knee.",
            "On your back, lift one leg and hold behind the thigh. Gently straighten the knee until you feel a stretch.", hold = 30, perSide = true),
        Move("seated_fold", "Seated forward fold", Group.STRETCH, 2, 0, "Long spine first, then fold.",
            "Sit with legs straight. Sit tall, then fold forward from the hips, reaching toward your feet.", hold = 30),
        Move("chest_opener", "Doorway chest stretch", Group.STRETCH, 1, 0, "Forearm on the frame, step through.",
            "Forearm on a door frame at shoulder height. Step through gently until you feel a stretch across your chest.", hold = 30, perSide = true),

        // Finisher: short, low-impact, heart rate up.
        Move("fast_march", "Fast march", Group.FINISHER, 1, 0, "Knees high, arms pumping.",
            "March on the spot as fast as you can, knees high, arms swinging. No jumping needed.", hold = 45),
        Move("step_jacks", "Step jacks", Group.FINISHER, 2, 0, "Step out wide, arms overhead.",
            "A jumping jack without the jump: step one foot out while your arms go up, step back in, other side. Keep a fast rhythm.", hold = 40),
        Move("skaters", "Skater steps", Group.FINISHER, 3, 0, "Side to side, swing your arms.",
            "Step wide to one side and swing your back leg behind you, like a speed skater. Switch sides quickly.", hold = 40, hard = setOf(KNEES)),
    )

    fun byId(id: String): Move? = all.firstOrNull { it.id == id }

    /** The level the program is at: easy for 2 weeks, then medium, then hard. */
    private fun targetLevel(week: Int): Int = when {
        week <= 2 -> 1
        week <= 6 -> 2
        else -> 3
    }

    fun allowed(s: Settings, m: Move): Boolean =
        m.id !in s.avoid &&
            (!m.dumbbell || Profile.hasDumbbells(s)) &&
            m.hard.none { it in Profile.injuries(s) }

    /** Picks one move from [group]. A move marked too hard drops the group one level. */
    private fun pick(s: Settings, group: Group, week: Int, rnd: java.util.Random): Move? {
        val options = all.filter { it.group == group && allowed(s, it) }
        if (options.isEmpty()) return null
        val easierHere = options.any { it.id in s.easier } || all.any { it.group == group && it.id in s.easier }
        val level = (targetLevel(week) - if (easierHere) 1 else 0).coerceAtLeast(1)
        val fresh = options.filter { it.id !in s.easier }
        val pool = fresh.ifEmpty { options }
        // Prefer the target level; fall back to the closest lower one.
        val best = (level downTo 1).firstNotNullOfOrNull { l -> pool.filter { it.level == l }.takeIf { it.isNotEmpty() } }
            ?: pool.sortedBy { it.level }.take(2)
        return best[rnd.nextInt(best.size)]
    }

    /**
     * Today's strength session. Same answer all day (seeded by date), different combinations from
     * session to session. One move per group, plus a finisher when the goal is fat loss.
     */
    fun session(s: Settings, date: LocalDate, week: Int): List<Prescribed> {
        val rnd = java.util.Random(date.toEpochDay() * 31L + 7L)
        val sets = if (week <= 2) 2 else 3
        val repBonus = ((week - 1) / 2).coerceAtMost(4)
        val groups = buildList {
            addAll(listOf(Group.LEGS, Group.HIPS, Group.PUSH, Group.PULL, Group.CORE))
            if (Profile.wantsFatLoss(s) || s.intensity >= 2) add(Group.FINISHER)
        }
        return groups.mapNotNull { g ->
            val m = pick(s, g, week, rnd) ?: return@mapNotNull null
            val hold = if (m.hold > 0) m.hold + repBonus * 5 else 0
            val reps = if (m.hold > 0) 0 else m.reps + repBonus
            Prescribed(m, if (g == Group.FINISHER) 2 else sets, reps, hold)
        }
    }

    fun totalSets(list: List<Prescribed>): Int = list.sumOf { it.sets }

    const val NOTE =
        "Rest 60 s between sets. If the last 2 reps feel easy, the System moves you up. " +
            "Strength training is one of the best things you can do for blood sugar and hormones."

    const val SPOT_NOTE =
        "Honest truth: you can't burn fat from one spot. Belly and chest fat go when overall body fat goes down. " +
            "That comes from steps, after-meal walks, strength training, sleep and protein. All of that is in your quests. " +
            "Push and core moves make the chest and waist look firmer as the fat comes off."
}
