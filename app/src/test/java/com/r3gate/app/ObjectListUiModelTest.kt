package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class ObjectListUiModelTest {
    private fun item(id: String, tag: String, archived: Boolean) =
        ObjectListUiModel.Item(id, tag, "Firma", "Gdańsk", "12", archived)

    @Test fun activeListExcludesArchivedAndUsesNumericTagOrdering() {
        val result = ObjectListUiModel.display(listOf(
            item("old", "GDA-02", true),
            item("ten", "GDA-10", false),
            item("two", "GDA-02", false)
        ), showArchived = false)
        assertEquals(listOf("two", "ten"), result.rows.map { it.id })
        assertEquals("GDA-02 · Firma · Gdańsk · 12", result.rows.first().title)
        assertEquals("Aktywny", result.rows.first().status)
        assertEquals("GDA-02 · Firma · Gdańsk · 12, Aktywny", result.rows.first().accessibilityLabel)
        assertNull(result.emptyMessage)
    }

    @Test fun archiveListShowsOnlyArchivedObjects() {
        val result = ObjectListUiModel.display(listOf(
            item("active", "GDA-01", false),
            item("archived", "GDA-02", true)
        ), showArchived = true)
        assertEquals(listOf("archived"), result.rows.map { it.id })
        assertEquals("Zarchiwizowany", result.rows.single().status)
    }

    @Test fun emptyStatesExplainTheSelectedList() {
        assertEquals("Brak zarchiwizowanych obiektów",
            ObjectListUiModel.display(listOf(item("a", "GDA-01", false)), true).emptyMessage)
        assertEquals("Dodaj pierwszy obiekt, aby przypisywać pracę, materiały i zdjęcia.",
            ObjectListUiModel.display(emptyList(), false).emptyMessage)
    }

    @Test fun duplicateTagsKeepStableIdOrderingWithoutMutatingInput() {
        val original = listOf(item("z", "GDA-01", false), item("a", "GDA-01", false))
        assertEquals(listOf("a", "z"), ObjectListUiModel.display(original, false).rows.map { it.id })
        assertEquals(listOf("z", "a"), original.map { it.id })
    }
    @Test fun cityFilterIgnoresAccentsCaseAndWhitespace() {
        val gdansk = item("gdansk", "GDA-01", false)
        val tczew = ObjectListUiModel.Item("tczew", "TCZ-01", "", "Tczew", "1", false)
        val rows = ObjectListUiModel.display(listOf(gdansk, tczew), false, "  GDANSK ").rows
        assertEquals(listOf("gdansk"), rows.map { it.id })
    }

    @Test fun cityFilterDoesNotRevealArchivedObjects() {
        val rows = ObjectListUiModel.display(listOf(
            item("archived", "GDA-01", true),
            item("active", "GDA-02", false)
        ), false, "Gdańsk").rows
        assertEquals(listOf("active"), rows.map { it.id })
    }

    @Test fun unmatchedCityUsesExistingEmptyStateMessage() {
        val result = ObjectListUiModel.display(
            listOf(item("gdansk", "GDA-01", false)), false, "Warszawa")
        assertTrue(result.rows.isEmpty())
        assertEquals("Brak obiektów dla wybranych filtrów.", result.emptyMessage)
    }

    @Test fun searchesNameCompanyAddressAndTagFromHtmlPrototype() {
        val hospital = ObjectListUiModel.Item(
            "h", "GDA-07", "Serwis", "Gdańsk", "Grunwaldzka 32", false,
            name = "Poradnia Lekarza")
        for (query in listOf("poradnia", "SERWIS", "grunwaldzka", "gda-07")) {
            assertEquals(query, listOf("h"),
                ObjectListUiModel.display(listOf(hospital), false, query).rows.map { it.id })
        }
    }

    @Test fun archiveSearchDoesNotLeakActiveResults() {
        val archived = ObjectListUiModel.Item(
            "archive", "KOP-01", "Firma", "Łódź", "Główna 1", true)
        val active = ObjectListUiModel.Item(
            "active", "KOP-02", "Firma", "Łódź", "Główna 2", false)
        assertEquals(listOf("archive"),
            ObjectListUiModel.display(listOf(active, archived), true, "lodz").rows.map { it.id })
        assertEquals(listOf("active"),
            ObjectListUiModel.display(listOf(active, archived), false, "lodz").rows.map { it.id })
    }

    @Test fun blankSearchKeepsDistinctArchiveEmptyState() {
        val result = ObjectListUiModel.display(emptyList(), true, "   ")
        assertEquals("Brak zarchiwizowanych obiektów", result.emptyMessage)
    }

}
