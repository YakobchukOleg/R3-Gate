package com.r3gate.app

/**
 * Offers a manual destination picker only after a fresh, confirmed location
 * matches the current GPS fix at a saved object. It never edits work time.
 */
object GpsHomeArrivalRules {
    const val MAX_FIX_AGE_MS=2*60_000L

    fun canChooseObject(
        trackingEnabled:Boolean,
        serviceActive:Boolean,
        shiftActive:Boolean,
        confirmedObjectId:String?,
        nearestObjectId:String?,
        lastFixMs:Long,
        lastArrivalEvidenceMs:Long,
        nowMs:Long
    ):Boolean =
        trackingEnabled && serviceActive && shiftActive &&
            !confirmedObjectId.isNullOrBlank() &&
            confirmedObjectId==nearestObjectId &&
            nowMs>0L && lastFixMs>0L && lastArrivalEvidenceMs>0L &&
            nowMs>=lastFixMs && nowMs>=lastArrivalEvidenceMs &&
            nowMs-lastFixMs<=MAX_FIX_AGE_MS &&
            nowMs-lastArrivalEvidenceMs<=MAX_FIX_AGE_MS
}
