package com.r3gate.app

/**
 * Breaks in GPS coverage cannot validate a prior location suggestion.
 * Both the outage journal and time ranges are treated conservatively.
 */
object CoreGpsCoverageRules {
    data class Gap(val startMs: Long, val endMs: Long)

    fun trustworthy(
        eventMs: Long,
        nowMs: Long,
        outageEventTimes: Collection<Long>,
        coverageGaps: Collection<Gap>
    ): Boolean {
        if (eventMs <= 0L || nowMs < eventMs) return false
        // Missing or malformed outage timestamps must fail closed.
        if (outageEventTimes.any { it <= 0L }) return false
        if (outageEventTimes.any { it >= eventMs && it <= nowMs }) return false
        return coverageGaps.none { gap ->
            if (gap.startMs <= 0L || gap.endMs < gap.startMs) true
            else gap.startMs <= nowMs && gap.endMs >= eventMs
        }
    }
}
