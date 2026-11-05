package com.r3gate.app

/**
 * Read-only reconciliation of legacy stage history. Never rewrites a confirmed shift.
 * Gaps and overlapping stages are reported rather than guessed or silently summed.
 */
object WorkStageHistory {
    data class Stage(val start: Long, val end: Long, val kind: String, val objectId: String = "")
    data class Slice(val start: Long, val end: Long, val kind: String, val objectId: String = "")
    data class Result(val slices: List<Slice>, val gapMillis: Long, val overlapMillis: Long, val invalidStages: Int)

    /** Diagnostics for a history screen; these values never change stored work time. */
    data class Diagnostics(
        val shiftMillis: Long,
        val attributedMillis: Long,
        val gapMillis: Long,
        val overlapMillis: Long,
        val invalidStages: Int,
        val hasWarnings: Boolean
    )

    fun diagnose(shiftStart: Long, shiftEnd: Long, stages: List<Stage>): Diagnostics {
        val inspected = inspect(shiftStart, shiftEnd, stages)
        val shiftMillis = if (shiftStart >= 0 && shiftEnd > shiftStart) shiftEnd-shiftStart else 0L
        val attributed = inspected.slices.sumOf { it.end-it.start }
        val invalidShift = shiftMillis == 0L
        return Diagnostics(
            shiftMillis, attributed, inspected.gapMillis, inspected.overlapMillis,
            inspected.invalidStages,
            invalidShift || inspected.gapMillis > 0 || inspected.overlapMillis > 0 || inspected.invalidStages > 0
        )
    }

    fun inspect(shiftStart: Long, shiftEnd: Long, stages: List<Stage>): Result {
        if (shiftStart < 0 || shiftEnd <= shiftStart) return Result(emptyList(), 0, 0, stages.size)
        val valid = stages.filter { WorkShiftIntegrity.isValid(it.start, it.end) }
        val invalid = stages.size - valid.size
        val boundaries = (listOf(shiftStart, shiftEnd) + valid.flatMap { listOf(it.start, it.end) }
            .filter { it > shiftStart && it < shiftEnd }).distinct().sorted()
        val slices = mutableListOf<Slice>()
        var gap = 0L
        var overlap = 0L
        for ((a,b) in boundaries.zipWithNext()) {
            val covering = valid.filter { it.start <= a && it.end >= b }
            if (covering.isEmpty()) { gap += b-a; continue }
            if (covering.size > 1) { overlap += b-a; continue }
            val stage = covering.single()
            slices.add(Slice(a,b,stage.kind,stage.objectId))
        }
        return Result(slices, gap, overlap, invalid)
    }
}
