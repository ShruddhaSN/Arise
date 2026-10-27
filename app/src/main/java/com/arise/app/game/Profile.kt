package com.arise.app.game

import com.arise.app.data.Settings

data class Option(val id: String, val label: String, val icon: String = "")

enum class QType { SINGLE, MULTI }

/**
 * One assessment question. [showIf] looks at the answers so far, so each answer decides
 * which questions come next.
 */
data class Question(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val options: List<Option>,
    val type: QType = QType.SINGLE,
    /** MULTI: the most you can pick. */
    val max: Int = 99,
    val showIf: (Map<String, List<String>>) -> Boolean = { true },
)

/** The life assessment: where you are now, so the System can build the plan around you. */
object Profile {
    // Answer ids used by the plan.
    const val AREAS = "areas"
    const val HEALTH = "health"
    const val MIND = "mind"
    const val CAREER = "career"
    const val DISCIPLINE = "discipline"
    const val SOCIAL = "social"
    const val LEARN = "learn"
    const val SPIRIT = "spirit"
    const val CRISIS = "crisis"

    private fun Map<String, List<String>>.has(q: String, o: String) = o in (this[q] ?: emptyList())
    private fun Map<String, List<String>>.area(a: String) = has(AREAS, a)

    val questions: List<Question> = listOf(
        Question(
            "gender", "What's your gender?", "Used for training defaults and hormone-related tips. Nothing else.",
            listOf(
                Option("female", "Female", "♀"),
                Option("male", "Male", "♂"),
                Option("nonbinary", "Non-binary", "⚧"),
                Option("unsaid", "Prefer not to say", "·"),
            ),
        ),
        Question(
            "hormonal", "Do you have any of these?", "Pick all that apply. The System adjusts its tips (never crash diets).",
            listOf(
                Option("none", "None", "✅"),
                Option("pcos", "PCOS / PCOD", "🩺"),
                Option("thyroid", "Thyroid condition", "🦋"),
                Option("endo", "Endometriosis", "🩺"),
                Option("irregular", "Irregular periods", "📅"),
            ),
            type = QType.MULTI,
            showIf = { it.has("gender", "female") || it.has("gender", "nonbinary") },
        ),
        Question(
            "occupation", "What do you do?", "Your days get built around this.",
            listOf(
                Option("student", "Student", "🎓"),
                Option("working", "Working professional", "💼"),
                Option("business", "Business / freelance", "🧾"),
                Option("homemaker", "Homemaker", "🏡"),
                Option("between", "Between jobs / on a break", "🔍"),
            ),
        ),
        Question(
            "work_mode", "How do you work?", "",
            listOf(
                Option("office", "From an office, every work day", "🏢"),
                Option("wfh", "From home, every work day", "🏠"),
                Option("hybrid", "Hybrid: some days office, some from home", "🔀"),
            ),
            showIf = { it.has("occupation", "working") || it.has("occupation", "business") },
        ),
        Question(
            "study_mode", "How do you study?", "",
            listOf(
                Option("office", "In person: college or school", "🏫"),
                Option("wfh", "Online, from home", "💻"),
                Option("hybrid", "A mix of both", "🔀"),
            ),
            showIf = { it.has("occupation", "student") },
        ),
        Question(
            "living", "Where do you live?", "",
            listOf(
                Option("home", "At home with family", "🏡"),
                Option("pg", "PG / paying guest", "🛏"),
                Option("hostel", "Hostel", "🏢"),
                Option("alone", "On my own", "🔑"),
                Option("roommates", "With roommates / flatmates", "👥"),
            ),
        ),
        Question(
            "meals_fixed", "Are meals served at fixed times?", "Like a PG or hostel mess that closes.",
            listOf(
                Option("yes", "Yes, fixed times", "⏰"),
                Option("no", "No, I eat when I want", "🍽"),
            ),
            showIf = { it.has("living", "pg") || it.has("living", "hostel") },
        ),
        Question(
            "kitchen", "What can you cook with?", "So food tips are realistic.",
            listOf(
                Option("full", "A full kitchen", "🍳"),
                Option("basic", "Basics: kettle, microwave", "☕"),
                Option("none", "Nothing, I buy or get served food", "🥡"),
            ),
        ),
        Question(
            "life", "How would you describe your life right now?", "Be honest. Nobody sees this but you.",
            listOf(
                Option("satisfied", "Mostly satisfied, I want to level up", "😊"),
                Option("alright", "I'm alright and want to improve", "🙂"),
                Option("okay", "Doing okay. Not good, not bad", "😐"),
                Option("sad", "Sad, rarely happy", "😔"),
                Option("lowest", "At my lowest, I need help", "🫥"),
            ),
        ),
        Question(
            AREAS, "Which areas need the most attention?", "Pick up to 3. The System focuses there first.",
            listOf(
                Option(HEALTH, "Health & fitness", "💪"),
                Option(MIND, "Mental health & well-being", "🧠"),
                Option(CAREER, "Career & productivity", "💼"),
                Option(DISCIPLINE, "Discipline & habits", "⛓"),
                Option(SOCIAL, "Relationships & social life", "🤝"),
                Option(LEARN, "Education & learning", "📚"),
                Option(SPIRIT, "Spiritual growth & purpose", "🕯"),
                Option(CRISIS, "Life crisis & rebuilding", "🧱"),
            ),
            type = QType.MULTI, max = 3,
        ),

        // ---- Health & fitness ----
        Question(
            "health_goal", "What's your main health goal?", "",
            listOf(
                Option("lose_weight", "Lose weight", "⚖"),
                Option("muscle", "Build muscle & strength", "🏋"),
                Option("fitter", "Get fitter overall", "🏃"),
                Option("energy", "More energy & better sleep", "⚡"),
                Option("confident", "Look better & feel confident", "✨"),
            ),
            showIf = { it.area(HEALTH) },
        ),
        Question(
            "body_focus", "Where do you most want to see change?", "Pick any. The System will be honest about what works.",
            listOf(
                Option("belly", "Belly", "🎯"),
                Option("chest", "Chest & upper body", "🎯"),
                Option("arms", "Arms", "🎯"),
                Option("thighs", "Thighs & hips", "🎯"),
                Option("overall", "Overall weight", "⚖"),
            ),
            type = QType.MULTI,
            showIf = { it.area(HEALTH) && (it.has("health_goal", "lose_weight") || it.has("health_goal", "confident") || it.has("health_goal", "fitter")) },
        ),
        Question(
            "fitness", "What describes your fitness level?", "",
            listOf(
                Option("beginner", "Complete beginner", "🌱"),
                Option("inconsistent", "I've tried, but I'm inconsistent", "🔁"),
                Option("sometimes", "I exercise sometimes", "🚶"),
                Option("active", "Already active, I want more", "🔥"),
            ),
            showIf = { it.area(HEALTH) },
        ),
        Question(
            "blocker", "What's the biggest thing stopping you?", "",
            listOf(
                Option("time", "No time", "⏰"),
                Option("motivation", "No motivation", "🪫"),
                Option("start", "I don't know how to start", "🧭"),
                Option("quit", "I always quit after a few days", "📉"),
                Option("injury", "Injuries or pain", "🩹"),
            ),
            showIf = { it.area(HEALTH) || it.area(DISCIPLINE) },
        ),
        Question(
            "body", "Does anything affect your body?", "The System avoids moves that could hurt. Pick all that apply.",
            listOf(
                Option("none", "Nothing", "✅"),
                Option("knees", "Knees", "🦵"),
                Option("back", "Lower back", "🔙"),
                Option("shoulders", "Shoulders", "💪"),
                Option("wrists", "Wrists", "✋"),
            ),
            type = QType.MULTI,
            showIf = { it.area(HEALTH) },
        ),
        Question(
            "access", "What do you have access to?", "Pick all that apply.",
            listOf(
                Option("dumbbells", "Dumbbells", "🏋"),
                Option("treadmill", "Treadmill", "🏃"),
                Option("cycle", "Cycle / exercise bike", "🚲"),
                Option("gym", "A full gym", "🏟"),
                Option("pool", "Swimming pool", "🏊"),
                Option("mat", "Yoga mat", "🧘"),
                Option("nothing", "Nothing, just me", "🙋"),
            ),
            type = QType.MULTI,
            showIf = { it.area(HEALTH) },
        ),
        Question(
            "where", "Where would you rather move?", "",
            listOf(
                Option("indoors", "Indoors", "🏠"),
                Option("outdoors", "Outdoors", "🌳"),
                Option("either", "Either", "🔀"),
            ),
            showIf = { it.area(HEALTH) },
        ),
        Question(
            "sleep", "How's your sleep?", "",
            listOf(
                Option("bad", "Bad. I struggle to sleep", "😵"),
                Option("irregular", "Irregular. Different every night", "🌀"),
                Option("okay", "Okay, but I wake up tired", "🥱"),
                Option("good", "Good", "😴"),
            ),
            showIf = { it.area(HEALTH) || it.area(MIND) || it.area(DISCIPLINE) },
        ),

        // ---- Mental health ----
        Question(
            "mind_struggle", "What weighs on you most?", "Pick up to 2.",
            listOf(
                Option("stress", "Stress", "🌋"),
                Option("overthinking", "Overthinking", "🌀"),
                Option("low", "Low mood", "🌧"),
                Option("anxiety", "Anxiety", "💓"),
                Option("burnout", "Burnout", "🪫"),
                Option("lonely", "Loneliness", "🫂"),
            ),
            type = QType.MULTI, max = 2,
            showIf = { it.area(MIND) },
        ),
        Question(
            "unwind", "How do you usually unwind?", "",
            listOf(
                Option("scroll", "Scrolling my phone", "📱"),
                Option("shows", "Shows and dramas", "📺"),
                Option("music", "Music", "🎧"),
                Option("talk", "Talking to someone", "💬"),
                Option("nothing", "I don't really unwind", "😶"),
            ),
            showIf = { it.area(MIND) || it.area(DISCIPLINE) },
        ),

        // ---- Career ----
        Question(
            "career_challenge", "What's your biggest challenge at work?", "",
            listOf(
                Option("focus", "Focus & procrastination", "🎯"),
                Option("skills", "Skill gaps", "🧩"),
                Option("burnout", "Burnout", "🪫"),
                Option("direction", "No clear direction", "🧭"),
                Option("time", "Time management", "⏳"),
            ),
            showIf = { it.area(CAREER) },
        ),
        Question(
            "career_outcome", "What outcome matters most?", "",
            listOf(
                Option("promotion", "Promotion or raise", "📈"),
                Option("newjob", "A new job", "🚪"),
                Option("skills", "Build skills", "🛠"),
                Option("income", "Side income", "💰"),
                Option("balance", "Better work-life balance", "⚖"),
            ),
            showIf = { it.area(CAREER) },
        ),

        // ---- Discipline ----
        Question(
            "breaks", "What breaks your routine most?", "Pick up to 2.",
            listOf(
                Option("phone", "Phone / scrolling", "📱"),
                Option("sleep", "Irregular sleep", "🌙"),
                Option("mood", "My mood", "🎭"),
                Option("plans", "Plans changing", "📅"),
                Option("noplan", "No clear plan", "🗺"),
            ),
            type = QType.MULTI, max = 2,
            showIf = { it.area(DISCIPLINE) },
        ),
        Question(
            "phone_worst", "When is your phone use worst?", "",
            listOf(
                Option("morning", "Right after waking up", "🌅"),
                Option("work", "During work", "💻"),
                Option("evening", "Evenings", "🌆"),
                Option("bed", "In bed at night", "🛏"),
            ),
            showIf = { it.area(DISCIPLINE) || it.has("breaks", "phone") || it.has("unwind", "scroll") },
        ),

        // ---- Relationships ----
        Question(
            "social_goal", "What do you want to work on?", "",
            listOf(
                Option("friends", "Make or keep friends", "🧑‍🤝‍🧑"),
                Option("family", "Family", "🏡"),
                Option("confidence", "Confidence talking to people", "🗣"),
                Option("partner", "Partner / dating", "💞"),
                Option("boundaries", "Setting boundaries", "🚧"),
            ),
            showIf = { it.area(SOCIAL) },
        ),

        // ---- Learning ----
        Question(
            "learn_what", "What do you want to learn?", "Pick all that apply.",
            listOf(
                Option("languages", "Languages", "🗣"),
                Option("tech", "Tech", "💻"),
                Option("money", "Money & finance", "💰"),
                Option("world", "How the world works", "🌍"),
                Option("mind", "Psychology & the mind", "🪞"),
                Option("body", "The body & health", "🫀"),
                Option("sign", "Sign language", "🤟"),
                Option("exam", "For an exam", "📝"),
            ),
            type = QType.MULTI,
            showIf = { it.area(LEARN) },
        ),
        Question(
            "learn_how", "How do you learn best?", "Pick all that apply.",
            listOf(
                Option("videos", "Videos", "▶"),
                Option("reading", "Reading", "📖"),
                Option("speaking", "Speaking it out loud", "🎤"),
                Option("handnotes", "Notes by hand", "✍"),
            ),
            type = QType.MULTI,
            showIf = { it.area(LEARN) },
        ),
        Question(
            "learn_time", "How much time a day for learning?", "",
            listOf(
                Option("10", "10 min", "⏱"),
                Option("20", "20 min", "⏱"),
                Option("30", "30 min", "⏱"),
                Option("45", "45 min or more", "⏱"),
            ),
            showIf = { it.area(LEARN) },
        ),

        // ---- Purpose ----
        Question(
            "spirit_draw", "What draws you?", "",
            listOf(
                Option("gratitude", "Gratitude", "🙏"),
                Option("meditation", "Meditation & stillness", "🕯"),
                Option("faith", "My faith practice", "📿"),
                Option("purpose", "Finding my purpose", "🧭"),
                Option("journal", "Journaling", "📓"),
            ),
            showIf = { it.area(SPIRIT) },
        ),

        // ---- Rebuilding ----
        Question(
            "crisis_what", "What happened?", "You don't have to explain. Just pick what's closest.",
            listOf(
                Option("breakup", "A breakup", "💔"),
                Option("job", "Job loss or career setback", "📉"),
                Option("health", "A health scare", "🩺"),
                Option("loss", "Losing someone", "🕊"),
                Option("lost", "I just feel lost", "🌫"),
            ),
            showIf = { it.area(CRISIS) },
        ),
        Question(
            "crisis_need", "What do you need most right now?", "",
            listOf(
                Option("structure", "Structure", "🧱"),
                Option("wins", "Small wins", "🏅"),
                Option("checkin", "Someone checking in on me", "📣"),
                Option("rest", "Rest and recovery", "🛌"),
            ),
            showIf = { it.area(CRISIS) },
        ),

        // ---- Everyone ----
        Question(
            "push", "How hard should the System push you?", "You can change this later in Player.",
            listOf(
                Option("1", "Steady: build it slowly", "🌱"),
                Option("2", "Hard: push me", "⚔"),
                Option("3", "Disappear mode: the rest of the year is war", "🌑"),
            ),
        ),
    )

