package com.r3gate.app

/** Read-only presentation for existing photo and material records. */
object WorkRecordListUi {
    enum class Kind { PHOTOS, MATERIALS, NOTES }
    data class Header(val heading: String, val description: String, val countLabel: String, val emptyMessage: String)
    data class Card(val title: String, val subtitle: String, val badge: String, val accessibilityLabel: String)

    fun kind(key: String): Kind? = when (key) {
        "links" -> Kind.PHOTOS
        "materials" -> Kind.MATERIALS
        "notes" -> Kind.NOTES
        else -> null
    }

    fun header(kind: Kind, count: Int, filtered: Boolean): Header {
        require(count >= 0)
        val heading = when (kind) {
            Kind.PHOTOS -> "Zdjęcia i dokumentacja"
            Kind.MATERIALS -> "Zużyte materiały"
            Kind.NOTES -> "Notatki obiektu"
        }
        val description = when (kind) {
            Kind.PHOTOS -> "Dokumentacja przypisana do obiektów i dni pracy"
            Kind.MATERIALS -> "Materiały wykorzystane na obiektach"
            Kind.NOTES -> "Wpisy i ważne informacje przypisane do pracy"
        }
        val noun=if(count==1) "wpis" else if(count%10 in 2..4 && count%100 !in 12..14) "wpisy" else "wpisów"
        val empty = if (filtered) "Brak wpisów dla wybranych filtrów."
            else when (kind) {
                Kind.PHOTOS -> "Brak zdjęć. Dodaj pierwszą dokumentację."
                Kind.MATERIALS -> "Brak materiałów. Dodaj pierwszy wpis."
                Kind.NOTES -> "Brak notatek. Dodaj pierwszą notatkę."
            }
        return Header(heading,description,"${count} ${noun}",empty)
    }

    fun photos(photoCount: Int, driveLink: String, objectName: String, status: String): Card {
        require(photoCount >= 0)
        val title = when (photoCount) {
            0 -> if (driveLink.isBlank()) "Dokumentacja bez zdjęć" else "Link Google Drive"
            1 -> "1 zdjęcie"
            else -> "${photoCount} zdjęć"
        }
        val detail = objectName.trim().ifEmpty { "Bez obiektu" }
        val badge = status.trim().ifBlank { if (driveLink.isNotBlank()) "Link" else "Lokalnie" }
        return Card(title, detail, badge, "${title} · ${detail} · ${badge}")
    }

    fun materials(name: String, quantity: String, unit: String, objectName: String): Card {
        val title = name.trim().ifEmpty { "Materiał" }
        val qty = quantity.trim()
        val suffix = unit.trim().ifEmpty { "szt." }
        val badge = if (qty.isEmpty()) "Bez ilości" else "${qty} ${suffix}"
        val detail = objectName.trim().ifEmpty { "Bez obiektu" }
        return Card(title,detail,badge,"${title} · ${badge} · ${detail}")
    }

    fun materialsGroup(date: String, objectName: String, itemCount: Int): Card {
        require(itemCount >= 0)
        val name=objectName.trim().ifEmpty { "Bez obiektu" }
        val badge=if(itemCount==1) "1 materiał" else "${itemCount} materiałów"
        return Card(name, date.trim(),badge,"${name} · ${badge} · ${date.trim()}")
    }
}
