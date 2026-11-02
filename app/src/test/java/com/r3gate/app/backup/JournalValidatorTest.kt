package com.r3gate.app.backup

import org.junit.Assert.*
import org.junit.Test

class JournalValidatorTest {
    @Test fun archivedObjectAndStageReferencesRemainValid() {
        val json = """{"objects":[{"id":"obj-1","archived":true}],"shifts":[{"objectId":"obj-1","stages":[{"objectId":"obj-1"}]}],"materials":[{"objectId":"obj-1"}]}"""
        val report = JournalValidator.inspect(json)
        assertTrue(report.canRestore)
        assertEquals(1, report.archivedCount)
        assertEquals(3, report.referenceCount)
        assertTrue(report.warnings.isEmpty())
    }
    @Test fun duplicateObjectIdsBlockRestore() {
        val report = JournalValidator.inspect("""{"objects":[{"id":"a"},{"id":"a"}]}""")
        assertFalse(report.canRestore)
        assertTrue(report.errors.any { it.contains("Duplicate") })
    }
    @Test fun orphanLinksAreReportedWithoutDeletion() {
        val report = JournalValidator.inspect("""{"objects":[],"shifts":[{"objectId":"old-1"}],"photos":[{"objectId":"old-1"}]}""")
        assertTrue(report.canRestore)
        assertEquals(2, report.warnings.size)
    }
    @Test fun invalidJsonIsRejected() {
        assertFalse(JournalValidator.inspect("""{"objects":[""").canRestore)
    }
    @Test fun malformedStageBlocksRestore() {
        val report = JournalValidator.inspect("""{"objects":[{"id":"a"}],"shifts":[{"stages":[null]}]}""")
        assertFalse(report.canRestore)
    }
}
