package com.r3gate.app

import org.json.JSONObject
import org.json.JSONArray
import java.net.URL
import java.net.HttpURLConnection
import java.net.URLEncoder

/** App-private Drive files only. Reusing and renaming the legacy root preserves existing photo links. */
internal class DriveStore(
    private val openConnection: (String) -> HttpURLConnection =
        { URL(it).openConnection() as HttpURLConnection }
) {
    internal fun connect(url: String): HttpURLConnection = openConnection(url)

    fun json(method:String,url:String,token:String,body:JSONObject?=null):JSONObject {
        val c=connect(url)
        try {
            c.requestMethod=method; c.connectTimeout=20000; c.readTimeout=30000
            c.setRequestProperty("Authorization","Bearer "+token)
            if(body!=null) {
                c.doOutput=true; c.setRequestProperty("Content-Type","application/json; charset=UTF-8")
                c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            if(c.responseCode !in 200..299) throw DriveHttpException.from(c)
            return JSONObject(c.inputStream.bufferedReader().use { it.readText() }.ifBlank { "{}" })
        } finally { c.disconnect() }
    }
    fun root(token:String):String = synchronized(folderLock) {
        val q="trashed = false and mimeType = 'application/vnd.google-apps.folder' and (name = 'WorkMeter' or name = 'WT' or name = 'R3 Gate') and 'root' in parents"
        val list=json("GET","https://www.googleapis.com/drive/v3/files?q="+URLEncoder.encode(q,"UTF-8")+"&fields=files(id,name)&pageSize=100",token).optJSONArray("files")
        if(list!=null && list.length()>0) {
            val roots=(0 until list.length()).map { list.getJSONObject(it) }
            val chosen=roots.firstOrNull { it.optString("name")=="WorkMeter" } ?: roots.first()
            if(chosen.optString("name")!="WorkMeter") json("PATCH","https://www.googleapis.com/drive/v3/files/"+chosen.getString("id")+"?fields=id",token,JSONObject().put("name","WorkMeter"))
            return@synchronized chosen.getString("id")
        }
        folder("WorkMeter",null,token)
    }
    fun folder(name:String,parent:String?,token:String):String = synchronized(folderLock) {
        val safeName=name.replace("\\","\\\\").replace("'","\\'")
        val q="trashed = false and mimeType = 'application/vnd.google-apps.folder' and name = '"+safeName+"' and '"+(parent ?: "root")+"' in parents"
        val found=json("GET","https://www.googleapis.com/drive/v3/files?q="+URLEncoder.encode(q,"UTF-8")+"&fields=files(id)&pageSize=1",token).optJSONArray("files")
        if(found!=null && found.length()>0) return@synchronized found.getJSONObject(0).getString("id")
        val meta=JSONObject().put("name",name).put("mimeType","application/vnd.google-apps.folder")
        if(parent!=null) meta.put("parents",JSONArray().put(parent))
        json("POST","https://www.googleapis.com/drive/v3/files?fields=id",token,meta).getString("id")
    }
    companion object { val folderLock=Any() }
}


internal class DriveHttpException(
    val status: Int, val reasons: Set<String> = emptySet()
) : java.io.IOException("Drive HTTP $status") {
    companion object {
        fun from(connection: HttpURLConnection): DriveHttpException {
            val status = connection.responseCode
            val reasons = try {
                connection.errorStream?.use { input ->
                    val buffer = ByteArray(8192)
                    var length = 0
                    while (length < buffer.size) {
                        val count = input.read(buffer, length, buffer.size - length)
                        if (count <= 0) break
                        length += count
                    }
                    parseReasons(String(buffer, 0, length, Charsets.UTF_8))
                }.orEmpty()
            } catch (_: java.io.IOException) {
                emptySet()
            }
            return DriveHttpException(status, reasons)
        }

        internal fun parseReasons(body: String): Set<String> {
            val errors = try {
                JSONObject(body).optJSONObject("error")?.optJSONArray("errors")
            } catch (_: org.json.JSONException) {
                null
            } ?: return emptySet()
            return (0 until errors.length()).mapNotNull {
                errors.optJSONObject(it)?.optString("reason")?.takeIf(String::isNotBlank)
            }.toSet()
        }
    }
}
