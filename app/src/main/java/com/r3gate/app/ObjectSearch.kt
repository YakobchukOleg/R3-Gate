package com.r3gate.app

import java.text.Normalizer
import java.util.Locale

object ObjectSearch {
    val fieldKeys = listOf("TAG", "name", "organization", "Opis", "city", "Adres", "street", "building", "postcode")

    /** Presentation ranking only; eligibility and object IDs remain owned by Core. */
    fun relevance(fields: List<String>, query: String): Int {
        val tokens=normalized(query).split(Regex("\\s+")).filter(String::isNotBlank)
        if(tokens.isEmpty()) return 0
        val values=fields.map(::normalized)
        return tokens.sumOf { token -> values.maxOfOrNull { value ->
            when {
                value==token -> 100
                value.startsWith(token) -> 50
                value.split(Regex("\\s+")).any { it.startsWith(token) } -> 25
                value.contains(token) -> 10
                else -> 0
            }
        } ?: 0 }
    }
    private fun normalized(value:String)=Normalizer.normalize(value.replace('ł','l').replace('Ł','L'),Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"").lowercase(Locale.ROOT).trim()
    fun matches(value: String, query: String): Boolean = matchesFields(listOf(value), query)
    fun matchesFields(fields: List<String>, query: String): Boolean {
        val text = fields.joinToString(" ") { normalized(it) }
        return normalized(query).split(Regex("\\s+")).filter(String::isNotBlank).all(text::contains)
    }
    fun compareTags(a:String,b:String):Int {
        val prefix=normalized(a.substringBeforeLast('-',a)).compareTo(normalized(b.substringBeforeLast('-',b)))
        if(prefix!=0) return prefix
        val an=a.substringAfterLast('-').toLongOrNull(); val bn=b.substringAfterLast('-').toLongOrNull()
        return if(an!=null && bn!=null) an.compareTo(bn) else a.compareTo(b,true)
    }
}
