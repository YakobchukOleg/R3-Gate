package com.r3gate.app

/**
 * GPS-only estimated travel time, kept separate from authoritative shift stages.
 * Incomplete trips and events outside the chosen shift never enter the totals.
 */
object GpsShiftEstimate {
    data class Event(val type:String,val timestampMs:Long)
    data class Window(val startMs:Long,val endMs:Long)
    data class Summary(val actualTravelMs:Long,val payableTravelMs:Long,val completeTrips:Int)
    fun calculate(events:List<Event>,window:Window,payTravel:Boolean):Summary {
        if(window.endMs<=window.startMs) return Summary(0,0,0)
        var opened:Long?=null
        var actual=0L
        var trips=0
        for(event in events.sortedBy { it.timestampMs }) {
            when(event.type) {
                "TRIP_START" -> opened=event.timestampMs
                "GPS_GAP" -> opened=null
                "TRIP_STOP" -> {
                    val start=opened
                    if(start!=null && event.timestampMs>start) {
                        val from=maxOf(start,window.startMs)
                        val to=minOf(event.timestampMs,window.endMs)
                        if(to>from) { actual+=to-from;trips++ }
                    }
                    opened=null
                }
            }
        }
        return Summary(actual,if(payTravel) actual else 0L,trips)
    }
}
