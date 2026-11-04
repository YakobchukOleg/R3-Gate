package com.r3gate.app

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId

object WalletRules {
    data class Shift(val start:Long,val end:Long,val rate:BigDecimal,val privatePeriods:List<PrivateTimeRules.Interval> = emptyList())
    data class Payment(val from:String,val to:String,val cents:Long,val method:String)
    data class Summary(val earned:Long,val cash:Long,val card:Long,val overlapping:Int,val conflictingRateMillis:Long=0L) {
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
    data class Earnings(val cents: Long, val conflictingRateMillis: Long)
    private data class Change(val time: Long, val rate: BigDecimal?, val delta: Int)

    fun earned(shifts:List<Shift>,from:LocalDate?=null,to:LocalDate?=null,zone:ZoneId=ZoneId.systemDefault()):Long =
        calculateEarnings(shifts,from,to,zone).cents

    fun calculateEarnings(
        shifts: List<Shift>, from: LocalDate? = null, to: LocalDate? = null,
        zone: ZoneId = ZoneId.systemDefault()
    ): Earnings {
        val lower = from?.atStartOfDay(zone)?.toInstant()?.toEpochMilli() ?: 0L
        val upper = to?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.toEpochMilli() ?: Long.MAX_VALUE
        val changes = buildList {
            fun addInterval(start: Long, end: Long, rate: BigDecimal?) {
                add(Change(start,rate,1))
                add(Change(end,rate,-1))
            }
            for (shift in shifts) {
                if (!WorkShiftIntegrity.isValid(shift.start,shift.end)) continue
                val start = maxOf(lower,shift.start)
                val end = minOf(upper,shift.end)
                if (end <= start) continue
                addInterval(start,end,shift.rate.stripTrailingZeros())
                for (pause in shift.privatePeriods) {
                    if (!WorkShiftIntegrity.isValid(pause.start,pause.end)) continue
                    val a = maxOf(start,pause.start)
                    val b = minOf(end,pause.end)
                    if (b > a) addInterval(a,b,null)
                }
            }
        }.sortedBy { it.time }
        val rates = mutableMapOf<BigDecimal,Int>()
        var pauses = 0
        var previous = changes.firstOrNull()?.time ?: 0L
        var weighted = BigDecimal.ZERO
        var conflicts = 0L
        for (change in changes) {
            val elapsed = change.time - previous
            if (elapsed > 0 && pauses == 0) {
                if (rates.size == 1) weighted += rates.keys.single() * BigDecimal.valueOf(elapsed)
                if (rates.size > 1) conflicts += elapsed
            }
            val rate = change.rate
            if (rate == null) pauses += change.delta else {
                val count = (rates[rate] ?: 0) + change.delta
                if (count == 0) rates.remove(rate) else rates[rate] = count
            }
            previous = change.time
        }
        val cents = weighted.movePointRight(2).divide(BigDecimal.valueOf(3600000),0,RoundingMode.HALF_UP)
        return Earnings(cents.longValueExact(),conflicts)
    }

    fun summary(shifts:List<Shift>,payments:List<Payment>,from:LocalDate,to:LocalDate,zone:ZoneId=ZoneId.systemDefault()):Summary {
        require(!to.isBefore(from))
        val a=from.toString(); val b=to.toString()
        val included=payments.filter { it.from>=a && it.to<=b }
        val overlapping=payments.count { it.from<=b && it.to>=a && !(it.from>=a && it.to<=b) }
        val earnings=calculateEarnings(shifts,from,to,zone)
        return Summary(earnings.cents,included.filter { it.method=="cash" }.sumOf { it.cents },included.filter { it.method=="card" }.sumOf { it.cents },overlapping,earnings.conflictingRateMillis)
    }
}
