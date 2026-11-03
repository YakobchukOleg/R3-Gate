package com.r3gate.app

/** A new service session is not itself evidence of a lost GPS track. */
object GpsSessionBoundaryRules {
    fun shouldRecordStartupGap(lastAcceptedFixMs:Long, nowMs:Long):Boolean =
        lastAcceptedFixMs>0L && nowMs>lastAcceptedFixMs

    /** A wall-clock rollback must not keep every new fix behind the old checkpoint. */
    fun clockRolledBack(lastAcceptedFixMs:Long, nowMs:Long):Boolean =
        lastAcceptedFixMs>0L && nowMs>0L && nowMs<lastAcceptedFixMs

    /** A running service can observe a clock rollback without restarting. */
    fun shouldResetActiveCheckpoint(lastFixMs:Long, nowMs:Long, sourceFixMs:Long):Boolean =
        clockRolledBack(lastFixMs,nowMs) && sourceFixMs>0L && sourceFixMs<=nowMs

    /** Preserve a confirmed object, never a candidate backed by pre-rollback time. */
    data class ArrivalCheckpoint(val confirmedId:String?,val candidateId:String?,val candidateSinceMs:Long,val lastFixMs:Long)
    fun resetArrival(checkpoint:ArrivalCheckpoint):ArrivalCheckpoint =
        ArrivalCheckpoint(checkpoint.confirmedId,null,0L,0L)

    fun shouldRecordShutdownGap(sessionStarted:Boolean):Boolean = sessionStarted
}
