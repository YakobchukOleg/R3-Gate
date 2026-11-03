package com.r3gate.app

/**
 * Validate the shape of persisted GPS-coverage records before they authorize
 * an automatic suggestion. Malformed records must not be ignored as if the
 * GPS journal proved uninterrupted location tracking.
 */
object CoreGpsCoverageJournal {
    data class Boundary(val kind: String?, val timestampMs: Long?)
    data class Interval(val startMs: Long?, val endMs: Long?)
    data class Evidence(
        val outages: List<Long>,
        val gaps: List<CoreGpsCoverageRules.Gap>
    )

    fun validated(
        boundaries: Collection<Boundary?>,
        intervals: Collection<Interval?>
    ): Evidence? {
        val outages = ArrayList<Long>()
        for (boundary in boundaries) {
            if (boundary == null || boundary.kind.isNullOrBlank()) return null
            val time = boundary.timestampMs ?: return null
            if (time <= 0L) return null
            if (boundary.kind == "GPS_GAP") outages.add(time)
        }
        val gaps = ArrayList<CoreGpsCoverageRules.Gap>()
        for (interval in intervals) {
            if (interval == null) return null
            val start = interval.startMs ?: return null
            val end = interval.endMs ?: return null
            if (start <= 0L || end < start) return null
            gaps.add(CoreGpsCoverageRules.Gap(start, end))
        }
        return Evidence(outages, gaps)
    }

    fun trustworthy(
        eventMs: Long,
        nowMs: Long,
        boundaries: Collection<Boundary?>,
        intervals: Collection<Interval?>
    ): Boolean {
        val evidence = validated(boundaries, intervals) ?: return false
        return CoreGpsCoverageRules.trustworthy(
            eventMs, nowMs, evidence.outages, evidence.gaps
        )
    }
}
