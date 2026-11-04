package com.r3gate.app

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class WorkShiftEditTest {
    private val zone = ZoneId.of("Europe/Warsaw")

    @Test fun autumnRepeatedHourKeepsOriginalOccurrence() {
        val repeated = LocalDateTime.of(2026, 10, 25, 2, 30)
        val earlier = repeated.toInstant(ZoneOffset.ofHours(2)).toEpochMilli()
        val later = repeated.toInstant(ZoneOffset.ofHours(1)).toEpochMilli()
        assertEquals(earlier, WorkShiftIntegrity.resolveEditTime(repeated, zone, earlier))
        assertEquals(later, WorkShiftIntegrity.resolveEditTime(repeated, zone, later))
    }

    @Test fun springClockGapRequiresAnotherTime() {
        val nonexistent = LocalDateTime.of(2026, 3, 29, 2, 30)
        assertNull(WorkShiftIntegrity.resolveEditTime(nonexistent, zone, 0L))
    }

    @Test fun openShiftBlocksOverlapButAllowsAdjacentHistory() {
        val open = WorkShiftIntegrity.Interval(50L, Long.MAX_VALUE)
        assertTrue(WorkShiftIntegrity.canSave(WorkShiftIntegrity.Interval(20L, 50L), listOf(open)))
        assertFalse(WorkShiftIntegrity.canSave(WorkShiftIntegrity.Interval(20L, 51L), listOf(open)))
    }

    @Test fun trimmingUnrecordedGapsPreservesStages() {
        val original = WorkShiftIntegrity.Interval(10L, 60L)
        val stage = WorkShiftIntegrity.Interval(20L, 50L)
        assertTrue(WorkShiftIntegrity.preservesRecordedStages(original, stage, listOf(stage)))
        assertFalse(WorkShiftIntegrity.preservesRecordedStages(original,
            WorkShiftIntegrity.Interval(21L, 50L), listOf(stage)))
    }

    @Test fun preexistingOutOfBoundsStageDoesNotBlockUnchangedCoverage() {
        val original = WorkShiftIntegrity.Interval(10L, 60L)
        val legacy = WorkShiftIntegrity.Interval(0L, 30L)
        assertTrue(WorkShiftIntegrity.preservesRecordedStages(original,
            WorkShiftIntegrity.Interval(10L, 55L), listOf(legacy)))
    }

    @Test fun stagesCanBeAddedInAnUnassignedGapAndMeetAtBoundaries() {
        val shift=WorkShiftIntegrity.Interval(10L,60L)
        val others=listOf(WorkShiftIntegrity.Interval(10L,20L),WorkShiftIntegrity.Interval(40L,60L))
        assertTrue(WorkShiftIntegrity.canSaveStage(shift,WorkShiftIntegrity.Interval(20L,40L),others))
    }

    @Test fun stagesCannotOverlapOrExtendOutsideTheShift() {
        val shift=WorkShiftIntegrity.Interval(10L,60L)
        val others=listOf(WorkShiftIntegrity.Interval(20L,40L))
        assertFalse(WorkShiftIntegrity.canSaveStage(shift,WorkShiftIntegrity.Interval(39L,50L),others))
        assertFalse(WorkShiftIntegrity.canSaveStage(shift,WorkShiftIntegrity.Interval(9L,20L),others))
        assertFalse(WorkShiftIntegrity.canSaveStage(shift,WorkShiftIntegrity.Interval(50L,61L),others))
        assertFalse(WorkShiftIntegrity.canSaveStage(shift,WorkShiftIntegrity.Interval(41L,41L),others))
    }

    @Test fun invalidLegacyStageCanBeRepairedWithoutErasingOtherInvalidHistory() {
        val shift=WorkShiftIntegrity.Interval(10L,60L)
        val others=listOf(WorkShiftIntegrity.Interval(-1L,20L),WorkShiftIntegrity.Interval(50L,60L))
        assertTrue(WorkShiftIntegrity.canSaveStage(shift,WorkShiftIntegrity.Interval(10L,50L),others))
    }

    @Test fun clockRollbackCannotOverwriteTheActiveStageBoundary() {
        assertFalse(WorkShiftIntegrity.canCloseCurrentStage(10,20,19,emptyList()))
        assertFalse(WorkShiftIntegrity.canCloseCurrentStage(10,9,21,emptyList()))
        assertTrue(WorkShiftIntegrity.canCloseCurrentStage(10,20,21,emptyList()))
    }

    @Test fun closingAStageRequiresNoOverlapWithRecordedHistory() {
        val existing=listOf(WorkShiftIntegrity.Interval(10,30))
        assertFalse(WorkShiftIntegrity.canCloseCurrentStage(10,20,40,existing))
        assertTrue(WorkShiftIntegrity.canCloseCurrentStage(10,30,40,existing))
    }
}
