package com.r3gate.app

/** Only neighboring, recent location fixes can contribute to trip detection. */
object GpsMotionContinuityRules {
    fun contiguous(previousMs:Long,currentMs:Long):Boolean =
        previousMs>0 && currentMs>previousMs &&
            currentMs-previousMs<=GpsTripRules.MAX_EVIDENCE_GAP_MS
}
