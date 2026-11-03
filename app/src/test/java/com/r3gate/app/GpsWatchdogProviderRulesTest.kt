package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsWatchdogProviderRulesTest {
    @Test fun initialRegistrationFailureIsRecoverable() {
        val s=GpsWatchdogProviderRules.status(true,false,null,"gps")
        assertEquals(GpsWatchdogProviderRules.Status.NEEDS_SUBSCRIPTION,s)
        assertTrue(GpsWatchdogProviderRules.shouldReacquire(s,false,100000L,0L))
    }
    @Test fun activeHealthyProviderNeedsNoRetry() {
        val s=GpsWatchdogProviderRules.status(true,true,"gps","gps")
        assertFalse(GpsWatchdogProviderRules.shouldReacquire(s,false,100000L,0L))
    }
    @Test fun freshFixStallCanTriggerResubscription() {
        val s=GpsWatchdogProviderRules.status(true,true,"gps","gps")
        assertTrue(GpsWatchdogProviderRules.shouldReacquire(s,true,100000L,0L))
    }
    @Test fun noProviderMustNotAttemptSubscription() {
        val s=GpsWatchdogProviderRules.status(false,false,null,null)
        assertFalse(GpsWatchdogProviderRules.shouldReacquire(s,true,100000L,0L))
    }
    @Test fun missingProviderNameCannotTriggerRequest() {
        val s=GpsWatchdogProviderRules.status(true,false,null,null)
        assertEquals(GpsWatchdogProviderRules.Status.NO_PROVIDER,s)
    }
    @Test fun providerHandoverRequiresResubscription() {
        val s=GpsWatchdogProviderRules.status(true,true,"network","gps")
        assertEquals(GpsWatchdogProviderRules.Status.NEEDS_SUBSCRIPTION,s)
    }
    @Test fun retryCooldownPreventsBusyLoop() {
        assertFalse(GpsWatchdogProviderRules.shouldReacquire(
            GpsWatchdogProviderRules.Status.NEEDS_SUBSCRIPTION,false,200000L,100000L))
    }
    @Test fun retryAllowedAfterCooldown() {
        assertTrue(GpsWatchdogProviderRules.shouldReacquire(
            GpsWatchdogProviderRules.Status.NEEDS_SUBSCRIPTION,false,280000L,100000L))
    }
    @Test fun futureAttemptTimestampPreventsRetry() {
        assertFalse(GpsWatchdogProviderRules.shouldReacquire(
            GpsWatchdogProviderRules.Status.NEEDS_SUBSCRIPTION,false,100000L,200000L))
    }
    @Test fun invalidClockPreventsRetry() {
        assertFalse(GpsWatchdogProviderRules.shouldReacquire(
            GpsWatchdogProviderRules.Status.NEEDS_SUBSCRIPTION,false,0L,0L))
    }
}
