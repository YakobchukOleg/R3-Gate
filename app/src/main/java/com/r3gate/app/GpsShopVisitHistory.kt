package com.r3gate.app

/** Counts confirmed GPS shop visits without changing timesheets or billing categories. */
object GpsShopVisitHistory {
    const val LOOKBACK_MS=30L*24*60*60_000
    const val MAX_ARRIVAL_TO_STOP_MS=5*60_000L
    data class Event(val type:String,val timeMs:Long,val placeId:String?=null)
    fun confirmedVisits(events:List<Event>,shopId:String,nowMs:Long):Int {
        if(shopId.isBlank() || nowMs<=0) return 0
        var arrivalAt:Long?=null
        var visits=0
        for(e in events.filter { it.timeMs in 1..nowMs }.sortedBy { it.timeMs }) {
            when(e.type) {
                "ARRIVAL" -> arrivalAt=if(e.placeId==shopId) e.timeMs else null
                "TRIP_STOP" -> {
                    val at=arrivalAt
                    if(at!=null && e.timeMs-at in 0..MAX_ARRIVAL_TO_STOP_MS &&
                        e.timeMs>=nowMs-LOOKBACK_MS) visits++
                    arrivalAt=null
                }
                "GPS_GAP","DEPARTURE","TRIP_START" -> arrivalAt=null
            }
        }
        return visits
    }
}
