package com.r3gate.app

/**
 * Conservative sampling hints from accelerometer observations.
 * Motion is only a hint, never proof of travel or presence on site.
 * GPS continues at least every 90 seconds while enabled.
 */
object GpsAdaptiveSampling {
    const val ACTIVE_INTERVAL_MS=30_000L
    const val QUIET_INTERVAL_MS=90_000L
    const val QUIET_AFTER_MS=5*60_000L
    data class State(val lastMotionMs:Long=0L,val startedMs:Long=0L)
    fun observe(state:State,nowMs:Long,accelerationDelta:Double):State {
        if(nowMs<=0 || !accelerationDelta.isFinite() || accelerationDelta<0) return state
        if(state.startedMs==0L) return State(if(accelerationDelta>=1.5) nowMs else 0L,nowMs)
        if(nowMs<state.startedMs || (state.lastMotionMs>0 && nowMs<state.lastMotionMs)) return state
        return if(accelerationDelta>=1.5) state.copy(lastMotionMs=nowMs) else state
    }
    fun interval(state:State,nowMs:Long,enabled:Boolean):Long {
        if(!enabled || state.startedMs<=0 || nowMs<state.startedMs) return ACTIVE_INTERVAL_MS
        val activeFrom=maxOf(state.startedMs,state.lastMotionMs)
        return if(nowMs-activeFrom>=QUIET_AFTER_MS) QUIET_INTERVAL_MS else ACTIVE_INTERVAL_MS
    }
}
