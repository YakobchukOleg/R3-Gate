package com.r3gate.app

/**
 * Local GPS-to-work-stage prompts are only valid while user-enabled
 * tracking belongs to an open, non-private work shift. This is independent
 * of the GPS service's actual lifecycle, which is managed elsewhere.
 */
object CoreGpsSessionGate {
    data class Snapshot(
        val gpsEnabled: Boolean,
        val shiftOpen: Boolean,
        val shiftKind: String,
        val shiftStartMs: Long,
        val stageStartMs: Long,
        val nowMs: Long
    )

    fun isEligible(state: Snapshot): Boolean =
        state.gpsEnabled && state.shiftOpen &&
            (WorkPlaceRules.isOnSiteStage(state.shiftKind) || state.shiftKind == "Przejazd") &&
            state.shiftStartMs > 0L &&
            state.stageStartMs >= state.shiftStartMs &&
            state.nowMs >= state.stageStartMs
}
