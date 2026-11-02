package com.r3gate.app.backup

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class ArchivePreflightTest {
    private fun archive(vararg files: Pair<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        WorkMeterArchive.write(output, files.map { (name, value) ->
            WorkMeterArchive.Source(name) { ByteArrayInputStream(value.toByteArray(Charsets.UTF_8)) }
        })
        return output.toByteArray()
    }
    private fun rejected(block: () -> Unit) {
        try { block(); fail("Expected preflight rejection") }
        catch (_: IllegalArgumentException) { }
    }
    @Test fun previewPreservesArchivedObjectsAndPolishNames() {
        val json = """{"objects":[{"id":"Łódź-01","archived":true}],"shifts":[{"objectId":"Łódź-01"}]}"""
        val bytes = archive(ArchivePreflight.JOURNAL to json)
        val preview = ArchivePreflight.verify { ByteArrayInputStream(bytes) }
        assertEquals(1, preview.version)
        assertEquals(1, preview.entries)
        assertEquals(json, preview.journalJson)
    }
    @Test fun noJournalRejectedBeforeRestore() {
        val bytes = archive("unrelated.txt" to "hello")
        rejected { ArchivePreflight.verify { ByteArrayInputStream(bytes) } }
    }
    @Test fun rejectsChangedJournalBetweenReads() {
        val first = archive(ArchivePreflight.JOURNAL to """{"a":1}""")
        val second = archive(ArchivePreflight.JOURNAL to """{"b":2}""")
        var count = 0
        rejected { ArchivePreflight.verify { ByteArrayInputStream(if (++count == 1) first else second) } }
    }
    @Test fun rejectsNonObjectJournal() {
        val bytes = archive(ArchivePreflight.JOURNAL to "[1,2,3]")
        rejected { ArchivePreflight.verify { ByteArrayInputStream(bytes) } }
    }
    @Test fun damagedArchiveRejected() {
        val bytes = archive(ArchivePreflight.JOURNAL to """{"id":"abc"}""")
        rejected { ArchivePreflight.verify { ByteArrayInputStream(bytes.copyOf(20)) } }
    }
    @Test fun duplicateObjectIdsPreventPreflight() {
        val bytes = archive(ArchivePreflight.JOURNAL to """{"objects":[{"id":"x"},{"id":"x"}]}""")
        rejected { ArchivePreflight.verify { ByteArrayInputStream(bytes) } }
    }
    @Test fun orphanedHistoricalReferencesAreVisibleInPreview() {
        val bytes = archive(ArchivePreflight.JOURNAL to """{"objects":[],"shifts":[{"objectId":"old-object"}]}""")
        val result = ArchivePreflight.verify { ByteArrayInputStream(bytes) }
        assertTrue(result.validation.canRestore)
        assertEquals(1, result.validation.warnings.size)
    }
    @Test fun malformedJsonWithObjectBracesCannotPassPreflight() {
        val bytes = archive(ArchivePreflight.JOURNAL to """{"objects":[}""")
        rejected { ArchivePreflight.verify { ByteArrayInputStream(bytes) } }
    }

}
