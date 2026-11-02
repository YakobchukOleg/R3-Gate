package com.r3gate.app

/** Invalidates stale GPS evidence without inventing arrivals, departures or billable time. */
object GpsRecoveryRules {
    const val MAX_GAP_MS=15*60_000L
    data class Plan(val resetArrival:Boolean,val resetMotion:Boolean,val recordGap:Boolean)
    fun plan(lastFixMs:Long,newFixMs:Long,maxGapMs:Long=MAX_GAP_MS):Plan {
        if(newFixMs<=0 || maxGapMs<=0) return Plan(false,false,false)
        // No previous fix means no observed coverage gap. The service startup
        // already discards any unconfirmed arrival candidate; retain its last
        // confirmed object while restarting motion classification from scratch.
        if(lastFixMs<=0) return Plan(false,true,false)
        if(newFixMs<=lastFixMs) return Plan(false,false,false)
        val stale=newFixMs-lastFixMs>maxGapMs
        return Plan(stale,stale,stale)
    }
}
