package com.r3gate.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ObjectDetailsUiTextTest {
    @Test fun headingFallsBackForMissingTag() {
        assertEquals("Obiekt", ObjectDetailsUiText.heading("  "))
        assertEquals("GDA-12", ObjectDetailsUiText.heading(" GDA-12 "))
    }

    @Test fun totalTimeRetainsProvidedDuration() {
        assertEquals("Razem: 12 h 30 min", ObjectDetailsUiText.totalTimeLabel(" 12 h 30 min "))
    }

    @Test fun legacyHistoryCopyExplainsMissingAssignment() {
        assertEquals("Starsze zmiany bez etapów nie są przypisywane automatycznie do obiektu.",
            ObjectDetailsUiText.legacyUnassignedLabel())
        assertEquals("Brak zarejestrowanego czasu na tym obiekcie.",
            ObjectDetailsUiText.emptyHistoryLabel())
    }

    @Test fun archivedStateShowsHistoryPreservation() {
        assertEquals("Obiekt jest zarchiwizowany. Historia pracy i wcześniejsze wpisy pozostają dostępne.",
            ObjectDetailsUiText.archiveHint(true))
        assertEquals("Archiwizacja usuwa obiekt z listy aktywnych. Historia pracy i wpisy pozostają dostępne.",
            ObjectDetailsUiText.archiveHint(false))
    }

    @Test fun confirmationLabelsRemainConsistentWithObjectList() {
        for (archived in listOf(false, true)) {
            assertEquals(ObjectUiPresentation.archiveAction(archived),
                ObjectDetailsUiText.confirmationTitle(archived))
            assertEquals(ObjectUiPresentation.archiveConfirmation(archived),
                ObjectDetailsUiText.confirmationMessage(archived))
        }
    }
}
