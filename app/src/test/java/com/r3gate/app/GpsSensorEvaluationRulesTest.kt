package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsSensorEvaluationRulesTest {
    private fun evaluate(now:Long,last:Long=0L,wake:Boolean=false) =
        GpsSensorEvaluationRules.shouldEvaluate(now,last,wake)
    @Test fun firstValidSensorObservationEvaluates() { assertTrue(evaluate(1_000L)) }
    @Test fun duplicateTimestampThrottled() { assertFalse(evaluate(1_000L,1_000L)) }
    @Test fun oneMillisecondAfterThrottled() { assertFalse(evaluate(1_001L,1_000L)) }
    @Test fun 999MillisecondsAfterThrottled() { assertFalse(evaluate(1_999L,1_000L)) }
    @Test fun oneSecondBoundaryEvaluates() { assertTrue(evaluate(2_000L,1_000L)) }
    @Test fun laterSensorEvaluationAccepted() { assertTrue(evaluate(2_001L,1_000L)) }
    @Test fun wakeBypassesThrottle() { assertTrue(evaluate(1_001L,1_000L,true)) }
    @Test fun wakeBypassesDuplicateTimestamp() { assertTrue(evaluate(1_000L,1_000L,true)) }
    @Test fun zeroTimeNeverEvaluates() { assertFalse(evaluate(0L,0L,true)) }
    @Test fun negativeTimeNeverEvaluates() { assertFalse(evaluate(-1L,0L,true)) }
    @Test fun clockRollbackEvaluatesImmediately() { assertTrue(evaluate(5_000L,50_000L)) }
    @Test fun clockRollbackEvenWithWakeEvaluates() { assertTrue(evaluate(5_000L,50_000L,true)) }
    @Test fun initialEvaluationWithNoPriorTimestamp() { assertTrue(evaluate(500L,0L)) }
    @Test fun initialEvaluationWithInvalidPreviousTimestamp() { assertTrue(evaluate(500L,-5L)) }
    @Test fun thresholdExactly1000() { assertEquals(1_000L,GpsSensorEvaluationRules.MIN_EVALUATION_GAP_MS) }
    @Test fun highFrequencyStreamOneCheckPerSecond() { var last=0L;var checks=0;for(now in 1_000L..2_999L step 100L){if(evaluate(now,last)){last=now;checks++}};assertEquals(2,checks) }
    @Test fun wakeDuringThrottledStreamGetsCheck() { assertFalse(evaluate(1_300L,1_000L));assertTrue(evaluate(1_400L,1_000L,true)) }
    @Test fun resetAfterWakeDelaysNextOrdinaryCheck() { assertFalse(evaluate(1_600L,1_400L));assertTrue(evaluate(2_400L,1_400L)) }
    @Test fun timestampNearMaxDoesNotOverflow() { assertTrue(evaluate(Long.MAX_VALUE,Long.MAX_VALUE-1_000L)) }
    @Test fun timestampNearMaxBelowThresholdThrottled() { assertFalse(evaluate(Long.MAX_VALUE,Long.MAX_VALUE-999L)) }
    @Test fun monotonicOneSecondCadence() { var last=1_000L;for(now in listOf(1_100L,1_400L,1_999L)){assertFalse(evaluate(now,last))};assertTrue(evaluate(2_000L,last)) }
    @Test fun sensorMotionHintNotTurnedIntoTravelProof() { assertTrue(evaluate(1_500L,1_000L,true));assertFalse(evaluate(1_500L,1_500L,false)) }
}
