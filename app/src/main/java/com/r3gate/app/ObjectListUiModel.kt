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
    data class Row(
        val id: String, val title: String, val status: String,
        val accessibilityLabel: String, val city: String = "",
        val tag: String = "", val name: String = "",
        val addressLine: String = "", val organization: String = ""
    )
    data class Display(val rows: List<Row>, val emptyMessage: String?)
    data class Group(val heading: String, val rows: List<Row>)

    fun display(items: List<Item>, showArchived: Boolean, query: String = "", city: String? = null): Display {
        val rows = items.asSequence()
            .filter { it.archived == showArchived }
            .filter { city == null || it.city.equals(city, ignoreCase = true) }
            .filter { item ->
                query.isBlank() || listOf(
                    item.tag, item.name, item.organization, item.city, item.address
                ).any { ObjectSearch.matches(it, query) }
            }
            .sortedWith { a, b -> ObjectSearch.compareTags(a.tag, b.tag).takeIf { it != 0 } ?: a.id.compareTo(b.id) }
            .map {
                val title = ObjectUiPresentation.title(it.tag, it.organization, it.city, it.address)
                Row(it.id, title, ObjectUiPresentation.status(it.archived),
                    ObjectUiPresentation.accessibilityLabel(title, it.archived), it.city.trim(),
                    it.tag.trim(), it.name.trim().ifEmpty { it.organization.trim() }
                        .ifEmpty { it.tag.trim() }.ifEmpty { "Obiekt" },
                    listOf(it.city.trim(), it.address.trim()).filter(String::isNotBlank)
                        .joinToString(", "), it.organization.trim())
            }.toList()
        val message = if (rows.isNotEmpty()) null
            else if (query.isNotBlank() || city != null) "Brak obiektów dla wybranych filtrów."
            else ObjectUiPresentation.emptyList(showArchived)
        return Display(rows, message)
    }
    /**
     * The HTML object-list prototype groups active results by city.
     * Archived results remain in one flat archive section.
     */
    fun groups(display: Display, groupByCity: Boolean, showArchived: Boolean): List<Group> {
        if (display.rows.isEmpty()) return emptyList()
        if (!groupByCity || showArchived) return listOf(Group("Obiekty", display.rows))
        return display.rows.groupBy { it.city.ifEmpty { "Bez miasta" } }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
            .map { (city, rows) -> Group(city, rows) }
    }

    /** Cities available as prototype filter chips, excluding archived records. */
    fun activeCities(items: List<Item>): List<String> =
        items.asSequence().filterNot { it.archived }.map { it.city.trim() }
            .filter { it.isNotBlank() }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER).toList()

    /** Count of visible records for the current archive/search/city combination. */
    fun resultCount(items: List<Item>, showArchived: Boolean, query: String = "", city: String? = null): Int =
        display(items, showArchived, query, city).rows.size

}
