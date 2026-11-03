package com.r3gate.app

/** Conservative, read-only activity candidates. No classification changes saved work time. */
object GpsActivitySuggestionRules {
    enum class Kind { WORKSITE, SHOPPING, STOP }
    data class Event(val type:String,val timeMs:Long,val placeId:String?=null)
    data class Suggestion(val kind:Kind,val placeId:String?,val atMs:Long)
    const val MAX_ARRIVAL_STOP_MS=5*60_000L
    const val MAX_HINT_AGE_MS=20*60_000L
    fun latest(events:List<Event>,placeKinds:Map<String,Kind>,nowMs:Long):Suggestion? {
        if(nowMs<=0) return null
        var arrival:Event?=null
        var stopped:Event?=null
        var hint:Suggestion?=null
        for(e in events.filter { it.timeMs in 1..nowMs }.sortedBy { it.timeMs }) {
            when(e.type) {
                "ARRIVAL" -> {
                    arrival=if(!e.placeId.isNullOrBlank()) e else null
                    hint=null
                    val recentStop=stopped
                    if(arrival!=null && recentStop!=null &&
                        e.timeMs-recentStop.timeMs in 0..MAX_ARRIVAL_STOP_MS) {
                        val kind=placeKinds[arrival.placeId]
                        if(kind==Kind.WORKSITE || kind==Kind.SHOPPING)
                            hint=Suggestion(kind,arrival.placeId,recentStop.timeMs)
                    }
                }
                "TRIP_STOP" -> {
                    stopped=e
                    val a=arrival
                    if(a!=null && e.timeMs>=a.timeMs &&
                        e.timeMs-a.timeMs<=MAX_ARRIVAL_STOP_MS) {
                        val kind=placeKinds[a.placeId]
                        if(kind==Kind.WORKSITE || kind==Kind.SHOPPING)
                            hint=Suggestion(kind,a.placeId,e.timeMs)
                    }
                }
                "LONG_STOP" -> {
                    if(hint==null && stopped!=null && arrival==null)
                        hint=Suggestion(Kind.STOP,null,e.timeMs)
                }
                "DEPARTURE","TRIP_START","GPS_GAP" -> {
                    arrival=null; stopped=null; hint=null
                }
            }
        }
        return hint?.takeIf { nowMs-it.atMs<=MAX_HINT_AGE_MS }
    }
}
