package com.r3gate.app

/** Accepts only fresh source timestamps so late callbacks cannot confirm dwell. */
object GpsFixOrderRules {
    fun accept(fixTimeMs:Long,lastAcceptedFixTimeMs:Long):Boolean =
        fixTimeMs>0 && (lastAcceptedFixTimeMs<=0 || fixTimeMs>lastAcceptedFixTimeMs)
}
