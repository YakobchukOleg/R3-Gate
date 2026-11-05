package com.r3gate.app

/** A running/retried job may send only the photos explicitly requested by the user. */
internal object PhotoSendSelection {
    fun shouldRequest(saved: Set<String>, previous: Set<String>, queued: Boolean, failed: Boolean): Boolean =
        saved.isNotEmpty() && (!queued || failed || saved != previous)

    fun eligible(saved: List<String>, requested: Set<String>): List<String> =
        saved.filter { it.isNotBlank() && it in requested }.distinct()
}
