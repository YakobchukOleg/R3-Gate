package com.r3gate.app

import java.util.Locale

/** Five-minute ceiling for recorded shift endings and reported work totals. */
internal object WorkTimeRounding {
    const val STEP_MILLIS = 5L * 60_000L
    fun end(timestamp: Long): Long {
        require(timestamp >= 0L)
        val remainder = timestamp % STEP_MILLIS
        if (remainder == 0L) return timestamp
        return Math.addExact(timestamp, STEP_MILLIS - remainder)
    }
    fun minutes(elapsed: Long): Long = if (elapsed <= 0L) 0L else ((elapsed - 1L) / STEP_MILLIS + 1L) * 5L
    fun format(elapsed: Long): String {
        val minutes = minutes(elapsed)
        return String.format(Locale.ROOT, "%02d:%02d", minutes / 60L, minutes % 60L)
    }
}
