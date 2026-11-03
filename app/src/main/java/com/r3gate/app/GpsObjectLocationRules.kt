package com.r3gate.app

/** Explicit, optional association of a verified current location with one saved object ID. */
object GpsObjectLocationRules {
    const val RADIUS_M=100.0
    data class Capture(val objectId:String,val latitude:Double,val longitude:Double,
                       val accuracyM:Double,val fixTimeMs:Long)
    fun valid(capture:Capture,nowMs:Long):Boolean =
        capture.objectId.isNotBlank() &&
        GpsLocationQuality.acceptable(nowMs,capture.fixTimeMs,capture.accuracyM,
            capture.latitude,capture.longitude)
    fun belongsTo(capture:Capture,objectId:String,nowMs:Long):Boolean =
        objectId.isNotBlank() && capture.objectId==objectId && valid(capture,nowMs)
}
