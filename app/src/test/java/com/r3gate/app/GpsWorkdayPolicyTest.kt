package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsWorkdayPolicyTest {
    @Test fun activeDayAndEnabledGpsCanTrack() {
        assertTrue(GpsWorkdayPolicy.shouldTrack(true,true))
    }
    @Test fun closingDayStopsTrackingEvenIfEnabledWasLeftTrue() {
        assertFalse(GpsWorkdayPolicy.shouldTrack(false,true))
    }
    @Test fun closedDayWithGpsOffNeverTracks() {
        assertFalse(GpsWorkdayPolicy.shouldTrack(false,false))
    }
    @Test fun pausedGpsDuringActiveDayDoesNotTrack() {
        assertFalse(GpsWorkdayPolicy.shouldTrack(true,false))
    }
}
