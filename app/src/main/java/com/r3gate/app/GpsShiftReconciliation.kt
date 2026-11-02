package com.r3gate.app

/** Read-only reconciliation of GPS trip evidence with a manually recorded shift. */
object GpsShiftReconciliation {
    data class Window(val startMs:Long,val endMs:Long)
    data class Review(
        val completedTrips:Int,
        val travelInsideMs:Long,
        val travelOutsideMs:Long,
        val incompleteTripStarts:Int,
        val overlappingTrips:Int,
        val missingLocationIntervals:Int,
        val requiresReview:Boolean
    )
    fun review(
        events:List<GpsShiftEstimate.Event>,
        shift:Window,
        fixTimes:List<Long> = emptyList(),
        gapThresholdMs:Long = 15 * 60_000L
    ):Review {
        if(shift.endMs<=shift.startMs) return Review(0,0,0,0,0,0,false)
        var opened:Long?=null
        var complete=0
        var inside=0L
        var outside=0L
        var overlapping=0
        for(e in events.sortedBy { it.timestampMs }) {
            when(e.type) {
                "TRIP_START" -> {
                    if(opened!=null) overlapping++
                    opened=e.timestampMs
                }
                "TRIP_STOP" -> {
                    val start=opened
                    if(start!=null && e.timestampMs>start) {
                        val duration=e.timestampMs-start
                        val overlap=(minOf(e.timestampMs,shift.endMs)-maxOf(start,shift.startMs)).coerceAtLeast(0L)
                        inside+=overlap
                        outside+=duration-overlap
                        complete++
                    }
                    opened=null
                }
            }
        }
        val fixes=fixTimes.filter { it in shift.startMs..shift.endMs }.sorted().distinct()
        val boundaries=(listOf(shift.startMs)+fixes+listOf(shift.endMs))
        val gaps=boundaries.zipWithNext().count { (a,b) -> b-a>gapThresholdMs }
        val unfinished=if(opened!=null && opened<shift.endMs) 1 else 0
        return Review(complete,inside,outside,unfinished,overlapping,gaps,
            unfinished>0 || overlapping>0 || outside>0 || gaps>0)
    }
}
