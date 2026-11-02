package com.r3gate.app

/** A new service session is not itself evidence of a lost GPS track. */
object GpsSessionBoundaryRules {
    fun shouldRecordStartupGap(lastAcceptedFixMs:Long, nowMs:Long):Boolean =
        lastAcceptedFixMs>0L && nowMs>lastAcceptedFixMs

    /** A wall-clock rollback must not keep every new fix behind the old checkpoint. */
    fun clockRolledBack(lastAcceptedFixMs:Long, nowMs:Long):Boolean =
        lastAcceptedFixMs>0L && nowMs>0L && nowMs<lastAcceptedFixMs

    fun shouldRecordShutdownGap(sessionStarted:Boolean):Boolean = sessionStarted
}
