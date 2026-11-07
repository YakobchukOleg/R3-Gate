package com.r3gate.app

import android.content.Context

internal object GpsServiceStatusUi {
    fun read(context: Context): String = context.getString(when (GpsStatusReader.read(context, pendingProposalCount = 0).status) {
        GpsStatusContract.Status.OFF -> R.string.gps_status_off
        GpsStatusContract.Status.ACTIVE -> R.string.gps_status_active
        GpsStatusContract.Status.WAITING_FOR_FIX -> R.string.gps_status_waiting
        GpsStatusContract.Status.UNAVAILABLE -> R.string.gps_status_unavailable
        GpsStatusContract.Status.PERMISSION_REQUIRED -> R.string.gps_status_permission
        GpsStatusContract.Status.ERROR -> R.string.gps_status_error
    })
}
