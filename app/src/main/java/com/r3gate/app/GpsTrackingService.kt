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
    private val watchdogHandler=Handler(Looper.getMainLooper())
    private var lastWatchdogProbeMs=0L
    private var activeGeofenceProbeSinceMs=0L
    private var lastGeofenceProbeMs=0L
    private var motionWakeState=GpsMotionWakeRules.State()
    private var motionWindow=GpsMotionWindow()
    private var motionProbeElapsedMs=0L
    private var significantMotionRegistered=false
    private var lastSensorBurstStartMs=0L
    private var lastSensorEvaluationMs=0L
    private val watchdogTick=object:Runnable {
        override fun run() {
            when(GpsWatchdogLifecycleRules.onTick(
                sessionStarted,GpsWorkdaySession.shouldTrack(prefs))) {
                GpsWatchdogLifecycleRules.Action.IGNORE -> return
                GpsWatchdogLifecycleRules.Action.STOP -> {
                    trace("TRACKING_DISABLED_WATCHDOG")
                    shutdown()
                    return
                }
                GpsWatchdogLifecycleRules.Action.TICK -> Unit
            }
            val now=System.currentTimeMillis()
            prefs.edit().putLong("gps_service_heartbeat_ms",now).apply()
            // Settings can be changed without restarting the foreground service.
            val adaptiveEnabled=prefs.getBoolean("gps_adaptive_sampling",true)
            when(GpsAdaptiveToggleRules.sensorAction(adaptiveEnabled,motionListenerRegistered)) {
                GpsAdaptiveToggleRules.Action.START_SENSOR -> startMotionSensor()
                GpsAdaptiveToggleRules.Action.STOP_SENSOR -> stopMotionSensor()
                else -> Unit
            }
            if(GpsAdaptiveToggleRules.cadenceAction(adaptiveEnabled,
                    samplingIntervalMs)==GpsAdaptiveToggleRules.Action.RESTORE_ACTIVE) {
                samplingIntervalMs=GpsAdaptiveSampling.ACTIVE_INTERVAL_MS
                samplingState=GpsAdaptiveSampling.State()
                try { updateSubscription() }
                catch(_:SecurityException) {
                    prefs.edit().putString("gps_service_problem","permission").apply()
                    shutdown()
                    return
                }
                catch(_:RuntimeException) { trace("ADAPTIVE_DISABLED_RESTORE_FAILED",now) }
            }
            // Temporary 15-second geofence probes must end even with zero new GPS fixes.
            val desiredCadence=if(adaptiveEnabled) safeSamplingInterval(now) else GpsAdaptiveSampling.ACTIVE_INTERVAL_MS
            val cadence=GpsProbeCadenceRules.Change(desiredCadence!=samplingIntervalMs,desiredCadence)
            if(cadence.update) {
                samplingIntervalMs=cadence.intervalMs
                try {
                    updateSubscription()
                    val applied=GpsWatchdogProviderRules.cadenceApplied(
                        currentProvider,subscribed,currentProvider,
                        samplingIntervalMs,subscribedIntervalMs)
                    trace(if(applied) "PROBE_CADENCE_RESTORED" else
                        "PROBE_CADENCE_UNAVAILABLE",now,
                        JSONObject().put("intervalMs",samplingIntervalMs))
                } catch(_:SecurityException) {
                    prefs.edit().putString("gps_service_problem","permission").apply()
                    shutdown()
                    return
                } catch(_:RuntimeException) {
                    trace("PROBE_CADENCE_FAILED",now)
                }
            }
            val lastFix=try { JSONObject(prefs.getString("gps_last_fix","{}") ?: "{}").optLong("time") }
                catch(_:Exception) { 0L }
            val selected=try { providerChoice() }
                catch(_:RuntimeException) { null }
            val providerStatus=GpsWatchdogProviderRules.status(
                selected!=null,subscribed,currentProvider,selected)
            val decision=GpsWatchdogRules.decide(now,
                prefs.getLong("gps_session_start",0L),lastFix,lastWatchdogProbeMs,
                true,selected!=null)
            if(GpsWatchdogProviderRules.shouldReacquire(
                    providerStatus,decision==GpsWatchdogRules.Action.PROBE,
                    now,lastWatchdogProbeMs)) {
                lastWatchdogProbeMs=now
                trace(if(providerStatus==GpsWatchdogProviderRules.Status.NEEDS_SUBSCRIPTION)
                    "WATCHDOG_SUBSCRIPTION_MISSING" else "WATCHDOG_NO_FIX",now,
                    JSONObject().put("lastFix",lastFix).put("provider",selected ?: ""))
                try {
                    // Force re-registration after a stalled stream. No synthetic fixes.
                    subscribedIntervalMs=-1L
                    updateSubscription()
                    if(GpsWatchdogProviderRules.cadenceApplied(
                            selected,subscribed,currentProvider,
                            samplingIntervalMs,subscribedIntervalMs)) {
                        trace("WATCHDOG_RESUBSCRIBED",now)
                    } else {
                        trace("WATCHDOG_RESUBSCRIBE_UNAVAILABLE",now)
                    }
                } catch(_:SecurityException) {
                    prefs.edit().putString("gps_service_problem","permission").apply()
                    shutdown()
                    return
                } catch(_:RuntimeException) {
                    trace("WATCHDOG_PROBE_FAILED",now)
                    prefs.edit().putString("gps_service_problem","provider").apply()
                }
            }
            watchdogHandler.postDelayed(this,GpsWatchdogRules.CHECK_INTERVAL_MS)
        }
    }
    private fun trace(kind:String,now:Long=System.currentTimeMillis(),details:JSONObject=JSONObject()) {
        GpsTraceJournal.append(prefs,kind,now,details)
        WorkMeterDiagnostics.record(this,"GPS_"+kind,JSONObject(details.toString()).put("evidenceTime",now),
            if(kind in listOf("FIX","ZONE","TRIP_EVIDENCE","REJECT_QUALITY","REJECT_ORDER","REJECT_JUMP","APPROXIMATE_LOCATION")) 10_000 else 0)
    }
    private fun provisionalRow(
        kind: String, time: Long, eventId: String,
        evidenceStart: Long? = null, tripId: String? = null
    ): JSONObject = JSONObject(GpsEventContract.metadata(kind,time,evidenceStart,tripId))
        .put("event",kind).put("time",time).put("eventId",eventId)

    private fun recordTripBoundary(now:Long=System.currentTimeMillis()) {
        val journal=try { JSONArray(prefs.getString("gps_trip_events","[]") ?: "[]") }
            catch(_:Exception) { JSONArray() }
        val eventId=GpsEventIdentity.id("GPS_GAP",now)
        if(!(0 until journal.length()).none {
                val existing=journal.optJSONObject(it)
                GpsEventIdentity.sameEvent(existing?.optString("eventId"),
                    existing?.optString("event"),existing?.optLong("time") ?: 0L,
                    null,"GPS_GAP",now)
            }) return
        journal.put(provisionalRow("GPS_GAP",now,eventId))
        val retained=JSONArray()
        for(i in maxOf(0,journal.length()-100) until journal.length()) retained.put(journal.opt(i))
        prefs.edit().putString("gps_trip_events",retained.toString()).apply()
        trace("GPS_GAP",now)
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
        val baseline=GpsSamplingSafety.interval(suggested,now,last.optLong("time"),
            last.optDouble("accuracy",Double.NaN),confirmedTravel)
        val travelling=if(confirmedTravel) minOf(baseline,10_000L) else baseline
        return GpsMotionProbe.interval(android.os.SystemClock.elapsedRealtime(),motionProbeElapsedMs,
            GpsGeofenceProbeRules.interval(now,activeGeofenceProbeSinceMs,travelling))
    }

    private val sensorListener=object:SensorEventListener {
        override fun onAccuracyChanged(sensor:Sensor?,accuracy:Int) {}
        override fun onSensorChanged(event:SensorEvent) {
            if(event.sensor.type!=Sensor.TYPE_ACCELEROMETER || !sessionStarted ||
                !prefs.getBoolean("gps_enabled",false) ||
                !prefs.getBoolean("gps_adaptive_sampling",true)) return
            val x=event.values[0].toDouble()
            val y=event.values[1].toDouble()
            val z=event.values[2].toDouble()
            val delta=kotlin.math.abs(sqrt(x*x+y*y+z*z)-SensorManager.GRAVITY_EARTH)
            val now=System.currentTimeMillis()
            samplingState=GpsAdaptiveSampling.observe(samplingState,now,delta)
            val evidence=motionWindow.observe(event.timestamp/1_000_000,delta)
            WorkMeterDiagnostics.record(this@GpsTrackingService,"SENSOR_WINDOW",JSONObject()
                .put("rms",evidence.rms).put("activeFraction",evidence.activeFraction).put("samples",evidence.samples),15_000)
            val wake=GpsMotionWakeRules.Result(motionWakeState,evidence.wake)
            if(evidence.wake) beginMotionProbe("accelerometer")
            if(!GpsSensorEvaluationRules.shouldEvaluate(
                    now,lastSensorEvaluationMs,wake.wake)) return
            lastSensorEvaluationMs=now
            val target=safeSamplingInterval(now)
            if(target!=samplingIntervalMs) {
                samplingIntervalMs=target
                try { updateSubscription() }
                catch(_:SecurityException) { prefs.edit().putString("gps_service_problem","permission").apply();shutdown() }
                catch(_:RuntimeException) { prefs.edit().putString("gps_service_problem","provider").apply() }
            }
        }
    }
    private val significantListener=object:android.hardware.TriggerEventListener() {
        override fun onTrigger(event:android.hardware.TriggerEvent?) {
            significantMotionRegistered=false
            if(!sessionStarted || !GpsWorkdaySession.shouldTrack(prefs) || !prefs.getBoolean("gps_adaptive_sampling",true)) return
            beginMotionProbe("significant_motion")
            armSignificantMotion()
        }
    }
    private fun armSignificantMotion() {
        if(significantMotionRegistered) return
        val sensor=sensors.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION) ?: return
        significantMotionRegistered=sensors.requestTriggerSensor(significantListener,sensor)
    }
    private fun beginMotionProbe(source:String) {
        val elapsed=android.os.SystemClock.elapsedRealtime()
        if(!GpsMotionProbe.mayStart(elapsed,motionProbeElapsedMs)) return
        motionProbeElapsedMs=elapsed
        trace("SENSOR_MOTION_WAKE",details=JSONObject().put("source",source).put("durationMs",GpsMotionProbe.DURATION_MS))
        samplingIntervalMs=safeSamplingInterval(System.currentTimeMillis())
        try { updateSubscription() }
        catch(_:SecurityException) { trace("SENSOR_PERMISSION_MISSING"); shutdown() }
        catch(_:RuntimeException) { trace("SENSOR_PROVIDER_UNAVAILABLE") }
    }
    private fun startMotionSensor() {
        if(motionListenerRegistered || significantMotionRegistered) return
        motionWindow=GpsMotionWindow()
        val sensor=sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        motionListenerRegistered=sensor!=null && sensors.registerListener(sensorListener,sensor,SensorManager.SENSOR_DELAY_NORMAL)
        armSignificantMotion()
        trace("SENSORS_STARTED",details=JSONObject().put("accelerometer",motionListenerRegistered).put("significantMotion",significantMotionRegistered))
    }
    private fun stopMotionSensor() {
        if(motionListenerRegistered) sensors.unregisterListener(sensorListener)
        if(significantMotionRegistered) sensors.cancelTriggerSensor(significantListener)
        motionListenerRegistered=false; significantMotionRegistered=false; motionProbeElapsedMs=0L
        motionWindow=GpsMotionWindow()
    }
    private val providerReceiver=object:BroadcastReceiver() {
        override fun onReceive(context:Context?,intent:Intent?) {
            if(intent?.action!=LocationManager.PROVIDERS_CHANGED_ACTION ||
                !GpsWorkdaySession.shouldTrack(prefs) ||
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

    private fun providerChoice():String? = when(GpsProviderRules.permitted(
        manager.isProviderEnabled(LocationManager.GPS_PROVIDER),
        manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER),
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED,
        checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED)) {
        GpsProviderRules.Provider.GPS -> LocationManager.GPS_PROVIDER
        GpsProviderRules.Provider.NETWORK -> LocationManager.NETWORK_PROVIDER
        null -> null
    }
    /** Cleanup must succeed even after Android revokes location permission. */
    private fun removeLocationUpdatesSafely() {
        if(!subscribed) return
        subscribed=false
        currentProvider=null
        try { manager.removeUpdates(this) }
        catch(_:SecurityException) {
            trace("REMOVE_UPDATES_PERMISSION_REVOKED")
        }
        catch(_:IllegalArgumentException) {
            trace("REMOVE_UPDATES_INVALID_LISTENER")
        }
    }
    private fun updateSubscription() {
        if (!sessionStarted || !GpsWorkdaySession.shouldTrack(prefs)) return
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
            prefs.edit().putString("gps_service_problem","permission").apply()
            throw SecurityException("Location permission revoked")
        }
        val chosen=providerChoice()
        if(chosen==null) {
            val issue=GpsProviderRules.availabilityProblem(
                manager.isProviderEnabled(LocationManager.GPS_PROVIDER),
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER),
                checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED,
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED)
            val problem=when(issue) {
                GpsProviderRules.AvailabilityProblem.PERMISSION -> "permission"
                GpsProviderRules.AvailabilityProblem.INCOMPATIBLE_PROVIDER -> "provider_unusable"
                else -> "no_provider"
            }
            trace("NO_PROVIDER",details=JSONObject().put("reason",problem))
            prefs.edit().putString("gps_service_problem",problem).apply()
            removeLocationUpdatesSafely()
            subscribed=false; currentProvider=null
            return
        }
        if(subscribed && chosen==currentProvider && subscribedIntervalMs==samplingIntervalMs) return
        removeLocationUpdatesSafely()
        subscribed=false;currentProvider=null
        manager.requestLocationUpdates(chosen,samplingIntervalMs,0f,this,Looper.getMainLooper())
        currentProvider=chosen;subscribed=true;subscribedIntervalMs=samplingIntervalMs
        trace("PROVIDER_SUBSCRIBED",details=JSONObject()
            .put("provider",chosen).put("intervalMs",samplingIntervalMs))
        prefs.edit().putLong("gps_requested_interval_ms",samplingIntervalMs).apply()
        // Registration only requests callbacks; an actual trusted fix is required
        // before clearing an outage diagnostic or its notification.
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
        if(intent?.action==ACTION_STOP) {
            GpsRecoveryMonitorWorker.cancel(this)
            trace("MANUAL_STOP")
            prefs.edit().putBoolean(GpsWorkdaySession.CONSENT,false)
                .putBoolean("gps_enabled",false).putBoolean("gps_resume_pending",false).apply()
            shutdown()
            return START_NOT_STICKY
        }
        when(GpsStartPermissionRules.decide(
            GpsWorkdaySession.shouldTrack(prefs),
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED,
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED)) {
            GpsStartPermissionRules.Decision.STOP_DISABLED -> {
                shutdown()
                return START_NOT_STICKY
            }
            GpsStartPermissionRules.Decision.STOP_PERMISSION -> {
                trace("PERMISSION_MISSING")
                // Keep user intent, but never present a failed service as active.
                prefs.edit().putString("gps_service_problem","permission").commit()
                shutdown()
                return START_NOT_STICKY
            }
            GpsStartPermissionRules.Decision.START -> Unit
        }
        try {
            if(Build.VERSION.SDK_INT>=29) startForeground(NOTIFICATION_ID,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            else startForeground(NOTIFICATION_ID,notification())
            if(!sessionStarted) {
                val startupMs=System.currentTimeMillis()
                val lastFix=try {
                    JSONObject(prefs.getString("gps_last_fix","{}") ?: "{}").optLong("time")
                } catch(_:Exception) { 0L }
                if(GpsSessionBoundaryRules.clockRolledBack(lastFix,startupMs)) {
                    // Reset the obsolete wall-clock checkpoint; retain historical
                    // GPS journals and any user-confirmed work records.
                    prefs.edit().remove("gps_last_fix").commit()
                } else if(GpsSessionBoundaryRules.shouldRecordStartupGap(lastFix,startupMs)) {
                    recordTripBoundary(startupMs)
                }
                // A new foreground service session must not join motion evidence from
                // a previous run, even if the device was switched off only briefly.
                val savedArrival=try {
                    JSONObject(prefs.getString("gps_arrival_state","{}") ?: "{}")
                } catch(_:Exception) { JSONObject() }
                val confirmed=savedArrival.optString("confirmed")
                val safeArrival=JSONObject().put("confirmed",confirmed)
                    .put("candidate","").put("since",0L).put("last",0L)
                val checkpointSaved=prefs.edit().remove("gps_trip_state").remove("gps_motion_point")
                    .remove("gps_last_provider").remove("gps_last_source_fix_time")
                    .putInt("gps_rejected_quality_count",0)
                    .putInt("gps_rejected_order_count",0)
                    .putInt("gps_rejected_jump_count",0)
                    .putString("gps_arrival_state",safeArrival.toString())
                    .putLong("gps_requested_interval_ms",GpsAdaptiveSampling.ACTIVE_INTERVAL_MS)
                    .putBoolean("gps_service_active",true)
                    .remove("gps_recovery_alert_at")
                    .putLong("gps_session_start",startupMs)
                    .putLong("gps_service_heartbeat_ms",startupMs).commit()
                if(!GpsSessionBoundaryRules.mayActivateAfterPersist(checkpointSaved)) {
                    trace("SESSION_CHECKPOINT_FAILED",startupMs)
                    throw IllegalStateException("GPS startup checkpoint was not persisted")
                }
                samplingState=GpsAdaptiveSampling.State()
                samplingIntervalMs=GpsAdaptiveSampling.ACTIVE_INTERVAL_MS
                motionWakeState=GpsMotionWakeRules.State()
                lastSensorBurstStartMs=0L
                lastSensorEvaluationMs=0L
                lastWatchdogProbeMs=0L
                activeGeofenceProbeSinceMs=0L
                lastGeofenceProbeMs=0L
                sessionStarted=true
                // A warning from the previous session must not hide a new outage.
                GpsRecoveryMonitorWorker.dismissAlert(this)
            }
            // With no provider keep the visible foreground service listening for location changes.
            if(prefs.getBoolean("gps_adaptive_sampling",true)) startMotionSensor()
            else {
                stopMotionSensor()
                samplingIntervalMs=GpsAdaptiveSampling.ACTIVE_INTERVAL_MS
                samplingState=GpsAdaptiveSampling.State()
            }
            try { updateSubscription() }
            catch(_:IllegalArgumentException) { trace("INITIAL_PROVIDER_REGISTRATION_FAILED") }
            catch(_:IllegalStateException) { trace("INITIAL_PROVIDER_REGISTRATION_FAILED") }
            watchdogHandler.removeCallbacks(watchdogTick)
            watchdogHandler.postDelayed(watchdogTick,GpsWatchdogRules.CHECK_INTERVAL_MS)
            GpsRecoveryMonitorWorker.schedule(this)
            trace("SERVICE_START",details=JSONObject()
                .put("provider",currentProvider ?: "").put("intervalMs",samplingIntervalMs))
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
        when(GpsLocationCallbackRules.decide(
            GpsWorkdaySession.shouldTrack(prefs),
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED,
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED,
            GpsFixSourceRules.accept(currentProvider,location.provider,true))) {
            GpsLocationCallbackRules.Action.STOP_DISABLED -> { shutdown();return }
            GpsLocationCallbackRules.Action.STOP_PERMISSION -> {
                trace("PERMISSION_REVOKED")
                prefs.edit().putString("gps_service_problem","permission").commit()
                shutdown()
                return
            }
            GpsLocationCallbackRules.Action.IGNORE_SOURCE -> {
                trace("REJECT_SOURCE",details=JSONObject()
                    .put("provider",location.provider ?: "").put("expected",currentProvider ?: ""))
                return
            }
            GpsLocationCallbackRules.Action.OBSERVE_APPROXIMATE -> {
                // Approximate fixes must not bridge precise motion or geofence dwell.
                if (prefs.contains("gps_motion_point") || prefs.contains("gps_trip_state")) {
                    recordTripBoundary()
                    val arrival=try { JSONObject(prefs.getString("gps_arrival_state","{}") ?: "{}") }
                        catch (_: org.json.JSONException) { JSONObject() }
                    prefs.edit().remove("gps_motion_point").remove("gps_trip_state")
                        .remove("gps_last_fix").remove("gps_last_source_fix_time")
                        .putString("gps_arrival_state",JSONObject()
                            .put("confirmed",arrival.optString("confirmed")).toString()).commit()
                }
                trace("APPROXIMATE_LOCATION")
                return
            }
            GpsLocationCallbackRules.Action.PROCESS -> Unit
        }
        val now=System.currentTimeMillis()
        val fixInfo=JSONObject().put("lat",location.latitude).put("lon",location.longitude)
            .put("accuracyM",location.accuracy.toDouble()).put("provider",location.provider ?: "")
            .put("sourceTime",location.time)
        trace("FIX",now,fixInfo)
        if(!GpsLocationQuality.acceptable(now,location.time,location.accuracy.toDouble(),
                location.latitude,location.longitude)) {
            trace("REJECT_QUALITY",now,fixInfo)
            prefs.edit().putLong("gps_rejected_fix_at",now)
                .putInt("gps_rejected_quality_count",
                    prefs.getInt("gps_rejected_quality_count",0)+1).apply()
            return
        }
        val previousFix=try { JSONObject(prefs.getString("gps_last_fix","{}") ?: "{}") } catch(_:Exception) { JSONObject() }
        val previousRecorded=previousFix.optLong("time")
        val rollback=GpsSessionBoundaryRules.shouldResetActiveCheckpoint(
            previousRecorded,now,location.time)
        if(previousRecorded>=now && !rollback) {
            trace("REJECT_CLOCK",now,JSONObject().put("previous",previousRecorded))
            return
        }
        if(rollback) {
            trace("CLOCK_ROLLBACK",now,JSONObject().put("previous",previousRecorded))
            // Discard only stale GPS evidence. Never edit historical events or work.
            val savedArrival=try {
                JSONObject(prefs.getString("gps_arrival_state","{}") ?: "{}")
            } catch(_:Exception) { JSONObject() }
            val reset=GpsSessionBoundaryRules.resetArrival(
                GpsSessionBoundaryRules.ArrivalCheckpoint(
                    savedArrival.optString("confirmed").ifBlank { null },
                    savedArrival.optString("candidate").ifBlank { null },
                    savedArrival.optLong("since"),savedArrival.optLong("last")))
            val safeArrival=JSONObject()
                .put("confirmed",reset.confirmedId ?: "")
                .put("candidate",reset.candidateId ?: "")
                .put("since",reset.candidateSinceMs).put("last",reset.lastFixMs)
            if(!prefs.edit().remove("gps_last_fix").remove("gps_last_source_fix_time")
                .remove("gps_motion_point").remove("gps_trip_state")
                .remove("gps_last_provider")
                .putString("gps_arrival_state",safeArrival.toString()).commit()) return
        }
        val lastRecorded=if(rollback) 0L else previousRecorded
        val sourceTime=location.time
        if(!GpsFixOrderRules.accept(sourceTime,
                prefs.getLong("gps_last_source_fix_time",0L))) {
            trace("REJECT_ORDER",now,fixInfo.put("previousSourceTime",
                prefs.getLong("gps_last_source_fix_time",0L)))
            prefs.edit().putInt("gps_rejected_order_count",
                prefs.getInt("gps_rejected_order_count",0)+1).apply()
            return
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
                trace("REJECT_JUMP",now,fixInfo.put("previousMotionTime",trustedTime))
                prefs.edit().putInt("gps_rejected_jump_count",
                    prefs.getInt("gps_rejected_jump_count",0)+1).apply()
                return
            }
        }
        // Source order is checkpointed with the completed motion update below.
        val source=location.provider ?: ""
        val transition=GpsSourceTransitionRules.compare(
            prefs.getString("gps_last_provider",null),source)
        val recovery=GpsRecoveryRules.plan(lastRecorded,now)
        val motionEvidenceGap=lastRecorded>0 && now>lastRecorded &&
            now-lastRecorded>GpsTripRules.MAX_EVIDENCE_GAP_MS
        if(motionEvidenceGap && !recovery.recordGap) recordTripBoundary(now)
        if(recovery.resetArrival) {
            val prior=try { JSONObject(prefs.getString("gps_arrival_state","{}") ?: "{}") }
                catch(_:Exception) { JSONObject() }
            val recovered=GpsArrivalRecoveryRules.afterCoverageGap(GpsArrivalRules.State(
                confirmedId=prior.optString("confirmed").ifBlank { null },
                candidateId=prior.optString("candidate").ifBlank { null },
                candidateSinceMs=prior.optLong("since"),
                lastFixMs=prior.optLong("last")))
            if(!prefs.edit().putString("gps_arrival_state",JSONObject()
                    .put("confirmed",recovered.confirmedId ?: "")
                    .put("candidate","").put("since",0L).put("last",0L).toString())
                    .commit()) return
            trace("GEOFENCE_RECHECK_AFTER_GAP",now,JSONObject()
                .put("confirmed",recovered.confirmedId ?: ""))
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
        val state=GpsArrivalRecoveryRules.afterPlacesChanged(GpsArrivalRules.State(
            saved.optString("confirmed").ifBlank { null },
            saved.optString("candidate").ifBlank { null },
            saved.optLong("since"), saved.optLong("last")),places)
        val confirmedId=state.confirmedId
        val zoneEvidence=GpsGeofenceEvidence.observe(point,places,confirmedId)
        val needsEvidence=zoneEvidence.status==GpsGeofenceEvidence.Status.UNCERTAIN ||
            zoneEvidence.placeId!=confirmedId
        if(GpsGeofenceProbeRules.clockRollbackRequiresReset(now,lastGeofenceProbeMs)) {
            // A future-dated probe must not block new geofence evidence indefinitely.
            lastGeofenceProbeMs=0L
            activeGeofenceProbeSinceMs=0L
            trace("GEOFENCE_PROBE_CLOCK_RESET",now)
        }
        if(GpsGeofenceProbeRules.shouldBegin(now,lastGeofenceProbeMs,needsEvidence,
            !confirmedId.isNullOrBlank() || zoneEvidence.placeId!=null)) {
            lastGeofenceProbeMs=now
            activeGeofenceProbeSinceMs=now
            trace("GEOFENCE_RECHECK",now,JSONObject().put("confirmed",confirmedId ?: "")
                .put("accuracyM",point.accuracyM))
            val faster=safeSamplingInterval(now)
            if(faster!=samplingIntervalMs) {
                samplingIntervalMs=faster
                try { updateSubscription() }
                catch(_:SecurityException) { prefs.edit().putString("gps_service_problem","permission").apply();shutdown();return }
                catch(_:RuntimeException) { trace("GEOFENCE_PROBE_FAILED",now) }
            }
        }
        val confirmedPlace=places.firstOrNull { it.id==confirmedId }
        val distanceToConfirmed=confirmedPlace?.let {
            GpsWorkRules.distanceM(point.latitude,point.longitude,it.latitude,it.longitude)
        }
        trace("ZONE",now,JSONObject()
            .put("status",zoneEvidence.status.name).put("observed",zoneEvidence.placeId ?: "")
            .put("confirmed",confirmedId ?: "").put("candidate",state.candidateId ?: "")
            .put("candidateSince",state.candidateSinceMs).put("distanceToConfirmedM",distanceToConfirmed)
            .put("accuracyM",point.accuracyM).put("configuredObjects",places.size))
        val observed=zoneEvidence.placeId
        // Inaccurate border fixes cannot count as a confirmed departure.
        val decision=if(zoneEvidence.status==GpsGeofenceEvidence.Status.UNCERTAIN)
            GpsArrivalRules.uncertain(state,now)
        else GpsArrivalRules.advance(state,observed,now)
        val next=decision.state
        if(GpsGeofenceProbeRules.resolved(
                zoneEvidence.status==GpsGeofenceEvidence.Status.UNCERTAIN,
                next.candidateSinceMs)) {
            // Only a definite, settled state can rearm a future visit/exit.
            lastGeofenceProbeMs=0L
            activeGeofenceProbeSinceMs=0L
        }
        val editor=prefs.edit().putString("gps_arrival_state",JSONObject()
                .put("confirmed",next.confirmedId ?: "").put("candidate",next.candidateId ?: "")
                .put("since",next.candidateSinceMs).put("last",next.lastFixMs).toString())
        if(decision.event!=null) {
            trace(decision.event,now,JSONObject().put("objectId",decision.placeId ?: "")
                .put("distanceToConfirmedM",distanceToConfirmed))
            val history=try { JSONArray(prefs.getString("gps_events","[]") ?: "[]") } catch(_:Exception) { JSONArray() }
            val eventId=GpsEventIdentity.id(decision.event,now,decision.placeId)
            if((0 until history.length()).none {
                    val existing=history.optJSONObject(it)
                    GpsEventIdentity.sameEvent(existing?.optString("eventId"),
                        existing?.optString("event"),existing?.optLong("time") ?: 0L,
                        existing?.optString("objectId"),decision.event,now,decision.placeId)
                }) history.put(provisionalRow(decision.event,now,eventId,state.candidateSinceMs)
                .put("objectId",decision.placeId ?: ""))
            val limited=JSONArray()
            for(i in maxOf(0,history.length()-100) until history.length()) limited.put(history.opt(i))
            editor.putString("gps_events",limited.toString())
        }
        // Persist the arrival state together with its event: a process death must not
        // advance the arrival state while losing the corresponding journal entry.
        if(!editor.commit()) return
        // Travel events are kept apart from work shifts until the user confirms billing rules.
        val previous=try { JSONObject(prefs.getString("gps_motion_point","{}") ?: "{}") } catch(_:Exception) { JSONObject() }
        val lastTime=previous.optLong("time")
        var tripTransitionRecorded=false
        if(GpsMotionContinuityRules.contiguous(lastTime,now)) {
            val previousPoint=GpsWorkRules.Point(previous.optDouble("lat"),previous.optDouble("lon"),
                previous.optDouble("accuracy"),lastTime)
            val moving=GpsWorkRules.traveling(previousPoint,point)
            if(moving && prefs.getBoolean("gps_adaptive_sampling",true)) {
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
            trace("TRIP_EVIDENCE",now,JSONObject().put("moving",moving)
                .put("before",tripState.mode.name).put("candidate",tripState.candidate.name)
                .put("candidateSince",tripState.candidateSinceMs)
                .put("distanceM",GpsWorkRules.distanceM(previousPoint.latitude,
                    previousPoint.longitude,point.latitude,point.longitude))
                .put("deltaMs",now-lastTime))
            val t=transition.state
            val previousTripId=previousState.optString("tripId").takeIf { it.isNotBlank() }
            val currentTripId=when {
                transition.event=="TRIP_START" -> GpsEventIdentity.id("TRIP_START",now)
                t.mode==GpsTripRules.Mode.MOVING -> previousTripId
                else -> null
            }
            val motionEditor=prefs.edit().putString("gps_trip_state",JSONObject()
                .put("tripId",currentTripId)
                .put("mode",t.mode.name).put("candidate",t.candidate.name)
                .put("candidateSince",t.candidateSinceMs).put("last",t.lastFixMs)
                .put("stoppedSince",t.stoppedSinceMs).put("longStopReported",t.longStopReported).toString())
            if(transition.event!=null) {
                trace(transition.event,now,JSONObject().put("mode",t.mode.name))
                val journal=try { JSONArray(prefs.getString("gps_trip_events","[]") ?: "[]") } catch(_:Exception) { JSONArray() }
                val eventId=GpsEventIdentity.id(transition.event,now)
                if((0 until journal.length()).none {
                        val existing=journal.optJSONObject(it)
                        GpsEventIdentity.sameEvent(existing?.optString("eventId"),
                            existing?.optString("event"),existing?.optLong("time") ?: 0L,
                            null,transition.event,now)
                    }) journal.put(provisionalRow(transition.event,now,eventId,
                    tripState.candidateSinceMs.takeIf { transition.event!="LONG_STOP" },
                    if (transition.event=="TRIP_STOP") previousTripId else currentTripId))
                val recent=JSONArray()
                for(i in maxOf(0,journal.length()-100) until journal.length()) recent.put(journal.opt(i))
                motionEditor.putString("gps_trip_events",recent.toString())
            }
            // The motion evidence point, classifier state, and generated event form
            // one durable transaction. Avoid replaying the same transition after a crash.
            motionEditor.putString("gps_motion_point",JSONObject()
                .put("lat",location.latitude).put("lon",location.longitude)
                .put("accuracy",location.accuracy.toDouble()).put("time",now).toString())
                .putString("gps_last_fix",JSONObject()
                    .put("lat",location.latitude).put("lon",location.longitude)
                    .put("accuracy",location.accuracy.toDouble()).put("time",now).toString())
                .putLong("gps_last_source_fix_time",sourceTime)
            if(!motionEditor.commit()) return
            tripTransitionRecorded=transition.event=="TRIP_START" || transition.event=="TRIP_STOP"
        } else if(lastTime>0 && now-lastTime>1_800_000L) {
            prefs.edit().remove("gps_trip_state").apply()
        }
        if(!GpsMotionContinuityRules.contiguous(lastTime,now)) {
            if(!prefs.edit().putString("gps_motion_point",JSONObject()
                .put("lat",location.latitude).put("lon",location.longitude)
                .put("accuracy",location.accuracy.toDouble()).put("time",now).toString())
                .putString("gps_last_fix",JSONObject()
                    .put("lat",location.latitude).put("lon",location.longitude)
                    .put("accuracy",location.accuracy.toDouble()).put("time",now).toString())
                .putLong("gps_last_source_fix_time",sourceTime).commit()) return
        }
        // A trusted fix confirms recovery; remove stale warnings and permit a new
        // notification if an independent outage occurs later in the same shift.
        val oldProblem=prefs.getString("gps_service_problem",null)
        if(GpsRecoveryMonitorRules.recoveredByTrustedFix(oldProblem)) {
            prefs.edit().remove("gps_service_problem")
                .remove("gps_recovery_alert_at").apply()
            GpsRecoveryMonitorWorker.dismissAlert(this)
            trace("GPS_RECOVERED",now,JSONObject().put("previousProblem",oldProblem))
        }
        if(decision.event!=null || tripTransitionRecorded || recovery.recordGap ||
            motionEvidenceGap || transition.resetMotion) CoreGpsStageBridge.refresh(this)
        if(prefs.getBoolean("gps_adaptive_sampling",true)) {
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
        CoreGpsStageBridge.dismiss(this)
        if (!GpsWorkdaySession.shouldTrack(prefs)) GpsRecoveryMonitorWorker.cancel(this)
        watchdogHandler.removeCallbacks(watchdogTick)
        if(sessionStarted) { trace("SERVICE_STOP"); recordTripBoundary(); sessionStarted=false }
        stopMotionSensor()
        removeLocationUpdatesSafely()
        prefs.edit().putBoolean("gps_service_active",false).apply()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    override fun onDestroy() {
        watchdogHandler.removeCallbacks(watchdogTick)
        if(sessionStarted) { recordTripBoundary(); sessionStarted=false }
        stopMotionSensor()
        unregisterReceiver(providerReceiver)
        removeLocationUpdatesSafely()
        prefs.edit().putBoolean("gps_service_active",false).apply()
        super.onDestroy()
    }
}
