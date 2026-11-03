package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsRecoveryAlertJournalTest {
    private val r=GpsRecoveryMonitorRules
    @Test fun mapsStalledService() { assertEquals("SERVICE_STALLED",r.alertJournalEvent(GpsRecoveryMonitorRules.Decision.ALERT)) }
    @Test fun mapsMissingFix() { assertEquals("GPS_NO_FIX_ALERT",r.alertJournalEvent(GpsRecoveryMonitorRules.Decision.ALERT_NO_FIX)) }
    @Test fun mapsPermissionFailure() { assertEquals("GPS_PERMISSION_ALERT",r.alertJournalEvent(GpsRecoveryMonitorRules.Decision.ALERT_PERMISSION)) }
    @Test fun mapsProviderDisabled() { assertEquals("GPS_PROVIDER_OFF_ALERT",r.alertJournalEvent(GpsRecoveryMonitorRules.Decision.ALERT_PROVIDER_OFF)) }
    @Test fun mapsProviderIncompatible() { assertEquals("GPS_PROVIDER_UNUSABLE_ALERT",r.alertJournalEvent(GpsRecoveryMonitorRules.Decision.ALERT_PROVIDER_UNUSABLE)) }
    @Test fun ignoresHealthy() { assertNull(r.alertJournalEvent(GpsRecoveryMonitorRules.Decision.HEALTHY)) }
    @Test fun ignoresSuppressed() { assertNull(r.alertJournalEvent(GpsRecoveryMonitorRules.Decision.IGNORE)) }
    @Test fun noJournalEntryWhenNotificationsBlocked() {
        val canPost=r.shouldMarkAlertSent(true,false)
        val event=if(canPost) r.alertJournalEvent(GpsRecoveryMonitorRules.Decision.ALERT_NO_FIX) else null
        assertNull(event)
    }
    @Test fun journalEntryWhenNotificationsGranted() {
        val canPost=r.shouldMarkAlertSent(true,true)
        val event=if(canPost) r.alertJournalEvent(GpsRecoveryMonitorRules.Decision.ALERT_NO_FIX) else null
        assertEquals("GPS_NO_FIX_ALERT",event)
    }
}
