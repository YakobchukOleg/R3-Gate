package com.r3gate.app

/**
 * Adapter for the durable `gps_events` journal. Only settled geofence
 * ARRIVAL/DEPARTURE records qualify as work-stage suggestions.
 *
 * A conflicting reused event ID is ambiguous and suppressed for this batch;
 * the GPS journal and confirmed work records are never edited.
 */
object CoreGpsJournalAdapter {
    data class Metadata(
        val timestampMs: Long,
        val source: String,
        val confirmation: String,
        val confidence: CoreGpsProposalInbox.Confidence,
        val evidenceStartMs: Long?,
        val evidenceEndMs: Long?,
        val tripId: String? = null
    )

    data class Raw(
        val event: String, val timeMs: Long, val objectId: String?, val eventId: String?,
        val metadata: Metadata? = null
    ) {
        val confidence: CoreGpsProposalInbox.Confidence
            get() = metadata?.confidence ?: CoreGpsProposalInbox.Confidence.HIGH
    }

    fun eventId(kind: String, timeMs: Long, objectId: String): String =
        "gps-v1:${kind.length}:$kind:$timeMs:${objectId.length}:$objectId"

    /** Travel and visit evidence is retained for review without authorizing work changes. */
    fun observations(
        journal: Collection<Raw>, shiftStartMs: Long, nowMs: Long
    ): List<CoreGpsProposalInbox.Event> {
        if (shiftStartMs <= 0L) return emptyList()
        val supported = setOf(CoreGpsProposalInbox.Kind.TRIP_START, CoreGpsProposalInbox.Kind.TRIP_STOP,
            CoreGpsProposalInbox.Kind.OBJECT_VISIT, CoreGpsProposalInbox.Kind.GPS_GAP)
        return journal.groupBy { it.eventId ?: eventId(it.event, it.timeMs, it.objectId.orEmpty()) }
            .mapNotNull { (id, matches) ->
                val raw = matches.distinct().singleOrNull() ?: return@mapNotNull null
                val kind = supported.firstOrNull { it.name == raw.event } ?: return@mapNotNull null
                if (id.isBlank() || raw.timeMs !in shiftStartMs..nowMs ||
                    !CoreGpsJournalIntegrity.validMetadata(raw)) return@mapNotNull null
                val objectId = raw.objectId?.takeIf { it.isNotBlank() }
                if (kind == CoreGpsProposalInbox.Kind.OBJECT_VISIT && objectId == null) return@mapNotNull null
                val confidence = raw.metadata?.confidence ?: CoreGpsProposalInbox.Confidence.MEDIUM
                if (kind != CoreGpsProposalInbox.Kind.GPS_GAP && confidence == CoreGpsProposalInbox.Confidence.LOW)
                    return@mapNotNull null
                CoreGpsProposalInbox.Event(id,kind,raw.timeMs,objectId,raw.metadata?.tripId,
                    if (kind == CoreGpsProposalInbox.Kind.GPS_GAP) CoreGpsProposalInbox.Confidence.LOW else confidence,
                    evidenceStartMs = raw.metadata?.evidenceStartMs,
                    evidenceEndMs = raw.metadata?.evidenceEndMs)
            }.sortedWith(compareBy<CoreGpsProposalInbox.Event> { it.timestampMs }.thenBy { it.eventId })
    }

    fun convert(
        journal: Collection<Raw>,
        shift: CoreGpsStageRules.Shift,
        knownActiveObjectIds: Set<String>,
        stageStartMs: Long,
        nowMs: Long
    ): List<CoreGpsProposalInbox.Event> {
        // Check identity collisions before stage/object filtering: otherwise an
        // ineligible conflicting row could conceal an ambiguous event ID.
        val keyed = journal.map { raw ->
            val id = raw.eventId?.takeIf { it.isNotBlank() }
                ?: raw.objectId?.takeIf { it.isNotBlank() }?.let {
                    eventId(raw.event, raw.timeMs, it)
                }
            id to raw
        }
        val candidates = keyed.filter { it.first != null }.groupBy { it.first!! }
            .values.mapNotNull { entries ->
                val first = entries.first().second
                if (entries.any { it.second != first }) null else entries.first()
            }
        return candidates.mapNotNull { (identity, raw) ->
            val kind = when (raw.event) {
                "ARRIVAL" -> CoreGpsProposalInbox.Kind.ARRIVAL
                "DEPARTURE" -> CoreGpsProposalInbox.Kind.DEPARTURE
                else -> return@mapNotNull null
            }
            val objectId = raw.objectId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            if (!CoreGpsJournalIntegrity.validMetadata(raw) ||
                raw.confidence == CoreGpsProposalInbox.Confidence.LOW ||
                CoreGpsStageRules.proposal(kind, objectId, shift, knownActiveObjectIds) == null ||
                stageStartMs <= 0L || raw.timeMs !in stageStartMs..nowMs ||
                nowMs - raw.timeMs > CoreGpsStageRules.MAX_REVIEW_AGE_MS) return@mapNotNull null
            CoreGpsProposalInbox.Event(
                identity!!, kind, raw.timeMs, objectId,
                confidence = raw.confidence,
                evidenceStartMs = raw.metadata?.evidenceStartMs,
                evidenceEndMs = raw.metadata?.evidenceEndMs
            )
        }.sortedWith(compareBy<CoreGpsProposalInbox.Event> { it.timestampMs }.thenBy { it.eventId })
    }
}
