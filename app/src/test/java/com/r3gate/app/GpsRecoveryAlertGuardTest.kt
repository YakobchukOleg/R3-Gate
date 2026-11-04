package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsRecoveryAlertGuardTest {
    private fun relevant(checked:Long=100_000L,current:Long=100_000L,
        desired:Boolean=true,now:Long=1_000_000L) =
        GpsRecoveryAlertGuard.stillRelevant(checked,current,desired,now)

    @Test fun sameActiveSessionMayPublishAlert() { assertTrue(relevant()) }
    @Test fun disabledGpsSuppressesAlert() { assertFalse(relevant(desired=false)) }
    @Test fun newlyRestartedGpsSessionSuppressesOldAlert() {
        assertFalse(relevant(current=200_000L))
    }
    @Test fun oldWorkerCannotAlertForNewerSession() {
        assertFalse(relevant(checked=200_000L,current=100_000L))
    }
    @Test fun uninitializedSessionCannotAlert() { assertFalse(relevant(checked=0L,current=0L)) }
    @Test fun invalidNegativeSessionCannotAlert() {
        assertFalse(relevant(checked=-1L,current=-1L))
    }
    @Test fun futureSessionCannotAlert() { assertFalse(relevant(checked=1_000_001L,current=1_000_001L)) }
    @Test fun sessionAtCurrentClockIsValid() { assertTrue(relevant(checked=1_000_000L,current=1_000_000L)) }
    @Test fun sameSessionCanAlertAfterLongOutage() {
        assertTrue(relevant(now=100_000_000L))
    }
    @Test fun clockRollbackInvalidatesCapturedSession() {
        assertFalse(relevant(now=90_000L))
    }
    @Test fun disabledTrackingWinsOverMatchingSession() {
        assertFalse(relevant(desired=false,checked=100_000L,current=100_000L))
    }
    @Test fun oneMillisecondDifferenceIndicatesRestart() {
        assertFalse(relevant(current=100_001L))
    }
}
