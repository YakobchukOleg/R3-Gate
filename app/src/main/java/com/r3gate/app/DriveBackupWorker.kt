package com.r3gate.app

import android.content.Context
import androidx.work.*
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import org.json.JSONObject
import com.r3gate.app.backup.BackupQueueActions
import com.r3gate.app.backup.BackupSnapshot
import com.r3gate.app.backup.BackupStatus
import com.r3gate.app.backup.BackupState
import com.r3gate.app.backup.DriveBackupFailure
import com.r3gate.app.backup.DriveBackupMaintenance
import com.r3gate.app.backup.DriveBackupUpload
import com.r3gate.app.backup.LocalBackupStore
import com.r3gate.app.backup.PhotoArchiveAndroid
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class DriveBackupWorker(context:Context,params:WorkerParameters):Worker(context,params) {
    private val prefs=applicationContext.getSharedPreferences("r3_gate",Context.MODE_PRIVATE)
    private val backupAccount = inputData.getString("backupAccount")
        ?: prefs.getString("driveEmail", "").orEmpty()
    private val queueId = inputData.getString(BackupStatus.QUEUE_ID)?.let(UUID::fromString) ?: id
    override fun doWork(): Result = DriveBackupMaintenance.whileActive(queueId, ::runBackup) ?: Result.retry()

    private fun runBackup(): Result {
        val email = prefs.getString("driveEmail", "").orEmpty()
        if (email.isBlank() || !prefs.getBoolean("driveConnected", false)) return needsAccount()
        if (!email.equals(backupAccount, true)) return Result.failure()
        val queue = applicationContext.getSharedPreferences("workmeter_drive_backup_queue", Context.MODE_PRIVATE)
        val key = queueId.toString()
        val root = File(applicationContext.filesDir, "drive-backup-queue")
        val directory = File(root, key)
        try {
            fun checkActive() {
                if (isStopped || !prefs.getBoolean("driveConnected", false) ||
                    !email.equals(prefs.getString("driveEmail", ""), true)) throw InterruptedException()
            }
            checkActive()
            cleanQueue(root, queue)
            checkActive()
            val pending = queue.getString(key, null)?.let(::JSONObject)
            if (pending == null && inputData.getBoolean(BackupStatus.RESUME_ONLY, false)) {
                return failed("Brak danych tej operacji. Utwórz nową kopię.")
            }
            if (pending != null && !email.equals(pending.optString("account"), true)) return Result.failure()
            if (pending?.optBoolean("complete") == true) {
                directory.deleteRecursively()
                if (runAttemptCount > 0 || inputData.getBoolean(BackupStatus.RESUME_ONLY, false)) return Result.success(workDataOf(BackupStatus.VERIFIED to true, BackupStatus.VERIFIED_ACCOUNT to email))
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
                existing ?: BackupSnapshot.create(applicationContext, directory, retain = 1, checkActive = ::checkActive)
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
            if (existing != null) {
                PhotoArchiveAndroid.verify {
                    checkActive()
                    archive.inputStream()
                }
            }
            setProgressAsync(workDataOf(BackupStatus.SENDING to true))
            upload.upload(driveId, parent, archive, operation.optString("session").takeIf(String::isNotBlank),
                saveSession = { session ->
                    checkActive()
                    if (session == null) operation.remove("session") else operation.put("session", session)
                    check(queue.edit().putString(key, operation.toString()).commit())
                }, checkActive = ::checkActive)
            operation.remove("session")
            com.r3gate.app.backup.BackupCompanions.publish(archive)
            for(path in com.r3gate.app.backup.BackupCompanions.paths) {
                val companion=com.r3gate.app.backup.BackupCompanions.file(archive,path)
                if(!companion.exists()) continue // Resuming an older, pre-diagnostics backup.
                val prefix=if(path==com.r3gate.app.backup.BackupCompanions.OBJECTS) "objects" else "diagnostics"
                val companionId=operation.optString(prefix+"Id").ifBlank {
                    upload.allocateId().also { allocated ->
                        operation.put(prefix+"Id",allocated)
                        check(queue.edit().putString(key,operation.toString()).commit())
                    }
                }
                upload.upload(companionId,parent,companion,operation.optString(prefix+"Session").takeIf(String::isNotBlank),
                    saveSession={ session ->
                        if(session==null) operation.remove(prefix+"Session") else operation.put(prefix+"Session",session)
                        check(queue.edit().putString(key,operation.toString()).commit())
                    },checkActive=::checkActive,mimeType=if(prefix=="objects") "application/json" else "application/x-ndjson")
            }
            checkActive()
            val whenSaved=java.text.SimpleDateFormat("dd-MM-yyyy HH:mm",java.util.Locale.US).format(java.util.Date())
            check(prefs.edit().putString("lastDriveBackup", whenSaved)
                .putString("backupStatus", "Kopia ZIP zapisana: $whenSaved")
                .putString("backupFolder", "WorkMeter / Backup")
                .putString(BackupStatus.VERIFIED_AT, java.time.Instant.now().toString())
                .putString(BackupStatus.VERIFIED_ACCOUNT, email)
                .remove(BackupStatus.PROBLEM_STATE).remove(BackupStatus.PROBLEM_ACCOUNT)
                .remove(BackupStatus.PROBLEM_MESSAGE).remove(BackupStatus.PROBLEM_ACTION).commit())
            check(queue.edit().putString(key, operation.put("complete", true).toString()).commit())
            directory.deleteRecursively()
            UploadNotifications.result(applicationContext,"backup","Kopia ZIP zapisana w WorkMeter / Backup",false)
            return Result.success(workDataOf(BackupStatus.VERIFIED to true, BackupStatus.VERIFIED_ACCOUNT to email))
        } catch(_:InterruptedException) { return Result.retry() }
        catch(e:Exception) {
            if(isStopped) return Result.retry()
            val failure = (e as? DriveHttpException)?.let { DriveBackupFailure.from(it) }
            if (failure == DriveBackupFailure.ACCOUNT) return needsAccount()
            if (failure == DriveBackupFailure.STORAGE_QUOTA) {
                return failed("Brak miejsca w Google Drive. Zwolnij miejsce i ponów kopię.", needsAction = true,
                    requiredAction = BackupStatus.RequiredAction.FREE_DRIVE_SPACE)
            }
            if (failure == DriveBackupFailure.REJECTED || e is FileNotFoundException ||
                e is SecurityException || e is IllegalArgumentException) {
                return failed("Nie można wysłać kopii ZIP. Sprawdź dane i dostęp do plików.")
            }
            if (runAttemptCount >= 5) {
                return failed("Nie udało się wysłać kopii ZIP po kilku próbach. Ponów kopię.")
            }
            val status = if (failure == DriveBackupFailure.RATE_LIMIT) {
                "Limit żądań Google Drive. Kopia zostanie ponowiona."
            } else "Oczekiwanie na internet lub ponowienie kopii"
            prefs.edit().putString("backupStatus", status).apply()
            return Result.retry()
        }
    }

    private fun cleanQueue(root: File, queue: android.content.SharedPreferences) {
        val states = try {
            WorkManager.getInstance(applicationContext).getWorkInfosByTag(BackupStatus.DRIVE_TAG)
                .get(3, TimeUnit.SECONDS)
        } catch (_: TimeoutException) {
            return
        } catch (_: ExecutionException) {
            return
        }
        val unfinished = states.filterNot { it.state.isFinished }
            .flatMap { listOf(it.id, BackupQueueActions.queueId(it)) }.toSet()
        val removed = DriveBackupMaintenance.removeStaleDirectories(root, queueId, unfinished)
        val editor = queue.edit()
        removed.forEach { editor.remove(it.toString()) }
        states.filter { it.state.isFinished }.map(BackupQueueActions::queueId).distinct()
            .filter { it != queueId && it !in unfinished && !File(root, it.toString()).exists() }
            .forEach { editor.remove(it.toString()) }
        editor.apply()
    }

    private fun failed(
        message: String, needsAction: Boolean = false,
        requiredAction: BackupStatus.RequiredAction = BackupStatus.RequiredAction.CHECK_FILES
    ): Result {
        recordProblem(message, if (needsAction) BackupState.ACTION_REQUIRED else BackupState.FAILED, requiredAction)
        return Result.failure(workDataOf(BackupStatus.ACTION_REQUIRED to needsAction))
    }

    private fun recordProblem(
        message: String, state: BackupState,
        requiredAction: BackupStatus.RequiredAction = BackupStatus.RequiredAction.CHECK_FILES
    ) {
        if (!backupAccount.equals(prefs.getString("driveEmail", "").orEmpty(), true)) return
        prefs.edit().putString("backupStatus", message)
            .putString(BackupStatus.PROBLEM_MESSAGE, message)
            .putString(BackupStatus.PROBLEM_STATE, state.name)
            .putString(BackupStatus.PROBLEM_ACTION, requiredAction.name)
            .putString(BackupStatus.PROBLEM_ACCOUNT, backupAccount).commit()
    }

    private fun needsAccount():Result {
        recordProblem("Połącz ponownie Google Drive, aby wznowić kopię", BackupState.ACTION_REQUIRED,
            BackupStatus.RequiredAction.RECONNECT_DRIVE)
        UploadNotifications.result(applicationContext,"backup","Kopia wymaga połączenia Google Disk — dotknij, aby wznowić",true)
        return Result.failure(workDataOf(BackupStatus.ACTION_REQUIRED to true))
    }
    companion object {
        fun snapshot(context:Context):JSONObject {
            val p=context.getSharedPreferences("r3_gate",Context.MODE_PRIVATE)
            return JSONObject(p.getString("data","{}")!!).put("wtBackupVersion",1)
                .put("wtSettings",JSONObject().put("widgetTransparency",p.getInt("widgetTransparency",0)))
                .put("backupCreatedAt",java.time.Instant.now().toString())
        }
        fun schedule(context:Context): Operation {
            com.r3gate.app.backup.LocalBackupWorker.schedule(context)
            val p=context.getSharedPreferences("r3_gate",Context.MODE_PRIVATE)
            val manager = WorkManager.getInstance(context)
            if (!p.getBoolean("driveConnected", false) || p.getString("driveEmail", "").isNullOrBlank() ||
                !p.getBoolean("dailyBackupEnabled", true)) {
                return manager.cancelUniqueWork(BackupStatus.DAILY_TAG)
            }
            val request=PeriodicWorkRequestBuilder<DriveBackupWorker>(1,TimeUnit.DAYS)
                .setInitialDelay(com.r3gate.app.backup.EveningBackup.delay(context),TimeUnit.MILLISECONDS)
                .setInputData(workDataOf("backupAccount" to p.getString("driveEmail", "").orEmpty()))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).addTag(BackupStatus.DRIVE_TAG).addTag(BackupStatus.DAILY_TAG).build()
            return manager.enqueueUniquePeriodicWork(BackupStatus.DAILY_TAG,ExistingPeriodicWorkPolicy.UPDATE,request)
        }
        fun manual(context: Context): Operation = queueManual(context, UUID.randomUUID(), resumeOnly = false)

        internal fun queueManual(context: Context, queueId: UUID, resumeOnly: Boolean): Operation {
            val prefs = context.getSharedPreferences("r3_gate", Context.MODE_PRIVATE)
            prefs.edit().putString("backupStatus", "Kopia w kolejce").apply()
            val request = OneTimeWorkRequestBuilder<DriveBackupWorker>()
                .setInputData(workDataOf("backupAccount" to prefs.getString("driveEmail", "").orEmpty(),
                    BackupStatus.QUEUE_ID to queueId.toString(), BackupStatus.RESUME_ONLY to resumeOnly))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag(BackupStatus.DRIVE_TAG).addTag(BackupQueueActions.queueTag(queueId)).build()
            return WorkManager.getInstance(context).enqueueUniqueWork(
                BackupStatus.MANUAL_WORK, ExistingWorkPolicy.KEEP, request)
        }
    }
}
