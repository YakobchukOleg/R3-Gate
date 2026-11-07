package com.r3gate.app

/** A checkmark means a previously verified backup, never inferred network/GPS success. */
internal object WidgetCloudEvidence {
    fun verified(connected:Boolean,account:String?,verifiedAccount:String?,verifiedAt:String?,now:Long):Boolean {
        if(!connected || account.isNullOrBlank() || !account.equals(verifiedAccount,true)) return false
        val whenSaved=runCatching { java.time.Instant.parse(verifiedAt).toEpochMilli() }.getOrNull() ?: return false
        return whenSaved>0 && whenSaved<=now
    }
}
