package com.r3gate.app

/**
 * Bluetooth is an optional, local hint to briefly check whether a trip started.
 * Never classifies a trip or changes a time sheet without independent GPS evidence.
 */
object GpsBluetoothDepartureRules {
    const val DEFAULT_CONFIRM_DELAY_MS = 20_000L
    const val MAX_CONFIRM_WINDOW_MS = 3*60_000L
    const val COOLDOWN_MS = 10*60_000L

    data class State(val connectedAtMs:Long=0L,val deviceId:String?=null,
        val lastPromptAtMs:Long=0L)

    /** The user must explicitly choose the car's paired Bluetooth device. */
    fun connected(state:State,deviceId:String?,approvedDeviceId:String?,
                  nowMs:Long,enabled:Boolean):State {
        if(!enabled || nowMs<=0 || deviceId.isNullOrBlank() ||
            approvedDeviceId.isNullOrBlank() || deviceId!=approvedDeviceId ||
            state.connectedAtMs>0 ||
            (state.lastPromptAtMs>0 && nowMs-state.lastPromptAtMs<COOLDOWN_MS))
            return state
        return state.copy(connectedAtMs=nowMs,deviceId=deviceId)
    }

    fun disconnected(state:State,deviceId:String?):State =
        if(state.deviceId!=null && state.deviceId==deviceId)
            state.copy(connectedAtMs=0L,deviceId=null) else state

    /** Only requests a GPS verification prompt, never starts a background FGS. */
    fun shouldVerify(state:State,nowMs:Long,trackingAllowed:Boolean):Boolean =
        trackingAllowed && nowMs>0 && state.connectedAtMs>0 &&
            nowMs>=state.connectedAtMs &&
            nowMs-state.connectedAtMs in DEFAULT_CONFIRM_DELAY_MS..MAX_CONFIRM_WINDOW_MS &&
            (state.lastPromptAtMs==0L || nowMs-state.lastPromptAtMs>=COOLDOWN_MS)

    fun prompted(state:State,nowMs:Long):State =
        if(!shouldVerify(state,nowMs,true)) state else
            state.copy(connectedAtMs=0L,deviceId=null,lastPromptAtMs=nowMs)

    /** Only a real GPS-confirmed departure from the same worksite may suggest travel. */
    fun travelCandidate(verificationRequested:Boolean,gpsDepartureConfirmed:Boolean,
                        leftKnownWorksite:Boolean):Boolean =
        verificationRequested && gpsDepartureConfirmed && leftKnownWorksite
}
