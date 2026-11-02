package com.r3gate.app

/** A trip requires an explicit category before it can be included in payable hours. */
object GpsTripBilling {
    enum class Category { UNKNOWN, OUTBOUND, RETURN, BETWEEN, SHOPPING, PRIVATE }
    data class Trip(val startMs:Long,val endMs:Long,val category:Category=Category.UNKNOWN)
    data class Rules(
        val outbound:Boolean=false,
        val returnTrip:Boolean=false,
        val between:Boolean=true,
        val shopping:Boolean=true
    )
    data class Total(val elapsedMs:Long,val payableMs:Long,val trips:Int,val uncategorized:Int)
    fun pair(events:List<GpsShiftEstimate.Event>,categories:Map<Long,Category>):List<Trip> {
        val result=mutableListOf<Trip>()
        var start:Long?=null
        for(event in events.sortedBy { it.timestampMs }) {
            when(event.type) {
                "TRIP_START" -> start=event.timestampMs
                "GPS_GAP" -> start=null
                "TRIP_STOP" -> {
                    val opened=start
                    if(opened!=null && event.timestampMs>opened)
                        result.add(Trip(opened,event.timestampMs,categories[opened] ?: Category.UNKNOWN))
                    start=null
                }
            }
        }
        return result
    }
    fun calculate(trips:List<Trip>,window:GpsShiftEstimate.Window,rules:Rules):Total {
        if(window.endMs<=window.startMs) return Total(0,0,0,0)
        var elapsed=0L;var payable=0L;var count=0;var unknown=0
        for(t in trips) {
            if(t.endMs<=t.startMs) continue
            val duration=(minOf(t.endMs,window.endMs)-maxOf(t.startMs,window.startMs)).coerceAtLeast(0)
            if(duration==0L) continue
            elapsed+=duration;count++
            if(t.category==Category.UNKNOWN) unknown++
            if(when(t.category) {
                Category.OUTBOUND -> rules.outbound
                Category.RETURN -> rules.returnTrip
                Category.BETWEEN -> rules.between
                Category.SHOPPING -> rules.shopping
                Category.UNKNOWN,Category.PRIVATE -> false
            }) payable+=duration
        }
        return Total(elapsed,payable,count,unknown)
    }
}
