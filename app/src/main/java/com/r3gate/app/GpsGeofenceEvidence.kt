package com.r3gate.app

/**
 * Distinguishes confirmed exit from a geofence from an inaccurate border fix.
 * No work-time records are modified by this decision.
 */
object GpsGeofenceEvidence {
    enum class Status { INSIDE, OUTSIDE, UNCERTAIN }
    data class Observation(val status:Status,val placeId:String?=null)

    fun observe(point:GpsWorkRules.Point,places:List<GpsWorkRules.Place>,confirmedId:String?=null):Observation {
        val validPlaces=places.filter { p ->
            p.id.isNotBlank() && p.radiusM.isFinite() && p.radiusM in 10.0..2000.0 &&
                p.latitude.isFinite() && p.latitude in -90.0..90.0 &&
                p.longitude.isFinite() && p.longitude in -180.0..180.0
        }
        // Missing or corrupt saved sites are not evidence of physical departure.
        if(!confirmedId.isNullOrBlank() && validPlaces.none { it.id==confirmedId })
            return Observation(Status.UNCERTAIN)
        if(validPlaces.isEmpty()) return Observation(Status.UNCERTAIN)
        val inside=GpsWorkRules.nearest(point,validPlaces)
        if(inside!=null) return Observation(Status.INSIDE,inside.place.id)
        if(!point.latitude.isFinite() || point.latitude !in -90.0..90.0 ||
            !point.longitude.isFinite() || point.longitude !in -180.0..180.0 ||
            !point.accuracyM.isFinite() || point.accuracyM<=0.0 ||
            point.accuracyM>150.0) return Observation(Status.UNCERTAIN)
        val possibleZone=validPlaces.any { p ->
            GpsWorkRules.distanceM(point.latitude,point.longitude,
                p.latitude,p.longitude)-point.accuracyM<=p.radiusM
        }
        return Observation(if(possibleZone) Status.UNCERTAIN else Status.OUTSIDE)
    }
}
