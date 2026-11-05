package com.r3gate.app

/** A running/retried job may send only the photos explicitly requested by the user. */
internal object PhotoSendSelection {
    fun eligible(saved: List<String>, requested: Set<String>): List<String> =
        saved.filter { it.isNotBlank() && it in requested }.distinct()
}
