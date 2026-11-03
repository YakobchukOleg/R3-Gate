package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsAdaptiveToggleRulesTest {
    private val r=GpsAdaptiveToggleRules
    @Test fun newlyEnabledAdaptiveRegistersSensor() { assertEquals(r.Action.START_SENSOR,r.sensorAction(true,false)) }
    @Test fun alreadyRegisteredSensorNotDuplicated() { assertEquals(r.Action.NONE,r.sensorAction(true,true)) }
    @Test fun disablingAdaptiveStopsSensor() { assertEquals(r.Action.STOP_SENSOR,r.sensorAction(false,true)) }
    @Test fun disabledAdaptiveWithNoSensorDoesNothing() { assertEquals(r.Action.NONE,r.sensorAction(false,false)) }
    @Test fun disabledQuietIntervalReturnsToThirtySeconds() { assertEquals(r.Action.RESTORE_ACTIVE,r.cadenceAction(false,90_000L)) }
    @Test fun disabledProbeIntervalReturnsToThirtySeconds() { assertEquals(r.Action.RESTORE_ACTIVE,r.cadenceAction(false,15_000L)) }
    @Test fun disabledUnexpectedIntervalReturnsToThirtySeconds() { assertEquals(r.Action.RESTORE_ACTIVE,r.cadenceAction(false,60_000L)) }
    @Test fun disabledAlreadyActiveNeedsNoUpdate() { assertEquals(r.Action.NONE,r.cadenceAction(false,30_000L)) }
    @Test fun enabledQuietIntervalIsRetained() { assertEquals(r.Action.NONE,r.cadenceAction(true,90_000L)) }
    @Test fun enabledProbeIntervalIsRetained() { assertEquals(r.Action.NONE,r.cadenceAction(true,15_000L)) }
    @Test fun enabledActiveIntervalNeedsNoUpdate() { assertEquals(r.Action.NONE,r.cadenceAction(true,30_000L)) }
    @Test fun turningOnOnlyRegistersMissingSensor() { assertEquals(r.Action.START_SENSOR,r.sensorAction(true,false));assertEquals(r.Action.NONE,r.cadenceAction(true,90_000L)) }
    @Test fun turningOffStopsSensorAndRestoresCadence() { assertEquals(r.Action.STOP_SENSOR,r.sensorAction(false,true));assertEquals(r.Action.RESTORE_ACTIVE,r.cadenceAction(false,90_000L)) }
    @Test fun turningOffAgainDoesNotReRegisterOrResubscribe() { assertEquals(r.Action.NONE,r.sensorAction(false,false));assertEquals(r.Action.NONE,r.cadenceAction(false,30_000L)) }
    @Test fun reEnablingSensorDoesNotChangeSavedLocationFix() { assertEquals(r.Action.START_SENSOR,r.sensorAction(true,false));assertEquals(r.Action.NONE,r.cadenceAction(true,GpsAdaptiveSampling.ACTIVE_INTERVAL_MS)) }
    @Test fun negativeSavedIntervalRecoveredToActive() { assertEquals(r.Action.RESTORE_ACTIVE,r.cadenceAction(false,-1L)) }
}
