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
        return nextPrefixTag(prefix,counter,used)
    }
    fun customPrefix(tag:String):String = Normalizer.normalize(tag.trim().uppercase(Locale.ROOT).replace("Ł","L"),Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"),"").filter { it in 'A'..'Z' || it in '0'..'9' }.take(20)
    fun nextPrefixTag(prefix:String,counter:Int,used:Collection<String>):Pair<String,Int> {
        val highest=used.mapNotNull { tag ->
            if(tag.startsWith(prefix+"-")) tag.substringAfterLast("-").toIntOrNull() else null
        }.maxOrNull() ?: 0
        val n=maxOf(counter,highest)+1
        return (prefix+"-"+"%02d".format(Locale.ROOT,n)) to n
    }
    fun quantity(text:String,unit:String):BigDecimal? {
        val input = text.trim()
        if (input.length > 256 || unit !in listOf("szt.", "m")) return null
        val q = input.replace(",", ".").toBigDecimalOrNull() ?: return null
        // Bound the expanded decimal before it is written with toPlainString().
        if (q.signum() <= 0 || q.scale() !in -128..128 || q.precision() > 128) return null
        val normalized = q.stripTrailingZeros()
        if (unit == "szt." && normalized.scale() > 0) return null
        return normalized
    }
    fun inRange(date:String,from:String,to:String):Boolean {
        if(from.isBlank() && to.isBlank()) return true
        return try {
            val d = LocalDate.parse(date)
            (from.isBlank() || d >= LocalDate.parse(from)) && (to.isBlank() || d <= LocalDate.parse(to))
        } catch (_: java.time.format.DateTimeParseException) {
            false
        }
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
