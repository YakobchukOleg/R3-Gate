package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsLocationTimestampOverflowTest {
    private fun valid(now:Long,fix:Long):Boolean =
        GpsLocationQuality.acceptable(now,fix,10.0,54.35,18.65)

    @Test fun nearMaxTimeCurrentFixIsAccepted() {
        assertTrue(valid(Long.MAX_VALUE,Long.MAX_VALUE))
    }
    @Test fun nearMaxTimeRecentFixIsAccepted() {
        assertTrue(valid(Long.MAX_VALUE,Long.MAX_VALUE-120_000L))
    }
    @Test fun nearMaxTimeExpiredFixIsRejected() {
        assertFalse(valid(Long.MAX_VALUE,Long.MAX_VALUE-120_001L))
    }
    @Test fun nearMaxTimeFutureFixWithinSkewAccepted() {
        assertTrue(valid(Long.MAX_VALUE-10_000L,Long.MAX_VALUE))
    }
    @Test fun nearMaxTimeFutureFixBeyondSkewRejected() {
        assertFalse(valid(Long.MAX_VALUE-10_001L,Long.MAX_VALUE))
    }
    @Test fun futureTimestampAtBoundaryAccepted() {
        assertTrue(valid(1_000_000L,1_010_000L))
    }
    @Test fun futureTimestampOneMillisecondTooFarRejected() {
        assertFalse(valid(1_000_000L,1_010_001L))
    }
    @Test fun staleFixAtBoundaryAccepted() {
        assertTrue(valid(1_000_000L,880_000L))
    }
    @Test fun staleFixOneMillisecondTooOldRejected() {
        assertFalse(valid(1_000_000L,879_999L))
    }
    @Test fun sourceTimestampZeroRejected() {
        assertFalse(valid(1_000_000L,0L))
    }
    @Test fun currentClockZeroRejected() {
        assertFalse(valid(0L,1L))
    }
    @Test fun minPositiveNowAndFixAccepted() {
        assertTrue(valid(1L,1L))
    }
}
