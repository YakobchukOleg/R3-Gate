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
        val archived: Boolean
    )
    data class Row(val id: String, val title: String, val status: String, val accessibilityLabel: String)
    data class Display(val rows: List<Row>, val emptyMessage: String?)

    fun display(items: List<Item>, showArchived: Boolean): Display {
        val rows = items.asSequence()
            .filter { it.archived == showArchived }
            .sortedWith { a, b -> ObjectSearch.compareTags(a.tag, b.tag).takeIf { it != 0 } ?: a.id.compareTo(b.id) }
            .map {
                val title = ObjectUiPresentation.title(it.tag, it.organization, it.city, it.address)
                Row(it.id, title, ObjectUiPresentation.status(it.archived),
                    ObjectUiPresentation.accessibilityLabel(title, it.archived))
            }.toList()
        return Display(rows, if (rows.isEmpty()) ObjectUiPresentation.emptyList(showArchived) else null)
    }
}
