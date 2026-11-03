package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsWatchdogClockRollbackRecoveryTest {
    private val w=GpsWatchdogRules
    private val p=GpsWatchdogProviderRules
    private val future=500_000L
    private val now=400_000L
    private fun watchdog(nowMs:Long=now,probeMs:Long=future,
        fixMs:Long=1_000L,startedMs:Long=1_000L,tracking:Boolean=true,
        provider:Boolean=true)=w.decide(nowMs,startedMs,fixMs,probeMs,tracking,provider)
    private fun canRetry(nowMs:Long=now,attemptMs:Long=future,
        status:GpsWatchdogProviderRules.Status=GpsWatchdogProviderRules.Status.NEEDS_SUBSCRIPTION,
        stalled:Boolean=false)=p.shouldReacquire(status,stalled,nowMs,attemptMs)

    @Test fun significantRollbackAllowsStaleFixProbe() {
        assertEquals(GpsWatchdogRules.Action.PROBE,watchdog())
    }
    @Test fun significantRollbackAllowsMissingSubscriptionRetry() {
        assertTrue(canRetry())
    }
    @Test fun significantRollbackAllowsActiveStalledProviderRetry() {
        assertTrue(canRetry(status=GpsWatchdogProviderRules.Status.ACTIVE,stalled=true))
    }
    @Test fun significantRollbackDoesNotResubscribeHealthyStream() {
        assertFalse(canRetry(status=GpsWatchdogProviderRules.Status.ACTIVE,stalled=false))
    }
    @Test fun futureCheckpointOneMsBeforeResetThresholdStillThrottlesFix() {
        val probe=now+w.CLOCK_ROLLBACK_RESET_MS-1
        assertEquals(GpsWatchdogRules.Action.NONE,watchdog(probeMs=probe))
    }
    @Test fun futureCheckpointAtResetThresholdAllowsFixProbe() {
        val probe=now+w.CLOCK_ROLLBACK_RESET_MS
        assertEquals(GpsWatchdogRules.Action.PROBE,watchdog(probeMs=probe))
    }
    @Test fun futureCheckpointOneMsBeforeResetThresholdThrottlesSubscription() {
        assertFalse(canRetry(attemptMs=now+w.CLOCK_ROLLBACK_RESET_MS-1))
    }
    @Test fun futureCheckpointAtResetThresholdAllowsSubscription() {
        assertTrue(canRetry(attemptMs=now+w.CLOCK_ROLLBACK_RESET_MS))
    }
    @Test fun recentPastAttemptStillThrottlesWatchdog() {
        assertEquals(GpsWatchdogRules.Action.NONE,watchdog(probeMs=now-1_000L))
    }
    @Test fun recentPastAttemptStillThrottlesSubscription() {
        assertFalse(canRetry(attemptMs=now-1_000L))
    }
    @Test fun retryCooldownDeadlineAllowsWatchdog() {
        assertEquals(GpsWatchdogRules.Action.PROBE,
            watchdog(probeMs=now-w.RETRY_COOLDOWN_MS))
    }
    @Test fun retryCooldownDeadlineAllowsSubscription() {
        assertTrue(canRetry(attemptMs=now-w.RETRY_COOLDOWN_MS))
    }
    @Test fun missingProviderStillCannotProbe() {
        assertEquals(GpsWatchdogRules.Action.NONE,watchdog(provider=false))
    }
    @Test fun missingProviderStillCannotSubscribe() {
        assertFalse(canRetry(status=GpsWatchdogProviderRules.Status.NO_PROVIDER))
    }
    @Test fun trackingDisabledStillCannotProbe() {
        assertEquals(GpsWatchdogRules.Action.NONE,watchdog(tracking=false))
    }
    @Test fun freshFixStillDoesNotProbeDespiteClockCorrection() {
        assertEquals(GpsWatchdogRules.Action.NONE,watchdog(fixMs=now-1_000L))
    }
    @Test fun invalidServiceSessionStillCannotProbe() {
        assertEquals(GpsWatchdogRules.Action.NONE,watchdog(startedMs=now+1L))
    }
    @Test fun invalidClockStillCannotSubscribe() {
        assertFalse(canRetry(nowMs=0L))
    }
    @Test fun sourceClockBeforeEpochStillDoesNotProbe() {
        assertEquals(GpsWatchdogRules.Action.NONE,watchdog(nowMs=0L))
    }
    @Test fun hugeFutureProbeTimestampCanRecover() {
        assertEquals(GpsWatchdogRules.Action.PROBE,watchdog(probeMs=Long.MAX_VALUE))
    }
    @Test fun hugeFutureAttemptTimestampCanRecover() {
        assertTrue(canRetry(attemptMs=Long.MAX_VALUE))
    }
    @Test fun unchangedSourceDoesNotReprobeIfFixFresh() {
        assertEquals(GpsWatchdogRules.Action.NONE,
            watchdog(fixMs=now-w.STALL_AFTER_MS+1))
    }
}
