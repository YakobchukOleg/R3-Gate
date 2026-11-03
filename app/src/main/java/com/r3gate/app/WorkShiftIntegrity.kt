package com.r3gate.app

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

    /** An invalid stage must never be used for confirmed duration totals. */
    fun stagesWithinShift(shift: Interval, stages: Collection<Interval>): Boolean {
        if (!isValid(shift.start, shift.end)) return false
        val sorted = stages.sortedWith(compareBy<Interval> { it.start }.thenBy { it.end })
        return sorted.all { isValid(it.start, it.end) && it.start >= shift.start && it.end <= shift.end } &&
            sorted.zipWithNext().all { (a, b) -> a.end <= b.start }
    }
}
