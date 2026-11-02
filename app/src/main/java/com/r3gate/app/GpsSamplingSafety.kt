package com.r3gate.app

/** Keep GPS frequent while location evidence is weak or stale. Motion sensor is only a hint. */
object GpsSamplingSafety {
    const val MAX_ACCURACY_FOR_QUIET_M=50.0
    const val MAX_FIX_AGE_MS=120_000L
    fun interval(
        suggestedMs:Long,
        nowMs:Long,
        lastFixMs:Long,
        lastAccuracyM:Double,
        confirmedTravel:Boolean
    ):Long {
        if(confirmedTravel || nowMs<=0 || lastFixMs<=0 || lastFixMs>nowMs ||
            nowMs-lastFixMs>MAX_FIX_AGE_MS || !lastAccuracyM.isFinite() ||
            lastAccuracyM<=0.0 || lastAccuracyM>MAX_ACCURACY_FOR_QUIET_M)
            return GpsAdaptiveSampling.ACTIVE_INTERVAL_MS
        return if(suggestedMs==GpsAdaptiveSampling.QUIET_INTERVAL_MS)
            GpsAdaptiveSampling.QUIET_INTERVAL_MS
        else GpsAdaptiveSampling.ACTIVE_INTERVAL_MS
    }
}
