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
        // Use the same interval-union engine as GPS trip billing so imported,
        // duplicated or overlapping evidence cannot inflate estimated travel.
        val trips=GpsTripBilling.pair(events,emptyMap())
        val totals=GpsTripBilling.calculate(trips,window,GpsTripBilling.Rules())
        return Summary(totals.elapsedMs,if(payTravel) totals.elapsedMs else 0L,totals.trips)
    }
}
