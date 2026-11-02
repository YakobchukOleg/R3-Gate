package com.r3gate.app

/** A new service session is not itself evidence of a lost GPS track. */
object GpsSessionBoundaryRules {
    fun shouldRecordStartupGap(lastAcceptedFixMs:Long, nowMs:Long):Boolean =
        lastAcceptedFixMs>0L && nowMs>lastAcceptedFixMs

    fun shouldRecordShutdownGap(sessionStarted:Boolean):Boolean = sessionStarted
}
