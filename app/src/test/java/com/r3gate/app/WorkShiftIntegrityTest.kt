package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class WorkShiftIntegrityTest {
    private fun interval(start: Long, end: Long) = WorkShiftIntegrity.Interval(start, end)

    @Test fun rejectsEmptyOrReversedShifts() {
        assertFalse(WorkShiftIntegrity.isValid(100,100))
        assertFalse(WorkShiftIntegrity.isValid(200,100))
        assertFalse(WorkShiftIntegrity.isValid(-1,100))
        assertTrue(WorkShiftIntegrity.isValid(100,200))
    }

    @Test fun adjacentShiftsAreAllowed() {
        assertTrue(WorkShiftIntegrity.canSave(interval(200,300),listOf(interval(100,200))))
        assertTrue(WorkShiftIntegrity.canSave(interval(50,100),listOf(interval(100,200))))
    }

    @Test fun overlapIsRejectedEvenIfProposedShiftContainsAnother() {
        val previous=listOf(interval(100,200))
        assertFalse(WorkShiftIntegrity.canSave(interval(150,250),previous))
        assertFalse(WorkShiftIntegrity.canSave(interval(50,150),previous))
        assertFalse(WorkShiftIntegrity.canSave(interval(50,250),previous))
        assertFalse(WorkShiftIntegrity.canSave(interval(125,175),previous))
    }

    @Test fun overnightShiftsUseInstantTimestampsNotCalendarDay() {
        val midnight=1_800_000_000_000L
        assertTrue(WorkShiftIntegrity.canSave(interval(midnight-3_600_000, midnight+3_600_000),
            listOf(interval(midnight-7_200_000,midnight-3_600_000))))
    }

    @Test fun stagesMustBeInsideShiftAndNonOverlapping() {
        val shift=interval(100,400)
        assertTrue(WorkShiftIntegrity.stagesWithinShift(shift,listOf(interval(100,200),interval(200,300))))
        assertFalse(WorkShiftIntegrity.stagesWithinShift(shift,listOf(interval(100,201),interval(200,300))))
        assertFalse(WorkShiftIntegrity.stagesWithinShift(shift,listOf(interval(90,200))))
        assertFalse(WorkShiftIntegrity.stagesWithinShift(shift,listOf(interval(300,410))))
        assertFalse(WorkShiftIntegrity.stagesWithinShift(shift,listOf(interval(200,200))))
    }
}
