package com.r3gate.app

/**
 * A work-stage confirmation needs a recent trustworthy GPS fix, not only
 * an old persisted geofence event or a still-visible notification.
 *
 * A fix before the event cannot verify that the geofence is still valid.
 */
object CoreGpsFixFreshness {
    const val MAX_LAST_FIX_AGE_MS = 2 * 60_000L

    fun isFresh(eventMs: Long, fixMs: Long, nowMs: Long): Boolean =
        eventMs > 0L && fixMs >= eventMs && nowMs >= fixMs &&
            nowMs - fixMs <= MAX_LAST_FIX_AGE_MS
}
