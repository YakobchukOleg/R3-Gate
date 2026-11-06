package com.r3gate.app.backup

import com.r3gate.app.DriveHttpException
import org.junit.Assert.assertEquals
import org.junit.Test

class DriveBackupFailureTest {
    @Test fun storageQuotaIsNotARateLimit() {
        val reasons = DriveHttpException.parseReasons("""{"error":{"errors":[{"reason":"storageQuotaExceeded"}]}}""")
        assertEquals(DriveBackupFailure.STORAGE_QUOTA, DriveBackupFailure.from(DriveHttpException(403, reasons)))
    }

    @Test fun rateLimitUsesRetryForBothHttpStatuses() {
        for (error in listOf(DriveHttpException(429), DriveHttpException(403, setOf("rateLimitExceeded")),
            DriveHttpException(403, setOf("userRateLimitExceeded")))) {
            assertEquals(DriveBackupFailure.RATE_LIMIT, DriveBackupFailure.from(error))
        }
    }

    @Test fun unauthorizedRequiresAccount() =
        assertEquals(DriveBackupFailure.ACCOUNT, DriveBackupFailure.from(DriveHttpException(401)))

    @Test fun forbiddenWithoutQuotaIsRejected() =
        assertEquals(DriveBackupFailure.REJECTED, DriveBackupFailure.from(DriveHttpException(403)))

    @Test fun serverErrorsAndTimeoutsAreTemporary() {
        for (status in listOf(408, 500, 503)) {
            assertEquals(DriveBackupFailure.TEMPORARY, DriveBackupFailure.from(DriveHttpException(status)))
        }
    }

    @Test fun invalidErrorBodyKeepsStatusClassification() {
        val reasons = DriveHttpException.parseReasons("<html>unavailable</html>")
        assertEquals(emptySet<String>(), reasons)
        assertEquals(DriveBackupFailure.TEMPORARY, DriveBackupFailure.from(DriveHttpException(503, reasons)))
    }
}
