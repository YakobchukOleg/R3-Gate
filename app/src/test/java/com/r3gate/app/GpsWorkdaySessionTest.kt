package com.r3gate.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsWorkdaySessionTest {
    @Test fun closedDayRejectsAnEnabledLegacySession() {
        assertFalse(GpsWorkdaySession.shouldTrack(false, true, true))
    }

    @Test fun manualOptOutWinsOverStaleSessionFlags() {
        assertFalse(GpsWorkdaySession.hasConsent(false, true, true))
        assertFalse(GpsWorkdaySession.shouldTrack(true, false, true))
    }

    @Test fun existingOptInSurvivesAnUpdateOrReboot() {
        assertTrue(GpsWorkdaySession.hasConsent(null, true, false))
        assertTrue(GpsWorkdaySession.hasConsent(null, false, true))
        assertTrue(GpsWorkdaySession.hasConsent(true, false, false))
    }

    @Test fun freshInstallRequiresOptIn() {
        assertFalse(GpsWorkdaySession.hasConsent(null, false, false))
    }

    @Test fun consentAloneCannotStartAStoppedSession() {
        assertFalse(GpsWorkdaySession.shouldTrack(true, true, false))
        assertTrue(GpsWorkdaySession.shouldTrack(true, true, true))
    }

    @Test fun privateTimeRejectsTrackingEvenWithStaleEnabledFlags() {
        assertFalse(GpsWorkdaySession.shouldTrack(true,true,true,privateTime = true))
    }
}
