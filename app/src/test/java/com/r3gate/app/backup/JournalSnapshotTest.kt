package com.r3gate.app.backup

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class JournalSnapshotTest {
    private fun journalFromArchive(bytes: ByteArray): String {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            val entry = zip.nextEntry
            assertEquals(JournalSnapshot.ENTRY, entry.name)
            return zip.bufferedReader(Charsets.UTF_8).readText()
        }
    }

    @Test fun preservesObjectIdsAndArchivedObjectsExactly() {
        val json = """{"objects":[{"id":"obj-17","archived":true}],"shifts":[{"objectId":"obj-17"}],"stages":[{"objectId":"obj-17"}],"materials":[{"objectId":"obj-17"}],"photos":[{"objectId":"obj-17"}],"notes":[{"objectId":"obj-17"}]}"""
        val output = ByteArrayOutputStream()
        JournalSnapshot.write(json, output)
        assertEquals(json, journalFromArchive(output.toByteArray()))
        assertEquals(1, WorkMeterArchive.inspect(ByteArrayInputStream(output.toByteArray())).files.size)
    }

    @Test fun retainsPolishAndOldUnknownFields() {
        val json = """{"objects":[{"id":"Łódź-01","name":"Zażółć gęślą jaźń","legacyField":42}]}"""
        val output = ByteArrayOutputStream()
        JournalSnapshot.write(json, output)
        assertEquals(json, journalFromArchive(output.toByteArray()))
    }

    @Test fun rejectsNonObjectSnapshot() {
        for (value in listOf("", "[]", "null", "42")) {
            try {
                JournalSnapshot.write(value, ByteArrayOutputStream())
                fail("Should reject non-object journal")
            } catch (_: IllegalArgumentException) { }
        }
    }
}
