package com.arise.app.game

import com.arise.app.data.RWhen
import com.arise.app.data.RoutineDef

/** Common routine steps to pick from when building a morning or night routine. You can add your own too. */
object Routines {
    val morningCatalog = listOf(
        RoutineDef("m_brush", "Brush teeth", "🪥"),
        RoutineDef("m_face", "Wash face", "🫧"),
        RoutineDef("m_water", "A glass of water", "💧"),
        RoutineDef("m_ghee", "1 spoon of ghee with warm water", "🥄"),
        RoutineDef("m_lemon", "Warm lemon water", "🍋"),
        RoutineDef("m_seed", "Seed powder in a cup of water", "🌱"),
        RoutineDef("m_powder", "Daily powder / supplement", "🥛"),
        RoutineDef("m_chia", "Chia seeds (soaked last night)", "🌰", RWhen.HOME),
        RoutineDef("m_sun", "2 minutes of sunlight", "🌞"),
        RoutineDef("m_bed", "Make your bed", "🛏"),
        RoutineDef("m_stretch", "2-minute stretch", "🙆"),
        RoutineDef("m_skin", "Skincare + sunscreen", "🧴"),
        RoutineDef("m_pray", "Prayer or meditation", "🕯"),
        RoutineDef("m_plan", "Write today's top 3", "📝"),
    )

    val nightCatalog = listOf(
        RoutineDef("n_rose", "Rose water", "🌹", optional = true),
        RoutineDef("n_skin", "Serum / Moisturizer", "🧴", alternate = true),
        RoutineDef("n_guasha", "Gua sha", "🪨"),
        RoutineDef("n_brush", "Brush teeth", "🪥"),
        RoutineDef("n_chia", "Soak chia for tomorrow", "🌰", RWhen.BEFORE_HOME),
        RoutineDef("n_clothes", "Lay out tomorrow's clothes", "👕"),
        RoutineDef("n_phone", "Phone on charge, away from the bed", "🔌"),
        RoutineDef("n_dim", "Dim the lights", "💡"),
        RoutineDef("n_read", "Read 10 pages", "📖"),
        RoutineDef("n_journal", "Write one line about today", "📓"),
    )

    /** A fresh id for a custom item. */
    fun newId(prefix: String): String = "${prefix}_c${System.currentTimeMillis() % 1_000_000}"
}
