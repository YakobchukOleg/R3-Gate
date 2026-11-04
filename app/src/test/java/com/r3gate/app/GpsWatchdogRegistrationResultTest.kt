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

    @Test fun matchingProviderAndIntervalConfirmRestoredCadence() {
        assertTrue(rules.cadenceApplied("gps",true,"gps",30_000L,30_000L))
    }
    @Test fun matchingNetworkProviderAndIntervalConfirmRestoredCadence() {
        assertTrue(rules.cadenceApplied("network",true,"network",90_000L,90_000L))
    }
    @Test fun staleSamplingIntervalDoesNotConfirmRecovery() {
        assertFalse(rules.cadenceApplied("gps",true,"gps",15_000L,90_000L))
    }
    @Test fun missingRegistrationDoesNotConfirmRecovery() {
        assertFalse(rules.cadenceApplied("gps",false,"gps",15_000L,15_000L))
    }
    @Test fun changedProviderDoesNotConfirmRecovery() {
        assertFalse(rules.cadenceApplied("gps",true,"network",30_000L,30_000L))
    }
    @Test fun noSelectedProviderDoesNotConfirmRecovery() {
        assertFalse(rules.cadenceApplied(null,true,"gps",30_000L,30_000L))
    }
    @Test fun invalidRequestedIntervalDoesNotConfirmRecovery() {
        assertFalse(rules.cadenceApplied("gps",true,"gps",0L,0L))
    }
    @Test fun oldFastIntervalDoesNotConfirmQuietRestoration() {
        assertFalse(rules.cadenceApplied("gps",true,"gps",90_000L,15_000L))
    }
}
