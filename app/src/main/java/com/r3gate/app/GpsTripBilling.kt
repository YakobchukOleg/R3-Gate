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
    /** Count each millisecond only once, even if imported evidence overlaps. */
    private fun coveredMs(intervals:List<Pair<Long,Long>>):Long {
        var total=0L
        var end=Long.MIN_VALUE
        for((from,to) in intervals.sortedWith(compareBy<Pair<Long,Long>> { it.first }.thenBy { it.second })) {
            if(to<=from) continue
            val uncoveredStart=maxOf(from,end)
            if(to>uncoveredStart) total+=to-uncoveredStart
            end=maxOf(end,to)
        }
        return total
    }
    fun calculate(trips:List<Trip>,window:GpsShiftEstimate.Window,rules:Rules):Total {
        if(window.endMs<=window.startMs) return Total(0,0,0,0)
        val all=mutableListOf<Pair<Long,Long>>()
        val paid=mutableListOf<Pair<Long,Long>>()
        var count=0
        var unknown=0
        for(t in trips) {
            if(t.endMs<=t.startMs) continue
            val from=maxOf(t.startMs,window.startMs)
            val to=minOf(t.endMs,window.endMs)
            if(to<=from) continue
            all.add(from to to)
            count++
            if(t.category==Category.UNKNOWN) unknown++
            if(when(t.category) {
                Category.OUTBOUND -> rules.outbound
                Category.RETURN -> rules.returnTrip
                Category.BETWEEN -> rules.between
                Category.SHOPPING -> rules.shopping
                Category.UNKNOWN,Category.PRIVATE -> false
            }) paid.add(from to to)
        }
        return Total(coveredMs(all),coveredMs(paid),count,unknown)
    }
}
