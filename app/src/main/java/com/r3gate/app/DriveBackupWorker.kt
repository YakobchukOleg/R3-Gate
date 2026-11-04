package com.r3gate.app

import android.content.Context
import androidx.work.*
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import org.json.JSONObject
import android.net.Uri
import com.r3gate.app.backup.DriveBackupUpload
import com.r3gate.app.backup.LocalBackupStore
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.TimeUnit

class DriveBackupWorker(context:Context,params:WorkerParameters):Worker(context,params) {
    private val prefs=applicationContext.getSharedPreferences("r3_gate",Context.MODE_PRIVATE)
    override fun doWork():Result {
        val email = prefs.getString("driveEmail", "").orEmpty()
        if (email.isBlank() || !prefs.getBoolean("driveConnected", false)) return needsAccount()
        val expectedEmail = inputData.getString("backupAccount") ?: email
        if (!email.equals(expectedEmail, true)) return Result.failure()
        val queue = applicationContext.getSharedPreferences("workmeter_drive_backup_queue", Context.MODE_PRIVATE)
        val key = id.toString()
        val directory = File(applicationContext.filesDir, "drive-backup-queue/$key")
        try {
            fun checkActive() {
                if (isStopped || !prefs.getBoolean("driveConnected", false) ||
                    !email.equals(prefs.getString("driveEmail", ""), true)) throw InterruptedException()
            }
            checkActive()
            val pending = queue.getString(key, null)?.let(::JSONObject)
            if (pending != null && !email.equals(pending.optString("account"), true)) return Result.failure()
            if (pending?.optBoolean("complete") == true) {
                directory.deleteRecursively()
                if (runAttemptCount > 0) return Result.success()
                check(queue.edit().remove(key).commit())
            }
            val authorization = Tasks.await(Identity.getAuthorizationClient(applicationContext).authorize(
                AuthorizationRequest.builder().setRequestedScopes(listOf(Scope("https://www.googleapis.com/auth/drive.file"))).build()
            ), 60, TimeUnit.SECONDS)
            if (authorization.hasResolution() || authorization.accessToken.isNullOrBlank()) return needsAccount()
            val token = authorization.accessToken!!
            val drive = DriveStore()
            val actualEmail = drive.json("GET", "https://www.googleapis.com/drive/v3/about?fields=user(emailAddress)", token)
                .optJSONObject("user")?.optString("emailAddress")
            if (!email.equals(actualEmail, true)) return needsAccount()
            val upload = DriveBackupUpload(drive, token)
            val operation = pending?.takeUnless { it.optBoolean("complete") }
                ?: JSONObject().put("account", email)
            check(queue.edit().putString(key, operation.toString()).commit())
            val existing = LocalBackupStore.list(directory).singleOrNull()
            val archive = if (operation.has("file")) {
                require(existing?.name == operation.getString("file")) { "Pending backup archive missing" }
                checkNotNull(existing)
            } else {
                existing ?: LocalBackupStore.create(directory, snapshot(applicationContext).toString(), { value ->
                    checkActive()
                    val uri = Uri.parse(value)
                    require(uri.scheme == "content" || uri.scheme == "file")
                    applicationContext.contentResolver.openInputStream(uri) ?: throw FileNotFoundException()
                }, retain = 1, beforePublish = ::checkActive)
            }
            val (parent, driveId) = if (operation.has("driveId")) {
                operation.getString("parent") to operation.getString("driveId")
            } else {
                checkActive()
                val parent = drive.folder("Backup", drive.root(token), token)
                val driveId = upload.allocateId()
                operation.put("file", archive.name).put("parent", parent).put("driveId", driveId)
                check(queue.edit().putString(key, operation.toString()).commit())
                parent to driveId
            }
            upload.upload(driveId, parent, archive, ::checkActive)
            checkActive()
            val whenSaved=java.text.SimpleDateFormat("dd-MM-yyyy HH:mm",java.util.Locale.US).format(java.util.Date())
            check(prefs.edit().putString("lastDriveBackup", whenSaved)
                .putString("backupStatus", "Kopia ZIP zapisana: $whenSaved")
                .putString("backupFolder", "WorkMeter / Backup").commit())
            check(queue.edit().putString(key, operation.put("complete", true).toString()).commit())
            directory.deleteRecursively()
            UploadNotifications.result(applicationContext,"backup","Kopia ZIP zapisana w WorkMeter / Backup",false)
            return Result.success()
        } catch(_:InterruptedException) { return Result.retry() }
        catch(e:Exception) {
            if(isStopped) return Result.retry()
            if (e is DriveHttpException && e.status == 401) return needsAccount()
            if (e is FileNotFoundException || e is SecurityException || e is IllegalArgumentException ||
                e is DriveHttpException && e.status in listOf(400, 404, 413) || runAttemptCount >= 5) {
                prefs.edit().putString("backupStatus", "Nie można wysłać kopii ZIP. Sprawdź dane i dostęp do plików.").apply()
                return Result.failure()
            }
            prefs.edit().putString("backupStatus","Oczekiwanie na internet lub ponowienie kopii").apply()
            return Result.retry()
        }
    }
    private fun needsAccount():Result {
        prefs.edit().putString("backupStatus","Połącz ponownie Google Disk, aby wznowić kopię").apply()
        UploadNotifications.result(applicationContext,"backup","Kopia wymaga połączenia Google Disk — dotknij, aby wznowić",true)
        return Result.failure(workDataOf("needsAccount" to true))
    }
    companion object {
        fun snapshot(context:Context):JSONObject {
            val p=context.getSharedPreferences("r3_gate",Context.MODE_PRIVATE)
            return JSONObject(p.getString("data","{}")!!).put("wtBackupVersion",1)
                .put("wtSettings",JSONObject().put("widgetTransparency",p.getInt("widgetTransparency",0)))
                .put("backupCreatedAt",java.time.Instant.now().toString())
        }
        fun schedule(context:Context) {
            com.r3gate.app.backup.LocalBackupWorker.schedule(context)
            val p=context.getSharedPreferences("r3_gate",Context.MODE_PRIVATE)
            if(!p.getBoolean("driveConnected",false) || p.getString("driveEmail","").isNullOrBlank() || !p.getBoolean("dailyBackupEnabled",true)) return
            val request=PeriodicWorkRequestBuilder<DriveBackupWorker>(1,TimeUnit.DAYS)
                .setInputData(workDataOf("backupAccount" to p.getString("driveEmail", "").orEmpty()))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).addTag("driveBackup").build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("workmeter-daily-backup",ExistingPeriodicWorkPolicy.UPDATE,request)
        }
        fun manual(context:Context) {
            val prefs = context.getSharedPreferences("r3_gate", Context.MODE_PRIVATE)
            prefs.edit().putString("backupStatus", "Kopia w kolejce").apply()
            val request=OneTimeWorkRequestBuilder<DriveBackupWorker>()
                .setInputData(workDataOf("backupAccount" to prefs.getString("driveEmail", "").orEmpty()))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).addTag("driveBackup").build()
            WorkManager.getInstance(context).enqueueUniqueWork("workmeter-manual-backup",ExistingWorkPolicy.KEEP,request)
        }
    }
}
