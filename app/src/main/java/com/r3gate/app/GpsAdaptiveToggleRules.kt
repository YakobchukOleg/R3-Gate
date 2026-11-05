package com.r3gate.app

/** Settings may change while GPS foreground service stays alive. */
object GpsAdaptiveToggleRules {
    enum class Action { START_SENSOR, STOP_SENSOR, RESTORE_ACTIVE, NONE }

    fun sensorAction(adaptiveEnabled:Boolean,sensorRegistered:Boolean):Action = when {
        adaptiveEnabled && !sensorRegistered -> Action.START_SENSOR
        !adaptiveEnabled && sensorRegistered -> Action.STOP_SENSOR
        else -> Action.NONE
    }

    fun cadenceAction(adaptiveEnabled:Boolean,currentIntervalMs:Long):Action =
        if(!adaptiveEnabled && currentIntervalMs!=GpsAdaptiveSampling.ACTIVE_INTERVAL_MS)
            Action.RESTORE_ACTIVE else Action.NONE
}
