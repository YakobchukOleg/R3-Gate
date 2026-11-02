package com.r3gate.app

/**
 * Suspicious GPS teleports are not stationary evidence either.
 * Reject them before advancing zone dwell or trip state.
 */
object GpsMotionOutlierRules {
    fun impossibleJump(previous:GpsWorkRules.Point,current:GpsWorkRules.Point):Boolean {
        if(!GpsMotionContinuityRules.contiguous(previous.timestampMs,current.timestampMs))
            return false
        if(listOf(previous,current).any {
                !it.latitude.isFinite() || it.latitude !in -90.0..90.0 ||
                !it.longitude.isFinite() || it.longitude !in -180.0..180.0 ||
                !it.accuracyM.isFinite() || it.accuracyM<=0.0 || it.accuracyM>100.0
            }) return false
        val seconds=(current.timestampMs-previous.timestampMs)/1000.0
        // Sub-five-second fixes can still reveal a teleport; only sub-second
        // intervals are too short for a dependable speed estimate.
        if(seconds<1.0) return false
        val distance=GpsWorkRules.distanceM(previous.latitude,previous.longitude,
            current.latitude,current.longitude)
        val minimumDisplacement=(distance-previous.accuracyM-current.accuracyM).coerceAtLeast(0.0)
        return minimumDisplacement/seconds>GpsWorkRules.MAX_PLAUSIBLE_GROUND_SPEED_MPS
    }
}
