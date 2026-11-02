package com.r3gate.app

/**
 * Distinguishes confirmed exit from a geofence from an inaccurate border fix.
 * No work-time records are modified by this decision.
 */
object GpsGeofenceEvidence {
    enum class Status { INSIDE, OUTSIDE, UNCERTAIN }
    data class Observation(val status:Status,val placeId:String?=null)

    fun observe(point:GpsWorkRules.Point,places:List<GpsWorkRules.Place>,confirmedId:String?=null):Observation {
        // Removing a saved geozone must never look like physically leaving it.
        if(!confirmedId.isNullOrBlank() && places.none { it.id==confirmedId })
            return Observation(Status.UNCERTAIN)
        if(places.isEmpty()) return Observation(Status.UNCERTAIN)
        val inside=GpsWorkRules.nearest(point,places)
        if(inside!=null) return Observation(Status.INSIDE,inside.place.id)
        if(!point.latitude.isFinite() || point.latitude !in -90.0..90.0 ||
            !point.longitude.isFinite() || point.longitude !in -180.0..180.0 ||
            !point.accuracyM.isFinite() || point.accuracyM<=0.0 ||
            point.accuracyM>150.0) return Observation(Status.UNCERTAIN)
        val possibleZone=places.any { place ->
            place.id.isNotBlank() && place.radiusM.isFinite() &&
                place.radiusM in 10.0..2000.0 &&
                place.latitude.isFinite() && place.latitude in -90.0..90.0 &&
                place.longitude.isFinite() && place.longitude in -180.0..180.0 &&
                GpsWorkRules.distanceM(point.latitude,point.longitude,
                    place.latitude,place.longitude)-point.accuracyM<=place.radiusM
        }
        return Observation(if(possibleZone) Status.UNCERTAIN else Status.OUTSIDE)
    }
}
