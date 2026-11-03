package com.r3gate.app

import org.junit.Assert.assertEquals
import org.junit.Test

class GpsWatchdogLifecycleRulesTest {
    private fun decide(started:Boolean,enabled:Boolean) =
        GpsWatchdogLifecycleRules.onTick(started,enabled)

    @Test fun runningAndEnabledContinuesChecking() {
        assertEquals(GpsWatchdogLifecycleRules.Action.TICK,decide(true,true))
    }
    @Test fun runningButDisabledStopsService() {
        assertEquals(GpsWatchdogLifecycleRules.Action.STOP,decide(true,false))
    }
    @Test fun neverStartedAndDisabledDoesNotInvokeStop() {
        assertEquals(GpsWatchdogLifecycleRules.Action.IGNORE,decide(false,false))
    }
    @Test fun notStartedButEnabledDoesNotStartInBackground() {
        assertEquals(GpsWatchdogLifecycleRules.Action.IGNORE,decide(false,true))
    }
    @Test fun reenabledRunningServiceCanContinue() {
        assertEquals(GpsWatchdogLifecycleRules.Action.STOP,decide(true,false))
        assertEquals(GpsWatchdogLifecycleRules.Action.TICK,decide(true,true))
    }
    @Test fun afterServiceShutdownTickIsIgnored() {
        assertEquals(GpsWatchdogLifecycleRules.Action.STOP,decide(true,false))
        assertEquals(GpsWatchdogLifecycleRules.Action.IGNORE,decide(false,false))
    }
    @Test fun disabledGpsNeverContinuesSampling() {
        for(started in listOf(true,false))
            assertEquals(false,decide(started,false)==GpsWatchdogLifecycleRules.Action.TICK)
    }
    @Test fun lifecycleRulesNeverStartService() {
        for(started in listOf(true,false)) for(enabled in listOf(true,false))
            assertEquals(false,decide(started,enabled)==GpsWatchdogLifecycleRules.Action.IGNORE &&
                started)
    }
}
