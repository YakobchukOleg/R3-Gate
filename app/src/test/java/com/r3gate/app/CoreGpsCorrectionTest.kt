package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class CoreGpsCorrectionTest {
    private val arrival = CoreGpsStageRules.Proposal.ARRIVE_OBJECT
    private val travel = CoreGpsStageRules.Proposal.START_TRAVEL
    private val objects = setOf("observed", "manual")

    @Test fun explicitAlternateDestinationIsAllowedOnlyWhenActive() {
        assertTrue(CoreGpsStageRules.canConfirmCorrection(arrival,"manual",objects,100,150,200,emptyList()))
        assertFalse(CoreGpsStageRules.canConfirmCorrection(arrival,"archived",objects,100,150,200,emptyList()))
    }

    @Test fun transitionMustBeAfterStageStartAndNotInTheFuture() {
        for (time in listOf(99L,100L,201L))
            assertFalse(CoreGpsStageRules.canConfirmCorrection(arrival,"manual",objects,100,time,200,emptyList()))
    }

    @Test fun correctionRejectsOverlapWithRecordedWorkButAllowsTouchingBoundaries() {
        val overlap=listOf(WorkShiftIntegrity.Interval(160,180))
        val adjacent=listOf(WorkShiftIntegrity.Interval(50,100))
        assertFalse(CoreGpsStageRules.canConfirmCorrection(arrival,"manual",objects,100,150,200,overlap))
        assertTrue(CoreGpsStageRules.canConfirmCorrection(arrival,"manual",objects,100,150,200,adjacent))
    }

    @Test fun departureDoesNotReplaceTheConfirmedOriginWithADifferentObject() {
        assertFalse(CoreGpsStageRules.canConfirmCorrection(travel,"manual",objects,100,150,200,emptyList()))
        assertTrue(CoreGpsStageRules.canConfirmCorrection(travel,null,objects,100,150,200,emptyList()))
    }
}
