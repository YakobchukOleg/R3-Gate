package com.r3gate.app

/**
 * Restore the configured GPS cadence when a temporary border-check burst expires,
 * even when no fresh location callback arrives.
 */
object GpsProbeCadenceRules {
    data class Change(val update:Boolean,val intervalMs:Long)

    fun evaluate(
        nowMs:Long,
        probeSinceMs:Long,
        baselineMs:Long,
        subscribedIntervalMs:Long,
        trackingEnabled:Boolean
    ):Change {
        if(!trackingEnabled || nowMs<=0L || baselineMs<=0L)
            return Change(false,subscribedIntervalMs)
        val desired=GpsGeofenceProbeRules.interval(nowMs,probeSinceMs,baselineMs)
        return Change(desired!=subscribedIntervalMs,desired)
    }
}
