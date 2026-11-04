package com.r3gate.app

import java.math.BigDecimal

object MaterialStock {
    data class Entry(val id:String,val name:String,val unit:String,val quantity:BigDecimal,val car:Boolean,val sourceId:String="")
    data class Draft(val name: String, val quantity: String, val unit: String)

    fun validateDraft(name: String, quantity: String, unit: String): Draft? {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return null
        val amount = DomainRules.quantity(quantity, unit) ?: return null
        return Draft(cleanName, amount.toPlainString(), unit)
    }

    fun remaining(source:Entry,entries:List<Entry>):BigDecimal = source.quantity-entries.filter { it.sourceId==source.id }.fold(BigDecimal.ZERO) { total,row -> total+row.quantity }
    fun valid(entries:List<Entry>):Boolean {
        if(entries.map { it.id }.distinct().size!=entries.size) return false
        return entries.all { row ->
            row.quantity>BigDecimal.ZERO && (!row.car || row.sourceId.isBlank()) &&
                (row.sourceId.isBlank() || entries.any { it.id==row.sourceId && it.car && it.name==row.name && it.unit==row.unit }) &&
                (!row.car || remaining(row,entries)>=BigDecimal.ZERO)
        }
    }
}
