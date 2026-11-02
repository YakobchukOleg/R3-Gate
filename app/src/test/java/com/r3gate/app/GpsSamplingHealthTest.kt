package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsSamplingHealthTest {
    @Test fun stoppedServiceDoesNotClaimFreshSampling() {
        assertEquals(GpsSamplingHealth.Level.NO_SESSION,
            GpsSamplingHealth.assess(false,1000,listOf(2000),3000).level)
    }
    @Test fun newSessionDoesNotReuseOldFix() {
        val x=GpsSamplingHealth.assess(true,200_000,listOf(100_000),210_000)
        assertEquals(GpsSamplingHealth.Level.WAITING_FOR_FIRST_FIX,x.level)
        assertEquals(0,x.samples)
    }
    @Test fun detectsRecentDelayedAndStaleFixes() {
        assertEquals(GpsSamplingHealth.Level.FRESH,
            GpsSamplingHealth.assess(true,1000,listOf(5000),100000).level)
        assertEquals(GpsSamplingHealth.Level.DELAYED,
            GpsSamplingHealth.assess(true,1000,listOf(5000),200000).level)
        assertEquals(GpsSamplingHealth.Level.STALE,
            GpsSamplingHealth.assess(true,1000,listOf(5000),1_000_000).level)
    }
    @Test fun duplicateAndFutureFixesAreIgnored() {
        val x=GpsSamplingHealth.assess(true,1000,listOf(5000,5000,8000,500_000),10000)
        assertEquals(2,x.samples)
        assertEquals(2000L,x.ageMs)
    }
}
