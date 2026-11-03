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
    @Test fun malformedTopLevelArraysMustBlockRestore() {
        for (key in listOf("objects", "shifts", "materials", "photos", "links",
            "notes", "logs", "payments", "expenses")) {
            val report = JournalValidator.inspect("""{"$key":{}}""")
            assertFalse("$key silently accepted", report.canRestore)
        }
    }

    @Test fun malformedActiveAndStagesMustBlockRestore() {
        assertFalse(JournalValidator.inspect("""{"active":"broken"}""").canRestore)
        val invalidStages = """{"objects":[],"shifts":[{"stages":"wrong"}]}"""
        assertFalse(JournalValidator.inspect(invalidStages).canRestore)
    }

    @Test fun emptyLegitimateArraysRemainSupported() {
        val report = JournalValidator.inspect("""{"objects":[],"shifts":[],"materials":[]}""")
        assertTrue(report.canRestore)
    }

    @Test fun numericOrNullObjectIdsCannotBeSilentlyCoerced() {
        for (raw in listOf("42", "null", "true", "{}")) {
            val report = JournalValidator.inspect("""{"objects":[{"id":$raw}]}""")
            assertFalse("id=$raw silently accepted", report.canRestore)
        }
    }

    @Test fun invalidArchivedFlagCannotBeSilentlyCoerced() {
        for (raw in listOf("\"true\"", "1", "null")) {
            val report = JournalValidator.inspect("""{"objects":[{"id":"obj","archived":$raw}]}""")
            assertFalse("archived=$raw silently accepted", report.canRestore)
        }
    }

    @Test fun mistypedReferencesFailInsteadOfBeingOrphanWarnings() {
        for (raw in listOf("123", "null", "false", "[]")) {
            val report = JournalValidator.inspect("""{"objects":[{"id":"obj"}],"shifts":[{"objectId":$raw}]}""")
            assertFalse("objectId=$raw silently accepted", report.canRestore)
        }
    }

    @Test fun stringReferencesAndMissingOptionalFieldsRemainCompatible() {
        val report = JournalValidator.inspect("""{"objects":[{"id":"007"},{"id":"archive","archived":true}],"shifts":[{"objectId":"007"},{}]}""")
        assertTrue(report.errors.toString(), report.canRestore)
        assertEquals(1, report.archivedCount)
        assertEquals(1, report.referenceCount)
    }

    @Test fun malformedTopLevelStagesBlockRestore() {
        val malformed = JournalValidator.inspect("""{"stages":{"objectId":"a"}}""")
        assertFalse(malformed.canRestore)
    }

    @Test fun topLevelStagesAndExpenseReferencesAreChecked() {
        val report = JournalValidator.inspect("""{"objects":[{"id":"a"}],"stages":[{"objectId":"a"}],"payments":[{"objectId":"a"}],"expenses":[{"objectId":"missing"}]}""")
        assertTrue(report.canRestore)
        assertEquals(3, report.referenceCount)
        assertEquals(1, report.warnings.size)
    }

    @Test fun malformedExpenseEntriesBlockRestore() {
        val report = JournalValidator.inspect("""{"expenses":[42],"payments":[null]}""")
        assertFalse(report.canRestore)
        assertTrue(report.errors.any { it.contains("expenses[0]") })
    }

}
