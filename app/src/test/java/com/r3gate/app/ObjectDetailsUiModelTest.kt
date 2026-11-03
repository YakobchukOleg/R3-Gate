package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class ObjectDetailsUiModelTest {
    private fun content(
        archived: Boolean = false,
        days: List<ObjectDetailsUiModel.Day> = emptyList(),
        legacy: Boolean = false
    ) = ObjectDetailsUiModel.Content(
        tag = " TCZ-01 ", name = " Szkoła ", city = " Tczew ",
        address = " Długa 3 ", organization = "Serwis",
        notes = " Notatka ", archived = archived,
        duration = "2 h 30 min", days = days,
        hasUnassignedLegacyShifts = legacy
    )

    @Test fun presentsObjectMetadataWithoutExtraWhitespace() {
        val model = ObjectDetailsUiModel.display(content())
        assertEquals("TCZ-01", model.heading)
        assertEquals("Szkoła", model.title)
        assertEquals("Tczew, Długa 3", model.location)
        assertEquals("Serwis", model.organization)
        assertEquals("Notatka", model.notes)
        assertEquals("Razem: 2 h 30 min", model.totalTime)
    }

    @Test fun showsEmptyHistoryOnlyWhenNoDaysExist() {
        val blank = ObjectDetailsUiModel.display(content())
        assertEquals(ObjectDetailsUiText.emptyHistoryLabel(), blank.emptyHistory)
        val populated = ObjectDetailsUiModel.display(content(
            days = listOf(ObjectDetailsUiModel.Day("03.11.2026", "1 h"))
        ))
        assertNull(populated.emptyHistory)
        assertEquals("1 h", populated.days.single().duration)
    }

    @Test fun archivedStatePreservesHistoryAndShowsArchiveMessage() {
        val model = ObjectDetailsUiModel.display(content(archived = true))
        assertEquals("Zarchiwizowany", model.archiveStatus)
        assertEquals(ObjectDetailsUiText.archiveHint(true), model.archiveHint)
    }

    @Test fun legacyStageWarningOnlyWhenNeeded() {
        assertNull(ObjectDetailsUiModel.display(content()).legacyNote)
        assertEquals(ObjectDetailsUiText.legacyUnassignedLabel(),
            ObjectDetailsUiModel.display(content(legacy = true)).legacyNote)
    }

    @Test fun blankMetadataFallsBackToUsefulHeading() {
        val blank = ObjectDetailsUiModel.Content(
            "", "", "", "", "", " ", false, "0 min", emptyList(), false
        )
        val model = ObjectDetailsUiModel.display(blank)
        assertEquals("Obiekt", model.heading)
        assertEquals("Obiekt", model.title)
        assertNull(model.location)
        assertNull(model.organization)
        assertNull(model.notes)
    }

    @Test fun viewModelDoesNotMutateHistoryInput() {
        val days = mutableListOf(ObjectDetailsUiModel.Day("03.11.2026", "5 min"))
        val model = ObjectDetailsUiModel.display(content(days = days))
        days.add(ObjectDetailsUiModel.Day("02.11.2026", "10 min"))
        assertEquals(1, model.days.size)
    }
}
