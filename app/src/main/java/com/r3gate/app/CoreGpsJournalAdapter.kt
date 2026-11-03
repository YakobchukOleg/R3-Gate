package com.r3gate.app

/**
 * Adapter for the durable `gps_events` journal. Only settled geofence
 * ARRIVAL/DEPARTURE records qualify as work-stage suggestions.
 *
 * A conflicting reused event ID is ambiguous and suppressed for this batch;
 * the GPS journal and confirmed work records are never edited.
 */
object CoreGpsJournalAdapter {
    data class Raw(val event: String, val timeMs: Long, val objectId: String?, val eventId: String?)

    fun eventId(kind: String, timeMs: Long, objectId: String): String =
        "gps-v1:${kind.length}:$kind:$timeMs:${objectId.length}:$objectId"

    fun convert(
        journal: Collection<Raw>,
        shift: CoreGpsStageRules.Shift,
        knownActiveObjectIds: Set<String>,
        stageStartMs: Long,
        nowMs: Long
    ): List<CoreGpsProposalInbox.Event> {
        val observed = journal.mapNotNull { raw ->
            val kind = when(raw.event) {
                "ARRIVAL" -> CoreGpsProposalInbox.Kind.ARRIVAL
                "DEPARTURE" -> CoreGpsProposalInbox.Kind.DEPARTURE
                else -> return@mapNotNull null
            }
            val objectId = raw.objectId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val eventId = raw.eventId?.takeIf { it.isNotBlank() }
                ?: eventId(raw.event, raw.timeMs, objectId)
            val candidate = CoreGpsProposalInbox.Event(
                eventId, kind, raw.timeMs, objectId,
                confidence = CoreGpsProposalInbox.Confidence.HIGH
            )
            candidate.takeIf {
                CoreGpsStageRules.shouldNotify(it, shift, knownActiveObjectIds, stageStartMs, nowMs)
            }
        }
        // Identical replays are fine, but a reused ID with different evidence
        // must never be used to select a worksite or departure.
        return observed.groupBy { it.eventId }.values.mapNotNull { versions ->
            val single=versions.first()
            single.takeIf { versions.all { it == single } }
        }.sortedWith(compareBy<CoreGpsProposalInbox.Event> { it.timestampMs }.thenBy { it.eventId })
    }
}
