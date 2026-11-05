package com.r3gate.app.backup

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DriveBackupConcurrencyTest {
    @Test fun overlappingManualAndDailyJobsCannotBothEnter() {
        val entered = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val running = executor.submit<String?> {
                DriveBackupMaintenance.whileActive(UUID.randomUUID()) {
                    entered.countDown()
                    check(finish.await(5, TimeUnit.SECONDS))
                    "complete"
                }
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertNull(DriveBackupMaintenance.whileActive(UUID.randomUUID()) { error("Concurrent upload entered") })
            finish.countDown()
            assertEquals("complete", running.get(5, TimeUnit.SECONDS))
            assertEquals("next", DriveBackupMaintenance.whileActive(UUID.randomUUID()) { "next" })
        } finally {
            finish.countDown()
            executor.shutdownNow()
        }
    }

    @Test fun exceptionReleasesSlotForNextBackup() {
        assertThrows(IllegalStateException::class.java) {
            DriveBackupMaintenance.whileActive(UUID.randomUUID()) { error("Simulated upload failure") }
        }
        assertEquals(true, DriveBackupMaintenance.whileActive(UUID.randomUUID()) { true })
    }
}
