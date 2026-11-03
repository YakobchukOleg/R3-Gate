package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class CoreGpsProposalEvidenceGuardTest {
    private fun proposal(
        id: String = "stable", time: Long = 1000L,
        site: String = "site-a",
        kind: CoreGpsProposalInbox.Kind = CoreGpsProposalInbox.Kind.ARRIVAL
    ) = CoreGpsProposalInbox.Event(
        id, kind, time, site,
        confidence = CoreGpsProposalInbox.Confidence.HIGH
    )
    private fun row(
        id: String? = "stable", time: Long = 1000L,
        site: String? = "site-a", kind: String = "ARRIVAL"
    ) = CoreGpsJournalAdapter.Raw(kind,time,site,id)

    @Test fun exactStoredArrivalMatchesCurrentJournal() {
        assertTrue(CoreGpsProposalEvidenceGuard.matches(proposal(),listOf(row())))
    }
    @Test fun identicalJournalReplayIsAllowed() {
        assertTrue(CoreGpsProposalEvidenceGuard.matches(proposal(),listOf(row(),row(),row())))
    }
    @Test fun missingJournalCannotConfirmWorkStage() {
        assertFalse(CoreGpsProposalEvidenceGuard.matches(proposal(),emptyList()))
    }
    @Test fun differentJournalIdentityDoesNotMatch() {
        assertFalse(CoreGpsProposalEvidenceGuard.matches(proposal(),listOf(row(id="other"))))
    }
    @Test fun changedArrivalDestinationBlocksReview() {
        assertFalse(CoreGpsProposalEvidenceGuard.matches(proposal(),listOf(row(site="site-b"))))
    }
    @Test fun changedTimestampBlocksReview() {
        assertFalse(CoreGpsProposalEvidenceGuard.matches(proposal(),listOf(row(time=1001))))
    }
    @Test fun recycledIdBetweenArrivalAndDepartureIsBlocked() {
        assertFalse(CoreGpsProposalEvidenceGuard.matches(proposal(),listOf(row(kind="DEPARTURE"))))
    }
    @Test fun mixedValidAndConflictingDestinationIsBlocked() {
        assertFalse(CoreGpsProposalEvidenceGuard.matches(proposal(),
            listOf(row(),row(site="site-b"))))
    }
    @Test fun mixedValidAndConflictingTimestampIsBlocked() {
        assertFalse(CoreGpsProposalEvidenceGuard.matches(proposal(),
            listOf(row(),row(time=1001))))
    }
    @Test fun mixedValidAndTripStopIsBlocked() {
        assertFalse(CoreGpsProposalEvidenceGuard.matches(proposal(),
            listOf(row(),row(kind="TRIP_STOP"))))
    }
    @Test fun validArrivalAndUnrelatedJournalIdDoNotConflict() {
        assertTrue(CoreGpsProposalEvidenceGuard.matches(proposal(),
            listOf(row(),row(id="other",site="site-b"))))
    }
    @Test fun missingStoredIdentityIsNeverAccepted() {
        assertFalse(CoreGpsProposalEvidenceGuard.matches(proposal(id=""),listOf(row(id=""))))
    }
    @Test fun zeroTimestampIsNeverAccepted() {
        assertFalse(CoreGpsProposalEvidenceGuard.matches(proposal(time=0),listOf(row(time=0))))
    }
    @Test fun invalidSourceCannotBeAccepted() {
        assertFalse(CoreGpsProposalEvidenceGuard.matches(
            proposal().copy(source="another"),listOf(row())))
    }
    @Test fun tripStartCannotAuthorizeStageTransition() {
        assertFalse(CoreGpsProposalEvidenceGuard.matches(
            proposal(kind=CoreGpsProposalInbox.Kind.TRIP_START),listOf(row(kind="TRIP_START"))))
    }
    @Test fun gapCannotAuthorizeStageTransition() {
        assertFalse(CoreGpsProposalEvidenceGuard.matches(
            proposal(kind=CoreGpsProposalInbox.Kind.GPS_GAP),listOf(row(kind="GPS_GAP"))))
    }
    @Test fun matchingDepartureCanAuthorizeReview() {
        assertTrue(CoreGpsProposalEvidenceGuard.matches(
            proposal(kind=CoreGpsProposalInbox.Kind.DEPARTURE),
            listOf(row(kind="DEPARTURE"))))
    }
    @Test fun missingObjectInJournalBlocksReview() {
        assertFalse(CoreGpsProposalEvidenceGuard.matches(proposal(),listOf(row(site=null))))
    }
    @Test fun blankObjectInJournalBlocksReview() {
        assertFalse(CoreGpsProposalEvidenceGuard.matches(proposal(),listOf(row(site=""))))
    }
    @Test fun generatedLegacyIdentityMatchesConsistently() {
        val id=CoreGpsJournalAdapter.eventId("ARRIVAL",1000,"site-a")
        assertTrue(CoreGpsProposalEvidenceGuard.matches(proposal(id=id),listOf(row(id=null))))
    }
    @Test fun legacyIdentityWithDifferentObjectCannotBeReused() {
        val id=CoreGpsJournalAdapter.eventId("ARRIVAL",1000,"site-a")
        assertFalse(CoreGpsProposalEvidenceGuard.matches(proposal(id=id),listOf(row(id=null,site="site-b"))))
    }
    @Test fun producerJournalTruncationInvalidatesOldProposal() {
        assertFalse(CoreGpsProposalEvidenceGuard.matches(proposal(),listOf(
            row(id="latest-1",time=2000),row(id="latest-2",time=2100))))
    }
    @Test fun journalOrderDoesNotChangeMatchResult() {
        val entries=listOf(row(id="old",time=900),row(),row(id="new",time=1100))
        assertTrue(CoreGpsProposalEvidenceGuard.matches(proposal(),entries))
        assertTrue(CoreGpsProposalEvidenceGuard.matches(proposal(),entries.reversed()))
    }
    @Test fun conflictingReplayOrderDoesNotChangeRejection() {
        val entries=listOf(row(),row(site="site-b"))
        assertFalse(CoreGpsProposalEvidenceGuard.matches(proposal(),entries))
        assertFalse(CoreGpsProposalEvidenceGuard.matches(proposal(),entries.reversed()))
    }
}
