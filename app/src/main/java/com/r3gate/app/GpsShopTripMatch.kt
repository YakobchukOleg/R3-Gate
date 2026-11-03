package com.r3gate.app

/**
 * Matches an explicitly tagged shop stop to an unambiguous, completed GPS trip.
 * Never infers shopping purpose or changes saved trip categories.
 */
object GpsShopTripMatch {
    fun matchingTrip(
        events:List<GpsShiftEstimate.Event>,
        stopMs:Long,
        shopTagged:Boolean
    ):GpsTripBilling.Trip? {
        if(!shopTagged || stopMs<=0) return null
        var opened:Long?=null
        var startCount=0
        var matchingStops=0
        var result:GpsTripBilling.Trip?=null
        for(event in events.filter { it.timestampMs>0 && it.timestampMs<=stopMs }
            .sortedBy { it.timestampMs }) {
            when(event.type) {
                "TRIP_START" -> {
                    opened=event.timestampMs
                    startCount++
                }
                "GPS_GAP" -> {
                    opened=null
                    startCount=0
                }
                "TRIP_STOP" -> {
                    if(event.timestampMs==stopMs) {
                        matchingStops++
                        if(startCount==1 && opened!=null && stopMs>opened)
                            result=GpsTripBilling.Trip(opened,stopMs)
                    }
                    opened=null
                    startCount=0
                }
            }
        }
        return result.takeIf { matchingStops==1 }
    }
}
