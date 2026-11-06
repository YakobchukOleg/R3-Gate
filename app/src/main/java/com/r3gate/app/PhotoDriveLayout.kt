package com.r3gate.app

import org.json.JSONObject
import java.time.LocalDate

/** Relative folder names under WorkMeter; independent of upload time and organization. */
internal object PhotoDriveLayout {
    fun path(recordDate: LocalDate, site: JSONObject?, storage: String): List<String> {
        val folder = if (site == null) {
            if (storage == "car") "W samochodzie" else "Bez obiektu"
        } else {
            fun value(key: String) = if (site.isNull(key)) "" else site.optString(key).trim()
            val address = value("Adres").ifBlank {
                listOf(value("street"), value("building"), value("postcode"))
                    .filter(String::isNotBlank).joinToString(" ")
            }
            listOf(value("TAG"), value("city"), address).filter(String::isNotBlank)
                .joinToString(" ").ifBlank { "Bez obiektu" }
        }
        return listOf(recordDate.year.toString(), folder)
    }
}
