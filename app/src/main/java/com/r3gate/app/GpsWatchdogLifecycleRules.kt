package com.r3gate.app

/**
 * Keep the foreground GPS service aligned with the user's tracking switch
 * even when the UI's explicit stop command was lost or delayed.
 */
object GpsWatchdogLifecycleRules {
    enum class Action { STOP, TICK, IGNORE }
    fun onTick(sessionStarted:Boolean,trackingEnabled:Boolean):Action = when {
        !sessionStarted -> Action.IGNORE
        !trackingEnabled -> Action.STOP
        else -> Action.TICK
    }
}
