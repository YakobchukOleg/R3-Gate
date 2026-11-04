package com.r3gate.app

object WorkPlaceRules {
    enum class Type(val label: String) { WORKSITE("Obiekt"), SHOP("Sklep"), OFFICE("Biuro") }

    fun type(saved: String?, legacyShop: Boolean = false): Type =
        Type.entries.firstOrNull { it.name == saved } ?: if (legacyShop) Type.SHOP else Type.WORKSITE

    // Shop visits still enter the explicit shopping workflow through Zakupy.
    fun workStage(type: Type): String = if (type == Type.OFFICE) "Biuro" else "Obiekt"

    fun isOnSiteStage(kind: String?): Boolean = kind == "Obiekt" || kind == "Biuro"
}
