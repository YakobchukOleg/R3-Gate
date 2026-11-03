package com.r3gate.app.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class PhotoArchiveJournalTest {
    private inline fun rejected(block: () -> Unit) {
        try { block(); fail("Invalid photo journal accepted") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun nestedRecordPhotosAndDuplicatesAreIncludedOnce() {
        val journal = """{"links":[{"photos":["content://one","content://two"]}],
            "materials":[{"batch":[{"photos":["content://two","content://three"]}]}],
            "notes":[{"photos":["content://four"]}],"active":{"photos":["content://five"]}}"""
        assertEquals(listOf("content://one", "content://two", "content://three",
            "content://four", "content://five"), PhotoArchiveJournal.references(journal))
    }

    @Test fun rejectsMalformedPhotoArray() {
        rejected { PhotoArchiveJournal.references("""{"links":[{"photos":"not an array"}]}""") }
        rejected { PhotoArchiveJournal.references("""{"materials":[{"photos":[null]}]}""") }
    }

    @Test fun archivedObjectsKeepUnchangedJournalAndPhotos() {
        val journal = """{"objects":[{"id":"a","archived":true}],
            "links":[{"objectId":"a","photos":["content://image"]}]}"""
        val output = ByteArrayOutputStream()
        PhotoArchivePackage.write(journal, PhotoArchiveJournal.references(journal),
            { ByteArrayInputStream(byteArrayOf(1, 2, 3)) }, output)
        val archive = output.toByteArray()
        val report = PhotoArchivePackage.verifyComplete(
            { ByteArrayInputStream(archive) }, PhotoArchiveJournal.references(journal))
        assertEquals(1, report.photoCount)
        assertTrue(report.complete)
    }
}
