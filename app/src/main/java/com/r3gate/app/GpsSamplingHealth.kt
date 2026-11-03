package com.r3gate.app

/** Diagnostic-only health assessment of foreground location sampling. */
object GpsSamplingHealth {
    enum class Level { NO_SESSION, WAITING_FOR_FIRST_FIX, FRESH, DELAYED, STALE }
    data class Result(val level:Level,val ageMs:Long?,val samples:Int)
    fun assess(enabled:Boolean,sessionStartMs:Long,fixTimes:List<Long>,nowMs:Long):Result {
        if(!enabled || sessionStartMs<=0 || nowMs<sessionStartMs) return Result(Level.NO_SESSION,null,0)
        val valid=fixTimes.filter { it>=sessionStartMs && it<=nowMs }.sorted().distinct()
        val latest=valid.lastOrNull()
        val age=nowMs-(latest ?: sessionStartMs)
        val level=when {
            latest==null && age<=120_000L -> Level.WAITING_FOR_FIRST_FIX
            age<=120_000L -> Level.FRESH
            age<=15*60_000L -> Level.DELAYED
            else -> Level.STALE
        }
        return Result(level,age,valid.size)
    }
}
