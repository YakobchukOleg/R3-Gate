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
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt
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
    private var currentProvider:String?=null
    private var subscribedIntervalMs=0L
    private var sessionStarted=false
    private fun recordTripBoundary(now:Long=System.currentTimeMillis()) {
        val journal=try { JSONArray(prefs.getString("gps_trip_events","[]") ?: "[]") }
            catch(_:Exception) { JSONArray() }
        journal.put(JSONObject().put("event","GPS_GAP").put("time",now))
        val retained=JSONArray()
        for(i in maxOf(0,journal.length()-100) until journal.length()) retained.put(journal.opt(i))
        prefs.edit().putString("gps_trip_events",retained.toString()).apply()
    }

    private val sensors by lazy { getSystemService(SENSOR_SERVICE) as SensorManager }
    private var motionListenerRegistered=false
    private var samplingState=GpsAdaptiveSampling.State()
    private var samplingIntervalMs=GpsAdaptiveSampling.ACTIVE_INTERVAL_MS
    private fun safeSamplingInterval(now:Long):Long {
        val suggested=GpsAdaptiveSampling.interval(samplingState,now,true)
        val last=try { JSONObject(prefs.getString("gps_last_fix","{}") ?: "{}") } catch(_:Exception) { JSONObject() }
        val motion=try { JSONObject(prefs.getString("gps_trip_state","{}") ?: "{}") } catch(_:Exception) { JSONObject() }
        val confirmedTravel=motion.optString("mode")=="MOVING" &&
            now-motion.optLong("last") in 0..GpsSamplingSafety.MAX_FIX_AGE_MS
        return GpsSamplingSafety.interval(suggested,now,last.optLong("time"),
            last.optDouble("accuracy",Double.NaN),confirmedTravel)
    }

    private val sensorListener=object:SensorEventListener {
        override fun onAccuracyChanged(sensor:Sensor?,accuracy:Int) {}
        override fun onSensorChanged(event:SensorEvent) {
            if(event.sensor.type!=Sensor.TYPE_ACCELEROMETER || !prefs.getBoolean("gps_enabled",false) ||
                !prefs.getBoolean("gps_adaptive_sampling",false)) return
            val x=event.values[0].toDouble()
            val y=event.values[1].toDouble()
            val z=event.values[2].toDouble()
            val delta=kotlin.math.abs(sqrt(x*x+y*y+z*z)-SensorManager.GRAVITY_EARTH)
            val now=System.currentTimeMillis()
            samplingState=GpsAdaptiveSampling.observe(samplingState,now,delta)
            val target=safeSamplingInterval(now)
            if(target!=samplingIntervalMs) {
                samplingIntervalMs=target
                try { updateSubscription() }
                catch(_:SecurityException) { prefs.edit().putString("gps_service_problem","permission").apply();shutdown() }
                catch(_:RuntimeException) { prefs.edit().putString("gps_service_problem","provider").apply() }
            }
        }
    }
    private fun startMotionSensor() {
        if(motionListenerRegistered) return
        val sensor=sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        motionListenerRegistered=sensors.registerListener(sensorListener,sensor,SensorManager.SENSOR_DELAY_NORMAL)
    }
    private fun stopMotionSensor() {
        if(motionListenerRegistered) sensors.unregisterListener(sensorListener)
        motionListenerRegistered=false
    }
    private val providerReceiver=object:BroadcastReceiver() {
        override fun onReceive(context:Context?,intent:Intent?) {
            if(intent?.action!=LocationManager.PROVIDERS_CHANGED_ACTION ||
                !prefs.getBoolean("gps_enabled",false) ||
                !prefs.getBoolean("gps_service_active",false)) return
            try { updateSubscription() }
            catch(_:SecurityException) {
                prefs.edit().putString("gps_service_problem","permission").apply()
                shutdown()
            }
            catch(_:RuntimeException) {
                prefs.edit().putString("gps_service_problem","provider").apply()
            }
        }
    }

    private fun providerChoice():String? = when(GpsProviderRules.preferred(
        manager.isProviderEnabled(LocationManager.GPS_PROVIDER),
        manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))) {
        GpsProviderRules.Provider.GPS -> LocationManager.GPS_PROVIDER
        GpsProviderRules.Provider.NETWORK -> LocationManager.NETWORK_PROVIDER
        null -> null
    }
    private fun updateSubscription() {
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
            prefs.edit().putString("gps_service_problem","permission").apply()
            throw SecurityException("Location permission revoked")
        }
        val chosen=providerChoice()
        if(chosen==null) {
            prefs.edit().putString("gps_service_problem","no_provider").apply()
            if(subscribed) manager.removeUpdates(this)
            subscribed=false; currentProvider=null
            return
        }
        if(subscribed && chosen==currentProvider && subscribedIntervalMs==samplingIntervalMs) return
        if(subscribed) manager.removeUpdates(this)
        subscribed=false;currentProvider=null
        manager.requestLocationUpdates(chosen,samplingIntervalMs,0f,this,Looper.getMainLooper())
        currentProvider=chosen;subscribed=true;subscribedIntervalMs=samplingIntervalMs
        prefs.edit().putLong("gps_requested_interval_ms",samplingIntervalMs).apply()
        prefs.edit().remove("gps_service_problem").apply()
    }
    override fun onProviderDisabled(provider:String) {
        try { updateSubscription() } catch(_:SecurityException) {
            prefs.edit().putString("gps_service_problem","permission").apply(); shutdown()
        } catch(_:RuntimeException) {
            prefs.edit().putString("gps_service_problem","provider").apply()
        }
    }
    override fun onProviderEnabled(provider:String) {
        try { updateSubscription() } catch(_:SecurityException) {
            prefs.edit().putString("gps_service_problem","permission").apply(); shutdown()
        } catch(_:RuntimeException) {
            prefs.edit().putString("gps_service_problem","provider").apply()
        }
    }

    override fun onBind(intent:Intent?):IBinder? = null
    override fun onCreate() {
        super.onCreate()
        val channel=NotificationChannel(CHANNEL,"WorkMeter · lokalizacja",NotificationManager.IMPORTANCE_LOW)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        registerReceiver(providerReceiver,IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION))
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
            if(!sessionStarted) {
                recordTripBoundary()
                // A new foreground service session must not join motion evidence from
                // a previous run, even if the device was switched off only briefly.
                val savedArrival=try {
                    JSONObject(prefs.getString("gps_arrival_state","{}") ?: "{}")
                } catch(_:Exception) { JSONObject() }
                val confirmed=savedArrival.optString("confirmed")
                val safeArrival=JSONObject().put("confirmed",confirmed)
                    .put("candidate","").put("since",0L).put("last",0L)
                prefs.edit().remove("gps_trip_state").remove("gps_motion_point")
                    .remove("gps_last_provider").remove("gps_last_source_fix_time")
                    .putInt("gps_rejected_quality_count",0)
                    .putInt("gps_rejected_order_count",0)
                    .putInt("gps_rejected_jump_count",0)
                    .putString("gps_arrival_state",safeArrival.toString())
                    .putLong("gps_requested_interval_ms",GpsAdaptiveSampling.ACTIVE_INTERVAL_MS)
                    .putBoolean("gps_service_active",true)
                    .putLong("gps_session_start",System.currentTimeMillis()).commit()
                samplingState=GpsAdaptiveSampling.State()
                samplingIntervalMs=GpsAdaptiveSampling.ACTIVE_INTERVAL_MS
                sessionStarted=true
            }
            // With no provider keep the visible foreground service listening for location changes.
            if(prefs.getBoolean("gps_adaptive_sampling",false)) startMotionSensor()
            else {
                stopMotionSensor()
                samplingIntervalMs=GpsAdaptiveSampling.ACTIVE_INTERVAL_MS
                samplingState=GpsAdaptiveSampling.State()
            }
            updateSubscription()
        } catch(_:SecurityException) { prefs.edit().putString("gps_service_problem","permission").apply(); shutdown() }
        catch(_:IllegalArgumentException) { prefs.edit().putString("gps_service_problem","provider").apply(); shutdown() }
        catch(_:RuntimeException) {
            // Android may reject a foreground service or provider update at runtime.
            prefs.edit().putString("gps_service_problem","runtime").apply()
            shutdown()
        }
        return START_NOT_STICKY
    }
    override fun onLocationChanged(location:Location) {
        if(!prefs.getBoolean("gps_enabled",false)) { shutdown();return }
        if(!GpsFixSourceRules.accept(currentProvider,location.provider,true)) return
        val now=System.currentTimeMillis()
        if(!GpsLocationQuality.acceptable(now,location.time,location.accuracy.toDouble(),
                location.latitude,location.longitude)) {
            prefs.edit().putLong("gps_rejected_fix_at",now)
                .putInt("gps_rejected_quality_count",
                    prefs.getInt("gps_rejected_quality_count",0)+1).apply()
            return
        }
        val previousFix=try { JSONObject(prefs.getString("gps_last_fix","{}") ?: "{}") } catch(_:Exception) { JSONObject() }
        val lastRecorded=previousFix.optLong("time")
        if(lastRecorded>=now) return
        val sourceTime=location.time
        if(!GpsFixOrderRules.accept(sourceTime,
                prefs.getLong("gps_last_source_fix_time",0L))) {
            prefs.edit().putInt("gps_rejected_order_count",
                prefs.getInt("gps_rejected_order_count",0)+1).apply()
            return
        }
        prefs.edit().putLong("gps_last_source_fix_time",sourceTime).commit()
        val source=location.provider ?: ""
        val transition=GpsSourceTransitionRules.compare(
            prefs.getString("gps_last_provider",null),source)
        val recovery=GpsRecoveryRules.plan(lastRecorded,now)
        val motionEvidenceGap=lastRecorded>0 && now>lastRecorded &&
            now-lastRecorded>GpsTripRules.MAX_EVIDENCE_GAP_MS
        if(motionEvidenceGap && !recovery.recordGap) recordTripBoundary(now)
        if(recovery.resetArrival) {
            prefs.edit().remove("gps_arrival_state").commit()
        } else if(transition.resetZoneCandidate) {
            // Keep the last confirmed object; discard only an unconfirmed handover.
            val existing=try {
                JSONObject(prefs.getString("gps_arrival_state","{}") ?: "{}")
            } catch(_:Exception) { JSONObject() }
            prefs.edit().putString("gps_arrival_state",JSONObject()
                .put("confirmed",existing.optString("confirmed"))
                .put("candidate","").put("since",0L)
                .put("last",existing.optLong("last")).toString()).commit()
        }
        if(transition.resetMotion && !recovery.recordGap && !motionEvidenceGap) {
            // A provider handover invalidates any open trip inferred from the old
            // source; a later stop must not complete that earlier trip.
            recordTripBoundary(now)
        }
        if(recovery.resetMotion || transition.resetMotion || motionEvidenceGap)
            prefs.edit().remove("gps_trip_state").remove("gps_motion_point").commit()
        prefs.edit().putString("gps_last_provider",source).apply()
        if(recovery.recordGap) {
            recordTripBoundary(now)
            val gaps=try { JSONArray(prefs.getString("gps_coverage_gaps","[]") ?: "[]") } catch(_:Exception) { JSONArray() }
            gaps.put(JSONObject().put("start",lastRecorded).put("end",now))
            val limited=JSONArray()
            for(i in maxOf(0,gaps.length()-100) until gaps.length()) limited.put(gaps.opt(i))
            prefs.edit().putString("gps_coverage_gaps",limited.toString()).commit()
        }
        val point=GpsWorkRules.Point(location.latitude,location.longitude,location.accuracy.toDouble(),now)
        // An impossible GPS jump is not evidence of stopping, starting a trip,
        // or entering/leaving any worksite. Preserve the last trustworthy fix.
        val trustedMotion=try { JSONObject(prefs.getString("gps_motion_point","{}") ?: "{}") }
            catch(_:Exception) { JSONObject() }
        val trustedTime=trustedMotion.optLong("time")
        if(GpsMotionContinuityRules.contiguous(trustedTime,now)) {
            val trustedPoint=GpsWorkRules.Point(trustedMotion.optDouble("lat"),
                trustedMotion.optDouble("lon"),trustedMotion.optDouble("accuracy"),trustedTime)
            if(GpsMotionOutlierRules.impossibleJump(trustedPoint,point)) {
                prefs.edit().putInt("gps_rejected_jump_count",
                    prefs.getInt("gps_rejected_jump_count",0)+1).apply()
                return
            }
        }
        // Keep bounded timestamps for detecting GPS coverage gaps, never modifying shifts.
        val fixes=try { JSONArray(prefs.getString("gps_fix_times","[]") ?: "[]") } catch(_:Exception) { JSONArray() }
        fixes.put(now)
        val retained=JSONArray()
        for(i in maxOf(0,fixes.length()-500) until fixes.length()) retained.put(fixes.optLong(i))
        prefs.edit().putString("gps_fix_times",retained.toString()).apply()

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
        val saved=try { JSONObject(prefs.getString("gps_arrival_state","{}") ?: "{}") } catch(_:Exception) { JSONObject() }
        val confirmedId=saved.optString("confirmed").ifBlank { null }
        val zoneEvidence=GpsGeofenceEvidence.observe(point,places,confirmedId)
        val observed=zoneEvidence.placeId
        val state=GpsArrivalRules.State(
            saved.optString("confirmed").ifBlank { null },
            saved.optString("candidate").ifBlank { null },
            saved.optLong("since"), saved.optLong("last"))
        // Inaccurate border fixes cannot count as a confirmed departure.
        val decision=if(zoneEvidence.status==GpsGeofenceEvidence.Status.UNCERTAIN)
            GpsArrivalRules.uncertain(state,now)
        else GpsArrivalRules.advance(state,observed,now)
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
        // Travel events are kept apart from work shifts until the user confirms billing rules.
        val previous=try { JSONObject(prefs.getString("gps_motion_point","{}") ?: "{}") } catch(_:Exception) { JSONObject() }
        val lastTime=previous.optLong("time")
        if(GpsMotionContinuityRules.contiguous(lastTime,now)) {
            val previousPoint=GpsWorkRules.Point(previous.optDouble("lat"),previous.optDouble("lon"),
                previous.optDouble("accuracy"),lastTime)
            val moving=GpsWorkRules.traveling(previousPoint,point)
            if(moving && prefs.getBoolean("gps_adaptive_sampling",false)) {
                samplingState=GpsAdaptiveSampling.gpsTravel(samplingState,now)
                val desired=safeSamplingInterval(now)
                if(desired!=samplingIntervalMs) {
                    samplingIntervalMs=desired
                    try { updateSubscription() }
                    catch(_:SecurityException) {
                        prefs.edit().putString("gps_service_problem","permission").apply()
                        shutdown()
                        return
                    }
                    catch(_:RuntimeException) {
                        prefs.edit().putString("gps_service_problem","provider").apply()
                    }
                }
            }
            val previousState=try { JSONObject(prefs.getString("gps_trip_state","{}") ?: "{}") } catch(_:Exception) { JSONObject() }
            val tripState=GpsTripRules.State(
                mode=GpsTripRules.Mode.entries.find { it.name==previousState.optString("mode") } ?: GpsTripRules.Mode.UNKNOWN,
                candidate=GpsTripRules.Mode.entries.find { it.name==previousState.optString("candidate") } ?: GpsTripRules.Mode.UNKNOWN,
                candidateSinceMs=previousState.optLong("candidateSince"),
                lastFixMs=previousState.optLong("last"),
                stoppedSinceMs=previousState.optLong("stoppedSince"),
                longStopReported=previousState.optBoolean("longStopReported")
            )
            val transition=GpsTripRules.advance(tripState,moving,now,
                longStopMs=prefs.getInt("gps_stop_minutes",10).coerceIn(5,30)*60_000L)
            val t=transition.state
            val motionEditor=prefs.edit().putString("gps_trip_state",JSONObject()
                .put("mode",t.mode.name).put("candidate",t.candidate.name)
                .put("candidateSince",t.candidateSinceMs).put("last",t.lastFixMs)
                .put("stoppedSince",t.stoppedSinceMs).put("longStopReported",t.longStopReported).toString())
            if(transition.event!=null) {
                val journal=try { JSONArray(prefs.getString("gps_trip_events","[]") ?: "[]") } catch(_:Exception) { JSONArray() }
                journal.put(JSONObject().put("event",transition.event).put("time",now))
                val recent=JSONArray()
                for(i in maxOf(0,journal.length()-100) until journal.length()) recent.put(journal.opt(i))
                motionEditor.putString("gps_trip_events",recent.toString())
            }
            motionEditor.apply()
        } else if(lastTime>0 && now-lastTime>1_800_000L) {
            prefs.edit().remove("gps_trip_state").apply()
        }
        prefs.edit().putString("gps_motion_point",JSONObject()
            .put("lat",location.latitude).put("lon",location.longitude)
            .put("accuracy",location.accuracy.toDouble()).put("time",now).toString()).apply()
        if(prefs.getBoolean("gps_adaptive_sampling",false)) {
            val desired=safeSamplingInterval(now)
            if(desired!=samplingIntervalMs) {
                samplingIntervalMs=desired
                try { updateSubscription() }
                catch(_:SecurityException) { prefs.edit().putString("gps_service_problem","permission").apply();shutdown() }
                catch(_:RuntimeException) { prefs.edit().putString("gps_service_problem","provider").apply() }
            }
        }
    }
    private fun shutdown() {
        if(sessionStarted) { recordTripBoundary(); sessionStarted=false }
        stopMotionSensor()
        if(subscribed) { manager.removeUpdates(this);subscribed=false;currentProvider=null }
        prefs.edit().putBoolean("gps_service_active",false).apply()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    override fun onDestroy() {
        if(sessionStarted) { recordTripBoundary(); sessionStarted=false }
        stopMotionSensor()
        unregisterReceiver(providerReceiver)
        if(subscribed) { manager.removeUpdates(this);subscribed=false;currentProvider=null }
        prefs.edit().putBoolean("gps_service_active",false).apply()
        super.onDestroy()
    }
}
