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

    private const val WATCH = "Watch it once (switch off autoplay). Then answer the question out loud. Notes in your notebook if you like."
    private const val APP = "Open it and do one short lesson, 5 to 10 minutes. Then answer the question out loud."
    private const val READ = "Read slowly. In your notebook: 2 key terms in your own words, and 1 way it applies to your life."

    private fun series(topic: Topic, name: String, count: Int, minutes: Int, source: String = "YouTube"): List<Lesson> =
        (1..count).map { n -> Lesson(topic, "$name #$n", source, minutes, yt("$name $n"), WATCH) }

    private fun v(topic: Topic, title: String, channel: String, minutes: Int, query: String = "$channel $title") =
        Lesson(topic, title, "$channel · YouTube", minutes, yt(query), WATCH)

    private fun app(topic: Topic, title: String, app: String, url: String) = Lesson(topic, title, "$app (free)", 10, url, APP)

    private fun play(q: String) = "https://play.google.com/store/search?q=" + URLEncoder.encode(q, "UTF-8") + "&c=apps"

    private val learn: List<Lesson> = listOf(
        v(Topic.LEARN, "How to practice effectively for just about anything", "TED-Ed", 5),
        v(Topic.LEARN, "How memories form and how we lose them", "TED-Ed", 4),
        app(Topic.LEARN, "Learning How to Learn: one video", "Coursera (free audit)", "https://www.coursera.org/learn/learning-how-to-learn"),
        v(Topic.LEARN, "The Science of Thinking", "Veritasium", 12),
        v(Topic.LEARN, "How to stay calm under pressure", "TED-Ed", 5),
        v(Topic.LEARN, "Why we procrastinate", "Vox", 7, "Vox why we procrastinate"),
    )

    private val body: List<Lesson> = listOf(
        v(Topic.BODY, "What would happen if you didn't sleep?", "TED-Ed", 5),
        v(Topic.BODY, "How the immune system actually works", "Kurzgesagt", 8, "Kurzgesagt immune system explained"),
        app(Topic.BODY, "Human biology: one lesson", "Khan Academy", "https://www.khanacademy.org/science/biology/human-biology"),
        v(Topic.BODY, "How does caffeine keep us awake?", "TED-Ed", 5),
        v(Topic.BODY, "Insulin and blood sugar, animated", "YouTube", 6, "how insulin works animation"),
        v(Topic.BODY, "PCOS, explained simply", "YouTube", 8, "PCOS explained animation hormones insulin"),
        v(Topic.BODY, "What happens to your brain when you exercise", "TED-Ed", 5, "TED-Ed brain benefits of exercise"),
        v(Topic.BODY, "How does your body digest food?", "TED-Ed", 5, "TED-Ed how your digestive system works"),
        app(Topic.BODY, "Health and medicine: one lesson", "Khan Academy", "https://www.khanacademy.org/science/health-and-medicine"),
        v(Topic.BODY, "Why do we get hungry?", "TED-Ed", 5, "TED-Ed why do we feel hungry"),
    )

    private val tech: List<Lesson> = listOf(
        v(Topic.TECH, "How machines learn", "CGP Grey", 9),
        v(Topic.TECH, "How does a smartphone work?", "Branch Education", 15, "Branch Education how do smartphones work"),
        app(Topic.TECH, "Coding: one lesson", "Sololearn", play("Sololearn")),
        v(Topic.TECH, "A.I.: Humanity's final invention?", "Kurzgesagt", 15, "Kurzgesagt AI humanity's final invention"),
        v(Topic.TECH, "How does the internet work?", "YouTube", 8, "how does the internet work animation"),
        app(Topic.TECH, "Computer science: one lesson", "Khan Academy", "https://www.khanacademy.org/computing/computer-science"),
        v(Topic.TECH, "How do computers remember?", "TED-Ed", 5, "TED-Ed how computer memory works"),
        v(Topic.TECH, "Crash Course Computer Science", "Crash Course", 12, "Crash Course Computer Science early computing"),
    )

    private val world: List<Lesson> = listOf(
        v(Topic.WORLD, "History of the entire world, I guess", "bill wurtz", 20),
        v(Topic.WORLD, "The rise and fall of the Mongol Empire", "TED-Ed", 5),
        app(Topic.WORLD, "Wikipedia rabbit hole: one random article", "Wikipedia", "https://en.wikipedia.org/wiki/Special:Random"),
        v(Topic.WORLD, "What really happened to the Library of Alexandria?", "TED-Ed", 5),
        v(Topic.WORLD, "How airlines price their tickets", "Wendover Productions", 12, "Wendover Productions airline ticket pricing"),
        v(Topic.WORLD, "Why the world map is wrong", "Vox", 6, "Vox why all world maps are wrong"),
        app(Topic.WORLD, "World history: one lesson", "Khan Academy", "https://www.khanacademy.org/humanities/world-history"),
        v(Topic.WORLD, "How the Indus Valley Civilisation worked", "YouTube", 10, "Indus Valley Civilisation animated documentary"),
    )

    private val money: List<Lesson> = listOf(
        v(Topic.MONEY, "How does the stock market work?", "TED-Ed", 5),
        v(Topic.MONEY, "What causes economic bubbles?", "TED-Ed", 5),
        app(Topic.MONEY, "Varsity: one short chapter", "Zerodha Varsity", play("Varsity by Zerodha")),
        v(Topic.MONEY, "Index funds, explained", "Two Cents (PBS)", 8, "Two Cents PBS index funds"),
        v(Topic.MONEY, "What is inflation?", "YouTube", 6, "inflation explained animation"),
        app(Topic.MONEY, "Personal finance: one lesson", "Khan Academy", "https://www.khanacademy.org/college-careers-more/personal-finance"),
        v(Topic.MONEY, "Compound interest, explained", "Two Cents (PBS)", 7, "compound interest explained animation"),
        v(Topic.MONEY, "Crash Course Economics", "Crash Course", 11, "Crash Course Economics intro"),
    )

    private val mind: List<Lesson> = listOf(
        v(Topic.MIND, "Loneliness", "Kurzgesagt", 9),
        v(Topic.MIND, "What is imposter syndrome and how can you combat it?", "TED-Ed", 5),
        v(Topic.MIND, "Optimistic Nihilism", "Kurzgesagt", 7),
        v(Topic.MIND, "How to be confident", "The School of Life", 6, "The School of Life confidence"),
        v(Topic.MIND, "The science of habits", "YouTube", 8, "how habits form animation science"),
        app(Topic.MIND, "Psychology: one lesson", "Khan Academy", "https://www.khanacademy.org/test-prep/mcat/behavior"),
        v(Topic.MIND, "Why do we dream?", "TED-Ed", 5),
        v(Topic.MIND, "How stress affects your brain", "TED-Ed", 4),
    )

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
        "Kurzgesagt The Fermi Paradox Where Are All The Aliens" to "The Fermi Paradox · Kurzgesagt",
        "Kurzgesagt The Immune System Explained Bacteria Infection" to "The immune system explained · Kurzgesagt",
        "Veritasium The Most Misunderstood Concept in Physics" to "The most misunderstood concept in physics · Veritasium",
        "Kurzgesagt What Are You" to "What are you? · Kurzgesagt",
        "Vsauce What Is The Speed of Dark" to "What is the speed of dark? · Vsauce",
        "Tom Scott the dot that changed the internet" to "A weird corner of the internet · Tom Scott",
        "Mark Rober glitter bomb" to "A science prank, explained · Mark Rober",
        "Kurzgesagt the size of the universe" to "How big is the universe? · Kurzgesagt",
        "TED-Ed the dangers of mixing drugs" to "Why mixing medicines can be risky · TED-Ed",
        "CGP Grey the trouble with tumbleweed" to "The trouble with tumbleweed · CGP Grey",
        "Veritasium why trees don't need pumps" to "How trees lift water 100 metres · Veritasium",
        "TED-Ed how does the Rubik's cube work" to "The maths of the Rubik's cube · TED-Ed",
    ).map { (q, title) -> Lesson(Topic.WILDCARD, title, "YouTube", 10, yt(q), WATCH) }

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

    /** Courses (language, sign language) end; the short picks loop, so learning never runs out. */
    fun lesson(topic: Topic, index: Int, lang: String? = null): Lesson? {
        val list = lessons(topic, lang)
        if (list.isEmpty()) return null
        return if (topic == Topic.LANGUAGE || topic == Topic.SIGN) list.getOrNull(index) else list[Math.floorMod(index, list.size)]
    }
}
