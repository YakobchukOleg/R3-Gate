package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsBootRecoveryRulesTest {
    private fun boot(enabled:Boolean,active:Boolean,pending:Boolean) =
        GpsBootRecoveryRules.afterBoot(GpsBootRecoveryRules.State(enabled,active,pending))

    @Test fun trackingEnabledAndActiveRequiresManualResume() {
        assertEquals(GpsBootRecoveryRules.State(false,false,true),boot(true,true,false))
    }
    @Test fun enabledButInactiveStillRequiresManualResume() {
        assertEquals(GpsBootRecoveryRules.State(false,false,true),boot(true,false,false))
    }
    @Test fun disabledInactiveRemainsOff() {
        assertEquals(GpsBootRecoveryRules.State(false,false,false),boot(false,false,false))
    }
    @Test fun disabledWithStaleActiveFlagIsCleared() {
        assertEquals(GpsBootRecoveryRules.State(false,false,false),boot(false,true,false))
    }
    @Test fun previouslyPendingResumeSurvivesAnotherBoot() {
        assertEquals(GpsBootRecoveryRules.State(false,false,true),boot(false,false,true))
    }
    @Test fun pendingResumeClearsStaleActiveFlag() {
        assertEquals(GpsBootRecoveryRules.State(false,false,true),boot(false,true,true))
    }
    @Test fun enabledAndPendingInactiveRemainsPending() {
        assertEquals(GpsBootRecoveryRules.State(false,false,true),boot(true,false,true))
    }
    @Test fun enabledAndPendingActiveRemainsPending() {
        assertEquals(GpsBootRecoveryRules.State(false,false,true),boot(true,true,true))
    }
    @Test fun repeatingBootIsIdempotent() {
        val once=boot(true,true,false)
        assertEquals(once,GpsBootRecoveryRules.afterBoot(once))
    }
    @Test fun bootNeverEnablesGPSForAnyPersistedState() {
        for(e in listOf(false,true)) for(a in listOf(false,true))
            for(p in listOf(false,true)) assertFalse(boot(e,a,p).enabled)
    }
    @Test fun bootNeverMarksServiceActiveForAnyPersistedState() {
        for(e in listOf(false,true)) for(a in listOf(false,true))
            for(p in listOf(false,true)) assertFalse(boot(e,a,p).active)
    }
    @Test fun resumePendingIsEquivalentToEnabledOrAlreadyPending() {
        for(e in listOf(false,true)) for(a in listOf(false,true))
            for(p in listOf(false,true)) assertEquals(e||p,boot(e,a,p).resumePending)
    }
}
