package com.r3gate.app

import android.content.Context

internal object GpsServiceStatusUi {
    fun read(context: Context): String = when (GpsStatusReader.read(context, pendingProposalCount = 0).status) {
        GpsStatusContract.Status.OFF -> "GPS: wyłączony"
        GpsStatusContract.Status.ACTIVE -> "GPS: aktualny pomiar"
        GpsStatusContract.Status.WAITING_FOR_FIX -> "GPS: brak aktualnego pomiaru"
        GpsStatusContract.Status.UNAVAILABLE -> "GPS: źródło niedostępne"
        GpsStatusContract.Status.PERMISSION_REQUIRED -> "GPS: brak pozwolenia"
        GpsStatusContract.Status.ERROR -> "GPS: sprawdź diagnostykę"
    }
}
