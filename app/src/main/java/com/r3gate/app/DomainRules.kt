package com.r3gate.app

import java.math.BigDecimal
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

object DomainRules {
    fun cityPrefix(city:String):String = Normalizer.normalize(city.trim().uppercase(Locale.ROOT).replace("Ł","L"),Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"),"").filter { it in 'A'..'Z' }.take(3).padEnd(3,'X')

    fun nextTag(city:String,counter:Int,used:Collection<String>):Pair<String,Int> {
        val prefix=cityPrefix(city)
        val highest=used.mapNotNull { tag ->
            if(tag.startsWith(prefix+"-")) tag.substringAfterLast("-").toIntOrNull() else null
        }.maxOrNull() ?: 0
        val n=maxOf(counter,highest)+1
        return (prefix+"-"+"%02d".format(Locale.ROOT,n)) to n
    }
    fun quantity(text:String,unit:String):BigDecimal? {
        val q=text.trim().replace(",",".").toBigDecimalOrNull() ?: return null
        if(q<=BigDecimal.ZERO || unit !in listOf("szt.","m")) return null
        if(unit=="szt." && q.stripTrailingZeros().scale()>0) return null
        return q.stripTrailingZeros()
    }
    fun inRange(date:String,from:String,to:String):Boolean {
        if(from.isBlank() && to.isBlank()) return true
        val d=try { LocalDate.parse(date) } catch (_:Exception) { return false }
        return (from.isBlank() || d>=LocalDate.parse(from)) && (to.isBlank() || d<=LocalDate.parse(to))
    }
    fun timeByDay(start:Long,end:Long,zone:ZoneId):Map<String,Long> {
        val result=linkedMapOf<String,Long>()
        var a=start
        while(a<end) {
            val day=Instant.ofEpochMilli(a).atZone(zone).toLocalDate()
            val boundary=day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val b=minOf(end,boundary)
            result[day.toString()]=(result[day.toString()] ?: 0L)+(b-a)
            a=b
        }
        return result
    }
    fun validNote(text:String,photoCount:Int) = text.isNotBlank() || photoCount>0
}
