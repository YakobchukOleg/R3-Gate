package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsGeofenceEvidenceTest {
    private val site=GpsWorkRules.Place("site",GpsWorkRules.Kind.OBJECT,54.092,18.776,100.0)
    private val center=GpsWorkRules.Point(54.092,18.776,8.0,1000)

    @Test fun clearObservationsRemainInsideOrOutside() {
        assertEquals(GpsGeofenceEvidence.Observation(GpsGeofenceEvidence.Status.INSIDE,"site"),
            GpsGeofenceEvidence.observe(center,listOf(site)))
        assertEquals(GpsGeofenceEvidence.Status.OUTSIDE,
            GpsGeofenceEvidence.observe(center.copy(latitude=54.10),listOf(site)).status)
    }

    @Test fun borderUncertaintyMustNotBecomeFalseDeparture() {
        val edge=center.copy(latitude=54.09272,accuracyM=30.0)
        assertEquals(GpsGeofenceEvidence.Status.UNCERTAIN,
            GpsGeofenceEvidence.observe(edge,listOf(site)).status)
    }

    @Test fun invalidAndOverlappingFixesAreNotConfirmedOutside() {
        assertEquals(GpsGeofenceEvidence.Status.UNCERTAIN,
            GpsGeofenceEvidence.observe(center.copy(accuracyM=Double.NaN),listOf(site)).status)
        val second=site.copy(id="nearby",longitude=18.7761)
        assertEquals(GpsGeofenceEvidence.Status.UNCERTAIN,
            GpsGeofenceEvidence.observe(center.copy(accuracyM=10.0),listOf(site,second)).status)
    }
    @Test fun removedOrArchivedWorksiteCannotPretendToBeGpsDeparture() {
        val outside=center.copy(latitude=54.10)
        assertEquals(GpsGeofenceEvidence.Status.UNCERTAIN,
            GpsGeofenceEvidence.observe(outside,emptyList(),"site").status)
        val another=site.copy(id="another",latitude=54.11)
        assertEquals(GpsGeofenceEvidence.Status.UNCERTAIN,
            GpsGeofenceEvidence.observe(outside,listOf(another),"site").status)
        assertEquals(GpsGeofenceEvidence.Status.OUTSIDE,
            GpsGeofenceEvidence.observe(outside,listOf(site),"site").status)
    }
    @Test fun noConfiguredPlacesAreUnknownNotDepartureEvidence() {
        assertEquals(GpsGeofenceEvidence.Status.UNCERTAIN,
            GpsGeofenceEvidence.observe(center,emptyList()).status)
    }
    @Test fun corruptedConfirmedRadiusDoesNotRegisterDeparture() {
        val corrupted=site.copy(radiusM=Double.NaN)
        val outside=center.copy(latitude=54.10)
        assertEquals(GpsGeofenceEvidence.Status.UNCERTAIN,
            GpsGeofenceEvidence.observe(outside,listOf(corrupted),"site").status)
    }

    @Test fun invalidConfirmedCoordinatesAreNotEvidenceOfLeaving() {
        val invalid=site.copy(latitude=95.0)
        assertEquals(GpsGeofenceEvidence.Status.UNCERTAIN,
            GpsGeofenceEvidence.observe(center.copy(latitude=54.10),listOf(invalid),"site").status)
    }

    @Test fun noValidPlacesDoesNotCreateDepartureEvenWithoutConfirmedId() {
        val broken=site.copy(longitude=Double.POSITIVE_INFINITY)
        assertEquals(GpsGeofenceEvidence.Status.UNCERTAIN,
            GpsGeofenceEvidence.observe(center.copy(latitude=54.10),listOf(broken)).status)
    }

    @Test fun otherValidSitesDoNotMaskCorruptedConfirmedSite() {
        val broken=site.copy(radiusM=0.0)
        val other=site.copy(id="other",latitude=54.11)
        assertEquals(GpsGeofenceEvidence.Status.UNCERTAIN,
            GpsGeofenceEvidence.observe(center.copy(latitude=54.10),listOf(broken,other),"site").status)
    }

    @Test fun distantPositionOutsideValidConfirmedSiteIsDepartureEvidence() {
        val far=center.copy(latitude=54.10,accuracyM=5.0)
        assertEquals(GpsGeofenceEvidence.Status.OUTSIDE,
            GpsGeofenceEvidence.observe(far,listOf(site),"site").status)
    }

    @Test fun inaccuratePositionOutsideCenterStillRemainsUncertain() {
        val nearBorder=center.copy(latitude=54.093,accuracyM=25.0)
        assertEquals(GpsGeofenceEvidence.Status.UNCERTAIN,
            GpsGeofenceEvidence.observe(nearBorder,listOf(site),"site").status)
    }

}
