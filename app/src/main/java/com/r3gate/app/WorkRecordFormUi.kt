package com.r3gate.app

/**
 * Presentation-only content for the existing material, photo and note forms.
 * Storage, date and object assignment rules remain with the existing activity.
 */
object WorkRecordFormUi {
    enum class Kind { MATERIALS, PHOTOS, NOTES }

    data class Section(val title: String, val hint: String)

    fun kind(key: String): Kind? = when (key) {
        "materials" -> Kind.MATERIALS
        "links" -> Kind.PHOTOS
        "notes" -> Kind.NOTES
        else -> null
    }

    fun title(kind: Kind): String = when (kind) {
        Kind.MATERIALS -> "Materiał na obiekcie"
        Kind.PHOTOS -> "Zdjęcia i dokumentacja"
        Kind.NOTES -> "Notatka obiektu"
    }

    fun assignment(kind: Kind): Section = when (kind) {
        Kind.MATERIALS -> Section("Przypisanie", "Wybierz datę, obiekt i miejsce użycia materiału.")
        Kind.PHOTOS -> Section("Przypisanie", "Powiąż dokumentację z właściwym obiektem i datą.")
        Kind.NOTES -> Section("Przypisanie", "Notatka może dotyczyć wybranego obiektu albo pozostać bez obiektu.")
    }

    fun details(kind: Kind): Section = when (kind) {
        Kind.MATERIALS -> Section("Materiał i ilość", "Dla sztuk użyj liczby całkowitej, dla metrów możesz użyć części dziesiętnej.")
        Kind.PHOTOS -> Section("Załączniki", "Dodaj zdjęcie z galerii, zrób zdjęcie lub wskaż link Google Drive.")
        Kind.NOTES -> Section("Treść notatki", "Dodaj treść lub co najmniej jedno zdjęcie.")
    }

    fun attachments(count: Int): String {
        require(count >= 0)
        return when (count) {
            0 -> "Brak dołączonych zdjęć"
            1 -> "1 dołączone zdjęcie"
            else -> "$count dołączonych zdjęć"
        }
    }

    fun pendingBatch(count: Int): String {
        require(count >= 0)
        return "Materiały w zestawie: $count"
    }
}
