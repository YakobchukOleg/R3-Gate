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
}
