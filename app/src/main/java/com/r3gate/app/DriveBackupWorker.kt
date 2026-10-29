package com.r3gate.app

import android.content.Context
import androidx.work.*
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class DriveBackupWorker(context:Context,params:WorkerParameters):Worker(context,params) {
    private val prefs=applicationContext.getSharedPreferences("r3_gate",Context.MODE_PRIVATE)
    override fun doWork():Result {
        val email=prefs.getString("driveEmail","").orEmpty()
        if(email.isBlank() || !prefs.getBoolean("driveConnected",false)) return needsAccount()
        try {
            val authorization=Tasks.await(Identity.getAuthorizationClient(applicationContext).authorize(AuthorizationRequest.builder().setRequestedScopes(listOf(Scope("https://www.googleapis.com/auth/drive.file"))).build()),60,TimeUnit.SECONDS)
            if(authorization.hasResolution() || authorization.accessToken.isNullOrBlank()) return needsAccount()
            val token=authorization.accessToken!!; val drive=DriveStore()
            val actualEmail=drive.json("GET","https://www.googleapis.com/drive/v3/about?fields=user(emailAddress)",token).optJSONObject("user")?.optString("emailAddress")
            if(!email.equals(actualEmail,true)) return needsAccount()
            val snapshot=snapshot(applicationContext)
            val day=LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
            val name="WorkMeter-backup-"+day+".json"
            synchronized(DriveStore.folderLock) {
                if(isStopped) return Result.retry()
                val root=drive.root(token); val folder=drive.folder("Backup",root,token)
                val q="trashed = false and '"+folder+"' in parents and name = '"+name+"'"
                val matches=drive.json("GET","https://www.googleapis.com/drive/v3/files?q="+URLEncoder.encode(q,"UTF-8")+"&fields=files(id)&pageSize=1",token).optJSONArray("files")
                val id=if(matches!=null && matches.length()>0) matches.getJSONObject(0).getString("id") else drive.json("POST","https://www.googleapis.com/drive/v3/files?fields=id",token,JSONObject().put("name",name).put("mimeType","application/json").put("parents",JSONArray().put(folder))).getString("id")
                if(isStopped) return Result.retry()
                val connection=URL("https://www.googleapis.com/upload/drive/v3/files/"+id+"?uploadType=media").openConnection() as HttpURLConnection
                try {
                    connection.requestMethod="PATCH"; connection.connectTimeout=20000; connection.readTimeout=30000; connection.doOutput=true
                    connection.setRequestProperty("Authorization","Bearer "+token);connection.setRequestProperty("Content-Type","application/json; charset=UTF-8")
                    connection.outputStream.use { it.write(snapshot.toString(2).toByteArray(Charsets.UTF_8)) }
                    if(connection.responseCode !in 200..299) throw DriveHttpException(connection.responseCode)
                } finally { connection.disconnect() }
            }
            val whenSaved=java.text.SimpleDateFormat("dd-MM-yyyy HH:mm",java.util.Locale.US).format(java.util.Date())
            prefs.edit().putString("lastDriveBackup",whenSaved).putString("backupStatus","Kopia zapisana: "+whenSaved).putString("backupFolder","WorkMeter / Backup").apply()
            UploadNotifications.result(applicationContext,"backup","Kopia dziennika zapisana w WorkMeter / Backup",false)
            return Result.success()
        } catch(_:InterruptedException) { return Result.retry() }
        catch(e:Exception) {
            if(isStopped) return Result.retry()
            if(e is DriveHttpException && e.status in listOf(400,403,404)) return needsAccount()
            prefs.edit().putString("backupStatus","Oczekiwanie na internet lub ponowienie kopii").apply()
            return Result.retry()
        }
    }
    private fun needsAccount():Result {
        prefs.edit().putString("backupStatus","Połącz ponownie Google Disk, aby wznowić kopię").apply()
        UploadNotifications.result(applicationContext,"backup","Kopia wymaga połączenia Google Disk — dotknij, aby wznowić",true)
        return Result.success(workDataOf("needsAccount" to true))
    }
    companion object {
        fun snapshot(context:Context):JSONObject {
            val p=context.getSharedPreferences("r3_gate",Context.MODE_PRIVATE)
            return JSONObject(p.getString("data","{}")!!).put("wtBackupVersion",1)
                .put("wtSettings",JSONObject().put("widgetTransparency",p.getInt("widgetTransparency",0)))
                .put("backupCreatedAt",java.time.Instant.now().toString())
        }
        fun schedule(context:Context) {
            val p=context.getSharedPreferences("r3_gate",Context.MODE_PRIVATE)
            if(!p.getBoolean("driveConnected",false) || p.getString("driveEmail","").isNullOrBlank() || !p.getBoolean("dailyBackupEnabled",true)) return
            val request=PeriodicWorkRequestBuilder<DriveBackupWorker>(1,TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).addTag("driveBackup").build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("workmeter-daily-backup",ExistingPeriodicWorkPolicy.KEEP,request)
        }
        fun manual(context:Context) {
            val request=OneTimeWorkRequestBuilder<DriveBackupWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).addTag("driveBackup").build()
            WorkManager.getInstance(context).enqueueUniqueWork("workmeter-manual-backup",ExistingWorkPolicy.KEEP,request)
            context.getSharedPreferences("r3_gate",Context.MODE_PRIVATE).edit().putString("backupStatus","Kopia w kolejce").apply()
        }
    }
}
