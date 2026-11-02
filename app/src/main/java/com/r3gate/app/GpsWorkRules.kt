package com.r3gate.app

import kotlin.math.*

/**
 * Pure, deterministic GPS decision engine. No remote calls and no location history upload.
 * Android location permissions and foreground/background collection must be handled separately.
 */
object GpsWorkRules {
    enum class Kind { OBJECT, SHOP, BASE, HOME }
    enum class Activity { OBJECT, TRAVEL, SHOPPING, BREAK, STOP }
    data class Point(val latitude:Double,val longitude:Double,val accuracyM:Double,val timestampMs:Long)
    data class Place(val id:String,val kind:Kind,val latitude:Double,val longitude:Double,val radiusM:Double=100.0)
    data class Fix(val place:Place,val distanceM:Double)
    data class Settings(
        val enabled:Boolean=true,
        val autoTravel:Boolean=true,
        val notifyLongStops:Boolean=true,
        val stopMinutes:Int=10,
        val minimumTravelSpeedMps:Double=2.0,
        val arrivalDwellMs:Long=60_000,
        val departureDwellMs:Long=30_000
    ) {
        init { require(stopMinutes in listOf(5,10,15,30)) }
    }
    data class PayRules(
        val outboundCommute:Boolean=false,
        val returnCommute:Boolean=false,
        val betweenObjects:Boolean=true,
        val shopping:Boolean=true,
        val forcedStop:Boolean=false,
        val onObject:Boolean=true
    )
    data class Segment(val startMs:Long,val endMs:Long,val activity:Activity,val commute:String?=null)
    fun distanceM(aLat:Double,aLng:Double,bLat:Double,bLng:Double):Double {
        val x=Math.toRadians(bLat-aLat)
        val y=Math.toRadians(bLng-aLng)
        val h=sin(x/2).pow(2)+cos(Math.toRadians(aLat))*cos(Math.toRadians(bLat))*sin(y/2).pow(2)
        return 6371000.0*2*asin(sqrt(h.coerceIn(0.0,1.0)))
    }
    fun nearest(point:Point,places:List<Place>):Fix? {
        if(!point.latitude.isFinite() || point.latitude !in -90.0..90.0 ||
            !point.longitude.isFinite() || point.longitude !in -180.0..180.0 ||
            !point.accuracyM.isFinite() || point.accuracyM<=0.0 ||
            point.accuracyM>150.0) return null
        val matches=places.asSequence()
            .filter { it.id.isNotBlank() && it.radiusM.isFinite() &&
                it.radiusM in 10.0..2000.0 &&
                it.latitude.isFinite() && it.latitude in -90.0..90.0 &&
                it.longitude.isFinite() && it.longitude in -180.0..180.0 }
            .map { Fix(it,distanceM(point.latitude,point.longitude,it.latitude,it.longitude)) }
            // Keep the full accuracy circle inside the geofence; border noise
            // must not create an arrival or a shopping suggestion.
            .filter { it.distanceM+point.accuracyM<=it.place.radiusM }
            .sortedBy { it.distanceM }.toList()
        if(matches.size>1) {
            // The same GPS fix must not assign two neighboring workplaces.
            // Wait for a less ambiguous location observation.
            val closest=matches[0]
            val second=matches[1]
            if(second.distanceM-closest.distanceM<=2*point.accuracyM) return null
        }
        return matches.firstOrNull()
    }
    fun traveling(previous:Point,current:Point,settings:Settings=Settings()):Boolean {
        if(!settings.enabled||!settings.autoTravel) return false
        val elapsed=(current.timestampMs-previous.timestampMs)/1000.0
        if(elapsed<=0||elapsed>1800||previous.accuracyM>100||current.accuracyM>100) return false
        val d=distanceM(previous.latitude,previous.longitude,current.latitude,current.longitude)
        val conservative=(d-previous.accuracyM-current.accuracyM).coerceAtLeast(0.0)
        return conservative/elapsed>=settings.minimumTravelSpeedMps
    }
    fun stable(previousDetectedAt:Long?,now:Long,dwellMs:Long)=previousDetectedAt!=null && now>=previousDetectedAt && now-previousDetectedAt>=dwellMs
    fun longStop(startMs:Long,nowMs:Long,settings:Settings):Boolean =
        settings.enabled && settings.notifyLongStops && nowMs>=startMs && nowMs-startMs>=settings.stopMinutes*60_000L
    fun paid(segment:Segment,rules:PayRules):Boolean = when(segment.activity) {
        Activity.OBJECT -> rules.onObject
        Activity.SHOPPING -> rules.shopping
        Activity.STOP -> rules.forcedStop
        Activity.BREAK -> false
        Activity.TRAVEL -> when(segment.commute) {
            "outbound" -> rules.outboundCommute
            "return" -> rules.returnCommute
            else -> rules.betweenObjects
        }
    }
    fun totals(segments:List<Segment>,rules:PayRules):Pair<Long,Long> {
        var actual=0L; var payable=0L
        for(s in segments) {
            if(s.endMs<=s.startMs) continue
            val duration=s.endMs-s.startMs
            actual+=duration
            if(paid(s,rules)) payable+=duration
        }
        return actual to payable
    }
    fun temporaryObjectId(arrivalMs:Long)="temporary-"+arrivalMs
}
