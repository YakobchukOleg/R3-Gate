package com.r3gate.app

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.security.MessageDigest

object PhotoQueue {
    data class Status(val sent:Int,val pending:Int,val error:Boolean,val failed:Int=0,val queued:Boolean=true) {
        val caption:String get()=(if(sent+pending==0) "Brak zdjęć" else if(pending==0) "WYSŁANO" else if(error) "BŁĄD WYSYŁANIA" else if(queued) "W KOLEJCE" else "LOKALNIE")+" · wysłano: $sent · oczekuje: ${pending-failed} · błędy: $failed"
        val color:Int get()=if(pending==0) 0xFFD1E8D6.toInt() else if(error) 0xFFF2CCCC.toInt() else 0xFFFFE1BE.toInt()
    }
    internal fun uploadedFiles(row: JSONObject): Map<String, String> {
        val files = row.optJSONObject("driveFiles") ?: return emptyMap()
        return files.keys().asSequence().mapNotNull { key ->
            val uri = key.substringAfter('|', "")
            val id = files.optString(key)
            if (uri.isNotBlank() && id.isNotBlank() && !files.isNull(key)) uri to id else null
        }.toMap()
    }

    fun status(context:Context,key:String,row:JSONObject):Status {
        val prefs=context.getSharedPreferences("r3_gate",0)
        val email=prefs.getString("driveEmail","").orEmpty()
        val account=MessageDigest.getInstance("SHA-256").digest(email.toByteArray()).joinToString("") { "%02x".format(it) }.take(24)+"|"
        val photos=row.optJSONArray("photos");val files=row.optJSONObject("driveFiles")
        val total=photos?.length() ?: 0
        val sent=(0 until total).count { !files?.optString(account+photos!!.optString(it)).isNullOrBlank() }
        val reference=key+":"+row.optString("id")
        val errors=runCatching { org.json.JSONArray(prefs.getString("uploadFailedPhotos_"+reference,"[]")) }.getOrDefault(org.json.JSONArray())
        val failed=(0 until total).count { index ->
            val uri=photos!!.optString(index)
            files?.optString(account+uri).isNullOrBlank() && (0 until errors.length()).any { errors.optString(it)==uri }
        }
        return Status(sent,total-sent,prefs.contains("uploadError_"+reference),failed,prefs.getBoolean("uploadQueued_"+reference,false))
    }
    fun markPending(context:Context,key:String,row:JSONObject) {
        if((row.optJSONArray("photos")?.length() ?: 0) > 0) context.getSharedPreferences("r3_gate",0).edit().remove("uploadError_"+key+":"+row.optString("id")).remove("uploadFailedPhotos_"+key+":"+row.optString("id")).apply()
    }
    fun preserveLegacyRequests(context: Context) {
        val prefs=context.getSharedPreferences("r3_gate",0)
        val data=runCatching { JSONObject(prefs.getString("data","{}")!!) }.getOrDefault(JSONObject())
        val edit=prefs.edit()
        for(key in listOf("links","materials","notes","expenses")) {
            val rows=data.optJSONArray(key) ?: continue
            for(i in 0 until rows.length()) {
                val row=rows.optJSONObject(i) ?: continue
                val ref=key+":"+row.optString("id")
                if(prefs.getBoolean("uploadQueued_"+ref,false) && !prefs.contains("uploadRequestedPhotos_"+ref))
                    edit.putString("uploadRequestedPhotos_"+ref,row.optJSONArray("photos")?.toString() ?: "[]")
            }
        }
        edit.commit()
    }
    fun enqueueAll(context:Context) {
        val prefs=context.getSharedPreferences("r3_gate",0)
        val email=prefs.getString("driveEmail","").orEmpty()
        if(email.isBlank() || !prefs.getBoolean("driveConnected",false)) return
        val data=runCatching { JSONObject(prefs.getString("data","{}")!!) }.getOrDefault(JSONObject())
        for(key in listOf("links","materials","notes","expenses")) {
            val list=data.optJSONArray(key) ?: continue
            for(i in 0 until list.length()) {
                val row=list.optJSONObject(i) ?: continue
                if(status(context,key,row).pending>0) DriveUploadWorker.enqueue(context,key+":"+row.optString("id"),email)
            }
        }
    }
    fun folderId(link:String):String? {
        val uri=runCatching { Uri.parse(link) }.getOrNull() ?: return null
        if(uri.scheme!="https" || uri.host!="drive.google.com") return null
        val id=Regex("/folders/([A-Za-z0-9_-]+)").find(uri.path.orEmpty())?.groupValues?.get(1) ?: return null
        return id.takeIf { it.length>=10 }
    }
}
