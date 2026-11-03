package com.r3gate.app

/** Acceleration is a wake-up hint only; never proof of travelling or working. */
object GpsMotionWakeRules {
    const val MIN_ACCEL_DELTA=1.5
    const val SUSTAIN_MS=2_000L
    const val CUE_GAP_MS=2_000L
    const val COOLDOWN_MS=60_000L
    data class State(val sinceMs:Long=0L,val lastSampleMs:Long=0L,val lastWakeMs:Long=0L)
    data class Result(val state:State,val wake:Boolean=false)
    fun observe(state:State,nowMs:Long,delta:Double):Result {
        if(nowMs<=0 || !delta.isFinite() || delta<0) return Result(state)
        // Reset only the provisional motion evidence after a backward clock change.
        // Never let stale timestamps permanently block future sensor wake-ups.
        val current=if(nowMs<state.lastSampleMs || nowMs<state.lastWakeMs ||
            nowMs<state.sinceMs) State() else state
        if(nowMs==current.lastSampleMs) return Result(current)
        if(delta<MIN_ACCEL_DELTA) return Result(current.copy(sinceMs=0L,lastSampleMs=nowMs))
        val uninterrupted=current.sinceMs>0 && current.lastSampleMs>0 && nowMs-current.lastSampleMs<=CUE_GAP_MS
        val since=if(uninterrupted) current.sinceMs else nowMs
        val cooled=current.lastWakeMs<=0 || nowMs-current.lastWakeMs>=COOLDOWN_MS
        val wake=uninterrupted && cooled && nowMs-since>=SUSTAIN_MS
        return Result(current.copy(sinceMs=if(wake) 0 else since,lastSampleMs=nowMs,
            lastWakeMs=if(wake) nowMs else current.lastWakeMs),wake)
    }
}
