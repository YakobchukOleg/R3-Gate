package com.r3gate.app

/**
 * Theme-independent status for Core, Home and adaptive widgets.
 * Does not expose coordinates or edit authoritative work records.
 */
object GpsStatusContract {
    enum class Status { OFF, ACTIVE, WAITING_FOR_FIX, UNAVAILABLE, PERMISSION_REQUIRED, ERROR }

    data class Snapshot(
        val status:Status,
        val confirmedObjectId:String?,
        val diagnostics:String?,
        val lastTrustedFixMs:Long?,
        val pendingProposalCount:Int
    )

    fun sessionSnapshot(
        sessionActive: Boolean,
        trackingAllowed: Boolean,
        locationPermission: Boolean,
        providerUsable: Boolean,
        sessionStartMs: Long,
        lastFixMs: Long,
        nowMs: Long,
        problem: String?,
        confirmedObjectId: String?,
        pendingProposalCount: Int
    ): Snapshot {
        val sessionCurrent = trackingAllowed && sessionActive && sessionStartMs in 1..nowMs
        val sessionFix = lastFixMs.takeIf { sessionCurrent && it in sessionStartMs..nowMs } ?: 0L
        return snapshot(sessionCurrent, trackingAllowed, locationPermission, providerUsable,
            sessionFix, nowMs, problem.takeIf { trackingAllowed },
            confirmedObjectId.takeIf { sessionFix > 0L }, pendingProposalCount)
    }

    fun status(
        sessionActive:Boolean,
        trackingAllowed:Boolean,
        locationPermission:Boolean,
        providerUsable:Boolean,
        lastFixMs:Long,
        nowMs:Long,
        problem:String?
    ):Status {
        if(!trackingAllowed) return Status.OFF
        if(!locationPermission || problem=="permission") return Status.PERMISSION_REQUIRED
        if(!providerUsable || problem=="no_provider" || problem=="provider_unusable")
            return Status.UNAVAILABLE
        if(!sessionActive) return if(problem.isNullOrBlank()) Status.WAITING_FOR_FIX else Status.ERROR
        if(!problem.isNullOrBlank()) return Status.ERROR
        if(nowMs<=0L || lastFixMs<=0L || lastFixMs>nowMs ||
            nowMs-lastFixMs>GpsSamplingSafety.MAX_FIX_AGE_MS) return Status.WAITING_FOR_FIX
        return Status.ACTIVE
    }

    fun snapshot(
        sessionActive:Boolean,
        trackingAllowed:Boolean,
        locationPermission:Boolean,
        providerUsable:Boolean,
        lastFixMs:Long,
        nowMs:Long,
        problem:String?,
        confirmedObjectId:String?,
        pendingProposalCount:Int
    ):Snapshot {
        val current=status(sessionActive,trackingAllowed,locationPermission,
            providerUsable,lastFixMs,nowMs,problem)
        return Snapshot(
            current,
            confirmedObjectId?.takeIf { it.isNotBlank() },
            problem?.takeIf { it.isNotBlank() },
            lastFixMs.takeIf { it>0L && nowMs>=it },
            pendingProposalCount.coerceAtLeast(0)
        )
    }
}
