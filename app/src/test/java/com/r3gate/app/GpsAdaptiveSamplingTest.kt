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
    @Test fun firstSensorSamplePreservesEarlierGpsTravelHint() {
        val gps=GpsAdaptiveSampling.gpsTravel(GpsAdaptiveSampling.State(),300_000)
        val sensed=GpsAdaptiveSampling.observe(gps,310_000,0.1)
        assertEquals(300_000L,sensed.lastGpsTravelMs)
        assertEquals(310_000L,sensed.startedMs)
        assertEquals(30_000L,GpsAdaptiveSampling.interval(sensed,400_000,true))
    }

    @Test fun firstMovingSensorSamplePreservesGpsEvidenceToo() {
        val gps=GpsAdaptiveSampling.gpsTravel(GpsAdaptiveSampling.State(),300_000)
        val sensed=GpsAdaptiveSampling.observe(gps,310_000,2.5)
        assertEquals(300_000L,sensed.lastGpsTravelMs)
        assertEquals(310_000L,sensed.lastMotionMs)
    }

    @Test fun sensorInitializationWithoutGpsEvidenceStartsNormally() {
        val result=GpsAdaptiveSampling.observe(GpsAdaptiveSampling.State(),5_000,0.2)
        assertEquals(5_000L,result.startedMs)
        assertEquals(0L,result.lastMotionMs)
        assertEquals(0L,result.lastGpsTravelMs)
    }

    @Test fun clockRollbackBeforeTravelEvidenceKeepsFrequentGps() {
        val state=GpsAdaptiveSampling.State(startedMs=1000,lastGpsTravelMs=600_000)
        assertEquals(30_000L,GpsAdaptiveSampling.interval(state,400_000,true))
    }

    @Test fun quietSamplingStartsExactlyFiveMinutesAfterLastEvidence() {
        val state=GpsAdaptiveSampling.State(startedMs=1000,lastMotionMs=61_000)
        assertEquals(30_000L,GpsAdaptiveSampling.interval(state,360_999,true))
        assertEquals(90_000L,GpsAdaptiveSampling.interval(state,361_000,true))
    }

    @Test fun initialGpsTravelCannotEnableQuietBeforeSensorInitialization() {
        val state=GpsAdaptiveSampling.gpsTravel(GpsAdaptiveSampling.State(),100_000)
        assertEquals(30_000L,GpsAdaptiveSampling.interval(state,600_000,true))
    }

    @Test fun rejectedInvalidSensorValuesDoNotDiscardGpsEvidence() {
        val gps=GpsAdaptiveSampling.gpsTravel(GpsAdaptiveSampling.State(),300_000)
        val invalid=GpsAdaptiveSampling.observe(gps,310_000,Double.NaN)
        assertEquals(gps,invalid)
        assertEquals(300_000L,invalid.lastGpsTravelMs)
    }

}
