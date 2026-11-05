package com.r3gate.app.backup

import com.r3gate.app.DriveHttpException

internal enum class DriveBackupFailure {
    ACCOUNT, STORAGE_QUOTA, RATE_LIMIT, REJECTED, TEMPORARY;

    companion object {
        fun from(failure: DriveHttpException): DriveBackupFailure = when {
            failure.status == 401 -> ACCOUNT
            failure.status == 403 && "storageQuotaExceeded" in failure.reasons -> STORAGE_QUOTA
            failure.status == 429 || failure.status == 403 && failure.reasons.any {
                it == "rateLimitExceeded" || it == "userRateLimitExceeded"
            } -> RATE_LIMIT
            failure.status in 400..499 && failure.status != 408 -> REJECTED
            else -> TEMPORARY
        }
    }
}
