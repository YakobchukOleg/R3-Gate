package com.r3gate.app.backup

import org.json.JSONArray
import org.json.JSONObject

/** Strict share-only whitelist: embedded notes, photos, materials and work history never cross it. */
object ObjectCatalog {
    const val FORMAT="WorkMeter-Objects"
    const val MAX_BYTES=4*1024*1024
    private val fields=listOf("id","TAG","city","Adres","organization","maps","placeType")
    data class Import(val journal:String,val locations:Map<String,String>,val added:Int,val skipped:Int)
    fun export(journal:String,locations:Map<String,String>):String {
        val source=JSONObject(journal).optJSONArray("objects") ?: JSONArray()
        val rows=JSONArray()
        require(source.length()<=10_000)
        for(i in 0 until source.length()) {
            val original=source.getJSONObject(i); val row=clean(original)
            locations[row.getString("id")]?.let { row.put("gps",gps(JSONObject(it))) }
            rows.put(row)
        }
        return JSONObject().put("format",FORMAT).put("version",1).put("exportedAt",java.time.Instant.now().toString())
            .put("objects",rows).toString().also { require(it.toByteArray().size<=MAX_BYTES) }
    }
    fun rows(text:String):List<JSONObject> {
        require(text.toByteArray().size<=MAX_BYTES)
        val root=JSONObject(text)
        require(root.getString("format")==FORMAT && root.getInt("version")==1)
        val values=root.getJSONArray("objects"); require(values.length()<=10_000)
        val rows=(0 until values.length()).map { n ->
            val raw=values.getJSONObject(n); clean(raw).also { row -> raw.optJSONObject("gps")?.let { row.put("gps",gps(it)) } }
        }
        require(rows.map { it.getString("id") }.distinct().size==rows.size) { "Powtórzone ID obiektu" }
        require(rows.map { it.optString("TAG").uppercase(java.util.Locale.ROOT) }.filter { it.isNotBlank() }.let { it.distinct().size==it.size }) { "Powtórzony TAG obiektu" }
        return rows
    }
    private fun clean(raw:JSONObject):JSONObject {
        val row=JSONObject()
        fields.forEach { key -> if(raw.has(key)) { val value=raw.get(key); require(value is String && value.length<=2000); row.put(key,value) } }
        require(row.optString("id").isNotBlank() && row.optString("id").length<=120)
        // Legacy records can lack address or TAG; a full backup must still preserve them.
        if(row.has("placeType")) require(row.getString("placeType") in listOf("WORKSITE","SHOP","OFFICE"))
        row.put("archived",raw.optBoolean("archived",false))
        return row
    }
    private fun gps(raw:JSONObject):JSONObject {
        val lat=raw.getDouble("lat"); val lon=raw.getDouble("lon"); val radius=raw.optDouble("radius",100.0)
        require(lat.isFinite() && lat in -90.0..90.0 && lon.isFinite() && lon in -180.0..180.0 && radius.isFinite() && radius in 10.0..2000.0)
        return JSONObject().put("lat",lat).put("lon",lon).put("radius",radius)
    }
    /** Additive import: same IDs are skipped; a TAG collision with another ID aborts the entire preview. */
    fun merge(journal:String,text:String):Import {
        val incoming=rows(text); val root=JSONObject(journal)
        val objects=root.optJSONArray("objects") ?: JSONArray().also { root.put("objects",it) }
        val existing=(0 until objects.length()).map { objects.getJSONObject(it) }
        val ids=existing.map { it.optString("id") }.toSet()
        val tags=existing.associate { it.optString("TAG").uppercase(java.util.Locale.ROOT) to it.optString("id") }
        incoming.forEach { row ->
            val tagOwner=row.optString("TAG").takeIf { it.isNotBlank() }?.let { tags[it.uppercase(java.util.Locale.ROOT)] }
            require(tagOwner==null || tagOwner==row.getString("id")) { "Konflikt TAG: "+row.getString("TAG") }
        }
        val locations=linkedMapOf<String,String>();var added=0
        for(row in incoming) {
            val id=row.getString("id");if(id in ids) continue
            row.optJSONObject("gps")?.let { locations[id]=it.toString() };row.remove("gps")
            objects.put(row);added++
        }
        return Import(root.toString(),locations,added,incoming.size-added)
    }
}
