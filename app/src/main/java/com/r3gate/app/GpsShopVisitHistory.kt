package com.r3gate.app

/** Counts confirmed GPS shop visits without changing timesheets or billing categories. */
object GpsShopVisitHistory {
    const val LOOKBACK_MS=30L*24*60*60_000
    const val MAX_ARRIVAL_TO_STOP_MS=5*60_000L
    data class Event(val type:String,val timeMs:Long,val placeId:String?=null)

    fun confirmedVisits(events:List<Event>,shopId:String,nowMs:Long):Int {
        if(shopId.isBlank() || nowMs<=0) return 0
        var arrivalAt:Long?=null
        var stopAt:Long?=null
        var visits=0
        for(e in events.filter { it.timeMs in 1..nowMs }.distinct().sortedBy { it.timeMs }) {
            when(e.type) {
                "ARRIVAL" -> {
                    if(e.placeId!=shopId) {
                        arrivalAt=null
                        stopAt=null
                        continue
                    }
                    val stop=stopAt
                    if(stop!=null && e.timeMs-stop in 0..MAX_ARRIVAL_TO_STOP_MS &&
                        e.timeMs>=nowMs-LOOKBACK_MS) {
                        visits++
                        arrivalAt=null
                        stopAt=null
                    } else {
                        arrivalAt=e.timeMs
                        stopAt=null
                    }
                }
                "TRIP_STOP" -> {
                    val arrival=arrivalAt
                    if(arrival!=null && e.timeMs-arrival in 0..MAX_ARRIVAL_TO_STOP_MS &&
                        e.timeMs>=nowMs-LOOKBACK_MS) {
                        visits++
                        arrivalAt=null
                        stopAt=null
                    } else {
                        arrivalAt=null
                        stopAt=e.timeMs
                    }
                }
                "GPS_GAP","DEPARTURE","TRIP_START" -> {
                    arrivalAt=null
                    stopAt=null
                }
            }
        }
        return visits
    }
}
