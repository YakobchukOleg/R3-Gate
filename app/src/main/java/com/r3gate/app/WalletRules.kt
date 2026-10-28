package com.r3gate.app

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId

object WalletRules {
    data class Shift(val start:Long,val end:Long,val rate:BigDecimal)
    data class Payment(val from:String,val to:String,val cents:Long,val method:String)
    data class Summary(val earned:Long,val cash:Long,val card:Long,val overlapping:Int) {
        val paid:Long get()=cash+card
        val due:Long get()=earned-paid
    }
    fun money(text:String,signed:Boolean=false):Long? {
        val value=text.trim().replace(',','.')
        val pattern=if(signed) "[+-]?[0-9]{1,9}(\\.[0-9]{1,2})?" else "[0-9]{1,9}(\\.[0-9]{1,2})?"
        if(!Regex(pattern).matches(value)) return null
        return try { value.toBigDecimal().movePointRight(2).longValueExact() } catch(_:Exception) { null }
    }
    fun rate(text:String):BigDecimal? {
        val cents=money(text) ?: return null
        if(cents<=0 || cents>100000000L) return null
        return BigDecimal.valueOf(cents,2)
    }
    fun earned(shifts:List<Shift>,from:LocalDate?=null,to:LocalDate?=null,zone:ZoneId=ZoneId.systemDefault()):Long {
        var sum=BigDecimal.ZERO
        for(s in shifts) {
            val start=maxOf(s.start,from?.atStartOfDay(zone)?.toInstant()?.toEpochMilli() ?: s.start)
            val end=minOf(s.end,to?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.toEpochMilli() ?: s.end)
            if(end>start) sum+=s.rate.multiply(BigDecimal.valueOf(end-start)).divide(BigDecimal.valueOf(3600000),12,RoundingMode.HALF_UP)
        }
        return sum.movePointRight(2).setScale(0,RoundingMode.HALF_UP).longValueExact()
    }
    fun summary(shifts:List<Shift>,payments:List<Payment>,from:LocalDate,to:LocalDate,zone:ZoneId=ZoneId.systemDefault()):Summary {
        require(!to.isBefore(from))
        val a=from.toString(); val b=to.toString()
        val included=payments.filter { it.from>=a && it.to<=b }
        val overlapping=payments.count { it.from<=b && it.to>=a && !(it.from>=a && it.to<=b) }
        return Summary(earned(shifts,from,to,zone),included.filter { it.method=="cash" }.sumOf { it.cents },included.filter { it.method=="card" }.sumOf { it.cents },overlapping)
    }
}
