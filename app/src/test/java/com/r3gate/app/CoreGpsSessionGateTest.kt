package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class CoreGpsSessionGateTest {
    private fun snapshot(
        gps: Boolean = true,
        open: Boolean = true,
        kind: String = "Obiekt",
        start: Long = 1_000L,
        stage: Long = 1_100L,
        now: Long = 1_500L
    ) = CoreGpsSessionGate.Snapshot(gps,open,kind,start,stage,now)

    @Test fun currentWorksiteCanReceiveGpsPrompt() {
        assertTrue(CoreGpsSessionGate.isEligible(snapshot()))
    }

    @Test fun activeTravelCanReceiveGpsPrompt() {
        assertTrue(CoreGpsSessionGate.isEligible(snapshot(kind="Przejazd")))
    }

    @Test fun disabledGpsSuppressesWorksiteProposal() {
        assertFalse(CoreGpsSessionGate.isEligible(snapshot(gps=false)))
    }

    @Test fun disabledGpsSuppressesTravelProposal() {
        assertFalse(CoreGpsSessionGate.isEligible(snapshot(gps=false,kind="Przejazd")))
    }

    @Test fun closedShiftSuppressesAllGpsProposals() {
        assertFalse(CoreGpsSessionGate.isEligible(snapshot(open=false)))
    }

    @Test fun privateTimeCannotReceiveWorksiteProposal() {
        assertFalse(CoreGpsSessionGate.isEligible(snapshot(kind="Prywatnie")))
    }

    @Test fun shoppingStageCannotBeChangedByGps() {
        assertFalse(CoreGpsSessionGate.isEligible(snapshot(kind="Zakupy")))
    }

    @Test fun unattributedStageCannotBeChangedByGps() {
        assertFalse(CoreGpsSessionGate.isEligible(snapshot(kind="Nieprzypisane")))
    }

    @Test fun unexpectedStageCannotBeChangedByGps() {
        assertFalse(CoreGpsSessionGate.isEligible(snapshot(kind="Unknown")))
    }

    @Test fun missingShiftStartIsInvalid() {
        assertFalse(CoreGpsSessionGate.isEligible(snapshot(start=0,stage=1_000)))
    }

    @Test fun stageCannotStartBeforeWorkday() {
        assertFalse(CoreGpsSessionGate.isEligible(snapshot(start=1_000,stage=999)))
    }

    @Test fun futureDatedStageIsInvalid() {
        assertFalse(CoreGpsSessionGate.isEligible(snapshot(stage=2_000,now=1_500)))
    }

    @Test fun validStageAtShiftBoundaryIsAllowed() {
        assertTrue(CoreGpsSessionGate.isEligible(snapshot(stage=1_000)))
    }

    @Test fun stageAtCurrentTimeIsValid() {
        assertTrue(CoreGpsSessionGate.isEligible(snapshot(stage=1_500)))
    }

    @Test fun restoredTrackingSwitchOffPreventsDelayedNotifications() {
        val before = snapshot()
        val after = before.copy(gpsEnabled=false)
        assertTrue(CoreGpsSessionGate.isEligible(before))
        assertFalse(CoreGpsSessionGate.isEligible(after))
    }

    @Test fun restoredShiftClosurePreventsDelayedNotifications() {
        val before = snapshot(kind="Przejazd")
        val after = before.copy(shiftOpen=false)
        assertTrue(CoreGpsSessionGate.isEligible(before))
        assertFalse(CoreGpsSessionGate.isEligible(after))
    }

    @Test fun changingToShoppingInvalidatesEarlierStagePrompt() {
        assertTrue(CoreGpsSessionGate.isEligible(snapshot()))
        assertFalse(CoreGpsSessionGate.isEligible(snapshot(kind="Zakupy")))
    }
}
