package com.r3gate.app

import org.junit.Assert.assertEquals
import org.junit.Test

class GpsLocationCallbackRulesTest {
    private val rules=GpsLocationCallbackRules
    private val PROCESS=GpsLocationCallbackRules.Action.PROCESS
    private val STOP_DISABLED=GpsLocationCallbackRules.Action.STOP_DISABLED
    private val STOP_PERMISSION=GpsLocationCallbackRules.Action.STOP_PERMISSION
    private val IGNORE=GpsLocationCallbackRules.Action.IGNORE_SOURCE

    @Test fun finePermissionAcceptsTrustedSource() {
        assertEquals(PROCESS,rules.decide(true,true,false,true))
    }
    @Test fun coarsePermissionAcceptsTrustedSource() {
        assertEquals(PROCESS,rules.decide(true,false,true,true))
    }
    @Test fun bothPermissionsAcceptTrustedSource() {
        assertEquals(PROCESS,rules.decide(true,true,true,true))
    }
    @Test fun missingBothPermissionsRejectsTrustedSource() {
        assertEquals(STOP_PERMISSION,rules.decide(true,false,false,true))
    }
    @Test fun revokedPermissionTakesPrecedenceOverUntrustedSource() {
        assertEquals(STOP_PERMISSION,rules.decide(true,false,false,false))
    }
    @Test fun finePermissionCannotAuthorizeUntrustedSource() {
        assertEquals(IGNORE,rules.decide(true,true,false,false))
    }
    @Test fun coarsePermissionCannotAuthorizeUntrustedSource() {
        assertEquals(IGNORE,rules.decide(true,false,true,false))
    }
    @Test fun bothPermissionsCannotAuthorizeUntrustedSource() {
        assertEquals(IGNORE,rules.decide(true,true,true,false))
    }
    @Test fun disabledGPSIgnoresTrustedSourceWithPermissions() {
        assertEquals(STOP_DISABLED,rules.decide(false,true,true,true))
    }
    @Test fun disabledGPSTakesPrecedenceOverRevokedPermission() {
        assertEquals(STOP_DISABLED,rules.decide(false,false,false,true))
    }
    @Test fun disabledGPSRejectsUntrustedSource() {
        assertEquals(STOP_DISABLED,rules.decide(false,true,true,false))
    }
    @Test fun losingFinePermissionStillAllowsCoarseCallbacks() {
        assertEquals(PROCESS,rules.decide(true,false,true,true))
    }
    @Test fun losingCoarsePermissionStillAllowsFineCallbacks() {
        assertEquals(PROCESS,rules.decide(true,true,false,true))
    }
    @Test fun revokingLastPermissionStopsAnExistingSession() {
        assertEquals(PROCESS,rules.decide(true,true,false,true))
        assertEquals(STOP_PERMISSION,rules.decide(true,false,false,true))
    }
    @Test fun restoringPermissionAllowsSubsequentTrustedCallbacks() {
        assertEquals(STOP_PERMISSION,rules.decide(true,false,false,true))
        assertEquals(PROCESS,rules.decide(true,false,true,true))
    }
    @Test fun noDisabledStateCanProcessLocation() {
        for(fine in listOf(false,true)) for(coarse in listOf(false,true))
            for(source in listOf(false,true))
                assertEquals(STOP_DISABLED,rules.decide(false,fine,coarse,source))
    }
}
