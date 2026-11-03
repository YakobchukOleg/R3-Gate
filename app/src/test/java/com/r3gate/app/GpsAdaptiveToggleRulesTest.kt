package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsAdaptiveToggleRulesTest {
    private val r=GpsAdaptiveToggleRules
    @Test fun newlyEnabledAdaptiveRegistersSensor() { assertEquals(GpsAdaptiveToggleRules.Action.START_SENSOR,r.sensorAction(true,false)) }
    @Test fun alreadyRegisteredSensorNotDuplicated() { assertEquals(GpsAdaptiveToggleRules.Action.NONE,r.sensorAction(true,true)) }
    @Test fun disablingAdaptiveStopsSensor() { assertEquals(GpsAdaptiveToggleRules.Action.STOP_SENSOR,r.sensorAction(false,true)) }
    @Test fun disabledAdaptiveWithNoSensorDoesNothing() { assertEquals(GpsAdaptiveToggleRules.Action.NONE,r.sensorAction(false,false)) }
    @Test fun disabledQuietIntervalReturnsToThirtySeconds() { assertEquals(GpsAdaptiveToggleRules.Action.RESTORE_ACTIVE,r.cadenceAction(false,90_000L)) }
    @Test fun disabledProbeIntervalReturnsToThirtySeconds() { assertEquals(GpsAdaptiveToggleRules.Action.RESTORE_ACTIVE,r.cadenceAction(false,15_000L)) }
    @Test fun disabledUnexpectedIntervalReturnsToThirtySeconds() { assertEquals(GpsAdaptiveToggleRules.Action.RESTORE_ACTIVE,r.cadenceAction(false,60_000L)) }
    @Test fun disabledAlreadyActiveNeedsNoUpdate() { assertEquals(GpsAdaptiveToggleRules.Action.NONE,r.cadenceAction(false,30_000L)) }
    @Test fun enabledQuietIntervalIsRetained() { assertEquals(GpsAdaptiveToggleRules.Action.NONE,r.cadenceAction(true,90_000L)) }
    @Test fun enabledProbeIntervalIsRetained() { assertEquals(GpsAdaptiveToggleRules.Action.NONE,r.cadenceAction(true,15_000L)) }
    @Test fun enabledActiveIntervalNeedsNoUpdate() { assertEquals(GpsAdaptiveToggleRules.Action.NONE,r.cadenceAction(true,30_000L)) }
    @Test fun turningOnOnlyRegistersMissingSensor() { assertEquals(GpsAdaptiveToggleRules.Action.START_SENSOR,r.sensorAction(true,false));assertEquals(GpsAdaptiveToggleRules.Action.NONE,r.cadenceAction(true,90_000L)) }
    @Test fun turningOffStopsSensorAndRestoresCadence() { assertEquals(GpsAdaptiveToggleRules.Action.STOP_SENSOR,r.sensorAction(false,true));assertEquals(GpsAdaptiveToggleRules.Action.RESTORE_ACTIVE,r.cadenceAction(false,90_000L)) }
    @Test fun turningOffAgainDoesNotReRegisterOrResubscribe() { assertEquals(GpsAdaptiveToggleRules.Action.NONE,r.sensorAction(false,false));assertEquals(GpsAdaptiveToggleRules.Action.NONE,r.cadenceAction(false,30_000L)) }
    @Test fun reEnablingSensorDoesNotChangeSavedLocationFix() { assertEquals(GpsAdaptiveToggleRules.Action.START_SENSOR,r.sensorAction(true,false));assertEquals(GpsAdaptiveToggleRules.Action.NONE,r.cadenceAction(true,GpsAdaptiveSampling.ACTIVE_INTERVAL_MS)) }
    @Test fun negativeSavedIntervalRecoveredToActive() { assertEquals(GpsAdaptiveToggleRules.Action.RESTORE_ACTIVE,r.cadenceAction(false,-1L)) }
}
