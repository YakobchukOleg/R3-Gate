package com.r3gate.app

/**
 * Different location providers can disagree by tens of metres.
 * Never use fixes from two different providers as contiguous movement evidence.
 */
object GpsSourceTransitionRules {
    data class Decision(val resetZoneCandidate:Boolean,val resetMotion:Boolean,val providerChanged:Boolean)
    fun compare(previousProvider:String?,newProvider:String?):Decision {
        if(previousProvider.isNullOrBlank() || newProvider.isNullOrBlank() ||
            previousProvider==newProvider) return Decision(false,false,false)
        return Decision(true,true,true)
    }
}
