package com.r3gate.app

/** Elapsed working time excludes explicit personal intervals, including overlaps. */
object PrivateTimeRules {
    data class Interval(val start: Long, val end: Long)
    fun worked(start: Long, end: Long, privatePeriods: List<Interval>): Long {
        if (end <= start) return 0L
        val clipped = privatePeriods.mapNotNull {
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
}
