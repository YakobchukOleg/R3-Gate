package com.r3gate.app

/** Reboot never silently resumes location tracking. */
object GpsBootRecoveryRules {
    data class State(val enabled:Boolean,val active:Boolean,val resumePending:Boolean)
    fun afterBoot(current:State):State = State(
        enabled=false,
        active=false,
        resumePending=current.enabled || current.resumePending
    )
}
