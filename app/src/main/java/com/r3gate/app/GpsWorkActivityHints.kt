package com.r3gate.app

/**
 * Read-only hints for a possible arrival at work. Never edits shifts or pay.
 * A GPS arrival alone is insufficient: movement must also have stopped.
 */
object GpsWorkActivityHints {
    data class Event(val type:String,val timeMs:Long,val placeId:String?=null)
    data class Suggestion(val objectId:String,val arrivalMs:Long,val stopMs:Long)
    const val MAX_EVENT_SEPARATION_MS=5*60_000L
    const val MAX_SUGGESTION_AGE_MS=20*60_000L

    fun suggest(events:List<Event>,nowMs:Long):Suggestion? {
        if(nowMs<=0) return null
        val sorted=events.filter { it.timeMs in 1..nowMs }.sortedBy { it.timeMs }
        var lastArrival:Event?=null
        var stop:Event?=null
        for(e in sorted) {
            when(e.type) {
                "ARRIVAL" -> {
                    if(!e.placeId.isNullOrBlank()) {
                        lastArrival=e
                        stop=null
                    }
                }
                "TRIP_STOP" -> if(lastArrival!=null && e.timeMs>=lastArrival.timeMs) stop=e
                "GPS_GAP","TRIP_START","DEPARTURE" -> {
                    lastArrival=null
                    stop=null
                }
            }
        }
        val arrival=lastArrival ?: return null
        val stopped=stop ?: return null
        if(stopped.timeMs-arrival.timeMs>MAX_EVENT_SEPARATION_MS ||
            nowMs-stopped.timeMs>MAX_SUGGESTION_AGE_MS) return null
        return Suggestion(arrival.placeId ?: return null,arrival.timeMs,stopped.timeMs)
    }
}
