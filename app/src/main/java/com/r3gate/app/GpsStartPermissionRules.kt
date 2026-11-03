package com.r3gate.app

/** Decide service startup without disabling a user's GPS preference on permission loss. */
object GpsStartPermissionRules {
    enum class Decision { STOP_DISABLED, STOP_PERMISSION, START }
    fun decide(enabled:Boolean,fineGranted:Boolean,coarseGranted:Boolean):Decision =
        when {
            !enabled -> Decision.STOP_DISABLED
            !fineGranted && !coarseGranted -> Decision.STOP_PERMISSION
            else -> Decision.START
        }
}
