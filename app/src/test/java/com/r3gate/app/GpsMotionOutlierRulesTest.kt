package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsMotionOutlierRulesTest {
    private val start=GpsWorkRules.Point(54.092,18.776,8.0,100_000)

    @Test fun impossibleTeleportIsRejectedBeforeTripOrGeofence() {
        val jumped=start.copy(latitude=54.50,timestampMs=130_000)
        assertTrue(GpsMotionOutlierRules.impossibleJump(start,jumped))
    }

    @Test fun normalDrivingRemainsAccepted() {
        val driving=start.copy(latitude=54.097,timestampMs=130_000)
        assertFalse(GpsMotionOutlierRules.impossibleJump(start,driving))
    }

    @Test fun parkedPhoneNoiseIsNotTeleport() {
        val parked=start.copy(latitude=54.09201,timestampMs=130_000)
        assertFalse(GpsMotionOutlierRules.impossibleJump(start,parked))
    }

    @Test fun outageCannotBeClassifiedAsInstantTeleport() {
        val late=start.copy(latitude=54.50,
            timestampMs=start.timestampMs+GpsTripRules.MAX_EVIDENCE_GAP_MS+1)
        assertFalse(GpsMotionOutlierRules.impossibleJump(start,late))
    }

    @Test fun simultaneousAndVeryCloseFixesDoNotProduceSpikes() {
        assertFalse(GpsMotionOutlierRules.impossibleJump(start,
            start.copy(latitude=54.50,timestampMs=100_001)))
        assertFalse(GpsMotionOutlierRules.impossibleJump(start,
            start.copy(latitude=54.50,timestampMs=100_000)))
    }

    @Test fun nonFiniteOrInvalidCoordinatesAreNotSpeedEvidence() {
        val moved=start.copy(latitude=54.50,timestampMs=130_000)
        assertFalse(GpsMotionOutlierRules.impossibleJump(start.copy(latitude=Double.NaN),moved))
        assertFalse(GpsMotionOutlierRules.impossibleJump(start,moved.copy(longitude=181.0)))
    }

    @Test fun largeAccuracyDoesNotCreateUnjustifiedTeleport() {
        val moved=start.copy(latitude=54.50,timestampMs=130_000)
        assertFalse(GpsMotionOutlierRules.impossibleJump(
            start.copy(accuracyM=Double.POSITIVE_INFINITY),moved))
    }

    @Test fun ordinaryFastMovementRemainsPossible() {
        val fast=start.copy(latitude=54.12,timestampMs=130_000)
        assertFalse(GpsMotionOutlierRules.impossibleJump(start,fast))
    }

    @Test fun teleportAfterOneSecondIsRejected() {
        assertTrue(GpsMotionOutlierRules.impossibleJump(
            start,start.copy(latitude=54.50,timestampMs=101_000)))
    }

    @Test fun teleportAfterTwoSecondsIsRejected() {
        assertTrue(GpsMotionOutlierRules.impossibleJump(
            start,start.copy(latitude=54.50,timestampMs=102_000)))
    }

    @Test fun teleportAfterFourSecondsIsRejected() {
        assertTrue(GpsMotionOutlierRules.impossibleJump(
            start,start.copy(latitude=54.50,timestampMs=104_000)))
    }

    @Test fun parkedNoiseAfterTwoSecondsIsAccepted() {
        assertFalse(GpsMotionOutlierRules.impossibleJump(
            start,start.copy(latitude=54.09201,timestampMs=102_000)))
    }

    @Test fun poorAccuracyDoesNotTurnTinyMovementIntoTeleport() {
        assertFalse(GpsMotionOutlierRules.impossibleJump(
            start.copy(accuracyM=95.0),
            start.copy(latitude=54.0925,accuracyM=95.0,timestampMs=102_000)))
    }

    @Test fun subsecondFixIsNotUsedForSpeedClassification() {
        assertFalse(GpsMotionOutlierRules.impossibleJump(
            start,start.copy(latitude=54.50,timestampMs=100_999)))
    }
}
