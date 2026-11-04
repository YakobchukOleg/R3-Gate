package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class CoreGpsProducerMetadataTest {
    private val metadata = CoreGpsJournalAdapter.Metadata(
        3_000L, "gps-v1", "PROVISIONAL", CoreGpsProposalInbox.Confidence.HIGH, 2_000L, 3_000L
    )
    private val row = CoreGpsJournalAdapter.Raw("ARRIVAL", 3_000L, "site", "stable", metadata)
    private val shift = CoreGpsStageRules.Shift(true, "Przejazd", null)
    private val objects = setOf("site")
    private fun convert(row: CoreGpsJournalAdapter.Raw) =
        CoreGpsJournalAdapter.convert(listOf(row), shift, objects, 1_000L, 4_000L)

    @Test fun highConfidencePreservesEvidenceThroughApprovalGuard() {
        val candidate = convert(row).single()
        assertEquals(2_000L, candidate.evidenceStartMs)
        assertEquals(3_000L, candidate.evidenceEndMs)
        assertTrue(CoreGpsProposalEvidenceGuard.matches(candidate, listOf(row)))
        assertFalse(CoreGpsProposalEvidenceGuard.matches(candidate,
            listOf(row.copy(metadata = metadata.copy(evidenceStartMs = 2_100L)))))
    }

    @Test fun mediumConfidenceIsRetainedWithoutNotificationOrApplication() {
        val candidate = convert(row.copy(metadata = metadata.copy(
            confidence = CoreGpsProposalInbox.Confidence.MEDIUM))).single()
        assertFalse(CoreGpsStageRules.shouldNotify(candidate, shift, objects, 1_000L, 4_000L))
        assertFalse(CoreGpsStageRules.canApply(candidate, shift, objects))
        assertEquals(1, CoreGpsProposalInbox.import(emptyList(), listOf(candidate), objects).added)
    }

    @Test fun lowConfidenceDoesNotCreateAWorkProposal() {
        assertTrue(convert(row.copy(metadata = metadata.copy(
            confidence = CoreGpsProposalInbox.Confidence.LOW))).isEmpty())
    }

    @Test fun malformedMetadataRejectsBatchAndCannotBecomeAProposal() {
        val malformed = listOf(metadata.copy(timestampMs = 4_000L),
            metadata.copy(source = "other"), metadata.copy(confirmation = "USER_CONFIRMED"),
            metadata.copy(evidenceEndMs = 4_000L), metadata.copy(evidenceStartMs = null))
        for (bad in malformed) {
            val badRow = row.copy(metadata = bad)
            assertNull(CoreGpsJournalIntegrity.validated(listOf(row, badRow)))
            assertTrue(convert(badRow).isEmpty())
        }
    }

    @Test fun legacyJournalRetainsItsIdentityAndHighConfidence() {
        val old = row.copy(metadata = null)
        val candidate = convert(old).single()
        assertEquals("stable", candidate.eventId)
        assertEquals(CoreGpsProposalInbox.Confidence.HIGH, candidate.confidence)
        assertTrue(CoreGpsProposalEvidenceGuard.matches(candidate, listOf(old)))
    }

    @Test fun overflowingFloatingTimestampIsRejectedWithoutRejectingExactLong() {
        assertNull(CoreGpsJournalIntegrity.exactTime(Long.MAX_VALUE.toDouble()))
        assertEquals(Long.MAX_VALUE, CoreGpsJournalIntegrity.exactTime(Long.MAX_VALUE))
    }
}
