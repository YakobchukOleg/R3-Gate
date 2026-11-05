package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class ObjectFilterUiStateTest {
    @Test fun archiveSwitchClearsCitySelection() {
        val original=ObjectFilterUiState.State(city="Tczew")
        val archive=ObjectFilterUiState.toggleArchive(original)
        assertTrue(archive.archived)
        assertNull(archive.city)
        assertFalse(original.archived)
    }

    @Test fun switchingBackKeepsCityClear() {
        val state=ObjectFilterUiState.toggleArchive(
            ObjectFilterUiState.State(archived=true))
        assertFalse(state.archived)
        assertNull(state.city)
    }

    @Test fun archivedModeNeverAcceptsCityChip() {
        val state=ObjectFilterUiState.city(
            ObjectFilterUiState.State(archived=true),"Gdańsk")
        assertNull(state.city)
    }

    @Test fun citySelectionNormalizesEmptyAndWhitespace() {
        val state=ObjectFilterUiState.State()
        assertEquals("Tczew",ObjectFilterUiState.city(state," Tczew ").city)
        assertNull(ObjectFilterUiState.city(state,"  ").city)
        assertNull(ObjectFilterUiState.city(state,null).city)
    }

    @Test fun staleChipClearsWhenDataChanges() {
        val state=ObjectFilterUiState.State(city="Łódź")
        assertNull(ObjectFilterUiState.restore(state,listOf("Tczew")).city)
    }

    @Test fun caseOnlyCityChangeDoesNotResetChip() {
        val state=ObjectFilterUiState.State(city="GDAŃSK")
        assertEquals("GDAŃSK",
            ObjectFilterUiState.restore(state,listOf("Gdańsk")).city)
    }

    @Test fun groupingSwitchDoesNotClearCity() {
        val state=ObjectFilterUiState.toggleGrouping(
            ObjectFilterUiState.State(city="Tczew"))
        assertFalse(state.grouped)
        assertEquals("Tczew",state.city)
    }

    @Test fun archiveRestoreDiscardsPreviouslySavedCity() {
        val state=ObjectFilterUiState.restore(
            ObjectFilterUiState.State(archived=true,city="Tczew"),listOf("Tczew"))
        assertNull(state.city)
    }

    @Test fun statusExplainsVisibleFilter() {
        assertEquals("Wszystkie miasta",ObjectFilterUiState.status(ObjectFilterUiState.State()))
        assertEquals("Miasto: Gdańsk",ObjectFilterUiState.status(ObjectFilterUiState.State(city="Gdańsk")))
        assertEquals("Archiwum",ObjectFilterUiState.status(ObjectFilterUiState.State(archived=true)))
    }
}
