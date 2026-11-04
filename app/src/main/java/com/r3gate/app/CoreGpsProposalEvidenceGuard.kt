package com.r3gate.app

/**
 * Cross-check a persisted GPS proposal against the producer's current
 * durable journal. Neither an orphaned event nor a recycled identity may
 * authorize a transition, even if the local proposal inbox was restored.
 *
 * Only one exact evidence tuple is accepted; identical replays are harmless.
 * This has no effect on work records or user decisions.
 */
object CoreGpsProposalEvidenceGuard {
    fun matches(
        proposal: CoreGpsProposalInbox.Event,
        journal: Collection<CoreGpsJournalAdapter.Raw>
    ): Boolean {
        if (proposal.eventId.isBlank() || proposal.timestampMs <= 0L ||
            proposal.source != "gps-v1" || proposal.confidence != CoreGpsProposalInbox.Confidence.HIGH) return false
        val matches = journal.filter { raw ->
            val identity = raw.eventId?.takeIf { it.isNotBlank() }
                ?: raw.objectId?.takeIf { it.isNotBlank() }?.let {
                    CoreGpsJournalAdapter.eventId(raw.event, raw.timeMs, it)
                }
            identity == proposal.eventId
        }
        if (matches.isEmpty()) return false
        val expected = when (proposal.kind) {
            CoreGpsProposalInbox.Kind.ARRIVAL -> "ARRIVAL"
            CoreGpsProposalInbox.Kind.DEPARTURE -> "DEPARTURE"
            else -> return false
        }
        return matches.all {
            it.event == expected && it.timeMs == proposal.timestampMs &&
                it.objectId == proposal.objectId &&
                !it.objectId.isNullOrBlank() &&
                CoreGpsJournalIntegrity.validMetadata(it) && it.confidence == proposal.confidence &&
                it.metadata?.evidenceStartMs == proposal.evidenceStartMs &&
                it.metadata?.evidenceEndMs == proposal.evidenceEndMs
        }
    }
}
