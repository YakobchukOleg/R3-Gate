package com.r3gate.app

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkMeterDestinationTest {
    @Test fun keepsObjectContextForNestedRecordLists() {
        assertEquals(WorkMeterDestination.OBJECTS, WorkMeterDestination.forScreen("object:site"))
        assertEquals(WorkMeterDestination.OBJECTS, WorkMeterDestination.forScreen("list:materials:site"))
        assertEquals(WorkMeterDestination.MORE, WorkMeterDestination.forScreen("list:materials:"))
    }

    @Test fun classifiesProtectedAndTimeScreens() {
        assertEquals(WorkMeterDestination.FINANCE, WorkMeterDestination.forScreen("wallet"))
        assertEquals(WorkMeterDestination.FINANCE, WorkMeterDestination.forScreen("payment"))
        assertEquals(WorkMeterDestination.TIME, WorkMeterDestination.forScreen("shift:123"))
        assertEquals(WorkMeterDestination.START, WorkMeterDestination.forScreen("home"))
    }
}
