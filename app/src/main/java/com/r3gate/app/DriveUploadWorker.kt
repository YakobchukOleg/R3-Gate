package com.r3gate.app

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.work.*
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import org.json.JSONObject
import org.json.JSONArray
import java.net.URL
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** Durable, network-constrained photo jobs; access tokens never enter WorkManager's database. */
class DriveUploadWorker(context:Context,params:WorkerParameters):Worker(context,params) {
    private val prefs=applicationContext.getSharedPreferences("r3_gate",Context.MODE_PRIVATE)
    private val contentResolver get()=applicationContext.contentResolver
    private val reference get()=inputData.getString("reference").orEmpty()
    private val email get()=inputData.getString("email").orEmpty()
    private val scope="https://www.googleapis.com/auth/drive.file"
    private fun hash(s:String)=MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun data()=JSONObject(prefs.getString("data","{}")!!)
    private fun arr(d:JSONObject,key:String)=d.optJSONArray(key) ?: JSONArray().also { d.put(key,it) }
    private fun rows(d:JSONObject,key:String):List<JSONObject> { val a=d.optJSONArray(key) ?: return emptyList(); return (0 until a.length()).mapNotNull { a.optJSONObject(it) } }
    private fun recordDate(row:JSONObject)=row.optString("date").ifBlank { LocalDate.now().toString() }
    private fun dateText(date:String)=try { LocalDate.parse(date).format(DateTimeFormatter.ofPattern("dd-MM-yyyy")) } catch(_:Exception) { date }
    private fun save(d:JSONObject,kind:String,detail:String) {
        arr(d,"logs").put(JSONObject().put("at",java.text.SimpleDateFormat("dd-MM-yyyy HH:mm",java.util.Locale.US).format(java.util.Date())).put("type",kind).put("detail",detail))
        prefs.edit().putString("data",d.toString()).commit()
    }
    private fun <T> onMain(block:()->T):T {
        val task=java.util.concurrent.FutureTask<T> { block() }
        Handler(Looper.getMainLooper()).post(task)
        return task.get()
    }
    override fun getForegroundInfo():ForegroundInfo=UploadNotifications.foreground(applicationContext,reference,"Przygotowanie zdjęć",0,0)
    private fun showProgress(message:String,done:Int,total:Int) { setForegroundAsync(UploadNotifications.foreground(applicationContext,reference,message,done,total)).get() }
    private var currentPhoto:String?=null
    private fun rememberError(message:String) {
        val parts=reference.split(":",limit=2)
        val row=if(parts.size==2) rows(data(),parts[0]).find { it.optString("id")==parts[1] } else null
        val accountKey=hash(email).take(24)+"|"
        val photos=row?.optJSONArray("photos") ?: JSONArray()
        val failed=JSONArray()
        for(i in 0 until photos.length()) {
            val uri=photos.optString(i)
            if((currentPhoto==null || currentPhoto==uri) && row?.optJSONObject("driveFiles")?.optString(accountKey+uri).isNullOrBlank()) failed.put(uri)
        }
        prefs.edit().putString("uploadError_"+reference,message).putString("uploadFailedPhotos_"+reference,failed.toString()).apply()
    }
    private fun finishJob(message:String,retry:Boolean):Result = synchronized(queueLock) {
        val currentRequest=prefs.getString("uploadRequestId_"+reference,null)
        val ownsQueue=currentRequest==inputData.getString("requestId")
        if(retry || ownsQueue) prefs.edit().remove("uploadQueued_"+reference).apply()
        if(retry) rememberError(message) else prefs.edit().remove("uploadError_"+reference).remove("uploadFailedPhotos_"+reference).apply()
        UploadNotifications.result(applicationContext,reference,if(!retry && !ownsQueue) "Paczka zapisana — kolejna czeka w kolejce" else message,retry)
        if(retry) Result.failure() else Result.success()
    }
    override fun doWork():Result {
        try {
            setForegroundAsync(getForegroundInfo()).get()
            val parts=reference.split(":"); if(parts.size!=2 || email.isBlank()) return finishJob("Wybierz konto Google Disk w aplikacji",true)
            val key=parts[0]; val id=parts[1]
            if(key !in listOf("links","materials","notes","expenses")) return finishJob("Nieprawidłowy wpis zdjęć",true)
            val row=rows(data(),key).find { it.optString("id")==id } ?: return finishJob("Wpis został usunięty",true)
            val obj=rows(data(),"objects").find { it.optString("id")==row.optString("objectId") }
            if(key !in listOf("notes","expenses") && obj==null && row.optString("storage")!="car") return finishJob("Wybierz obiekt przed wysłaniem zdjęć",true)
            val savedPhotos=(0 until arr(row,"photos").length()).map { arr(row,"photos").getString(it) }
            val requested=JSONArray(prefs.getString("uploadRequestedPhotos_"+reference,arr(row,"photos").toString()))
            val accountKey=hash(email).take(24)+"|"
            val photoList=PhotoSendSelection.eligible(savedPhotos,(0 until requested.length()).map(requested::optString).toSet())
                .filter { row.optJSONObject("driveFiles")?.optString(accountKey+it).isNullOrBlank() }
            if(photoList.isEmpty()) return finishJob("Brak nowych zdjęć w tej paczce",false)
            val authorization=Tasks.await(Identity.getAuthorizationClient(applicationContext).authorize(AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(scope))).build()),60,TimeUnit.SECONDS)
            if(authorization.hasResolution() || authorization.accessToken.isNullOrBlank()) return finishJob("Połącz ponownie Google Disk — dotknij, aby wznowić",true)
            val token=authorization.accessToken!!
            val currentEmail=driveJson("GET","https://www.googleapis.com/drive/v3/about?fields=user(emailAddress)",token).optJSONObject("user")?.optString("emailAddress")
            if(!email.equals(currentEmail,true)) return finishJob("Wybierz wcześniej połączone konto Google Disk",true)
            val rootId=driveRoot(token)
            // Always resolve the current canonical path. Saved links may point to the old
            // year/month/day hierarchy and must not route new photos back into it.
            val folders=PhotoDriveLayout.path(LocalDate.parse(recordDate(row)),obj,row.optString("storage"))
            var photoFolderId=rootId
            for(name in folders) photoFolderId=driveFolder(name,photoFolderId,token)
            for((index,photo) in photoList.withIndex()) {
                currentPhoto=photo
                if(isStopped) throw InterruptedException()
                showProgress("Zdjęcie ${index+1} / ${photoList.size}",index,photoList.size)
                val already=onMain { rows(data(),key).find { it.optString("id")==id }?.optJSONObject("driveFiles")?.optString(accountKey+photo).orEmpty() }
                if(already.isNotBlank()) continue
                // appProperties makes retries idempotent, even if a response was lost after upload.
                val uploadKey=id+"-"+hash(photo).take(24)
                val q="trashed = false and '"+photoFolderId+"' in parents and appProperties has { key='r3Upload' and value='"+uploadKey+"' }"
                val matches=driveJson("GET","https://www.googleapis.com/drive/v3/files?q="+URLEncoder.encode(q,"UTF-8")+"&fields=files(id)&pageSize=1",token).optJSONArray("files")
                val remoteId=if(matches!=null && matches.length()>0) matches.getJSONObject(0).getString("id") else {
                    val mime=contentResolver.getType(Uri.parse(photo)) ?: android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(Uri.parse(photo).lastPathSegment.orEmpty().substringAfterLast(".").lowercase()) ?: "image/jpeg"
                    val ext=android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "jpg"
                    val meta=JSONObject().put("name",key+"-"+id+"-"+(index+1)+"."+ext)
                        .put("parents",JSONArray().put(photoFolderId)).put("appProperties",JSONObject().put("r3Upload",uploadKey))
                    val c=URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable&fields=id").openConnection() as HttpURLConnection
                    val location:String
                    try {
                        c.requestMethod="POST"; c.connectTimeout=20000; c.readTimeout=30000; c.doOutput=true
                        c.setRequestProperty("Authorization","Bearer "+token)
                        c.setRequestProperty("Content-Type","application/json; charset=UTF-8")
                        c.setRequestProperty("X-Upload-Content-Type",mime)
                        c.outputStream.use { it.write(meta.toString().toByteArray(Charsets.UTF_8)) }
                        if(c.responseCode !in 200..299) throw DriveHttpException(c.responseCode)
                        location=c.getHeaderField("Location") ?: throw java.io.IOException("Brak sesji przesyłania")
                    } finally { c.disconnect() }
                    val u=URL(location)
                    if(u.protocol!="https" || !(u.host=="www.googleapis.com" || u.host.endsWith(".googleapis.com"))) throw java.io.IOException("Nieprawidłowa sesja Drive")
                    val upload=u.openConnection() as HttpURLConnection
                    try {
                        upload.requestMethod="PUT"; upload.doOutput=true; upload.connectTimeout=20000; upload.readTimeout=120000
                        upload.setRequestProperty("Authorization","Bearer "+token); upload.setRequestProperty("Content-Type",mime)
                        upload.setChunkedStreamingMode(256*1024)
                        val input=contentResolver.openInputStream(Uri.parse(photo)) ?: throw java.io.IOException("Zdjęcie niedostępne")
                        input.use { source -> upload.outputStream.use { target -> source.copyTo(target) } }
                        if(upload.responseCode !in 200..299) throw DriveHttpException(upload.responseCode)
                        JSONObject(upload.inputStream.bufferedReader().use { it.readText() }).getString("id")
                    } finally { upload.disconnect() }
                }
                try {
                    PhotoPreview.rememberThumbnail(applicationContext, Uri.parse(photo), remoteId)
                } catch (_: Exception) {
                    // The Drive receipt remains valid even if a local thumbnail cannot be cached.
                }
                onMain {
                    val d=data(); val current=rows(d,key).find { it.optString("id")==id }
                    if(current!=null) {
                        val files=current.optJSONObject("driveFiles") ?: JSONObject().also { current.put("driveFiles",it) }
                        files.put(accountKey+photo,remoteId); current.put("Link Google Drive","https://drive.google.com/drive/folders/"+photoFolderId)
                        save(d,"DRIVE_UPLOAD",id)
                    }
                }
                showProgress("Zapisano ${index+1} / ${photoList.size}",index+1,photoList.size)
            }
            return finishJob("Zdjęcia zapisane na Google Disk",false)
        } catch(_:InterruptedException) { return Result.retry() }
        catch(e:Exception) {
            if(isStopped) return Result.retry()
            if(e is java.io.FileNotFoundException || e is SecurityException) return finishJob("Brak dostępu do zdjęcia — wybierz je ponownie w aplikacji",true)
            if(e is DriveHttpException && e.status in listOf(400,403,404)) return finishJob("Google Disk odrzucił żądanie (${e.status}). Sprawdź dostęp i ponów",true)
            rememberError("Ponowienie po odzyskaniu połączenia")
            UploadNotifications.result(applicationContext,reference,"Oczekiwanie na ponowienie — zapisane zdjęcia pozostają na dysku",true)
            return Result.retry()
        }
    }
    private val drive=DriveStore()
    private fun driveJson(method:String,url:String,token:String,body:JSONObject?=null)=drive.json(method,url,token,body)
    private fun driveRoot(token:String)=drive.root(token)
    private fun driveFolder(name:String,parent:String?,token:String)=drive.folder(name,parent,token)
    companion object {
        private val queueLock=Any()
        fun enqueue(context:Context,reference:String,email:String) {
            synchronized(queueLock) {
                val prefs=context.getSharedPreferences("r3_gate",Context.MODE_PRIVATE)
                val parts=reference.split(":",limit=2)
                if(parts.size!=2) return
                val saved=runCatching { JSONObject(prefs.getString("data","{}")!!) }.getOrDefault(JSONObject())
                val entries=saved.optJSONArray(parts[0]) ?: return
                val row=(0 until entries.length()).mapNotNull(entries::optJSONObject).singleOrNull { it.optString("id")==parts[1] } ?: return
                val photos=row.optJSONArray("photos") ?: JSONArray()
                val previous=runCatching { JSONArray(prefs.getString("uploadRequestedPhotos_"+reference,"[]")) }.getOrDefault(JSONArray())
                val selected=(0 until photos.length()).map(photos::optString).filter(String::isNotBlank).toSet()
                val before=(0 until previous.length()).map(previous::optString).filter(String::isNotBlank).toSet()
                if(!PhotoSendSelection.shouldRequest(selected,before,prefs.getBoolean("uploadQueued_"+reference,false),prefs.contains("uploadError_"+reference))) return
                val requestId=java.util.UUID.randomUUID().toString()
                if(!prefs.edit().putBoolean("uploadQueued_"+reference,true)
                    .putString("uploadRequestId_"+reference,requestId)
                    .putString("uploadRequestedPhotos_"+reference,row.optJSONArray("photos")?.toString() ?: "[]")
                    .remove("uploadError_"+reference).remove("uploadFailedPhotos_"+reference).commit()) return
                val request=OneTimeWorkRequestBuilder<DriveUploadWorker>()
                    .setInputData(workDataOf("reference" to reference,"email" to email,"requestId" to requestId))
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).addTag("driveUpload").build()
                WorkManager.getInstance(context).enqueueUniqueWork("drive:"+reference,ExistingWorkPolicy.APPEND_OR_REPLACE,request)
                UploadNotifications.result(context,reference,"Zdjęcia w kolejce — można zwinąć aplikację",false)
            }
        }
    }
}

