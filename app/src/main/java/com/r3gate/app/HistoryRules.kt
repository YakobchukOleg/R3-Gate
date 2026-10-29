package com.r3gate.app
import java.time.Instant
import java.time.ZoneId

object HistoryRules {
    data class Slice(val day:String,val start:Long,val end:Long)
    fun slices(start:Long,end:Long,zone:ZoneId):List<Slice> {
        if(end<=start) return listOf(Slice(Instant.ofEpochMilli(start).atZone(zone).toLocalDate().toString(),start,start))
        val result=mutableListOf<Slice>(); var cursor=start
        while(cursor<end) {
            val day=Instant.ofEpochMilli(cursor).atZone(zone).toLocalDate()
            val until=minOf(end,day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
            result.add(Slice(day.toString(),cursor,until));cursor=until
        }
        return result
    }
}
