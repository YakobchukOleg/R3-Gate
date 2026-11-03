package com.r3gate.app

/**
 * Coalesce expensive adaptive-cadence / provider checks triggered by accelerometer
 * samples. Process EVERY sample as motion evidence; only throttle re-evaluation.
 * Never delay a confirmed wake hint, or carry throttle state across clock rollback.
 */
object GpsSensorEvaluationRules {
    const val MIN_EVALUATION_GAP_MS=1_000L

    fun shouldEvaluate(nowMs:Long,lastEvaluationMs:Long,motionWake:Boolean):Boolean =
        nowMs>0L && (motionWake || lastEvaluationMs<=0L ||
            nowMs<lastEvaluationMs ||
            nowMs-lastEvaluationMs>=MIN_EVALUATION_GAP_MS)
}
