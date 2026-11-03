package com.r3gate.app

/** Rate-limits routine GPS trace writes while retaining important transitions. */
object GpsTraceRules {
    const val MAX_ENTRIES=2800
    const val FIX_INTERVAL_MS=60_000L
    const val REJECTION_INTERVAL_MS=60_000L

    fun shouldLog(kind:String,nowMs:Long,lastKindMs:Long):Boolean {
        if(nowMs<=0L) return false
        val interval=when(kind) {
            "FIX","ZONE","TRIP_EVIDENCE","APPROXIMATE_LOCATION" -> FIX_INTERVAL_MS
            "REJECT_QUALITY","REJECT_ORDER","REJECT_JUMP","REJECT_SOURCE" -> REJECTION_INTERVAL_MS
            else -> return true
        }
        return lastKindMs<=0L || nowMs<lastKindMs || nowMs-lastKindMs>=interval
    }
    fun retainedStart(size:Int,maximum:Int=MAX_ENTRIES):Int =
        (size-maximum.coerceAtLeast(1)).coerceAtLeast(0)
}
