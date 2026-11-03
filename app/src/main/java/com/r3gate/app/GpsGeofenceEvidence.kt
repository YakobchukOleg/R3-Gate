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
        // Duplicate IDs make the previous geofence ambiguous: never infer a
        // departure or a new arrival from contradictory saved coordinates.
        if(!confirmedId.isNullOrBlank() && validPlaces.count { it.id==confirmedId }!=1)
            return Observation(Status.UNCERTAIN)
        // Missing or corrupt saved sites are not evidence of physical departure.
        if(!confirmedId.isNullOrBlank() && validPlaces.none { it.id==confirmedId })
            return Observation(Status.UNCERTAIN)
        if(validPlaces.isEmpty()) return Observation(Status.UNCERTAIN)
        val inside=GpsWorkRules.nearest(point,validPlaces)
        // A fix may be well inside a neighboring site yet still overlap the
        // previously confirmed site's uncertainty circle. Never switch sites
        // until it is clear we actually left the previous one.
        if(inside!=null && !confirmedId.isNullOrBlank() && inside.place.id!=confirmedId) {
            val previous=validPlaces.first { it.id==confirmedId }
            if(point.accuracyM.isFinite() && point.accuracyM>0 &&
                GpsWorkRules.distanceM(point.latitude,point.longitude,
                    previous.latitude,previous.longitude)-point.accuracyM<=previous.radiusM)
                return Observation(Status.UNCERTAIN)
        }
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
