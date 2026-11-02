package com.r3gate.app

/**
 * Presentation-only labels for object cards and archive confirmations.
 * Does not decide whether an object may be linked to a record.
 * The Core module remains the authority for object lifecycle rules.
 */
object ObjectUiPresentation {
    fun title(tag: String, organization: String, city: String, address: String): String =
        listOf(tag, organization, city, address)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .joinToString(" · ")

    fun accessibilityLabel(title: String, archived: Boolean): String =\n        listOf(title.trim(), status(archived)).filter(String::isNotEmpty).joinToString(\", \" )\n\n    fun status(archived: Boolean): String =
        if (archived) "Zarchiwizowany" else "Aktywny"

    fun archiveAction(archived: Boolean): String =
        if (archived) "Przywróć obiekt" else "Archiwizuj obiekt"

    fun archiveConfirmation(archived: Boolean): String =
        if (archived) "Przywrócić obiekt do listy aktywnych?"
        else "Zarchiwizować obiekt? Historia pracy i wcześniejsze wpisy pozostaną dostępne."

    fun emptyList(archived: Boolean): String =
        if (archived) "Brak zarchiwizowanych obiektów"
        else "Dodaj pierwszy obiekt, aby przypisywać pracę, materiały i zdjęcia."
}
