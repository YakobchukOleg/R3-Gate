package com.r3gate.app

/**
 * UI copy and grouping for the existing object form.
 * Saving, tag generation and validation remain in MainActivity/DomainRules.
 */
object ObjectFormUiModel {
    data class Field(val key: String, val caption: String, val required: Boolean, val multiline: Boolean = false)
    data class Section(val title: String, val fields: List<Field>)

    val sections = listOf(
        Section("Lokalizacja", listOf(
            Field("city", "Miasto *", true),
            Field("Adres", "Adres *", true)
        )),
        Section("Informacje o obiekcie", listOf(
            Field("organization", "Nazwa organizacji", false),
            Field("notes", "Notatki / kontakt", false, multiline = true),
            Field("maps", "Link Google Maps (opcjonalnie)", false)
        ))
    )

    fun title(editing: Boolean) = if (editing) "Edytuj obiekt" else "Nowy obiekt"
    fun tagHint(tag: String): String =
        if (tag.isBlank()) "TAG zostanie nadany automatycznie po zapisaniu miasta."
        else "TAG: " + tag.trim() + " (stały)"

    fun requiredHint() = "* Pola wymagane"
    fun mapHint() = "Link musi zaczynać się od https://"
    fun footer(editing: Boolean) =
        if (editing) "Zmiany dotyczą tylko tego obiektu. Historia pracy pozostaje zachowana."
        else "Po zapisaniu obiekt będzie dostępny na liście aktywnych."
}
