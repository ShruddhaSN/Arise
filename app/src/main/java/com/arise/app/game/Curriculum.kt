package com.arise.app.game

import java.net.URLEncoder

enum class Topic(val label: String, val icon: String, val weight: Double) {
    LEARN("Learning how to learn", "🧠", 1.6),
    BODY("Your body", "🫀", 1.0),
    TECH("Tech", "💻", 1.0),
    WORLD("How the world works", "🌍", 1.0),
    MONEY("Money", "💰", 1.0),
    MIND("Mind & psychology", "🪞", 1.0),
    KOREAN("Korean deep", "🇰🇷", 1.6),
    SIGN("Sign language", "🤟", 1.3),
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
            yt("Barbara Oakley Learning how to learn TEDxOaklandUniversity"), WATCH),
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
            Lesson(Topic.BODY, "Sleep is your superpower", "Matt Walker · TED", 20, yt("Matt Walker Sleep is your superpower TED"), WATCH),
            Lesson(Topic.BODY, "Insulin resistance, explained", "YouTube", 12, yt("insulin resistance explained simply"), WATCH),
            Lesson(Topic.BODY, "PCOS: what's actually happening", "YouTube", 12, yt("PCOS explained hormones insulin"), WATCH),
            Lesson(Topic.BODY, "The brain-changing benefits of exercise", "Wendy Suzuki · TED", 13, yt("Wendy Suzuki brain changing benefits of exercise TED"), WATCH),
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
        add(Lesson(Topic.KOREAN, "Hangul reading practice", "Go! Billy Korean · YouTube", 15, yt("Go Billy Korean Hangul reading practice"),
            "Read along out loud. Then write 10 Korean words you can now read without stopping."))
        add(Lesson(Topic.KOREAN, "Hangul speed reading", "YouTube", 12, yt("Korean Hangul reading practice words"),
            "Read every word out loud before the answer appears. Note the ones you missed."))
        var level1 = 1
        var easy = 1
        while (level1 <= 25) {
            add(Lesson(Topic.KOREAN, "TTMIK Level 1 · Lesson $level1", "Talk To Me In Korean (free)", 15,
                yt("Talk To Me In Korean Level 1 Lesson $level1"),
                "Write the grammar point and 3 example sentences. Say each one out loud 3 times."))
            if (level1 % 4 == 0) {
                add(Lesson(Topic.KOREAN, "Easy Korean street interview #$easy", "Easy Korean · YouTube", 12, yt("Easy Korean $easy"),
                    "Watch with Korean + English subtitles. Write down 5 words or phrases you heard."))
                easy++
            }
            level1++
        }
        (1..30).forEach { n ->
            add(Lesson(Topic.KOREAN, "TTMIK Level 2 · Lesson $n", "Talk To Me In Korean (free)", 15,
                yt("Talk To Me In Korean Level 2 Lesson $n"),
                "Write the grammar point and 3 example sentences about your own life."))
        }
    }

    private val sign: List<Lesson> = (1..10).flatMap { m ->
        listOf("Part A", "Part B").map { part ->
            Lesson(
                Topic.SIGN, "Indian Sign Language · Module $m, $part", "ISLRTC (Govt. of India)", 15,
                "https://islrtc.nic.in/online-basic-isl-course-in-self-learning-mode/",
                "Copy each sign in front of a mirror 3 times. Sketch or describe 5 signs in your notebook. " +
                    "If the ISLRTC site doesn't work, do Lifeprint ASL Lesson $m instead (lifeprint.com).",
            )
        }
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

    fun lessons(topic: Topic): List<Lesson> = when (topic) {
        Topic.LEARN -> learn
        Topic.BODY -> body
        Topic.TECH -> tech
        Topic.WORLD -> world
        Topic.MONEY -> money
        Topic.MIND -> mind
        Topic.KOREAN -> korean
        Topic.SIGN -> sign
        Topic.WILDCARD -> wildcards
    }

    /** Wildcards loop forever; other topics retire once finished. */
    fun lesson(topic: Topic, index: Int): Lesson? {
        val list = lessons(topic)
        return if (topic == Topic.WILDCARD) list[Math.floorMod(index, list.size)] else list.getOrNull(index)
    }
}
