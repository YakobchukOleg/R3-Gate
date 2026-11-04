package com.r3gate.app

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Pure validation of manually confirmed work-shift boundaries. */
object WorkShiftIntegrity {
    data class Interval(val start: Long, val end: Long)

    fun isValid(start: Long, end: Long): Boolean = start >= 0 && end > start

    /** Adjacent shifts are allowed; any strictly positive overlap is rejected. */
    fun overlaps(first: Interval, second: Interval): Boolean =
        first.start < second.end && second.start < first.end

    fun canSave(
        proposed: Interval,
        existing: Collection<Interval>
    ): Boolean = isValid(proposed.start, proposed.end) &&
        existing.all { isValid(it.start, it.end) && !overlaps(proposed, it) }

    fun resolveEditTime(local: LocalDateTime, zone: ZoneId, originalMs: Long): Long? {
        val offsets = zone.rules.getValidOffsets(local)
        if (offsets.isEmpty()) return null
        val originalOffset = Instant.ofEpochMilli(originalMs).atZone(zone).offset
        val offset = originalOffset.takeIf { it in offsets } ?: offsets.first()
        return local.toInstant(offset).toEpochMilli()
    }

    fun preservesRecordedStages(original: Interval, proposed: Interval, stages: Collection<Interval>): Boolean =
        stages.filter { isValid(it.start, it.end) }.all { stage ->
            val coveredStart = maxOf(stage.start, original.start)
            val coveredEnd = minOf(stage.end, original.end)
            coveredEnd <= coveredStart ||
                (proposed.start <= coveredStart && proposed.end >= coveredEnd)
        }

    /** An invalid stage must never be used for confirmed duration totals. */
    fun stagesWithinShift(shift: Interval, stages: Collection<Interval>): Boolean {
        if (!isValid(shift.start, shift.end)) return false
        val sorted = stages.sortedWith(compareBy<Interval> { it.start }.thenBy { it.end })
        return sorted.all { isValid(it.start, it.end) && it.start >= shift.start && it.end <= shift.end } &&
            sorted.zipWithNext().all { (a, b) -> a.end <= b.start }
    }
}
