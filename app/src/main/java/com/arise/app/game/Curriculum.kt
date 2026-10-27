package com.arise.app.game

import java.net.URLEncoder

enum class Topic(val label: String, val icon: String, val weight: Double) {
    LEARN("Learning how to learn", "🧠", 1.6),
    BODY("Your body", "🫀", 1.0),
    TECH("Tech", "💻", 1.0),
    WORLD("How the world works", "🌍", 1.0),
    MONEY("Money", "💰", 1.0),
    MIND("Mind & psychology", "🪞", 1.0),
    LANGUAGE("Language", "🗣", 1.6),
    SIGN("Sign language (ASL)", "🤟", 1.3),
    WILDCARD("Wildcard", "⚡", 0.0),
}

data class Lesson(
    val topic: Topic,
    val title: String,
    val source: String,
    val minutes: Int,
    val url: String,
    /** What to do. */
    val task: String,
    /** Hand-written recall questions with the points a good answer covers. Empty = general questions. */
    val recall: List<Pair<String, String>> = emptyList(),
)

/**
 * Free resources only. YouTube lessons open a search for the exact title, so links never go stale.
 * Each topic is a list of lessons; finished finite topics drop out of the shuffle.
 */
object Curriculum {
    private fun yt(q: String) = "https://www.youtube.com/results?search_query=" + URLEncoder.encode(q, "UTF-8")

    private const val WATCH = "Watch it once without pausing. Then write 3 bullet notes and 1 question you still have. " +
        "Don't let the next video autoplay."
    private const val READ = "Read slowly. In your notebook: 2 key terms in your own words, and 1 way it applies to your life."

    private fun series(topic: Topic, name: String, count: Int, minutes: Int, source: String = "YouTube"): List<Lesson> =
        (1..count).map { n -> Lesson(topic, "$name #$n", source, minutes, yt("$name $n"), WATCH) }

    private val learn: List<Lesson> = listOf(
        Lesson(Topic.LEARN, "Learning how to learn (TEDx talk)", "Barbara Oakley · YouTube", 18,
            yt("Barbara Oakley Learning how to learn TEDxOaklandUniversity"), WATCH,
            recall = listOf(
                "What are the focused mode and the diffuse mode, and when do you need each one?" to
                    "Focused = concentrating hard on a problem. Diffuse = relaxed, wandering mind (walks, showers, sleep) where new connections form. You need both, switching between them.",
                "What is the Pomodoro technique and why does it beat procrastination?" to
                    "25 minutes of focus, then a short break. Starting is the painful part; a timer makes the start small, and the break is the reward.",
            )),
    ) + (1..4).flatMap { m ->
        listOf("first", "second").map { half ->
            Lesson(
                Topic.LEARN, "Learning How to Learn · Module $m ($half half of the videos)", "Coursera (free audit)", 25,
                "https://www.coursera.org/learn/learning-how-to-learn",
                "On Coursera choose Enroll, then the free audit option. Watch the $half half of Module $m's videos. " +
                    "Notes: one technique you'll use this week.",
            )
        }
    }

