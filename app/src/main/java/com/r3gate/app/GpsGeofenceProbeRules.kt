package com.r3gate.app

/** A one-shot 90s burst for new arrival, confirmed departure, or uncertain borders. */
object GpsGeofenceProbeRules {
    const val PROBE_INTERVAL_MS=15_000L
    const val PROBE_WINDOW_MS=90_000L
    fun interval(nowMs:Long,probeSinceMs:Long,normalMs:Long):Long {
        if(nowMs<=0 || probeSinceMs<=0 || probeSinceMs>nowMs ||
            nowMs-probeSinceMs>PROBE_WINDOW_MS) return normalMs
        return minOf(normalMs,PROBE_INTERVAL_MS)
    }
    /** No rearming while evidence remains ambiguous: avoids indefinite high-power polling. */
    fun shouldBegin(nowMs:Long,lastProbeMs:Long,needsEvidence:Boolean,hasRelevantZone:Boolean):Boolean =
        needsEvidence && hasRelevantZone && nowMs>0L && lastProbeMs<=0L
    fun resolved(uncertain:Boolean,candidateSinceMs:Long):Boolean =
        !uncertain && candidateSinceMs<=0L
}
