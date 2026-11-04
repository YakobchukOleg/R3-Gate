package com.r3gate.app

import java.time.*

object TimeAnalytics {
    data class Stage(val start:Long,val end:Long,val category:Int)
    data class Shift(val start:Long,val end:Long,val stages:List<Stage>)
    const val PERSONAL = 4
    data class Result(val totals:LongArray,val hours:Array<LongArray>,val personalMillis:Long=0L)

    enum class Period { TODAY, CURRENT_WEEK, PREVIOUS_WEEK, CURRENT_MONTH, PREVIOUS_MONTH, CUSTOM }

    fun previousWeek(today: LocalDate): Pair<LocalDate, LocalDate> =
        week(today.minusWeeks(1))

    fun previousMonth(today: LocalDate): Pair<LocalDate, LocalDate> =
        month(today.minusMonths(1))

    fun dateRange(
        period: Period,
        today: LocalDate,
        custom: Pair<LocalDate, LocalDate>? = null
    ): Pair<LocalDate, LocalDate>? = when (period) {
        Period.TODAY -> today to today
        Period.CURRENT_WEEK -> week(today)
        Period.PREVIOUS_WEEK -> previousWeek(today)
        Period.CURRENT_MONTH -> month(today)
        Period.PREVIOUS_MONTH -> previousMonth(today)
        Period.CUSTOM -> custom?.takeIf { it.first <= it.second }
    }

    private fun week(day: LocalDate): Pair<LocalDate, LocalDate> {
        val monday = day.minusDays((day.dayOfWeek.value - 1).toLong())
        return monday to monday.plusDays(6)
    }

    private fun month(day: LocalDate): Pair<LocalDate, LocalDate> =
        day.withDayOfMonth(1) to day.withDayOfMonth(day.lengthOfMonth())

    private data class Slice(val start: Long, val end: Long, val category: Int, val stageStart: Long?)
    private data class Boundary(val time: Long, val sliceIndex: Int, val begins: Boolean)

    fun calculate(shifts:List<Shift>,from:LocalDate,to:LocalDate,zone:ZoneId):Result {
        val totals = LongArray(4)
        val hours = Array(24) { LongArray(4) }
        if (to < from) return Result(totals, hours)
        val lower = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val upper = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val slices = buildList {
            for (shift in shifts) {
                if (!WorkShiftIntegrity.isValid(shift.start, shift.end)) continue
                val start = maxOf(shift.start, lower)
                val end = minOf(shift.end, upper)
                if (end <= start) continue
                add(Slice(start, end, 3, null))
                for (stage in shift.stages) {
                    if (!WorkShiftIntegrity.isValid(stage.start, stage.end)) continue
                    val stageStart = maxOf(stage.start, start)
                    val stageEnd = minOf(stage.end, end)
                    if (stageEnd > stageStart)
                        add(Slice(stageStart, stageEnd, stage.category.takeIf { it in 0..PERSONAL } ?: 3, stage.start))
                }
            }
        }
        val boundaries = slices.flatMapIndexed { index, slice ->
            listOf(Boundary(slice.start, index, true), Boundary(slice.end, index, false))
        }.sortedBy { it.time }
        // Prefer recorded stages to unclassified gaps, then preserve latest-stage precedence.
        // A recorded personal interval must not appear as work, even in conflicting legacy shifts.
        val active = java.util.TreeSet(compareBy<Int> { slices[it].category == PERSONAL }
            .thenBy { slices[it].stageStart ?: Long.MIN_VALUE }.thenByDescending { it })
        var previous = lower
        var personalMillis = 0L
        for (boundary in boundaries) {
            if (previous < boundary.time && active.isNotEmpty()) {
                val category = slices[active.last()].category
                if (category == PERSONAL) {
                    personalMillis += boundary.time - previous
                } else {
                    totals[category] += boundary.time - previous
                    addHours(hours, category, previous, boundary.time, zone)
                }
            }
            if (boundary.begins) active.add(boundary.sliceIndex) else active.remove(boundary.sliceIndex)
            previous = boundary.time
        }
        return Result(totals, hours, personalMillis)
    }

    private fun addHours(hours: Array<LongArray>, category: Int, start: Long, end: Long, zone: ZoneId) {
        var cursor = start
        while (cursor < end) {
            val instant = Instant.ofEpochMilli(cursor)
            val local = instant.atZone(zone)
            val nextHour = local.toLocalDateTime().withMinute(0).withSecond(0).withNano(0)
                .plusHours(1).toInstant(local.offset).toEpochMilli()
            val transition = zone.rules.nextTransition(instant)?.instant?.toEpochMilli() ?: Long.MAX_VALUE
            val stop = minOf(end, nextHour, transition)
            hours[local.hour][category] += stop - cursor
            cursor = stop
        }
    }
}
