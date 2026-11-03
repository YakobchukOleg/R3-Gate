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

    @Test fun groupsActiveObjectsByCityInAlphabeticalOrder() {
        val items = listOf(
            ObjectListUiModel.Item("t", "TCZ-01", "", "Tczew", "1", false),
            ObjectListUiModel.Item("g2", "GDA-10", "", "Gdańsk", "2", false),
            ObjectListUiModel.Item("g1", "GDA-02", "", "Gdańsk", "1", false)
        )
        val display = ObjectListUiModel.display(items, false)
        val groups = ObjectListUiModel.groups(display, groupByCity = true, showArchived = false)
        assertEquals(listOf("Gdańsk", "Tczew"), groups.map { it.heading })
        assertEquals(listOf("g1", "g2"), groups.first().rows.map { it.id })
        assertEquals(3, display.rows.size)
    }

    @Test fun archiveAndUngroupedModesUseSingleSection() {
        val items = listOf(
            ObjectListUiModel.Item("a", "TCZ-01", "", "Tczew", "1", true),
            ObjectListUiModel.Item("b", "GDA-01", "", "Gdańsk", "1", true)
        )
        val display = ObjectListUiModel.display(items, true)
        val grouped = ObjectListUiModel.groups(display, groupByCity = true, showArchived = true)
        assertEquals(listOf("Obiekty"), grouped.map { it.heading })
        assertEquals(2, grouped.single().rows.size)
        assertEquals(listOf("Obiekty"),
            ObjectListUiModel.groups(display, groupByCity = false, showArchived = true).map { it.heading })
    }

    @Test fun emptyFilteredResultsContainNoGroups() {
        val display = ObjectListUiModel.display(emptyList(), false, "Gdańsk")
        assertTrue(ObjectListUiModel.groups(display, true, false).isEmpty())
    }

    @Test fun missingCityHasPolishFallbackGroup() {
        val display = ObjectListUiModel.display(
            listOf(ObjectListUiModel.Item("x", "OBJ-01", "", "", "1", false)), false)
        assertEquals("Bez miasta", ObjectListUiModel.groups(display, true, false).single().heading)
    }

    @Test fun cityChipsExcludeArchivedObjectsAndSortCities() {
        val items = listOf(
            ObjectListUiModel.Item("a", "A-01", "", "Tczew", "", false),
            ObjectListUiModel.Item("b", "A-02", "", "Gdańsk", "", false),
            ObjectListUiModel.Item("c", "A-03", "", "Tczew", "", false),
            ObjectListUiModel.Item("d", "A-04", "", "Łódź", "", true),
            ObjectListUiModel.Item("e", "A-05", "", " ", "", false)
        )
        assertEquals(listOf("Gdańsk", "Tczew"), ObjectListUiModel.activeCities(items))
    }

    @Test fun cityChipAndTextSearchCombineWithoutMutatingRecords() {
        val items = listOf(
            ObjectListUiModel.Item("a", "TCZ-01", "Firma", "Tczew", "Pierwsza 1", false),
            ObjectListUiModel.Item("b", "TCZ-02", "Firma", "Tczew", "Druga 2", false),
            ObjectListUiModel.Item("c", "GDA-01", "Firma", "Gdańsk", "Pierwsza 1", false)
        )
        assertEquals(listOf("a"), ObjectListUiModel.display(items, false, "pierwsza", "Tczew").rows.map { it.id })
        assertEquals(1, ObjectListUiModel.resultCount(items, false, "pierwsza", "Tczew"))
        assertEquals(3, items.size)
    }

    @Test fun archivedEntriesCannotLeakThroughCityChip() {
        val items = listOf(
            ObjectListUiModel.Item("a", "A-01", "", "Tczew", "", true),
            ObjectListUiModel.Item("b", "A-02", "", "Tczew", "", false)
        )
        assertEquals(listOf("b"), ObjectListUiModel.display(items, false, city = "Tczew").rows.map { it.id })
        assertEquals(listOf("a"), ObjectListUiModel.display(items, true, city = "Tczew").rows.map { it.id })
    }

    @Test fun emptyAndUnknownCityFiltersAreSafe() {
        assertTrue(ObjectListUiModel.activeCities(emptyList()).isEmpty())
        assertEquals("Brak obiektów dla wybranych filtrów.",
            ObjectListUiModel.display(emptyList(), true, city = "Tczew").emptyMessage)
        assertEquals(0, ObjectListUiModel.resultCount(emptyList(), false))
    }

    @Test fun cardPresentationHasTitleAddressAndCompanyFromPrototype() {
        val item = ObjectListUiModel.Item("id", "TCZ-02", "Serwis", "Tczew",
            "Czyżykowska 17", false, name = "Zespół Szkół Branżowych")
        val row = ObjectListUiModel.display(listOf(item), false).rows.single()
        assertEquals("TCZ-02", row.tag)
        assertEquals("Zespół Szkół Branżowych", row.name)
        assertEquals("Tczew, Czyżykowska 17", row.addressLine)
        assertEquals("Serwis", row.organization)
        assertEquals("TCZ-02 · Serwis · Tczew · Czyżykowska 17", row.title)
    }

    @Test fun cardNameFallbackNeverGoesBlank() {
        val namedByOrganization = ObjectListUiModel.Item("1", "TCZ-01", "Firma", "", "", false)
        assertEquals("Firma", ObjectListUiModel.display(listOf(namedByOrganization), false).rows.single().name)
        val namedByTag = ObjectListUiModel.Item("2", "TCZ-02", "", "", "", false)
        assertEquals("TCZ-02", ObjectListUiModel.display(listOf(namedByTag), false).rows.single().name)
        val nameless = ObjectListUiModel.Item("3", "", "", "", "", false)
        assertEquals("Obiekt", ObjectListUiModel.display(listOf(nameless), false).rows.single().name)
    }

    @Test fun addressLineDoesNotShowEmptySeparators() {
        val row = ObjectListUiModel.display(listOf(
            ObjectListUiModel.Item("x", "A-01", "", "Tczew", "", false)), false).rows.single()
        assertEquals("Tczew", row.addressLine)
    }

    @Test fun emptyCityChipIsAFilteredEmptyState() {
        val item = ObjectListUiModel.Item("x", "A-01", "", "Gdańsk", "", false)
        val display = ObjectListUiModel.display(listOf(item), false, city = "Tczew")
        assertEquals("Brak obiektów dla wybranych filtrów.", display.emptyMessage)
    }

}
