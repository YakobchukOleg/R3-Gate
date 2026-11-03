package com.r3gate.app

/** Conservative in-service watchdog: detect missing fixes without synthesizing GPS evidence. */
object GpsWatchdogRules {
    const val CHECK_INTERVAL_MS=60_000L
    const val STALL_AFTER_MS=5*60_000L
    const val RETRY_COOLDOWN_MS=3*60_000L
    enum class Action { NONE, PROBE }
    fun decide(nowMs:Long,startedMs:Long,lastFixMs:Long,lastProbeMs:Long,tracking:Boolean,providerAvailable:Boolean):Action {
        if(!tracking || !providerAvailable || nowMs<=0 || startedMs<=0 || startedMs>nowMs) return Action.NONE
        val checkpoint=if(lastFixMs in 1..nowMs) lastFixMs else startedMs
        if(nowMs-checkpoint<STALL_AFTER_MS) return Action.NONE
        if(lastProbeMs>nowMs) return Action.NONE
        if(lastProbeMs>0 && nowMs-lastProbeMs<RETRY_COOLDOWN_MS) return Action.NONE
        return Action.PROBE
    }
}
