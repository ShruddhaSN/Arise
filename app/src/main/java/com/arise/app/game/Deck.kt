package com.arise.app.game

import com.arise.app.data.AppData
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * The Today deck: quest cards join when their time arrives and leave once settled
 * (done, logged or skipped). "Later" sends a card to the back by stamping it with the current time.
 */
object Deck {
    private fun millis(date: LocalDate, t: LocalTime): Long =
        LocalDateTime.of(date, t).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun cards(data: AppData, date: LocalDate, now: LocalTime): List<Block> {
        val rec = data.records[date]
        return Game.plan(data, date).blocks
            .filter { b ->
                val q = b.quest
                q != null && !b.time.isAfter(now) && !Game.settled(q, rec)
            }
            .sortedBy { b -> rec?.deferred?.get(b.quest!!.id) ?: millis(date, b.time) }
    }

    /** The next thing on the schedule after [now]: a quest or a reminder. */
    fun upNext(data: AppData, date: LocalDate, now: LocalTime): Block? {
        val rec = data.records[date]
        return Game.plan(data, date).blocks.firstOrNull { b ->
            b.time.isAfter(now) && b.special == Special.NONE && (b.quest == null || !Game.settled(b.quest, rec))
        }
    }
}
