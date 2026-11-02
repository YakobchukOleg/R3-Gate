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
}
