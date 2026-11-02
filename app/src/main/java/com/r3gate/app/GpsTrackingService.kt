package com.r3gate.app

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.*
import org.json.JSONArray
import org.json.JSONObject

/** User-initiated foreground GPS collection. Never starts itself or survives a manual stop. */
class GpsTrackingService : Service(), LocationListener {
    companion object {
        const val ACTION_START = "com.r3gate.app.GPS_START"
        const val ACTION_STOP = "com.r3gate.app.GPS_STOP"
        private const val CHANNEL = "workmeter_gps"
        private const val NOTIFICATION_ID = 4501
    }
    private val prefs by lazy { getSharedPreferences("r3_gate",MODE_PRIVATE) }
    private val manager by lazy { getSystemService(LOCATION_SERVICE) as LocationManager }
    private var subscribed=false

    override fun onBind(intent:Intent?):IBinder? = null
    override fun onCreate() {
        super.onCreate()
        val channel=NotificationChannel(CHANNEL,"WorkMeter · lokalizacja",NotificationManager.IMPORTANCE_LOW)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }
    private fun notification():Notification {
        val stop=PendingIntent.getService(this,1,Intent(this,GpsTrackingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open=PendingIntent.getActivity(this,2,Intent(this,MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this,CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("WorkMeter · GPS włączony")
            .setContentText("Rejestrowanie stref obiektów · dotknij, aby otworzyć")
            .setContentIntent(open).setOngoing(true)
            .addAction(Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel,"Wyłącz GPS",stop).build())
            .build()
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(intent?.action==ACTION_STOP || !prefs.getBoolean("gps_enabled",false)) {
            if(intent?.action==ACTION_STOP) prefs.edit().putBoolean("gps_enabled",false).apply()
            shutdown()
            return START_NOT_STICKY
        }
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
            shutdown()
            return START_NOT_STICKY
        }
        try {
            if(Build.VERSION.SDK_INT>=29) startForeground(NOTIFICATION_ID,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            else startForeground(NOTIFICATION_ID,notification())
            prefs.edit().putBoolean("gps_service_active",true).apply()
            if(!subscribed) {
                val provider=when {
                    manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                    manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                    else -> null
                }
                if(provider==null) { shutdown();return START_NOT_STICKY }
                manager.requestLocationUpdates(provider,30_000L,25f,this,Looper.getMainLooper())
                subscribed=true
            }
        } catch(_:SecurityException) { shutdown() }
        catch(_:IllegalArgumentException) { shutdown() }
        return START_NOT_STICKY
    }
    override fun onLocationChanged(location:Location) {
        if(!prefs.getBoolean("gps_enabled",false)) { shutdown();return }
        if(location.accuracy<=0f || location.accuracy>100f || !location.latitude.isFinite() || !location.longitude.isFinite()) return
        val now=System.currentTimeMillis()
        val point=GpsWorkRules.Point(location.latitude,location.longitude,location.accuracy.toDouble(),now)
        val raw=try { JSONObject(prefs.getString("data","{}") ?: "{}") } catch(_:Exception) { JSONObject() }
        val objects=raw.optJSONArray("objects") ?: JSONArray()
        val places=mutableListOf<GpsWorkRules.Place>()
        for(i in 0 until objects.length()) {
            val o=objects.optJSONObject(i) ?: continue
            if(o.optBoolean("archived")) continue
            val id=o.optString("id")
            val stored=prefs.getString("gps_object_"+id,null) ?: continue
            try {
                val geo=JSONObject(stored)
                places.add(GpsWorkRules.Place(id,GpsWorkRules.Kind.OBJECT,
                    geo.getDouble("lat"),geo.getDouble("lon"),geo.optDouble("radius",100.0)))
            } catch(_:Exception) { }
        }
        val observed=GpsWorkRules.nearest(point,places)?.place?.id
        val saved=try { JSONObject(prefs.getString("gps_arrival_state","{}") ?: "{}") } catch(_:Exception) { JSONObject() }
        val state=GpsArrivalRules.State(
            saved.optString("confirmed").ifBlank { null },
            saved.optString("candidate").ifBlank { null },
            saved.optLong("since"), saved.optLong("last"))
        val decision=GpsArrivalRules.advance(state,observed,now)
        val next=decision.state
        val editor=prefs.edit().putString("gps_last_fix",JSONObject()
            .put("lat",location.latitude).put("lon",location.longitude)
            .put("accuracy",location.accuracy.toDouble()).put("time",now).toString())
            .putString("gps_arrival_state",JSONObject()
                .put("confirmed",next.confirmedId ?: "").put("candidate",next.candidateId ?: "")
                .put("since",next.candidateSinceMs).put("last",next.lastFixMs).toString())
        if(decision.event!=null) {
            val history=try { JSONArray(prefs.getString("gps_events","[]") ?: "[]") } catch(_:Exception) { JSONArray() }
            history.put(JSONObject().put("event",decision.event).put("objectId",decision.placeId ?: "").put("time",now))
            val limited=JSONArray()
            for(i in maxOf(0,history.length()-100) until history.length()) limited.put(history.opt(i))
            editor.putString("gps_events",limited.toString())
        }
        editor.apply()
    }
    private fun shutdown() {
        if(subscribed) { manager.removeUpdates(this);subscribed=false }
        prefs.edit().putBoolean("gps_service_active",false).apply()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    override fun onDestroy() {
        if(subscribed) { manager.removeUpdates(this);subscribed=false }
        prefs.edit().putBoolean("gps_service_active",false).apply()
        super.onDestroy()
    }
}
