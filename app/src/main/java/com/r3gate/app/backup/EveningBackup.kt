package com.r3gate.app.backup

import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

internal object EveningBackup {
    const val HOUR="evening_hour"
    fun delay(now:Instant,hour:Int,zone:ZoneId=ZoneId.of("Europe/Warsaw")):Long {
        require(hour in 0..23)
        val local=now.atZone(zone);var next=local.toLocalDate().atTime(hour,0).atZone(zone)
        if(!next.toInstant().isAfter(now)) next=local.toLocalDate().plusDays(1).atTime(hour,0).atZone(zone)
        return java.time.Duration.between(now,next.toInstant()).toMillis()
    }
    fun delay(context:android.content.Context):Long=delay(Instant.now(),context.getSharedPreferences(LocalBackupWorker.PREFERENCES,0).getInt(HOUR,21).coerceIn(0,23))
    fun initialize(context:android.content.Context) {
        val p=context.getSharedPreferences("workmeter_diagnostics",0)
        if(p.getBoolean("evening_initialized",false)) return
        LocalBackups.updateSchedule(context,LocalBackups.Schedule(true,24,7,21))
        context.getSharedPreferences("r3_gate",0).edit().putBoolean("dailyBackupEnabled",true).apply()
        p.edit().putBoolean("evening_initialized",true).apply()
    }
}
