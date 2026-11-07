package com.r3gate.app
import org.junit.Assert.*
import org.junit.Test
class GpsMotionWindowTest {
    @Test fun walkingSurvivesWeakSamplesBetweenSteps() {
        val detector=GpsMotionWindow();var wake=false
        for(i in 0..40) wake=detector.observe(1000L+i*100,if(i%2==0) 0.9 else 0.05).wake || wake
        assertTrue(wake)
    }
    @Test fun isolatedShakeDoesNotWake() {
        val detector=GpsMotionWindow()
        for(i in 0..50) assertFalse(detector.observe(1000L+i*100,if(i==20) 15.0 else 0.01).wake)
    }
    @Test fun quietPhoneNeverWakes() {
        val detector=GpsMotionWindow()
        for(i in 0..100) assertFalse(detector.observe(1000L+i*100,0.04).wake)
    }
    @Test fun invalidValuesAreIgnored() {
        val detector=GpsMotionWindow()
        for(value in listOf(Double.NaN,Double.POSITIVE_INFINITY,-1.0)) assertEquals(0,detector.observe(1000,value).samples)
    }
    @Test fun sampleMemoryIsBounded() {
        val detector=GpsMotionWindow();var result=detector.observe(1000,0.1)
        for(i in 1..5000) result=detector.observe(1000L+i*10,0.1)
        assertTrue(result.samples<=51)
    }
    @Test fun continuousWorkDoesNotKeepRestartingBurst() {
        val detector=GpsMotionWindow();var wakes=0
        for(i in 0..1800) if(detector.observe(1000L+i*100,0.8).wake) wakes++
        assertEquals(1,wakes)
    }
    @Test fun evidenceGapRequiresFreshWindow() {
        val detector=GpsMotionWindow()
        for(i in 0..10) detector.observe(1000L+i*100,0.9)
        assertFalse(detector.observe(20000,0.9).wake)
    }
    @Test fun burstEndsWithoutNewFixes() {
        assertEquals(3000L,GpsMotionProbe.interval(2000,1000,90000))
        assertEquals(90000L,GpsMotionProbe.interval(61000,1000,90000))
    }
    @Test fun burstsHaveBoundedRearm() {
        assertFalse(GpsMotionProbe.mayStart(60000,1000));assertTrue(GpsMotionProbe.mayStart(91000,1000))
        assertFalse(GpsMotionProbe.mayStart(999,1000))
    }
    @Test fun highRateCallbacksKeepLastMeasuredSummaryInsteadOfZeros() {
        val detector=GpsMotionWindow()
        for(i in 0..40) detector.observe(1000L+i*100,0.9)
        val summary=detector.observe(5001,0.9)
        assertEquals(0.9,summary.rms,0.001);assertFalse(summary.wake)
    }
    @Test fun aNewMovementAfterQuietCanWakeAgain() {
        val detector=GpsMotionWindow();var first=false
        for(i in 0..40) first=detector.observe(1000L+i*100,0.9).wake || first
        for(i in 41..1000) detector.observe(1000L+i*100,0.01)
        var next=false
        for(i in 1001..1060) next=detector.observe(1000L+i*100,0.9).wake || next
        assertTrue(first);assertTrue(next)
    }
    @Test fun horizontalVehicleAccelerationIsRetainedByLinearSensor() {
        assertEquals(1.0,GpsAcceleration.magnitude(1.0,0.0,0.0,true),0.0001)
        val detector=GpsMotionWindow();var wake=false
        for(i in 0..30) wake=detector.observe(1000L+i*100,GpsAcceleration.magnitude(1.0,0.0,0.0,true)).wake || wake
        assertTrue(wake)
    }
    @Test fun rawFallbackDoesNotTreatGravityAsMotion() {
        assertEquals(0.0,GpsAcceleration.magnitude(0.0,0.0,9.80665,false),0.0001)
    }
}
