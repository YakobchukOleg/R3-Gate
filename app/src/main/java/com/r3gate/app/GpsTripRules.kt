package com.r3gate.app

/** GPS movement classifier. Requires sustained evidence; never edits a work shift by itself. */
object GpsTripRules {
    const val MAX_EVIDENCE_GAP_MS=4*60_000L
    enum class Mode { UNKNOWN, MOVING, STOPPED }
    data class State(
        val mode:Mode=Mode.UNKNOWN,
        val candidate:Mode=Mode.UNKNOWN,
        val candidateSinceMs:Long=0L,
        val lastFixMs:Long=0L,
        val stoppedSinceMs:Long=0L,
        val longStopReported:Boolean=false
    )
    data class Result(val state:State,val event:String?=null)
    fun advance(
        state:State,
        moving:Boolean,
        nowMs:Long,
        movementDwellMs:Long=60_000L,
        stopDwellMs:Long=120_000L,
        longStopMs:Long=600_000L
    ):Result {
        if(nowMs<=state.lastFixMs || nowMs<=0) return Result(state)
        // Do not treat time without valid fixes as observed movement or stationary dwell.
        if(state.lastFixMs>0 && nowMs-state.lastFixMs>MAX_EVIDENCE_GAP_MS)
            return Result(State(candidate=if(moving) Mode.MOVING else Mode.STOPPED,
                candidateSinceMs=nowMs,lastFixMs=nowMs))
        val observed=if(moving) Mode.MOVING else Mode.STOPPED
        if(state.mode==observed) {
            val endLongStop=observed==Mode.STOPPED && !state.longStopReported &&
                state.stoppedSinceMs>0 && nowMs-state.stoppedSinceMs>=longStopMs
            val next=state.copy(candidate=Mode.UNKNOWN,candidateSinceMs=0,lastFixMs=nowMs,
                longStopReported=state.longStopReported || endLongStop)
            return Result(next,if(endLongStop) "LONG_STOP" else null)
        }
        if(state.candidate!=observed || state.candidateSinceMs<=0)
            return Result(state.copy(candidate=observed,candidateSinceMs=nowMs,lastFixMs=nowMs))
        val dwell=if(observed==Mode.MOVING) movementDwellMs else stopDwellMs
        if(nowMs-state.candidateSinceMs<dwell)
            return Result(state.copy(lastFixMs=nowMs))
        val next=State(mode=observed,lastFixMs=nowMs,
            stoppedSinceMs=if(observed==Mode.STOPPED) state.candidateSinceMs else 0)
        // A stationary phone on service startup (or after a coverage gap) is
        // not the end of an observed trip. Only MOVING -> STOPPED emits TRIP_STOP.
        val event=when {
            observed==Mode.MOVING -> "TRIP_START"
            state.mode==Mode.MOVING -> "TRIP_STOP"
            else -> null
        }
        return Result(next,event)
    }
}
