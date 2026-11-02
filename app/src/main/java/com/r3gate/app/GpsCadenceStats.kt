package com.r3gate.app

/** Observed fix cadence, not an estimate of battery power used. */
object GpsCadenceStats {
    data class Summary(val samples:Int,val intervals:Int,val medianIntervalMs:Long?,val longestGapMs:Long?,val delayedIntervals:Int,
        val coverageInterruptions:Int=0)
    fun assess(fixTimes:List<Long>,sessionStartMs:Long,nowMs:Long,expectedIntervalMs:Long):Summary {
        if(nowMs<=0 || sessionStartMs<=0 || nowMs<sessionStartMs || expectedIntervalMs<=0)
            return Summary(0,0,null,null,0)
        val ordered=fixTimes.filter { it in sessionStartMs..nowMs }.distinct().sorted()
        val gaps=ordered.zipWithNext().map { (a,b) -> b-a }.filter { it>0 }
        val sortedGaps=gaps.sorted()
        val median=if(sortedGaps.isEmpty()) null else sortedGaps[(sortedGaps.size-1)/2]
        // Division-based comparison avoids overflow for malformed or huge intervals.
        val delayed=gaps.count { gap -> gap>expectedIntervalMs &&
            gap/2>expectedIntervalMs || gap>expectedIntervalMs &&
            gap/2==expectedIntervalMs && gap%2>0 }
        val interruptions=gaps.count { it>GpsTripRules.MAX_EVIDENCE_GAP_MS }
        return Summary(ordered.size,gaps.size,median,gaps.maxOrNull(),delayed,interruptions)
    }
}
