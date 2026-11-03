package com.r3gate.app

/** Location collection belongs to a user-opened workday, never a closed day. */
object GpsWorkdayPolicy {
    fun shouldTrack(workdayOpen:Boolean,gpsEnabled:Boolean):Boolean =
        workdayOpen && gpsEnabled
}
