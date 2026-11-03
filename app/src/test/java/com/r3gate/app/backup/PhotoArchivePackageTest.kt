package com.r3gate.app.backup

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class PhotoArchivePackageTest {
    private val journal = """{"objects":[{"id":"obj1","archived":true}],"links":[{"objectId":"obj1","photos":["content://a","content://b"]}]}"""
    private val photos = listOf("content://a", "content://b")
    private fun backup(refs: List<String> = photos): ByteArray {
        val out = ByteArrayOutputStream()
        PhotoArchivePackage.write(journal, refs, { uri ->
            ByteArrayInputStream(("PHOTO:" + uri).toByteArray(Charsets.UTF_8))
        }, out)
        return out.toByteArray()
    }
    private inline fun rejected(action: () -> Unit) {
        try { action(); fail("Invalid photo backup accepted") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun archivesAllPhotosWithJournalAndIndex() {
        val bytes = backup()
        val checked = PhotoArchivePackage.verifyComplete({ ByteArrayInputStream(bytes) }, photos)
        assertTrue(checked.complete)
        assertEquals(2, checked.photoCount)
        assertFalse(checked.legacy)
    }

    @Test fun duplicateReferencesOnlyProduceOneFile() {
        val bytes = backup(listOf(photos[0], photos[0], photos[1]))
        assertEquals(4, WorkMeterArchive.inspect(ByteArrayInputStream(bytes)).files.size)
    }

    @Test fun missingPhotoIsDetectedWithoutSilentlyDroppingIt() {
        val bytes = backup(listOf(photos[0]))
        val inspection = PhotoArchivePackage.inspect({ ByteArrayInputStream(bytes) }, photos)
        assertFalse(inspection.complete)
        assertEquals(listOf("content://b"), inspection.missing)
        rejected { PhotoArchivePackage.verifyComplete({ ByteArrayInputStream(bytes) }, photos) }
    }

    @Test fun legacyJournalOnlyBackupIsIdentifiedAsIncomplete() {
        val out = ByteArrayOutputStream()
        JournalSnapshot.write(journal, out)
        val bytes = out.toByteArray()
        val report = PhotoArchivePackage.inspect({ ByteArrayInputStream(bytes) }, photos)
        assertTrue(report.legacy)
        assertFalse(report.complete)
        assertEquals(2, report.missing.size)
    }

    @Test fun unreadableSourceNeverCreatesSuccessfulBackup() {
        try {
            PhotoArchivePackage.write(journal, photos, { throw java.io.FileNotFoundException("Missing") },
                ByteArrayOutputStream())
            fail("Unreadable photo accepted")
        } catch (_: java.io.FileNotFoundException) { }
    }

    @Test fun rejectsCorruptContainerAndPhotoBytes() {
        val bytes = backup()
        rejected { PhotoArchivePackage.verifyComplete({ ByteArrayInputStream(bytes.copyOf(bytes.size / 2)) }, photos) }
        val damaged = bytes.copyOf()
        val index = damaged.indexOfFirst { it.toInt() == 'P'.code }
        if (index >= 0) damaged[index] = (damaged[index].toInt() xor 0x01).toByte()
        rejected { PhotoArchivePackage.verifyComplete({ ByteArrayInputStream(damaged) }, photos) }
    }
}
