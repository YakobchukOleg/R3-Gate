package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsStartupCheckpointTest {
    @Test fun successfulCheckpointEnablesSession() {
        assertTrue(GpsSessionBoundaryRules.mayActivateAfterPersist(true))
    }
    @Test fun failedDiskCommitDoesNotEnableSession() {
        assertFalse(GpsSessionBoundaryRules.mayActivateAfterPersist(false))
    }
    @Test fun noSuccessfulCheckpointCannotBeTreatedAsActive() {
        val commitSucceeded=false
        val shouldActivate=GpsSessionBoundaryRules.mayActivateAfterPersist(commitSucceeded)
        assertFalse(shouldActivate)
    }
    @Test fun retriedSuccessfulCheckpointCanActivate() {
        assertFalse(GpsSessionBoundaryRules.mayActivateAfterPersist(false))
        assertTrue(GpsSessionBoundaryRules.mayActivateAfterPersist(true))
    }
    @Test fun startupCommitIsNotEquivalentToUserGpsIntent() {
        assertFalse(GpsSessionBoundaryRules.mayActivateAfterPersist(false))
        assertEquals(GpsWatchdogLifecycleRules.Action.IGNORE,
            GpsWatchdogLifecycleRules.onTick(false,true))
    }
    @Test fun failureCannotBorrowPreviousSessionEnabledFlag() {
        assertFalse(GpsSessionBoundaryRules.mayActivateAfterPersist(false))
        assertFalse(GpsRecoveryAlertGuard.stillRelevant(100_000L,200_000L,true,250_000L))
    }
    @Test fun successfulActivationDoesNotChangeMissingFixPolicy() {
        assertTrue(GpsSessionBoundaryRules.mayActivateAfterPersist(true))
        assertEquals(GpsWatchdogRules.Action.NONE,
            GpsWatchdogRules.decide(150_000L,100_000L,0L,0L,true,true))
    }
    @Test fun failedActivationDoesNotAuthorizeBackgroundWatchdog() {
        val started=GpsSessionBoundaryRules.mayActivateAfterPersist(false)
        assertEquals(GpsWatchdogLifecycleRules.Action.IGNORE,
            GpsWatchdogLifecycleRules.onTick(started,true))
    }
}
