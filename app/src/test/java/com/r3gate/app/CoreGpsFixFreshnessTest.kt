package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class CoreGpsFixFreshnessTest {
    private fun fresh(event:Long=1_000L,fix:Long=1_500L,now:Long=2_000L) =
        CoreGpsFixFreshness.isFresh(event,fix,now)

    @Test fun recentTrustedFixEnablesPrompt() { assertTrue(fresh()) }
    @Test fun noLastFixFailsClosed() { assertFalse(fresh(fix=0)) }
    @Test fun negativeFixFailsClosed() { assertFalse(fresh(fix=-1)) }
    @Test fun negativeEventFailsClosed() { assertFalse(fresh(event=-1)) }
    @Test fun missingEventTimeFailsClosed() { assertFalse(fresh(event=0)) }
    @Test fun fixBeforeArrivalCannotConfirmArrival() { assertFalse(fresh(event=1600)) }
    @Test fun fixAtArrivalIsEligible() { assertTrue(fresh(event=1500)) }
    @Test fun fixAfterArrivalIsEligible() { assertTrue(fresh(event=1000)) }
    @Test fun futureFixCannotConfirm() { assertFalse(fresh(fix=2001)) }
    @Test fun staleFixFromPhoneSleepIsIneligible() {
        assertFalse(fresh(now=1_500L+CoreGpsFixFreshness.MAX_LAST_FIX_AGE_MS+1))
    }
    @Test fun fixExactlyAtAgeLimitIsEligible() {
        assertTrue(fresh(now=1_500L+CoreGpsFixFreshness.MAX_LAST_FIX_AGE_MS))
    }
    @Test fun fixJustAfterAgeLimitIsIneligible() {
        assertFalse(fresh(now=1_500L+CoreGpsFixFreshness.MAX_LAST_FIX_AGE_MS+1))
    }
    @Test fun fixAtCurrentTimeIsEligible() { assertTrue(fresh(fix=2000)) }
    @Test fun fixBeyondCurrentTimeRejectsClockRollback() { assertFalse(fresh(now=1499)) }
    @Test fun longTimeSinceEventStillNeedsRecentFix() {
        assertTrue(fresh(event=1000,fix=200_000,now=200_100))
    }
    @Test fun recentlyCapturedEvidenceExpiresWithoutNewFix() {
        val event=10_000L
        assertTrue(fresh(event,event,event+1_000))
        assertFalse(fresh(event,event,event+CoreGpsFixFreshness.MAX_LAST_FIX_AGE_MS+1))
    }
    @Test fun freshAfterRestartWithPersistedFixRemainsValid() {
        val persistedFix=1_500L
        assertEquals(fresh(fix=persistedFix),fresh(fix=persistedFix))
    }
    @Test fun eventEqualFixAndNowIsValid() { assertTrue(fresh(1000,1000,1000)) }
}