    private val body: List<Lesson> = run {
        val specials = listOf(
            Lesson(Topic.BODY, "Sleep is your superpower", "Matt Walker · TED", 20, yt("Matt Walker Sleep is your superpower TED"), WATCH,
                recall = listOf(
                    "What happens to memory and learning when you don't sleep enough?" to
                        "Sleep after learning saves new memories ('save button'); sleep before learning prepares the brain to take them in. Without it, learning drops sharply.",
                    "Name two things from the talk you'll change about your own sleep." to
                        "His tips: regular sleep and wake times, even on weekends, and a cool, dark room. Your answer should name your own two changes.",
                )),
            Lesson(Topic.BODY, "Insulin resistance, explained", "YouTube", 12, yt("insulin resistance explained simply"), WATCH,
                recall = listOf(
                    "Explain insulin resistance like you're talking to a friend." to
                        "Insulin lets sugar into cells. With resistance the cells respond less, so the body makes more insulin; high insulin makes fat storage easier and hunger harder to control.",
                    "Which three habits improve insulin sensitivity the most?" to
                        "Strength training (muscle soaks up sugar), walking after meals, and enough sleep. Protein and fibre before carbs also help.",
                )),
            Lesson(Topic.BODY, "PCOS: what's actually happening", "YouTube", 12, yt("PCOS explained hormones insulin"), WATCH,
                recall = listOf(
                    "How are insulin and PCOS connected?" to
                        "High insulin pushes the ovaries to make more androgens (male-type hormones), which disrupts ovulation and cycles. Lower insulin often means fewer symptoms.",
                    "What can you do every day that helps PCOS?" to
                        "Strength training, walking after meals, steady sleep, protein-first meals, managing stress. No crash diets.",
                )),
            Lesson(Topic.BODY, "The brain-changing benefits of exercise", "Wendy Suzuki · TED", 13, yt("Wendy Suzuki brain changing benefits of exercise TED"), WATCH,
                recall = listOf(
                    "What does one workout do to your brain right away?" to
                        "It releases neurotransmitters like dopamine, serotonin and noradrenaline: better mood and focus for at least 2 hours.",
                    "What does regular exercise do over the long term?" to
                        "It grows new brain cells in the hippocampus (memory) and strengthens the prefrontal cortex (focus), protecting the brain as you age.",
                )),
        )
        val ap = series(Topic.BODY, "Crash Course Anatomy and Physiology", 47, 11)
        // One special every third lesson, then the series continues.
        buildList {
            var s = 0
            ap.forEachIndexed { i, l ->
                if (i % 3 == 0 && s < specials.size) add(specials[s++])
                add(l)
            }
        }
    }

    private val tech = series(Topic.TECH, "Crash Course Computer Science", 40, 12)
    private val world = series(Topic.WORLD, "Crash Course World History", 42, 12)
    private val mind = series(Topic.MIND, "Crash Course Psychology", 40, 11)

    private val money: List<Lesson> = (1..8).map { i ->
        Lesson(
            Topic.MONEY, "Zerodha Varsity · Module 1, chapters ${2 * i - 1}–${2 * i}", "Zerodha Varsity (free)", 20,
            "https://zerodha.com/varsity/module/introduction-to-stock-markets/", READ,
        )
    } + series(Topic.MONEY, "Crash Course Economics", 35, 11)

    private val korean: List<Lesson> = buildList {
        add(Lesson(Topic.LANGUAGE, "Hangul reading practice", "Go! Billy Korean · YouTube", 15, yt("Go Billy Korean Hangul reading practice"),
            "Read along out loud. Then write 10 Korean words you can now read without stopping."))
        add(Lesson(Topic.LANGUAGE, "Hangul speed reading", "YouTube", 12, yt("Korean Hangul reading practice words"),
            "Read every word out loud before the answer appears. Note the ones you missed."))
        var level1 = 1
        var easy = 1
        while (level1 <= 25) {
            add(Lesson(Topic.LANGUAGE, "TTMIK Level 1 · Lesson $level1", "Talk To Me In Korean (free)", 15,
                yt("Talk To Me In Korean Level 1 Lesson $level1"),
                "Write the grammar point and 3 example sentences. Say each one out loud 3 times."))
            if (level1 % 4 == 0) {
                add(Lesson(Topic.LANGUAGE, "Easy Korean street interview #$easy", "Easy Korean · YouTube", 12, yt("Easy Korean $easy"),
                    "Watch with Korean + English subtitles. Write down 5 words or phrases you heard."))
                easy++
            }
            level1++
        }
        (1..30).forEach { n ->
            add(Lesson(Topic.LANGUAGE, "TTMIK Level 2 · Lesson $n", "Talk To Me In Korean (free)", 15,
                yt("Talk To Me In Korean Level 2 Lesson $n"),
                "Write the grammar point and 3 example sentences about your own life."))
        }
    }

