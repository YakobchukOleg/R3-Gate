package com.r3gate.app

/**
 * Read-only list presentation for the Obiekty screen.
 * Core remains responsible for object lifecycle, persistence and eligibility.
 */
object ObjectListUiModel {
    data class Item(
        val id: String,
        val tag: String,
        val organization: String,
        val city: String,
        val address: String,
        val archived: Boolean,
        val name: String = ""
    )
    data class Row(val id: String, val title: String, val status: String, val accessibilityLabel: String)
    data class Display(val rows: List<Row>, val emptyMessage: String?)

    fun display(items: List<Item>, showArchived: Boolean, query: String = ""): Display {
        val rows = items.asSequence()
            .filter { it.archived == showArchived }
            .filter { item ->
                query.isBlank() || listOf(
                    item.tag, item.name, item.organization, item.city, item.address
                ).any { ObjectSearch.matches(it, query) }
            }
            .sortedWith { a, b -> ObjectSearch.compareTags(a.tag, b.tag).takeIf { it != 0 } ?: a.id.compareTo(b.id) }
            .map {
                val title = ObjectUiPresentation.title(it.tag, it.organization, it.city, it.address)
                Row(it.id, title, ObjectUiPresentation.status(it.archived),
                    ObjectUiPresentation.accessibilityLabel(title, it.archived))
            }.toList()
        val message = if (rows.isNotEmpty()) null
            else if (query.isNotBlank()) "Brak obiektów dla wybranych filtrów."
            else ObjectUiPresentation.emptyList(showArchived)
        return Display(rows, message)
    }
}
