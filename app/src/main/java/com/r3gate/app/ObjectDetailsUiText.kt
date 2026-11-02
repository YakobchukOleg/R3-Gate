package com.r3gate.app

/**
 * Presentation-only labels for the object's existing details screen.
 * No object writes, archive decisions or record eligibility are made here.
 */
object ObjectDetailsUiText {
    fun heading(tag: String, fallback: String = "Obiekt"): String =
        tag.trim().ifEmpty { fallback }

    fun totalTimeLabel(duration: String): String = "Razem: " + duration.trim()

    fun emptyHistoryLabel(): String = "Brak zarejestrowanego czasu na tym obiekcie."

    fun legacyUnassignedLabel(): String =
        "Starsze zmiany bez etapów nie są przypisywane automatycznie do obiektu."

    fun archiveHint(archived: Boolean): String = if (archived)
        "Obiekt jest zarchiwizowany. Historia pracy i wcześniejsze wpisy pozostają dostępne."
    else
        "Archiwizacja usuwa obiekt z listy aktywnych. Historia pracy i wpisy pozostają dostępne."

    fun confirmationTitle(archived: Boolean): String =
        ObjectUiPresentation.archiveAction(archived)

    fun confirmationMessage(archived: Boolean): String =
        ObjectUiPresentation.archiveConfirmation(archived)
}
