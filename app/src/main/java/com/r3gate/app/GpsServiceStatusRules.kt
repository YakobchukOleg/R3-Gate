package com.r3gate.app

/** Conservative display policy: missing fixes do not prove a foreground service has stopped. */
object GpsServiceStatusRules {
    enum class Status { OFF, PERMISSION_REQUIRED, RESUME_AFTER_REBOOT, NO_RECENT_FIX, RECENT_FIX }
    fun status(enabled:Boolean,hasPermission:Boolean,resumePending:Boolean,lastFixMs:Long,nowMs:Long,
               staleAfterMs:Long=15*60_000L):Status {
        if(resumePending && !enabled) return Status.RESUME_AFTER_REBOOT
        if(!enabled) return Status.OFF
        if(!hasPermission) return Status.PERMISSION_REQUIRED
        if(lastFixMs<=0 || nowMs<lastFixMs || nowMs-lastFixMs>staleAfterMs)
            return Status.NO_RECENT_FIX
        return Status.RECENT_FIX
    }
}
