package com.r3gate.app

/**
 * Select at most one actionable GPS suggestion for the current manual work
 * stage. GPS evidence is never a command to change a stage.
 *
 * Multiple equally recent, different destinations are ambiguous: neither is
 * shown. Older competing suggestions are not shown after a newer one has been
 * selected. This also makes journal replay order irrelevant.
 */
object CoreGpsStageReviewQueue {
    data class Selection(
        val event: CoreGpsProposalInbox.Event?,
        val eligibleCount: Int,
        val ambiguous: Boolean
    )

    /** A notification tap must still refer to the newest unambiguous suggestion. */
    fun isCurrent(
        eventId: String,
        events: Collection<CoreGpsProposalInbox.Event>,
        shift: CoreGpsStageRules.Shift,
        activeObjectIds: Set<String>,
        currentStageStartMs: Long,
        nowMs: Long
    ): Boolean = eventId.isNotBlank() &&
        choose(events, shift, activeObjectIds, currentStageStartMs, nowMs)
            .event?.eventId == eventId

    fun choose(
        events: Collection<CoreGpsProposalInbox.Event>,
        shift: CoreGpsStageRules.Shift,
        activeObjectIds: Set<String>,
        currentStageStartMs: Long,
        nowMs: Long
    ): Selection {
        val eligible = events.filter {
            it.confidence == CoreGpsProposalInbox.Confidence.HIGH &&
                CoreGpsStageRules.shouldNotify(
                    it, shift, activeObjectIds, currentStageStartMs, nowMs
                )
        }.distinctBy { it.eventId }
        if (eligible.isEmpty()) return Selection(null, 0, false)
        val latest = eligible.maxOf { it.timestampMs }
        val recent = eligible.filter { it.timestampMs == latest }
        // A single accepted event ID may be replayed; two different events
        // at the same timestamp cannot safely determine one destination.
        if (recent.map { it.eventId }.distinct().size != 1)
            return Selection(null, eligible.size, true)
        return Selection(recent.single(), eligible.size, false)
    }
}
