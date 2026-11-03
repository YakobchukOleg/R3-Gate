package com.r3gate.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * WorkManager may run after the GPS foreground service is killed.
 * Never start a location foreground service from the background:
 * notify the user and require a tap to resume it.
 */
class GpsRecoveryMonitorWorker(context:Context,params:WorkerParameters):Worker(context,params) {
    override fun doWork():Result {
        val prefs=applicationContext.getSharedPreferences("r3_gate",Context.MODE_PRIVATE)
        val now=System.currentTimeMillis()
        val inspectedSession=prefs.getLong("gps_session_start",0L)
        val fine=applicationContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
        val coarse=applicationContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED
        val locationProviders=try {
            val locations=applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            Pair(locations.isProviderEnabled(LocationManager.GPS_PROVIDER),
                locations.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
        } catch(_:RuntimeException) {
            // Unknown provider state: never invent a disabled/unsupported provider fault.
            Pair(true,true)
        }
        val usable=GpsProviderRules.permitted(locationProviders.first,locationProviders.second,fine,coarse)!=null
        val decision=GpsRecoveryMonitorRules.decide(
            now,prefs.getBoolean("gps_enabled",false),
            prefs.getLong("gps_service_heartbeat_ms",0L),
            inspectedSession,
            prefs.getLong("gps_recovery_alert_at",0L),
            try { JSONObject(prefs.getString("gps_last_fix","{}") ?: "{}").optLong("time") }
                catch(_:Exception) { 0L },
            fine || coarse,
            locationProviders.first || locationProviders.second,
            usable)
        if(decision==GpsRecoveryMonitorRules.Decision.IGNORE ||
            decision==GpsRecoveryMonitorRules.Decision.HEALTHY) return Result.success()
        // The Worker may race a manual GPS stop or a new foreground session.
        if(!GpsRecoveryAlertGuard.stillRelevant(inspectedSession,
                prefs.getLong("gps_session_start",0L),
                prefs.getBoolean("gps_enabled",false),System.currentTimeMillis()))
            return Result.success()
        val noFix=decision==GpsRecoveryMonitorRules.Decision.ALERT_NO_FIX
        val permission=decision==GpsRecoveryMonitorRules.Decision.ALERT_PERMISSION
        val providerOff=decision==GpsRecoveryMonitorRules.Decision.ALERT_PROVIDER_OFF
        val providerUnusable=decision==GpsRecoveryMonitorRules.Decision.ALERT_PROVIDER_UNUSABLE
        prefs.edit().putLong("gps_recovery_alert_at",now)
            .putString("gps_service_problem",when {
                permission -> "permission"
                providerOff -> "no_provider"
                providerUnusable -> "provider_unusable"
                noFix -> "no_fix"
                else -> "stalled"
            }).apply()
        GpsTraceJournal.append(prefs,when {
            permission -> "GPS_PERMISSION_ALERT"
            providerOff -> "GPS_PROVIDER_OFF_ALERT"
            providerUnusable -> "GPS_PROVIDER_UNUSABLE_ALERT"
            noFix -> "GPS_NO_FIX_ALERT"
            else -> "SERVICE_STALLED"
        },now,
            JSONObject().put("lastHeartbeat",prefs.getLong("gps_service_heartbeat_ms",0)))
        val notifications=applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notifications.createNotificationChannel(NotificationChannel(
            CHANNEL,"WorkMeter · GPS monitor",NotificationManager.IMPORTANCE_DEFAULT))
        if(Build.VERSION.SDK_INT>=33 &&
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            return Result.success()
        val intent=Intent(applicationContext,MainActivity::class.java).apply {
            putExtra("gps_resume_alert",true)
            flags=Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending=PendingIntent.getActivity(applicationContext,4402,intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification=Notification.Builder(applicationContext,CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("WorkMeter · GPS wymaga uwagi")
            .setContentText(when {
                permission -> "Brak uprawnienia do lokalizacji. Dotknij, aby sprawdzić."
                providerOff -> "Lokalizacja telefonu jest wyłączona. Dotknij, aby włączyć."
                providerUnusable -> "Brak dostępnego źródła lokalizacji dla aktualnych uprawnień."
                noFix -> "Usługa działa, ale brak nowych pozycji GPS. Dotknij, aby sprawdzić."
                else -> "Nie otrzymano sygnału usługi GPS. Dotknij, aby sprawdzić i wznowić."
            })
            .setContentIntent(pending)
            .setAutoCancel(true).build()
        notifications.notify(NOTIFICATION_ID,notification)
        return Result.success()
    }
    companion object {
        private const val CHANNEL="workmeter_gps_monitor"
        private const val NOTIFICATION_ID=4502
        private const val NAME="workmeter-gps-recovery-monitor"
        fun schedule(context:Context) {
            val request=PeriodicWorkRequestBuilder<GpsRecoveryMonitorWorker>(15,TimeUnit.MINUTES)
                .addTag(NAME).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,ExistingPeriodicWorkPolicy.KEEP,request)
        }
        fun dismissAlert(context:Context) {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .cancel(NOTIFICATION_ID)
        }
        fun cancel(context:Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
            dismissAlert(context)
        }
    }
}
