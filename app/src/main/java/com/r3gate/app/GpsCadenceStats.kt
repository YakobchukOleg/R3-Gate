package com.r3gate.app

/** Observed fix cadence, not an estimate of battery power used. */
object GpsCadenceStats {
    data class Summary(val samples:Int,val intervals:Int,val medianIntervalMs:Long?,val longestGapMs:Long?,val delayedIntervals:Int)
    fun assess(fixTimes:List<Long>,sessionStartMs:Long,nowMs:Long,expectedIntervalMs:Long):Summary {
        if(nowMs<=0 || sessionStartMs<=0 || nowMs<sessionStartMs || expectedIntervalMs<=0)
            return Summary(0,0,null,null,0)
        val ordered=fixTimes.filter { it in sessionStartMs..nowMs }.distinct().sorted()
        val gaps=ordered.zipWithNext().map { (a,b) -> b-a }.filter { it>0 }
        val sortedGaps=gaps.sorted()
        val median=if(sortedGaps.isEmpty()) null else sortedGaps[(sortedGaps.size-1)/2]
        val delayed=gaps.count { it>expectedIntervalMs*2 }
        return Summary(ordered.size,gaps.size,median,gaps.maxOrNull(),delayed)
    }
}
