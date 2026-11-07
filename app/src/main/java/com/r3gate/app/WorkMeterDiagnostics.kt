package com.r3gate.app

import android.content.Context
import android.os.SystemClock
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Disk writes never run on a sensor/UI callback. No PIN, credentials or typed form values. */
internal object WorkMeterDiagnostics {
    private val dropped=AtomicLong()
    private val last=mutableMapOf<String,Long>()
    private val io=ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,ArrayBlockingQueue(256),
        java.util.concurrent.ThreadFactory { task -> Thread(task,"workmeter-diagnostics").apply { isDaemon=true } },
        java.util.concurrent.RejectedExecutionHandler { _,_ -> dropped.incrementAndGet() })
    private fun store(context:Context)=DiagnosticLogStore(File(context.filesDir,"diagnostics"))
    @Synchronized fun record(context:Context,kind:String,details:JSONObject=JSONObject(),throttleMs:Long=0) {
        val app=context.applicationContext; val now=System.currentTimeMillis(); val elapsed=SystemClock.elapsedRealtime()
        val previous=last[kind]
        if(previous!=null && throttleMs>0 && elapsed>=previous && elapsed-previous<throttleMs) return
        last[kind]=elapsed
        val prefs=app.getSharedPreferences("workmeter_diagnostics",0)
        val installation=prefs.getString("installation",null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("installation",it).apply() }
        val row=JSONObject().put("time",now).put("elapsedMs",elapsed).put("installation",installation)
            .put("kind",kind).put("details",details).toString()
        if(row.toByteArray().size>16_384) { dropped.incrementAndGet(); return }
        io.execute { try { store(app).append(now,row) } catch(_:Exception) { dropped.incrementAndGet() } }
    }
    fun snapshot(context:Context):String {
        // Flush all records queued before the snapshot without doing file IO on main.
        val task=java.util.concurrent.FutureTask { store(context).snapshot() }
        io.execute(task)
        val history=try { task.get(5,TimeUnit.SECONDS) } catch(_:Exception) { "{\"kind\":\"DIAGNOSTICS_SNAPSHOT_UNAVAILABLE\"}\n" }
        val p=context.getSharedPreferences("r3_gate",0)
        val header=JSONObject().put("kind","SNAPSHOT").put("schema",1).put("time",System.currentTimeMillis())
            .put("version",context.packageManager.getPackageInfo(context.packageName,0).versionName).put("android",android.os.Build.VERSION.SDK_INT)
            .put("manufacturer",android.os.Build.MANUFACTURER).put("model",android.os.Build.MODEL)
            .put("gpsEnabled",p.getBoolean("gps_enabled",false)).put("workdayOpen",GpsWorkdaySession.workdayOpen(p))
            .put("consent",GpsWorkdaySession.hasConsent(p)).put("adaptive",p.getBoolean("gps_adaptive_sampling",true))
            .put("serviceActive",p.getBoolean("gps_service_active",false)).put("serviceProblem",p.getString("gps_service_problem","none"))
            .put("requestedIntervalMs",p.getLong("gps_requested_interval_ms",0)).put("droppedRecords",dropped.get())
        val legacy=JSONObject().put("kind","LEGACY_GPS_REPORT").put("text",GpsTraceJournal.report(p))
        return header.toString()+"\n"+history+legacy.toString()+"\n"
    }
    fun importHistory(context:Context,bytes:ByteArray) = store(context).importHistory(bytes)
}
