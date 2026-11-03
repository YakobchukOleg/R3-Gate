package com.r3gate.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ObjectUiPresentationTest {
    @Test fun titleOmitsBlankSegmentsAndPreservesOrder() {
        assertEquals("KOP-01 · Firma · Kopytkowo · 24",
            ObjectUiPresentation.title("KOP-01", "Firma", "Kopytkowo", "24"))
        assertEquals("KOP-01 · Kopytkowo",
            ObjectUiPresentation.title(" KOP-01 ", " ", " Kopytkowo ", ""))
        assertEquals("", ObjectUiPresentation.title("", "", "", ""))
    }

    @Test fun archiveStatusAndActionsArePolishAndDistinct() {
        assertEquals("Aktywny", ObjectUiPresentation.status(false))
        assertEquals("Zarchiwizowany", ObjectUiPresentation.status(true))
        assertEquals("Archiwizuj obiekt", ObjectUiPresentation.archiveAction(false))
        assertEquals("Przywróć obiekt", ObjectUiPresentation.archiveAction(true))
    }

    @Test fun confirmationsProtectHistoricalInformation() {
        assertEquals("Przywrócić obiekt do listy aktywnych?",
            ObjectUiPresentation.archiveConfirmation(true))
        assertEquals("Zarchiwizować obiekt? Historia pracy i wcześniejsze wpisy pozostaną dostępne.",
            ObjectUiPresentation.archiveConfirmation(false))
    }

    @Test fun accessibilityLabelReflectsArchiveState() {
        assertEquals("KOP-01, Aktywny", ObjectUiPresentation.accessibilityLabel("KOP-01", false))
        assertEquals("KOP-01, Zarchiwizowany", ObjectUiPresentation.accessibilityLabel("KOP-01", true))
        assertEquals("Zarchiwizowany", ObjectUiPresentation.accessibilityLabel("  ", true))
    }

    @Test fun emptyStatesDistinguishActiveFromArchived() {
        assertEquals("Brak zarchiwizowanych obiektów",
            ObjectUiPresentation.emptyList(true))
        assertEquals("Dodaj pierwszy obiekt, aby przypisywać pracę, materiały i zdjęcia.",
            ObjectUiPresentation.emptyList(false))
    }
}
