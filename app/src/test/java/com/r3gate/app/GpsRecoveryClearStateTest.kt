package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsRecoveryClearStateTest {
    private fun clear(s:String?)=GpsRecoveryMonitorRules.recoveredByTrustedFix(s)

    @Test fun stalledServiceWarningClearsOnTrustedFix() { assertTrue(clear("stalled")) }
    @Test fun missingGpsFixWarningClearsOnTrustedFix() { assertTrue(clear("no_fix")) }
    @Test fun locationProviderOffWarningClearsOnTrustedFix() { assertTrue(clear("no_provider")) }
    @Test fun restoredPermissionWarningClearsOnTrustedFix() { assertTrue(clear("permission")) }
    @Test fun providerSubscriptionWarningClearsOnTrustedFix() { assertTrue(clear("provider")) }
    @Test fun transientRuntimeFailureClearsOnTrustedFix() { assertTrue(clear("runtime")) }
    @Test fun nullWarningIsUntouched() { assertFalse(clear(null)) }
    @Test fun blankWarningIsUntouched() { assertFalse(clear("")) }
    @Test fun unspecifiedWarningIsNotSilentlyCleared() { assertFalse(clear("unknown")) }
    @Test fun unrelatedApplicationWarningIsNotCleared() { assertFalse(clear("backup")) }
    @Test fun caseDoesNotHideUnknownWarning() { assertFalse(clear("STALLED")) }
    @Test fun partialWarningIsNotCleared() { assertFalse(clear("no_fix_extra")) }
}
