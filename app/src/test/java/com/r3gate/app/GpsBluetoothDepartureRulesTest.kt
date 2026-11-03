package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsBluetoothDepartureRulesTest {
    private val rules=GpsBluetoothDepartureRules

    @Test fun onlyExplicitlyApprovedDeviceCanTriggerVerification() {
        val empty=GpsBluetoothDepartureRules.State()
        assertEquals(empty,rules.connected(empty,"headphones","car",1000,true))
        assertEquals(empty,rules.connected(empty,"car","car",1000,false))
        val connected=rules.connected(empty,"car","car",1000,true)
        assertEquals("car",connected.deviceId)
        assertFalse(rules.shouldVerify(connected,20000,true))
        assertTrue(rules.shouldVerify(connected,21000,true))
        assertFalse(rules.shouldVerify(connected,21000,false))
    }

    @Test fun briefConnectionAndExpiredWindowDoNotTrigger() {
        val state=rules.connected(GpsBluetoothDepartureRules.State(),"car","car",1000,true)
        assertFalse(rules.shouldVerify(state,2000,true))
        assertFalse(rules.shouldVerify(state,200000,true))
        assertFalse(rules.shouldVerify(rules.disconnected(state,"car"),30000,true))
    }

    @Test fun notificationsHaveCooldownAndMustNotRepeat() {
        val state=rules.connected(GpsBluetoothDepartureRules.State(),"car","car",1000,true)
        val prompted=rules.prompted(state,21000)
        assertFalse(rules.shouldVerify(prompted,40000,true))
        assertEquals(prompted,rules.connected(prompted,"car","car",40000,true))
        val later=rules.connected(prompted,"car","car",621001,true)
        assertTrue(rules.shouldVerify(later,641001,true))
    }

    @Test fun bluetoothAloneCanNeverProveTripOrChangeWorkHours() {
        assertFalse(rules.travelCandidate(true,false,true))
        assertFalse(rules.travelCandidate(true,true,false))
        assertFalse(rules.travelCandidate(false,true,true))
        assertTrue(rules.travelCandidate(true,true,true))
    }
}
