package com.r3gate.app

/** Protects travel and arrival detection from cached or delayed Android location callbacks. */
object GpsLocationQuality {
    const val MAX_AGE_MS=120_000L
    const val MAX_FUTURE_SKEW_MS=10_000L
    fun acceptable(nowMs:Long,fixTimeMs:Long,accuracyM:Double,latitude:Double,longitude:Double):Boolean {
        if(nowMs<=0 || fixTimeMs<=0) return false
        // Compare positive deltas; nowMs + skew can overflow near Long.MAX_VALUE.
        if(fixTimeMs>nowMs) {
            if(fixTimeMs-nowMs>MAX_FUTURE_SKEW_MS) return false
        } else if(nowMs-fixTimeMs>MAX_AGE_MS) return false
        return accuracyM.isFinite() && accuracyM>0 && accuracyM<=100 &&
            latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0
    }
}
