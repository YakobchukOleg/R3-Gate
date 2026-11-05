package com.r3gate.app.backup

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream

class RestoreSummaryTest {
    private fun summary(json: String, current: String = "{}"): RestoreSummary {
        val output = ByteArrayOutputStream()
        val references = PhotoArchiveJournal.references(json)
        PhotoArchivePackage.write(json, references, { byteArrayOf(1).inputStream() }, output)
        val bytes = output.toByteArray()
        val verified = PhotoArchivePackage.verifyComplete({ bytes.inputStream() }, references)
        return RestoreSummary.from(RestorePlan.prepare({ bytes.inputStream() }, current), verified.photoCount)
    }

    @Test fun countsWorkAndFinancialRecordsFromIncomingJournal() {
        val result = summary("""{"shifts":[{},{}],"active":{},"materials":[{}],"notes":[{},{}],"payments":[{}],"expenses":[{},{},{}]}""")
        assertEquals(2, result.closedShifts)
        assertTrue(result.activeShift)
        assertEquals(1, result.materials)
        assertEquals(2, result.notes)
        assertEquals(1, result.payments)
        assertEquals(3, result.expenses)
    }

    @Test fun archivedObjectsAreIncludedInObjectCount() {
        val result = summary("""{"objects":[{"id":"old","archived":true},{"id":"current"}]}""")
        assertEquals(2, result.objects)
        assertEquals(1, result.archivedObjects)
    }

    @Test fun countsOnlyVerifiedPhotosAndReferenceWarnings() {
        val result = summary("""{"shifts":[{"objectId":"missing"}],"links":[{"photos":["file:///one.jpg","file:///one.jpg"]}]}""")
        assertEquals(1, result.photos)
        assertEquals(1, result.referenceWarnings)
    }

    @Test fun emptyJournalHasNoRecordsOrReplacement() {
        val result = summary("{}")
        assertFalse(result.replacesJournal)
        assertFalse(result.activeShift)
        assertEquals(0, result.closedShifts + result.photos + result.payments + result.expenses)
    }

    @Test fun differentJournalRequiresReplacement() {
        val result = summary("""{"notes":[{}]}""")
        assertTrue(result.replacesJournal)
    }
}
