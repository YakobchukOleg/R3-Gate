package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsServiceStatusRulesTest {
    @Test fun disabledGPSDoesNotClaimTracking() {
        assertEquals(GpsServiceStatusRules.Status.OFF,
            GpsServiceStatusRules.status(false,true,false,1000,2000))
    }
    @Test fun bootRequiresExplicitResume() {
        assertEquals(GpsServiceStatusRules.Status.RESUME_AFTER_REBOOT,
            GpsServiceStatusRules.status(false,true,true,1000,2000))
    }
    @Test fun missingPermissionIsNotMistakenForRunningGPS() {
        assertEquals(GpsServiceStatusRules.Status.PERMISSION_REQUIRED,
            GpsServiceStatusRules.status(true,false,false,1000,2000))
    }
    @Test fun staleLocationNeverClaimsServiceStopped() {
        assertEquals(GpsServiceStatusRules.Status.NO_RECENT_FIX,
            GpsServiceStatusRules.status(true,true,false,1000,1_000_000))
    }
    @Test fun freshLocationIsRecentButDoesNotProveServiceHealth() {
        assertEquals(GpsServiceStatusRules.Status.RECENT_FIX,
            GpsServiceStatusRules.status(true,true,false,1000,2000))
    }

    @Test fun zeroStalenessThresholdIsNotRecent() {
        assertEquals(GpsServiceStatusRules.Status.NO_RECENT_FIX,
            GpsServiceStatusRules.status(true,true,false,1000,1000,0))
    }
    @Test fun negativeStalenessThresholdIsNotRecent() {
        assertEquals(GpsServiceStatusRules.Status.NO_RECENT_FIX,
            GpsServiceStatusRules.status(true,true,false,1000,1000,-1))
    }
    @Test fun zeroCurrentTimeIsNotRecent() {
        assertEquals(GpsServiceStatusRules.Status.NO_RECENT_FIX,
            GpsServiceStatusRules.status(true,true,false,1000,0))
    }
    @Test fun negativeCurrentTimeIsNotRecent() {
        assertEquals(GpsServiceStatusRules.Status.NO_RECENT_FIX,
            GpsServiceStatusRules.status(true,true,false,1000,-1000))
    }
    @Test fun negativeLastFixIsNotRecent() {
        assertEquals(GpsServiceStatusRules.Status.NO_RECENT_FIX,
            GpsServiceStatusRules.status(true,true,false,-1,1000))
    }
    @Test fun exactFreshnessLimitIsRecent() {
        assertEquals(GpsServiceStatusRules.Status.RECENT_FIX,
            GpsServiceStatusRules.status(true,true,false,1000,2000,1000))
    }
    @Test fun oneMillisecondAfterLimitIsStale() {
        assertEquals(GpsServiceStatusRules.Status.NO_RECENT_FIX,
            GpsServiceStatusRules.status(true,true,false,1000,2001,1000))
    }
    @Test fun clockRollbackCannotShowRecentFix() {
        assertEquals(GpsServiceStatusRules.Status.NO_RECENT_FIX,
            GpsServiceStatusRules.status(true,true,false,5000,4000))
    }
    @Test fun permissionTakesPrecedenceOverStaleLocation() {
        assertEquals(GpsServiceStatusRules.Status.PERMISSION_REQUIRED,
            GpsServiceStatusRules.status(true,false,false,1000,100000))
    }
    @Test fun disabledStatusTakesPrecedenceOverInvalidClock() {
        assertEquals(GpsServiceStatusRules.Status.OFF,
            GpsServiceStatusRules.status(false,true,false,5000,0))
    }
    @Test fun rebootPendingTakesPrecedenceOverDisabled() {
        assertEquals(GpsServiceStatusRules.Status.RESUME_AFTER_REBOOT,
            GpsServiceStatusRules.status(false,true,true,0,0))
    }
    @Test fun validFixAtMaxTimestampIsRecent() {
        assertEquals(GpsServiceStatusRules.Status.RECENT_FIX,
            GpsServiceStatusRules.status(true,true,false,Long.MAX_VALUE-100,
                Long.MAX_VALUE,100))
    }
}
