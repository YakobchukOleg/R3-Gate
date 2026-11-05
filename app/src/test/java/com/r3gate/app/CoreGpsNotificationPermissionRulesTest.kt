package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class CoreGpsNotificationPermissionRulesTest {
    private fun request(sdk:Int=33, enabled:Boolean=true,
                        granted:Boolean=false, asked:Boolean=false) =
        CoreGpsNotificationPermissionRules.needsRequest(sdk,enabled,granted,asked)
    private fun button(sdk:Int=33, enabled:Boolean=true, granted:Boolean=false) =
        CoreGpsNotificationPermissionRules.showPromptButton(sdk,enabled,granted)

    @Test fun android13RequestsWhenGpsEnabledAndNotYetAsked() {
        assertTrue(request())
    }
    @Test fun android14RequestsWhenGpsEnabledAndNotYetAsked() {
        assertTrue(request(sdk=34))
    }
    @Test fun android15RequestsWhenGpsEnabledAndNotYetAsked() {
        assertTrue(request(sdk=35))
    }
    @Test fun android12DoesNotRequestRuntimeNotifications() {
        assertFalse(request(sdk=32))
    }
    @Test fun android11DoesNotRequestRuntimeNotifications() {
        assertFalse(request(sdk=30))
    }
    @Test fun disabledGpsDoesNotPromptUser() {
        assertFalse(request(enabled=false))
    }
    @Test fun alreadyGrantedPermissionIsNotRequestedAgain() {
        assertFalse(request(granted=true))
    }
    @Test fun priorRejectionDoesNotTriggerRepeatedPrompt() {
        assertFalse(request(asked=true))
    }
    @Test fun priorApprovalDoesNotTriggerRepeatedPrompt() {
        assertFalse(request(granted=true,asked=true))
    }
    @Test fun autoPromptIsOnlyTriggeredOncePerGpsEnableAttempt() {
        assertTrue(request())
        assertFalse(request(asked=true))
    }
    @Test fun settingsButtonAvailableAfterPermissionDenial() {
        assertTrue(button())
    }
    @Test fun settingsButtonAvailableEvenIfPreviouslyRequested() {
        assertTrue(button(sdk=35))
    }
    @Test fun settingsButtonHiddenOncePermissionGranted() {
        assertFalse(button(granted=true))
    }
    @Test fun settingsButtonHiddenWhileGpsDisabled() {
        assertFalse(button(enabled=false))
    }
    @Test fun settingsButtonHiddenOnAndroid12() {
        assertFalse(button(sdk=32))
    }
    @Test fun notificationWithoutRuntimePermissionIsAllowedOnAndroid12() {
        assertTrue(CoreGpsNotificationPermissionRules.canNotify(32,false))
    }
    @Test fun notificationWithRuntimePermissionIsAllowedOnAndroid13() {
        assertTrue(CoreGpsNotificationPermissionRules.canNotify(33,true))
    }
    @Test fun notificationWithoutRuntimePermissionIsBlockedOnAndroid13() {
        assertFalse(CoreGpsNotificationPermissionRules.canNotify(33,false))
    }
    @Test fun notificationWithoutRuntimePermissionIsBlockedOnAndroid15() {
        assertFalse(CoreGpsNotificationPermissionRules.canNotify(35,false))
    }
    @Test fun runtimePermissionGrantedStillAllowsNotificationsOnAndroid12() {
        assertTrue(CoreGpsNotificationPermissionRules.canNotify(32,true))
    }
    @Test fun sdkBoundaryIsExactlyApi33() {
        assertFalse(request(sdk=32))
        assertTrue(request(sdk=33))
    }
    @Test fun gpsRestartDoesNotForcePromptWhenAlreadyRequested() {
        assertFalse(request(enabled=true,asked=true))
    }
}
