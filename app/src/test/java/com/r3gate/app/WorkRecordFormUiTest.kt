package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class WorkRecordFormUiTest {
    @Test fun recognizesOnlySupportedForms() {
        assertEquals(WorkRecordFormUi.Kind.MATERIALS, WorkRecordFormUi.kind("materials"))
        assertEquals(WorkRecordFormUi.Kind.PHOTOS, WorkRecordFormUi.kind("links"))
        assertEquals(WorkRecordFormUi.Kind.NOTES, WorkRecordFormUi.kind("notes"))
        assertNull(WorkRecordFormUi.kind("objects"))
        assertNull(WorkRecordFormUi.kind("expenses"))
        assertNull(WorkRecordFormUi.kind("unknown"))
    }

    @Test fun eachFormHasDistinctHeading() {
        assertEquals(3, WorkRecordFormUi.Kind.values().map { WorkRecordFormUi.title(it) }.distinct().size)
    }

    @Test fun assignmentAndDetailsSectionsAreNonemptyForEveryForm() {
        for (kind in WorkRecordFormUi.Kind.values()) {
            assertEquals("Przypisanie", WorkRecordFormUi.assignment(kind).title)
            assertTrue(WorkRecordFormUi.assignment(kind).hint.isNotBlank())
            assertTrue(WorkRecordFormUi.details(kind).title.isNotBlank())
            assertTrue(WorkRecordFormUi.details(kind).hint.isNotBlank())
        }
    }

    @Test fun notesCanRemainUnassigned() {
        assertTrue(WorkRecordFormUi.assignment(WorkRecordFormUi.Kind.NOTES).hint.contains("bez obiektu"))
    }

    @Test fun photosSupportGalleryCameraAndDrive() {
        val hint=WorkRecordFormUi.details(WorkRecordFormUi.Kind.PHOTOS).hint
        assertTrue(hint.contains("galerii"))
        assertTrue(hint.contains("Google Drive"))
    }

    @Test fun materialsExplainQuantityRules() {
        val hint=WorkRecordFormUi.details(WorkRecordFormUi.Kind.MATERIALS).hint
        assertTrue(hint.contains("całkowitej"))
        assertTrue(hint.contains("dziesiętnej"))
    }

    @Test fun attachmentStatusUsesRealCounts() {
        assertEquals("Brak dołączonych zdjęć", WorkRecordFormUi.attachments(0))
        assertEquals("1 dołączone zdjęcie", WorkRecordFormUi.attachments(1))
        assertEquals("4 dołączonych zdjęć", WorkRecordFormUi.attachments(4))
    }

    @Test fun batchCountDoesNotInventMaterialLines() {
        assertEquals("Materiały w zestawie: 0", WorkRecordFormUi.pendingBatch(0))
        assertEquals("Materiały w zestawie: 3", WorkRecordFormUi.pendingBatch(3))
    }

    @Test fun negativeCountsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { WorkRecordFormUi.attachments(-1) }
        assertThrows(IllegalArgumentException::class.java) { WorkRecordFormUi.pendingBatch(-2) }
    }
}
