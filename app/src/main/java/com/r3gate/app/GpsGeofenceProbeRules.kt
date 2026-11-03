package com.r3gate.app

/** Ask for a quicker follow-up at uncertain borders, never infer entry or exit. */
object GpsGeofenceProbeRules {
    const val PROBE_INTERVAL_MS=15_000L
    const val PROBE_WINDOW_MS=90_000L
    const val MIN_REARM_MS=30_000L
    fun interval(nowMs:Long,ambiguousSinceMs:Long,normalMs:Long):Long {
        if(nowMs<=0 || ambiguousSinceMs<=0 || ambiguousSinceMs>nowMs ||
            nowMs-ambiguousSinceMs>PROBE_WINDOW_MS) return normalMs
        return minOf(normalMs,PROBE_INTERVAL_MS)
    }
    fun shouldBegin(nowMs:Long,lastProbeMs:Long,uncertain:Boolean,hasConfirmedZone:Boolean):Boolean =
        uncertain && hasConfirmedZone && nowMs>0 &&
            (lastProbeMs<=0 || nowMs>=lastProbeMs && nowMs-lastProbeMs>=MIN_REARM_MS)
}
