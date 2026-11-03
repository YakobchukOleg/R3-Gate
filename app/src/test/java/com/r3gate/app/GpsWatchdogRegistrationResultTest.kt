package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

/** A watchdog retry is not proof that Android accepted the subscription. */
class GpsWatchdogRegistrationResultTest {
    private val rules=GpsWatchdogProviderRules

    @Test fun exactGpsRegistrationIsSuccessful() {
        assertTrue(rules.registrationSucceeded("gps",true,"gps"))
    }
    @Test fun exactNetworkRegistrationIsSuccessful() {
        assertTrue(rules.registrationSucceeded("network",true,"network"))
    }
    @Test fun emptySelectedProviderIsNotSuccessful() {
        assertFalse(rules.registrationSucceeded("",true,""))
    }
    @Test fun nullSelectedProviderIsNotSuccessful() {
        assertFalse(rules.registrationSucceeded(null,true,"gps"))
    }
    @Test fun registrationNotYetActiveIsNotSuccessful() {
        assertFalse(rules.registrationSucceeded("gps",false,"gps"))
    }
    @Test fun providerSwitchedDuringRetryIsNotSuccessful() {
        assertFalse(rules.registrationSucceeded("gps",true,"network"))
    }
    @Test fun missingCurrentProviderIsNotSuccessful() {
        assertFalse(rules.registrationSucceeded("gps",true,null))
    }
    @Test fun gpsToNetworkTransitionMustNotClaimOldProviderRestored() {
        assertTrue(rules.registrationSucceeded("gps",true,"gps"))
        assertFalse(rules.registrationSucceeded("network",true,"gps"))
        assertTrue(rules.registrationSucceeded("network",true,"network"))
    }
    @Test fun networkToGpsTransitionMustNotClaimOldProviderRestored() {
        assertTrue(rules.registrationSucceeded("network",true,"network"))
        assertFalse(rules.registrationSucceeded("gps",true,"network"))
        assertTrue(rules.registrationSucceeded("gps",true,"gps"))
    }
    @Test fun providerUnavailableDuringRetryMustNotClaimRecovery() {
        assertFalse(rules.registrationSucceeded("gps",false,null))
        assertFalse(rules.registrationSucceeded(null,false,null))
    }
    @Test fun previouslyActiveSubscriptionDoesNotMaskMissingNewSubscription() {
        assertTrue(rules.registrationSucceeded("gps",true,"gps"))
        assertFalse(rules.registrationSucceeded("gps",false,null))
    }
    @Test fun caseMismatchIsNotSuccessful() {
        assertFalse(rules.registrationSucceeded("gps",true,"GPS"))
    }
}
