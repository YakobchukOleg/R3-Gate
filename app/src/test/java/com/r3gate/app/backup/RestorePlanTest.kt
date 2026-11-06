package com.r3gate.app.backup

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class RestorePlanTest {
    private fun backup(json: String): ByteArray {
        val output = ByteArrayOutputStream()
        JournalSnapshot.write(json, output)
        return output.toByteArray()
    }

    @Test fun replacementRequiresConfirmation() {
        val old = """{"objects":[{"id":"old"}]}"""
        val newer = """{"objects":[{"id":"new","archived":true}],"shifts":[{"objectId":"new"}]}"""
        val bytes = backup(newer)
        val plan = RestorePlan.prepare({ ByteArrayInputStream(bytes) }, old)
        assertEquals(RestorePlan.Decision.REPLACE_REQUIRES_CONFIRMATION, plan.decision)
        assertEquals(1, plan.archivedObjects)
        try {
            RestorePlan.confirmedReplacement(plan, old, false)
            fail("Unconfirmed replacement accepted")
        } catch (_: IllegalArgumentException) { }
        assertEquals(newer, RestorePlan.confirmedReplacement(plan, old, true))
    }

    @Test fun concurrentLocalEditBlocksReplacement() {
        val old = """{"objects":[]}"""
        val bytes = backup("""{"objects":[{"id":"x"}]}""")
        val plan = RestorePlan.prepare({ ByteArrayInputStream(bytes) }, old)
        try {
            RestorePlan.confirmedReplacement(plan, """{"objects":[{"id":"local"}]}""", true)
            fail("Stale preview accepted")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun repeatedRestoreDoesNotRequestDuplicates() {
        val data = """{"objects":[{"id":"x","archived":true}]}"""
        val bytes = backup(data)
        val plan = RestorePlan.prepare({ ByteArrayInputStream(bytes) }, data)
        assertEquals(RestorePlan.Decision.NO_CHANGES, plan.decision)
        assertEquals(data, RestorePlan.confirmedReplacement(plan, data, true))
    }

    @Test fun invalidArchiveNeverProducesRestorePlan() {
        try {
            RestorePlan.prepare({ ByteArrayInputStream(byteArrayOf(1, 2, 3)) }, """{"objects":[]}""")
            fail("Corrupted archive accepted")
        } catch (_: Exception) { }
    }
}
