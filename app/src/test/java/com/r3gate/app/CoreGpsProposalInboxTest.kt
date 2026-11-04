package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class CoreGpsProposalInboxTest {
    private fun event(
        id: String, time: Long = 100L,
        objectId: String? = "object-1",
        kind: CoreGpsProposalInbox.Kind = CoreGpsProposalInbox.Kind.ARRIVAL
    ) = CoreGpsProposalInbox.Event(
        eventId = id, kind = kind, timestampMs = time, objectId = objectId,
        confidence = CoreGpsProposalInbox.Confidence.HIGH
    )
    private val objects = setOf("object-1", "object-2")

    @Test fun duplicateDeliveryDoesNotCreateSecondProposal() {
        val first = event("a")
        val result = CoreGpsProposalInbox.import(emptyList(), listOf(first, first), objects)
        assertEquals(1, result.events.size)
        assertEquals(1, result.added)
        assertEquals(1, result.ignoredDuplicates)
        assertEquals(0, result.rejectedConflicts)
    }

    @Test fun confirmedAndRejectedDecisionsSurviveReplay() {
        val confirmed = CoreGpsProposalInbox.decide(
            listOf(event("a")), "a", CoreGpsProposalInbox.Decision.USER_CONFIRMED
        )
        val rejected = CoreGpsProposalInbox.decide(
            listOf(event("b")), "b", CoreGpsProposalInbox.Decision.USER_REJECTED
        )
        val result = CoreGpsProposalInbox.import(confirmed + rejected, listOf(event("b"), event("a")), objects)
        assertEquals(0, result.added)
        assertEquals(2, result.ignoredDuplicates)
        assertEquals(CoreGpsProposalInbox.Decision.USER_CONFIRMED, result.events[0].decision)
        assertEquals(CoreGpsProposalInbox.Decision.USER_REJECTED, result.events[1].decision)
    }

    @Test fun conflictingEvidenceCannotOverwriteStableEventId() {
        val previous = event("a", objectId = "object-1")
        val changed = event("a", objectId = "object-2")
        val result = CoreGpsProposalInbox.import(listOf(previous), listOf(changed), objects)
        assertEquals(1, result.rejectedConflicts)
        assertEquals(previous, result.events.single())
    }

    @Test fun unknownObjectAndInvalidTimeRemainOutOfInbox() {
        val invalid = listOf(event("a", time = 0L), event("b", objectId = "missing"), event(""))
        val result = CoreGpsProposalInbox.import(emptyList(), invalid, objects)
        assertTrue(result.events.isEmpty())
        assertEquals(3, result.rejectedInvalid)
    }

    @Test fun gpsGapCanBeStoredWithoutAnObjectAndDoesNotCreateWorkTime() {
        val gap = event("gap", 500L, null, CoreGpsProposalInbox.Kind.GPS_GAP)
        val result = CoreGpsProposalInbox.import(emptyList(), listOf(gap), objects)
        assertEquals(gap, result.events.single())
        assertEquals(CoreGpsProposalInbox.Decision.PROVISIONAL, result.events.single().decision)
    }

    @Test fun evidenceBoundsAndSourceAreValidated() {
        val sample = event("a")
        assertFalse(CoreGpsProposalInbox.valid(sample.copy(evidenceStartMs = 90L), objects))
        assertFalse(CoreGpsProposalInbox.valid(sample.copy(evidenceStartMs = 120L, evidenceEndMs = 100L), objects))
        assertFalse(CoreGpsProposalInbox.valid(sample.copy(source = "unknown"), objects))
        assertTrue(CoreGpsProposalInbox.valid(sample.copy(evidenceStartMs = 90L, evidenceEndMs = 100L), objects))
    }

    @Test fun incomingOrderIsDeterministicAndDoesNotChangeDecisions() {
        val result = CoreGpsProposalInbox.import(emptyList(),
            listOf(event("b", 200L), event("z", 100L), event("a", 100L)), objects)
        assertEquals(listOf("a", "z", "b"), result.events.map { it.eventId })
    }

    @Test fun onlyExplicitDecisionCanResolveAProvisionalProposal() {
        val proposal = event("a")
        val rejected = CoreGpsProposalInbox.decide(listOf(proposal), "a", CoreGpsProposalInbox.Decision.USER_REJECTED)
        val second = CoreGpsProposalInbox.decide(rejected, "a", CoreGpsProposalInbox.Decision.USER_CONFIRMED)
        assertEquals(CoreGpsProposalInbox.Decision.USER_REJECTED, second.single().decision)
        assertEquals(CoreGpsProposalInbox.Decision.PROVISIONAL, proposal.decision)
    }
}
