package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsRecoveryMutedNotificationTest {
    private fun maySend(runtime:Boolean=true,granted:Boolean=true,
                        appEnabled:Boolean=true,channelEnabled:Boolean=true) =
        GpsRecoveryMonitorRules.shouldMarkAlertSent(runtime,granted,appEnabled,channelEnabled)

    @Test fun allowedAndroid13AppAndChannelCanNotify() {
        assertTrue(maySend())
    }
    @Test fun appNotificationsOffMustNotConsumeCooldown() {
        assertFalse(maySend(appEnabled=false))
    }
    @Test fun notificationChannelDisabledMustNotConsumeCooldown() {
        assertFalse(maySend(channelEnabled=false))
    }
    @Test fun bothNotificationControlsDisabledCannotNotify() {
        assertFalse(maySend(appEnabled=false,channelEnabled=false))
    }
    @Test fun runtimePermissionDeniedStillPreventsNotification() {
        assertFalse(maySend(granted=false))
    }
    @Test fun olderAndroidStillRespectsAppNotificationSetting() {
        assertFalse(maySend(runtime=false,granted=false,appEnabled=false))
    }
    @Test fun olderAndroidStillRespectsChannelSetting() {
        assertFalse(maySend(runtime=false,granted=false,channelEnabled=false))
    }
    @Test fun olderAndroidDoesNotRequireRuntimePermission() {
        assertTrue(maySend(runtime=false,granted=false))
    }
    @Test fun restoringChannelAllowsNextNotification() {
        assertFalse(maySend(channelEnabled=false))
        assertTrue(maySend(channelEnabled=true))
    }
    @Test fun restoringAppSettingsAllowsNextNotification() {
        assertFalse(maySend(appEnabled=false))
        assertTrue(maySend(appEnabled=true))
    }
}
