package com.arise.app.game

import com.arise.app.data.AppData
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * The Today deck: quest cards join when their time arrives and leave once settled
 * (done, logged or skipped). "Later" reschedules a card: `deferred[id]` holds the epoch millis
 * when it comes back, at the end of the deck.
 */
object Deck {
    fun millisAt(date: LocalDate, t: LocalTime): Long = millis(date, t)

    private fun millis(date: LocalDate, t: LocalTime): Long =
        LocalDateTime.of(date, t).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Emergency quests vanish when their time limit runs out. */
    fun expired(b: Block, now: LocalTime): Boolean {
        val q = b.quest ?: return false
        val limit = q.limitMin ?: return false
        return q.expires && now.isAfter(b.time.plusMinutes(limit.toLong()))
    }

    private fun due(data: AppData, date: LocalDate, now: LocalTime): List<Block> {
        val rec = data.records[date]
        val plan = Game.plan(data, date)
        val wakeQ = plan.quests.firstOrNull { it.kind == Kind.WAKE }
        val awake = wakeQ == null || Game.settled(wakeQ, rec)
        return plan.blocks.filter { b ->
            val q = b.quest
            q != null && !b.time.isAfter(now) && !Game.settled(q, rec) && !expired(b, now) &&
                // The morning routine and phone-free quest unlock with "I'm up".
                (awake || (q.id != "morning" && q.id != "phonefree"))
        }
    }

    private fun snoozedUntil(data: AppData, date: LocalDate, b: Block): Long? =
        data.records[date]?.deferred?.get(b.quest!!.id)

    fun cards(data: AppData, date: LocalDate, now: LocalTime, nowMs: Long = System.currentTimeMillis()): List<Block> {
        val rec = data.records[date]
        return due(data, date, now)
            .filter { b -> (snoozedUntil(data, date, b) ?: 0L) <= nowMs }
            .sortedBy { b -> rec?.deferred?.get(b.quest!!.id) ?: millis(date, b.time) }
    }

    /** The core deck: only what counts for being on track. */
    fun core(data: AppData, date: LocalDate, now: LocalTime): List<Block> = cards(data, date, now).filter { !it.quest!!.optional }

    /** Side quests that are open now. Optional; nothing happens if you leave them. */
    fun side(data: AppData, date: LocalDate, now: LocalTime): List<Block> = cards(data, date, now).filter { it.quest!!.optional }

    /** Cards on snooze, with the time they come back. */
    fun snoozed(data: AppData, date: LocalDate, now: LocalTime, nowMs: Long = System.currentTimeMillis()): List<Pair<Block, Long>> =
        due(data, date, now).mapNotNull { b ->
            val until = snoozedUntil(data, date, b)
            if (until != null && until > nowMs) b to until else null
        }

    /** The next thing on the schedule after [now]: a quest or a reminder. */
    fun upNext(data: AppData, date: LocalDate, now: LocalTime): Block? {
        val rec = data.records[date]
        return Game.plan(data, date).blocks.firstOrNull { b ->
            b.time.isAfter(now) && b.special == Special.NONE && (b.quest == null || !Game.settled(b.quest, rec))
        }
    }
}
