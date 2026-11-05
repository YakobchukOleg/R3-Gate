package com.r3gate.app

import android.content.Context

/**
 * Android adapter for the Core-only proposal inbox. Do not place events in
 * "data" (confirmed work journal) or in the GPS service's existing keys.
 * An explicit backup contract is required before exporting this separate key.
 */
class AndroidCoreGpsProposalStorage(context: Context) : CoreGpsProposalStore.Storage {
    private val prefs = context.applicationContext.getSharedPreferences("r3_gate", Context.MODE_PRIVATE)

    override fun read(key: String): String? = prefs.getString(key, null)

    override fun write(key: String, value: String): Boolean =
        prefs.edit().putString(key, value).commit()
}
