package com.r3gate.app

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkMeterHomeStateTest {
    @Test
    fun arrivalCopyRequiresCredibleGpsEvidence() {
        assertEquals("Wybierz cel", WorkMeterHomeState.TRAVEL.primaryActionLabel(false))
        assertEquals("Potwierdź przyjazd", WorkMeterHomeState.TRAVEL.primaryActionLabel(true))
        WorkMeterHomeState.entries.filter { it != WorkMeterHomeState.TRAVEL }.forEach {
            assertEquals(it.actionLabel, it.primaryActionLabel(false))
            assertEquals(it.actionLabel, it.primaryActionLabel(true))
        }
    }
    @Test
    fun selectsAnActionFromTheCurrentStage() {
        assertEquals(WorkMeterHomeState.CLOSED, WorkMeterHomeState.from(false, "Przejazd", "old"))
        assertEquals(WorkMeterHomeState.ON_SITE, WorkMeterHomeState.from(true, "", "legacy"))
        assertEquals(WorkMeterHomeState.ON_SITE, WorkMeterHomeState.from(true, "Obiekt", "site"))
        assertEquals(WorkMeterHomeState.ON_SITE, WorkMeterHomeState.from(true, "Biuro", "office"))
        assertEquals(WorkMeterHomeState.TRAVEL, WorkMeterHomeState.from(true, "Przejazd", ""))
        assertEquals(WorkMeterHomeState.SHOPPING, WorkMeterHomeState.from(true, "Zakupy", ""))
        assertEquals(WorkMeterHomeState.PERSONAL, WorkMeterHomeState.from(true, "Prywatnie", ""))
        assertEquals(WorkMeterHomeState.UNASSIGNED, WorkMeterHomeState.from(true, "Obiekt", ""))
        assertEquals(WorkMeterHomeState.UNASSIGNED, WorkMeterHomeState.from(true, "Nieprzypisane", "stale"))
    }

    @Test
    fun stageTimerHandlesLegacyAndInconsistentTimestamps() {
        assertEquals(50L, WorkMeterHomeState.stageElapsed(200, 100, 150))
        assertEquals(100L, WorkMeterHomeState.stageElapsed(200, 100, 0))
        assertEquals(100L, WorkMeterHomeState.stageElapsed(200, 100, 50))
        assertEquals(0L, WorkMeterHomeState.stageElapsed(200, 100, 250))
        assertEquals(0L, WorkMeterHomeState.stageElapsed(200, 250, 250))
        assertEquals(0L, WorkMeterHomeState.stageElapsed(200, 0, 100))
    }
}
