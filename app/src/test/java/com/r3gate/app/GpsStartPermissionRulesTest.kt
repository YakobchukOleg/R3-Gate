package com.r3gate.app

import org.junit.Assert.assertEquals
import org.junit.Test

class GpsStartPermissionRulesTest {
    private val rules=GpsStartPermissionRules
    private val START=GpsStartPermissionRules.Decision.START
    private val DISABLED=GpsStartPermissionRules.Decision.STOP_DISABLED
    private val PERMISSION=GpsStartPermissionRules.Decision.STOP_PERMISSION

    @Test fun enabledWithFinePermissionStarts() {
        assertEquals(START,rules.decide(true,true,false))
    }
    @Test fun enabledWithCoarsePermissionStarts() {
        assertEquals(START,rules.decide(true,false,true))
    }
    @Test fun enabledWithBothPermissionsStarts() {
        assertEquals(START,rules.decide(true,true,true))
    }
    @Test fun enabledWithNoLocationPermissionStops() {
        assertEquals(PERMISSION,rules.decide(true,false,false))
    }
    @Test fun disabledWithNoPermissionsStaysDisabled() {
        assertEquals(DISABLED,rules.decide(false,false,false))
    }
    @Test fun disabledWithFinePermissionStaysDisabled() {
        assertEquals(DISABLED,rules.decide(false,true,false))
    }
    @Test fun disabledWithCoarsePermissionStaysDisabled() {
        assertEquals(DISABLED,rules.decide(false,false,true))
    }
    @Test fun disabledWithBothPermissionsStaysDisabled() {
        assertEquals(DISABLED,rules.decide(false,true,true))
    }
    @Test fun finePermissionRevokedWhileCoarseRemainsStillStarts() {
        assertEquals(START,rules.decide(true,false,true))
    }
    @Test fun coarsePermissionRevokedWhileFineRemainsStillStarts() {
        assertEquals(START,rules.decide(true,true,false))
    }
    @Test fun revokingLastLocationPermissionRequiresStop() {
        val before=rules.decide(true,true,false)
        val after=rules.decide(true,false,false)
        assertEquals(START,before)
        assertEquals(PERMISSION,after)
    }
    @Test fun restoringPermissionAllowsRestartWithoutChangingUserPreference() {
        val before=rules.decide(true,false,false)
        val after=rules.decide(true,false,true)
        assertEquals(PERMISSION,before)
        assertEquals(START,after)
    }
    @Test fun noCombinationCanStartWhenDisabled() {
        for(fine in listOf(false,true)) for(coarse in listOf(false,true))
            assertEquals(DISABLED,rules.decide(false,fine,coarse))
    }
    @Test fun permissionLossOnlyCausesPermissionStopWhenEnabled() {
        assertEquals(PERMISSION,rules.decide(true,false,false))
        assertEquals(DISABLED,rules.decide(false,false,false))
    }
}
