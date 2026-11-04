package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsStatusContractTest {
    private val contract=GpsStatusContract
    private fun status(active:Boolean=true,enabled:Boolean=true,permission:Boolean=true,
                       provider:Boolean=true,fix:Long=950_000L,now:Long=1_000_000L,
                       problem:String?=null)=contract.status(active,enabled,permission,provider,fix,now,problem)

    @Test fun manualOffDominatesAllTransientErrors() {
        assertEquals(GpsStatusContract.Status.OFF,status(enabled=false,permission=false,problem="runtime"))
    }
    @Test fun deniedPermissionIsActionable() {
        assertEquals(GpsStatusContract.Status.PERMISSION_REQUIRED,status(permission=false))
        assertEquals(GpsStatusContract.Status.PERMISSION_REQUIRED,status(problem="permission"))
    }
    @Test fun unavailableProviderIsNotTreatedAsFreshFix() {
        assertEquals(GpsStatusContract.Status.UNAVAILABLE,status(provider=false))
        assertEquals(GpsStatusContract.Status.UNAVAILABLE,status(problem="provider_unusable"))
        assertEquals(GpsStatusContract.Status.UNAVAILABLE,status(problem="no_provider"))
    }
    @Test fun missingFirstFixIsWaiting() {
        assertEquals(GpsStatusContract.Status.WAITING_FOR_FIX,status(fix=0L))
    }
    @Test fun recentTrustedFixIsActive() {
        assertEquals(GpsStatusContract.Status.ACTIVE,status())
    }
    @Test fun staleFixIsWaitingNotActive() {
        assertEquals(GpsStatusContract.Status.WAITING_FOR_FIX,status(fix=879_999L))
    }
    @Test fun futureFixIsNotActive() {
        assertEquals(GpsStatusContract.Status.WAITING_FOR_FIX,status(fix=1_001_000L))
    }
    @Test fun serviceStartupWithoutFixIsWaiting() {
        assertEquals(GpsStatusContract.Status.WAITING_FOR_FIX,status(active=false))
    }
    @Test fun serviceStoppedWithErrorIsError() {
        assertEquals(GpsStatusContract.Status.ERROR,status(active=false,problem="runtime"))
    }
    @Test fun providerRuntimeFailureDoesNotClaimActiveStatus() {
        assertEquals(GpsStatusContract.Status.ERROR,status(problem="runtime"))
    }
    @Test fun snapshotContainsOnlyIdAndThemeIndependentStatus() {
        val x=contract.snapshot(true,true,true,true,950_000L,1_000_000L,
            null,"object-123",2)
        assertEquals(GpsStatusContract.Status.ACTIVE,x.status)
        assertEquals("object-123",x.confirmedObjectId)
        assertEquals(2,x.pendingProposalCount)
        assertNull(x.diagnostics)
    }
    @Test fun snapshotSanitizesInvalidObjectAndNegativeCount() {
        val x=contract.snapshot(false,false,false,false,0L,1_000_000L,
            null," ",-5)
        assertNull(x.confirmedObjectId)
        assertEquals(0,x.pendingProposalCount)
        assertNull(x.lastTrustedFixMs)
    }
    @Test fun aPreviousSessionFixCannotMarkNewSessionActive() {
        val x = contract.sessionSnapshot(true, true, true, true,
            960_000L, 950_000L, 1_000_000L, null, "site", 2)
        assertEquals(GpsStatusContract.Status.WAITING_FOR_FIX, x.status)
        assertNull(x.lastTrustedFixMs)
        assertNull(x.confirmedObjectId)
        assertEquals(2, x.pendingProposalCount)
    }

    @Test fun currentSessionCanExposeFixTimeAndObjectWithoutCoordinates() {
        val x = contract.sessionSnapshot(true, true, true, true,
            940_000L, 950_000L, 1_000_000L, null, "site", 0)
        assertEquals(GpsStatusContract.Status.ACTIVE, x.status)
        assertEquals(950_000L, x.lastTrustedFixMs)
        assertEquals("site", x.confirmedObjectId)
    }

    @Test fun closedDayHidesRuntimeErrorAndOldLocation() {
        val x = contract.sessionSnapshot(true, false, true, true,
            940_000L, 950_000L, 1_000_000L, "runtime", "site", 1)
        assertEquals(GpsStatusContract.Status.OFF, x.status)
        assertNull(x.diagnostics)
        assertNull(x.lastTrustedFixMs)
        assertNull(x.confirmedObjectId)
        assertEquals(1, x.pendingProposalCount)
    }

    @Test fun futureSessionCannotReuseCheckpointAfterClockRollback() {
        val x = contract.sessionSnapshot(true, true, true, true,
            1_100_000L, 950_000L, 1_000_000L, null, "site", 0)
        assertEquals(GpsStatusContract.Status.WAITING_FOR_FIX, x.status)
        assertNull(x.lastTrustedFixMs)
    }
}
