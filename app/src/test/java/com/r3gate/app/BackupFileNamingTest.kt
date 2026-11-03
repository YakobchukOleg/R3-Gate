package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class BackupFileNamingTest {
    @Test fun localBackupIncludesDateAndTime() {
        assertEquals("WorkMeter-SOLO-v2.4-backup-2026-11-03_19-05-06.json",
            BackupFileNaming.fileName(LocalDateTime.of(2026,11,3,19,5,6)))
    }
    @Test fun dateMakesBackupsFromDifferentDaysDifferent() {
        val a=BackupFileNaming.fileName(LocalDateTime.of(2026,11,3,19,5,6))
        val b=BackupFileNaming.fileName(LocalDateTime.of(2026,11,4,19,5,6))
        assertNotEquals(a,b)
    }
    @Test fun timeMakesBackupsOnSameDayDifferent() {
        val a=BackupFileNaming.fileName(LocalDateTime.of(2026,11,3,19,5,6))
        val b=BackupFileNaming.fileName(LocalDateTime.of(2026,11,3,19,5,7))
        assertNotEquals(a,b)
    }
    @Test fun backupNameContainsSoloAndVersion() {
        val name=BackupFileNaming.fileName(LocalDateTime.of(2026,1,2,3,4,5),"3.1")
        assertTrue(name.startsWith("WorkMeter-SOLO-v3.1-"))
        assertTrue(name.endsWith(".json"))
    }
    @Test fun rejectsUnexpectedVersionCharactersInFilename() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupFileNaming.fileName(LocalDateTime.of(2026,1,2,3,4,5),"../bad")
        }
    }
}
