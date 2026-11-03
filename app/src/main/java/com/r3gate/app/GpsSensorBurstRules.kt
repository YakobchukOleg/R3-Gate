package com.r3gate.app

/**
 * Sensor activity requests a temporary precise GPS burst, but repeated motion
 * cannot keep a high-power interval alive indefinitely while the phone moves.
 */
object GpsSensorBurstRules {
    const val REARM_MS=5*60_000L

    fun mayStart(nowMs:Long,lastStartMs:Long,currentBurstSinceMs:Long):Boolean {
        if(nowMs<=0L || lastStartMs>nowMs || currentBurstSinceMs>nowMs) return false
        if(currentBurstSinceMs>0L &&
            nowMs-currentBurstSinceMs<=GpsGeofenceProbeRules.PROBE_WINDOW_MS) return false
        return lastStartMs<=0L || nowMs-lastStartMs>=REARM_MS
    }
}
