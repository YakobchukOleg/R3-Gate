package com.r3gate.app.backup

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream

class JournalCompatibilityTest {
    @Test fun rejectsFutureJournalAndBackupVersions() {
        for (json in listOf("""{"schemaVersion":3}""", """{"wtBackupVersion":2}""")) {
            assertFalse(JournalValidator.inspect(json).canRestore)
        }
    }

    @Test fun rejectsNonNumericFractionalOrNegativeVersions() {
        for (version in listOf("\"2\"", "2.1", "null", "-1", "{}")) {
            assertFalse(JournalValidator.inspect("""{"schemaVersion":$version}""").canRestore)
        }
    }

    @Test fun keepsSupportedAndUnversionedLegacyJournals() {
        for (json in listOf("{}", """{"schemaVersion":0}""", """{"schemaVersion":1}""",
            """{"schemaVersion":2,"wtBackupVersion":1}""")) {
            assertTrue(JournalValidator.inspect(json).canRestore)
        }
    }

    @Test fun validatesActiveShiftAndItsStages() {
        val valid = JournalValidator.inspect("""{"objects":[{"id":"site"}],"active":{"objectId":"site","stages":[{"objectId":"site"}]}}""")
        assertTrue(valid.canRestore)
        assertEquals(2, valid.referenceCount)
        for (active in listOf("""{"objectId":12}""", """{"stages":{}}""", """{"stages":[null]}""")) {
            assertFalse(JournalValidator.inspect("""{"active":$active}""").canRestore)
        }
    }

    @Test fun warnsAboutActiveShiftOrphanWithoutChangingJournal() {
        val json = """{"active":{"objectId":"old","stages":[{"objectId":"missing"}]}}"""
        val report = JournalValidator.inspect(json)
        assertTrue(report.canRestore)
        assertEquals(2, report.warnings.size)
    }

    @Test fun warnsAboutExplicitDuplicateRecordIdsInEachCollection() {
        for (key in listOf("shifts", "stages", "materials", "links", "notes", "payments", "expenses")) {
            val json = """{"$key":[{"id":"same"},{"id":"same"}]}"""
            val report = JournalValidator.inspect(json)
            assertTrue(report.canRestore)
            assertTrue(report.warnings.any { it.contains("Duplicate record ID in $key") })
        }
    }

    @Test fun missingLegacyIdsAndIdsInDifferentCollectionsStayCompatible() {
        val report = JournalValidator.inspect("""{"payments":[{},{}],"expenses":[{"id":"same"}],"shifts":[{"id":"same"}]}""")
        assertTrue(report.canRestore)
        assertTrue(report.warnings.isEmpty())
    }

    @Test fun duplicateStageAndClosedActiveShiftProduceWarnings() {
        val report = JournalValidator.inspect("""{"active":{"id":"shift","stages":[{"id":"stage"},{"id":"stage"}]},"shifts":[{"id":"shift"}]}""")
        assertTrue(report.canRestore)
        assertEquals(2, report.warnings.size)
    }

    @Test fun previewKeepsDuplicateFinancialRecordsForExplicitReview() {
        val json = """{"payments":[{"id":"same","amount":"10.25"},{"id":"same","amount":"20.75"}]}"""
        val out = ByteArrayOutputStream()
        JournalSnapshot.write(json, out)
        val plan = RestorePlan.prepare({ out.toByteArray().inputStream() }, "{}")
        assertEquals(json, plan.journalJson)
        assertTrue(plan.warnings.isNotEmpty())
        assertEquals(json, RestorePlan.confirmedReplacement(plan, "{}", true))
    }
}