    fun visible(answers: Map<String, List<String>>): List<Question> = questions.filter { it.showIf(answers) }

    // ---- What the plan reads ----

    fun areas(s: Settings): Set<String> = s.answer(AREAS).toSet()

    /** No assessment yet (older installs) means "assume the original setup": dumbbells and a treadmill. */
    fun hasDumbbells(s: Settings): Boolean {
        val a = s.answer("access")
        return a.isEmpty() || "dumbbells" in a || "gym" in a
    }

    fun hasTreadmill(s: Settings): Boolean {
        val a = s.answer("access")
        return a.isEmpty() || "treadmill" in a || "gym" in a
    }

    fun injuries(s: Settings): Set<String> = s.answer("body").toSet() - setOf("none")

    fun pcos(s: Settings): Boolean = s.picked("hormonal", "pcos")

    /** Which learning topics to rotate through. No answer = all of them. */
    fun topics(s: Settings): Set<Topic> {
        val want = s.answer("learn_what")
        val base = mutableSetOf(Topic.LEARN, Topic.WILDCARD)
        if (want.isEmpty()) {
            base += setOf(Topic.BODY, Topic.TECH, Topic.WORLD, Topic.MONEY, Topic.MIND)
        } else {
            if ("tech" in want) base += Topic.TECH
            if ("money" in want) base += Topic.MONEY
            if ("world" in want || "exam" in want) base += Topic.WORLD
            if ("mind" in want) base += Topic.MIND
            if ("body" in want) base += Topic.BODY
            if ("sign" in want) base += Topic.SIGN
        }
        if (s.language != null) base += Topic.LANGUAGE
        return base
    }

