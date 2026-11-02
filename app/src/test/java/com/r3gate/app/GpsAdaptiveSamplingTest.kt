package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsAdaptiveSamplingTest {
    @Test fun staysFrequentDuringInitialFiveMinutes() {
        val state=GpsAdaptiveSampling.observe(GpsAdaptiveSampling.State(),1000,0.1)
        assertEquals(30_000L,GpsAdaptiveSampling.interval(state,300_000,true))
    }
    @Test fun quietPhoneAllowsSparserGpsButNeverStopsIt() {
        val state=GpsAdaptiveSampling.observe(GpsAdaptiveSampling.State(),1000,0.1)
        assertEquals(90_000L,GpsAdaptiveSampling.interval(state,301_000,true))
    }
    @Test fun newMovementRestoresFrequentSamples() {
        val quiet=GpsAdaptiveSampling.observe(GpsAdaptiveSampling.State(),1000,0.0)
        val moving=GpsAdaptiveSampling.observe(quiet,400_000,2.5)
        assertEquals(30_000L,GpsAdaptiveSampling.interval(moving,401_000,true))
    }
    @Test fun rejectsInvalidOutOfOrderSensorData() {
        val state=GpsAdaptiveSampling.observe(GpsAdaptiveSampling.State(),5000,2.0)
        assertEquals(state,GpsAdaptiveSampling.observe(state,4000,5.0))
        assertEquals(state,GpsAdaptiveSampling.observe(state,6000,Double.NaN))
    }
    @Test fun disabledAdaptiveModeKeepsOriginalGpsCadence() {
        val state=GpsAdaptiveSampling.observe(GpsAdaptiveSampling.State(),1000,0.0)
        assertEquals(30_000L,GpsAdaptiveSampling.interval(state,800_000,false))
    }
    @Test fun carTravelKeepsFrequentGpsDespiteStillPhone() {
        val still=GpsAdaptiveSampling.observe(GpsAdaptiveSampling.State(),1000,0.0)
        assertEquals(90_000L,GpsAdaptiveSampling.interval(still,400_000,true))
        val traveling=GpsAdaptiveSampling.gpsTravel(still,400_000)
        assertEquals(30_000L,GpsAdaptiveSampling.interval(traveling,550_000,true))
        assertEquals(90_000L,GpsAdaptiveSampling.interval(traveling,701_000,true))
    }
    @Test fun outOfOrderTravelEvidenceCannotDelayQuietMode() {
        val current=GpsAdaptiveSampling.gpsTravel(
            GpsAdaptiveSampling.observe(GpsAdaptiveSampling.State(),1000,0.0),500_000)
        assertEquals(current,GpsAdaptiveSampling.gpsTravel(current,499_000))
    }
}
