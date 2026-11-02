package com.r3gate.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Boot never starts location tracking without a visible user action. */
class GpsBootReceiver : BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent?) {
        if(intent?.action!=Intent.ACTION_BOOT_COMPLETED) return
        val prefs=context.getSharedPreferences("r3_gate",Context.MODE_PRIVATE)
        if(prefs.getBoolean("gps_enabled",false)) {
            prefs.edit().putBoolean("gps_enabled",false)
                .putBoolean("gps_service_active",false)
                .putBoolean("gps_resume_pending",true).commit()
        }
    }
}
