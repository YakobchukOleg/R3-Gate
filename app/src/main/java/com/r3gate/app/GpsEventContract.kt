package com.r3gate.app

/** Metadata shared with Core. The existing journal identity and time fields remain stable. */
object GpsEventContract {
    enum class Confidence { LOW, MEDIUM, HIGH }

    fun confidence(kind: String): Confidence? = when (kind) {
        "GPS_GAP", "LONG_STOP" -> Confidence.LOW
        "TRIP_START", "TRIP_STOP", "OBJECT_VISIT" -> Confidence.MEDIUM
        "ARRIVAL", "DEPARTURE" -> Confidence.HIGH
        else -> null
    }

    fun metadata(
        kind: String,
        timestampMs: Long,
        evidenceStartMs: Long?,
        tripId: String? = null
    ): Map<String, Any> {
        val level = confidence(kind) ?: return emptyMap()
        require(timestampMs > 0L)
        return buildMap {
            put("timestampMs", timestampMs)
            put("source", "gps-v1")
            put("confirmation", "PROVISIONAL")
            put("confidence", level.name)
            if (evidenceStartMs != null && evidenceStartMs in 1..timestampMs) {
                put("evidenceStartMs", evidenceStartMs)
                put("evidenceEndMs", timestampMs)
            }
            if (!tripId.isNullOrBlank()) put("tripId", tripId)
        }
    }
}
