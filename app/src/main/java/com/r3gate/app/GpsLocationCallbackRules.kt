package com.r3gate.app

/** Decide what to do with a callback without trusting permissions held at service startup. */
object GpsLocationCallbackRules {
    enum class Action { STOP_DISABLED, STOP_PERMISSION, IGNORE_SOURCE, PROCESS }
    fun decide(enabled:Boolean,fineGranted:Boolean,coarseGranted:Boolean,sourceAccepted:Boolean):Action =
        when {
            !enabled -> Action.STOP_DISABLED
            !fineGranted && !coarseGranted -> Action.STOP_PERMISSION
            !sourceAccepted -> Action.IGNORE_SOURCE
            else -> Action.PROCESS
        }
}
