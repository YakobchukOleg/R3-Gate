package com.r3gate.app

import java.time.*

object TimeAnalytics {
    data class Stage(val start:Long,val end:Long,val category:Int)
    data class Shift(val start:Long,val end:Long,val stages:List<Stage>)
    data class Result(val totals:LongArray,val hours:Array<LongArray>)
    fun previousWeek(today:LocalDate):Pair<LocalDate,LocalDate> {
        val monday=today.minusDays((today.dayOfWeek.value-1).toLong())
        return monday.minusWeeks(1) to monday.minusDays(1)
    }
    fun previousMonth(today:LocalDate):Pair<LocalDate,LocalDate> {
        val first=today.withDayOfMonth(1)
        return first.minusMonths(1) to first.minusDays(1)
    }
    // Partition each shift at every stage boundary. Overlapping legacy stages never add time twice.
    fun calculate(shifts:List<Shift>,from:LocalDate,to:LocalDate,zone:ZoneId):Result {
        val totals=LongArray(4); val hours=Array(24){LongArray(4)}
        val lower=from.atStartOfDay(zone).toInstant().toEpochMilli()
        val upper=to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        for(sh in shifts) {
            val start=maxOf(sh.start,lower); val end=minOf(sh.end,upper)
            if(end<=start) continue
            val points=(listOf(start,end)+sh.stages.flatMap { listOf(it.start,it.end) }.filter { it>start && it<end }).distinct().sorted()
            for((a,b) in points.zipWithNext()) {
                val category=sh.stages.filter { it.start<=a && it.end>=b }.maxByOrNull { it.start }?.category?.takeIf { it in 0..3 } ?: 3
                totals[category]+=b-a
                var cursor=a
                while(cursor<b) {
                    val local=Instant.ofEpochMilli(cursor).atZone(zone)
                    val boundary=local.withMinute(0).withSecond(0).withNano(0).plusHours(1).toInstant().toEpochMilli()
                    val stop=minOf(b,boundary)
                    hours[local.hour][category]+=stop-cursor; cursor=stop
                }
            }
        }
        return Result(totals,hours)
    }
}
