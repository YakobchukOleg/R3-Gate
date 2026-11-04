package com.r3gate.app

/**
 * Notification permission policy for GPS stage review on Android.
 * Tracking remains opt-in and never relies on notification permission to run.
 */
object CoreGpsNotificationPermissionRules {
    const val ANDROID_13_API = 33

    fun needsRequest(
        sdkInt: Int,
        gpsEnabled: Boolean,
        permissionGranted: Boolean,
        alreadyRequested: Boolean
    ): Boolean = sdkInt >= ANDROID_13_API &&
        gpsEnabled && !permissionGranted && !alreadyRequested

    fun showPromptButton(
        sdkInt: Int,
        gpsEnabled: Boolean,
        permissionGranted: Boolean
    ): Boolean = sdkInt >= ANDROID_13_API && gpsEnabled && !permissionGranted

    fun canNotify(sdkInt: Int, permissionGranted: Boolean): Boolean =
        sdkInt < ANDROID_13_API || permissionGranted
}
