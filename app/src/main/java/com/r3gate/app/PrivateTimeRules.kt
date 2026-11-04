package com.r3gate.app

/** Elapsed working time excludes explicit personal intervals, including overlaps. */
object PrivateTimeRules {
    const val KIND = "Prywatnie"

    data class Interval(val start: Long, val end: Long)
    fun worked(start: Long, end: Long, privatePeriods: List<Interval>): Long {
        if (start < 0L || end <= start) return 0L
        val clipped = privatePeriods.mapNotNull {
            if (it.start < 0L || it.end <= it.start) return@mapNotNull null
            val a = maxOf(start, it.start)
            val b = minOf(end, it.end)
            if (b > a) Interval(a, b) else null
        }.sortedBy { it.start }
        var personal = 0L
        var cursor = start
        for (part in clipped) {
            val from = maxOf(cursor, part.start)
            if (part.end > from) personal += part.end - from
            cursor = maxOf(cursor, part.end)
        }
        return (end - start - personal).coerceAtLeast(0)
    }

    fun workedTotal(shifts: List<Interval>, privatePeriods: List<Interval>): Long {
        val covered = union(shifts)
        val personal = union(privatePeriods)
        var privateIndex = 0
        var total = 0L
        for (shift in covered) {
            total += shift.end - shift.start
            while (privateIndex < personal.size) {
                val pause = personal[privateIndex]
                if (pause.start >= shift.end) break
                total -= (minOf(shift.end, pause.end) - maxOf(shift.start, pause.start)).coerceAtLeast(0L)
                if (pause.end > shift.end) break
                privateIndex++
            }
        }
        return total
    }

    private fun union(intervals: List<Interval>): List<Interval> {
        val merged = mutableListOf<Interval>()
        for (interval in intervals.filter { it.start >= 0 && it.end > it.start }.sortedBy { it.start }) {
            val previous = merged.lastOrNull()
            if (previous != null && interval.start <= previous.end)
                merged[merged.lastIndex] = Interval(previous.start, maxOf(previous.end, interval.end))
            else merged.add(interval)
        }
        return merged
    }
}
