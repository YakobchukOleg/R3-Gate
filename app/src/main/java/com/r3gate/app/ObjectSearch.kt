package com.r3gate.app

import java.text.Normalizer
import java.util.Locale

object ObjectSearch {
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