    /** Where breakfast can come from when the mess has closed. */
    fun breakfastFallback(s: Settings): String = when (s.answer("kitchen").firstOrNull()) {
        "full" -> "Make eggs, paneer or besan chilla."
        "basic" -> "Kettle oats with milk, boiled eggs, or curd with fruit."
        else -> "Buy or order something with protein: eggs, curd, a sandwich with paneer."
    }

    fun learnBase(s: Settings): Int? = s.answer("learn_time").firstOrNull()?.toIntOrNull()

    fun wantsFatLoss(s: Settings): Boolean =
        s.picked("health_goal", "lose_weight") || s.answer("body_focus").any { it in setOf("belly", "chest", "overall") }

    fun quitter(s: Settings): Boolean = s.picked("blocker", "quit")

    fun needsSupport(answers: Map<String, List<String>>): Boolean =
        "lowest" in (answers["life"] ?: emptyList()) || "sad" in (answers["life"] ?: emptyList())

    /** Life Reset-style starting ratings (0–100) from the answers, plus where the plan can take you. */
    data class Rating(val label: String, val icon: String, val now: Int, val potential: Int)

    fun ratings(answers: Map<String, List<String>>, daysLeft: Int): List<Rating> {
        fun pick(q: String) = answers[q]?.firstOrNull()
        val str = when (pick("fitness")) {
            "beginner" -> 18
            "inconsistent" -> 27
            "sometimes" -> 38
            "active" -> 55
            else -> 30
        }
        val life = when (pick("life")) {
            "satisfied" -> 12
            "alright" -> 8
            "okay" -> 4
            else -> 0
        }
        val vit = when (pick("sleep")) {
            "bad" -> 20
            "irregular" -> 26
            "okay" -> 38
            "good" -> 52
            else -> 32
        } + life
        val dsc = (if (pick("blocker") == "quit") 18 else 28) + (if ("phone" in (answers["breaks"] ?: emptyList())) -4 else 0) + life
        val focus = (if (pick("career_challenge") == "focus") 24 else 34) + (if (pick("phone_worst") == "work") -4 else 0) + life
        val wis = 30 + (answers["learn_how"]?.size ?: 0) * 3 + life
        // Roughly how much a fully followed plan can add before the year ends.
        val gain = (daysLeft * 0.75).toInt().coerceIn(15, 55)
        fun r(label: String, icon: String, v: Int, weight: Double) =
            Rating(label, icon, v.coerceIn(5, 90), (v + gain * weight).toInt().coerceIn(v + 5, 96))
        val list = listOf(
            r("Strength", "⚔", str, 1.0),
            r("Vitality", "❤", vit, 1.0),
            r("Discipline", "⛓", dsc, 1.15),
            r("Focus", "🎯", focus, 0.9),
            r("Wisdom", "🧠", wis, 0.8),
        )
        val overallNow = list.sumOf { it.now } / list.size
        val overallPot = list.sumOf { it.potential } / list.size
        return listOf(Rating("Overall", "✦", overallNow, overallPot)) + list
    }
}