    // Dr. Bill Vicars (Lifeprint / ASL University): a real teacher, a structured free course, short lessons.
    private val sign: List<Lesson> = (1..30).map { n ->
        Lesson(
            Topic.SIGN, "ASL with Dr. Bill Vicars · Lesson $n", "Lifeprint ASLU · YouTube (free)", 20,
            yt("Bill Vicars ASL Lesson $n"),
            "Sign along with him in front of a mirror. Then, without the video, sign 5 words from today and " +
                "sketch them in your notebook with a tiny drawing.",
        )
    }

    private val wildcards = listOf(
        "Hans Rosling The best stats you've ever seen TED" to "The best stats you've ever seen · Hans Rosling",
        "Tim Urban Inside the mind of a master procrastinator TED" to "Inside the mind of a master procrastinator · Tim Urban",
        "Kurzgesagt The Fermi Paradox Where Are All The Aliens" to "The Fermi Paradox · Kurzgesagt",
        "Simon Sinek How great leaders inspire action TED" to "How great leaders inspire action · Simon Sinek",
        "Kurzgesagt Optimistic Nihilism" to "Optimistic Nihilism · Kurzgesagt",
        "Ken Robinson Do schools kill creativity TED" to "Do schools kill creativity? · Ken Robinson",
        "Kurzgesagt The Immune System Explained Bacteria Infection" to "The immune system explained · Kurzgesagt",
        "Veritasium The Most Misunderstood Concept in Physics" to "The most misunderstood concept in physics · Veritasium",
        "Kurzgesagt What Are You" to "What are you? · Kurzgesagt",
        "Brene Brown The power of vulnerability TED" to "The power of vulnerability · Brené Brown",
        "Kurzgesagt Loneliness" to "Loneliness · Kurzgesagt",
        "Vsauce What Is The Speed of Dark" to "What is the speed of dark? · Vsauce",
    ).map { (q, title) -> Lesson(Topic.WILDCARD, title, "YouTube", 15, yt(q), WATCH) }

    /** A beginner series for any language other than Korean (which has its own hand-picked course). */
    private fun languageSeries(lang: String): List<Lesson> = (1..60).map { n ->
        Lesson(
            Topic.LANGUAGE, "$lang for beginners · Lesson $n", "YouTube", 15,
            yt("learn $lang for beginners lesson $n"),
            "Repeat every sentence out loud. Write 5 new words and one sentence about your own day.",
        )
    }

    fun lessons(topic: Topic, lang: String? = null): List<Lesson> = when (topic) {
        Topic.LEARN -> learn
        Topic.BODY -> body
        Topic.TECH -> tech
        Topic.WORLD -> world
        Topic.MONEY -> money
        Topic.MIND -> mind
        Topic.LANGUAGE -> when {
            lang == null -> emptyList()
            lang.equals("Korean", ignoreCase = true) -> korean
            else -> languageSeries(lang)
        }
        Topic.SIGN -> sign
        Topic.WILDCARD -> wildcards
    }

    private val general = listOf(
        "Explain the main idea like you're telling a friend who's never heard of it.",
        "What's one thing from today's lesson that surprised you, and why?",
        "Teach it back: what were the 3 most important points?",
        "How could you use what you learned today in your own life this week?",
        "What's one question you still have after this lesson?",
        "If you had to summarise today's lesson in 30 seconds, what would you say?",
    )

    /** 1–2 questions to answer out loud after a lesson. Second = the key points to check yourself, if known. */
    fun recall(lesson: Lesson?, date: java.time.LocalDate): List<Pair<String, String?>> {
        if (lesson != null && lesson.recall.isNotEmpty()) return lesson.recall.map { it.first to it.second }
        val rnd = java.util.Random(date.toEpochDay() * 13L + 1L)
        return general.shuffled(rnd).take(2).map { it to null }
    }

    /** Wildcards loop forever; other topics retire once finished. */
    fun lesson(topic: Topic, index: Int, lang: String? = null): Lesson? {
        val list = lessons(topic, lang)
        return if (topic == Topic.WILDCARD) list[Math.floorMod(index, list.size)] else list.getOrNull(index)
    }
}
